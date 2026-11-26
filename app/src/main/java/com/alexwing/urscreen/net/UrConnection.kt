package com.alexwing.urscreen.net

import com.alexwing.urscreen.protocol.UrCodec
import com.alexwing.urscreen.protocol.UrObject
import com.alexwing.urscreen.protocol.UrProtocolException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Socket TCP con el framing de Unified Remote: cada paquete lleva delante su longitud en 4 bytes
 * big-endian. Las lecturas bloquean en un hilo de E/S y no se cancelan con la corrutina: para cortar una
 * lectura en curso hay que llamar a [close].
 */
class UrConnection(
    private val host: String,
    private val port: Int = DEFAULT_PORT,
    private val connectTimeoutMs: Int = 5_000,
    private val readTimeoutMs: Int = 15_000,
) : Closeable {

    private val socket = Socket()
    private lateinit var input: DataInputStream
    private lateinit var output: OutputStream
    private val writeLock = Mutex()

    suspend fun open() = withContext(Dispatchers.IO) {
        socket.tcpNoDelay = true
        socket.soTimeout = readTimeoutMs
        socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
        input = DataInputStream(BufferedInputStream(socket.getInputStream(), 64 * 1024))
        output = BufferedOutputStream(socket.getOutputStream(), 8 * 1024)
    }

    /** Envía un paquete. Se puede llamar desde varias corrutinas a la vez (el clic y el bucle de fotogramas). */
    suspend fun send(packet: UrObject) {
        val bytes = UrCodec.encode(packet)
        writeLock.withLock {
            withContext(Dispatchers.IO) {
                output.write(bytes)
                output.flush()
            }
        }
    }

    /** Envía varios paquetes seguidos, sin que otro envío se intercale entre ellos. */
    suspend fun sendAll(packets: List<UrObject>) {
        val encoded = packets.map { UrCodec.encode(it) }
        writeLock.withLock {
            withContext(Dispatchers.IO) {
                for (bytes in encoded) output.write(bytes)
                output.flush()
            }
        }
    }

    /** Lee el siguiente paquete completo del servidor. Solo debe leer una corrutina a la vez. */
    suspend fun receive(): UrObject = withContext(Dispatchers.IO) {
        val length = input.readInt()
        if (length < 3 || length > UrCodec.MAX_BODY) {
            throw UrProtocolException("longitud de paquete absurda: $length")
        }
        val body = ByteArray(length)
        input.readFully(body)
        UrCodec.decodeBody(body)
    }

    override fun close() {
        runCatching { socket.close() }
    }

    companion object {
        const val DEFAULT_PORT = 9800
    }
}
