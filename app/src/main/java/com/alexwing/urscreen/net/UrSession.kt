package com.alexwing.urscreen.net

import com.alexwing.urscreen.protocol.UrAction
import com.alexwing.urscreen.protocol.UrFrame
import com.alexwing.urscreen.protocol.UrMessages
import com.alexwing.urscreen.protocol.UrObject
import com.alexwing.urscreen.protocol.UrProtocolException
import com.alexwing.urscreen.protocol.UrSecurity
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.CRC32

open class UrAuthException(message: String) : IOException(message)

/** El servidor está en modo «usuarios»: además de la contraseña hay que dar un nombre de usuario. */
class UrUserRequiredException : UrAuthException("el servidor pide usuario y contraseña")

/**
 * Prueba de autenticación que se manda tras el handshake. Es la fórmula de la interfaz web del propio servidor
 * (`Manager/ur/ur.client.js`): SHA-256, sobre el texto en UTF-8 y en hexadecimal minúsculas, de
 * `nonce del servidor + contraseña + nonce del cliente`, y en modo usuarios de
 * `nonce del servidor + usuario + ":" + contraseña + nonce del cliente`.
 *
 * @throws UrUserRequiredException si el servidor trabaja con usuarios y no se dio ninguno.
 */
internal fun authProof(security: Int, serverNonce: String, clientNonce: String, password: String, username: String?): String =
    when (security) {
        UrSecurity.INVALID -> throw UrAuthException("el servidor rechazó el handshake")
        UrSecurity.ENABLE -> throw UrAuthException("el servidor exige cifrado, que este cliente no soporta")
        UrSecurity.USERS -> {
            val user = username ?: throw UrUserRequiredException()
            sha256Hex(serverNonce + user + ":" + password + clientNonce)
        }
        UrSecurity.ANONYMOUS, UrSecurity.DISABLED -> sha256Hex(serverNonce + password + clientNonce)
        else -> throw UrProtocolException("modo de seguridad desconocido: $security")
    }

private fun sha256Hex(text: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

/** Un fotograma con el tiempo que tardó el servidor en entregarlo desde que se pidió. */
class TimedFrame(val frame: UrFrame, val roundTripMs: Long, val monitor: Int = 0)

/**
 * Sesión con un servidor de Unified Remote: handshake, autenticación, carga de `Unified.Screen`,
 * bucle de fotogramas y ratón.
 *
 * @param deviceId identificador del cliente, con la forma `android-<16 hex>`; el servidor lo usa como
 *   nombre de la conexión y para enrutar las respuestas.
 */
class UrSession(
    private val connection: UrConnection,
    private val deviceId: String,
    private val password: String = "",
    private val username: String? = null,
    private val remote: String = UrMessages.SCREEN_REMOTE,
) {
    /** Nombre que da el servidor de sí mismo (el del PC), tras [connect]. */
    var serverName: String? = null
        private set

    /** Handshake, autenticación y carga del remote. Deja la sesión lista para pedir fotogramas. */
    suspend fun connect() {
        val clientNonce = UUID.randomUUID().toString()
        connection.send(UrMessages.handshake(deviceId, clientNonce))
        val hello = awaitPacket("handshake") { it.int("Action") == UrAction.HANDSHAKE }
        serverName = hello.string("Source")

        val proof = authProof(
            security = hello.int("Security") ?: UrSecurity.DISABLED,
            serverNonce = hello.string("Password").orEmpty(),
            clientNonce = clientNonce,
            password = password,
            username = username,
        )

        connection.send(UrMessages.authenticate(deviceId, proof))
        val auth = awaitPacket("autenticación") { it.int("Action") == UrAction.AUTHENTICATE }
        if (auth.int("Security") == UrSecurity.INVALID) throw UrAuthException("contraseña incorrecta")

        connection.send(UrMessages.hash(deviceId))
        awaitPacket("hash") { it.int("Action") == UrAction.HASH }

        connection.send(UrMessages.load(deviceId, remote))
        awaitPacket("carga de $remote") { it.int("Action") == UrAction.LOAD && it.string("ID") == remote }
        connection.send(UrMessages.state(deviceId, remote))
    }

    /**
     * Bucle de fotogramas. Mantiene [inFlight] peticiones en el aire: en cuanto llega un fotograma se
     * pide otro, así que el servidor captura y codifica el siguiente mientras el anterior viaja por la
     * red. Con una sola en vuelo, captura, codificación y transferencia van una detrás de otra y el
     * ritmo es la suma de las tres; con dos, es la más lenta de ellas.
     *
     * Nunca hay más de [inFlight] fotogramas pendientes, así que si el cliente va lento no se acumula
     * retraso: el `conflate` descarta los viejos.
     *
     * @param minIntervalMs tiempo mínimo entre peticiones; 0 pide el siguiente sin esperar.
     */
    fun frames(monitor: Int = 0, minIntervalMs: Long = 0, inFlight: Int = DEFAULT_IN_FLIGHT): Flow<TimedFrame> =
        frames({ monitor }, minIntervalMs, inFlight)

    /**
     * Como [frames], pero el monitor se consulta en cada petición, así que se puede cambiar sin reiniciar el
     * bucle. Los fotogramas que ya estaban pedidos del monitor anterior se descartan al llegar.
     */
    fun frames(monitor: () -> Int, minIntervalMs: Long = 0, inFlight: Int = DEFAULT_IN_FLIGHT): Flow<TimedFrame> = flow {
        require(inFlight >= 1) { "inFlight debe ser al menos 1" }
        class Pending(val monitor: Int, val sentAt: Long)
        val pending = ArrayDeque<Pending>()
        suspend fun request() {
            val m = monitor()
            connection.send(UrMessages.update(deviceId, m))
            pending.addLast(Pending(m, System.nanoTime()))
        }
        var lastArrival = 0L
        while (true) {
            // Mantener [inFlight] peticiones en el aire, salvo en pausa.
            while (pending.size < inFlight && !paused) request()
            if (pending.isEmpty()) {
                wake.receive() // en pausa y sin nada pendiente: esperar a que se reanude
                continue
            }
            val frame = awaitFrame()
            val arrival = System.nanoTime()
            val asked = pending.removeFirst()
            // Tiempo de servicio de este fotograma: desde que el servidor pudo empezarlo (cuando se pidió, o
            // cuando terminó el anterior si estaba ocupado) hasta que llegó. Con varias en vuelo, medir desde
            // que se pidió incluiría la espera detrás del fotograma anterior.
            val roundTripMs = (arrival - maxOf(asked.sentAt, lastArrival)) / 1_000_000
            lastArrival = arrival
            pacer.onFrame(frameHash(frame.jpeg))
            if (!paused && asked.monitor == monitor()) emit(TimedFrame(frame, roundTripMs, asked.monitor))
            // Si la pantalla no cambia se pide menos (cada fotograma cuesta al servidor un objeto GDI que no
            // libera); una acción del usuario corta la espera.
            val wait = maxOf(minIntervalMs - roundTripMs, pacer.waitMs(nowMs()))
            if (wait > 0) withTimeoutOrNull(wait) { wake.receive() }
        }
    }.conflate() // la red sigue leyendo mientras el colector decodifica; si va lento, se descartan fotogramas viejos

    /** Ritmo de peticiones: se frena con una pantalla quieta y vuelve a tope con la actividad del usuario. */
    private val pacer = FramePacer()
    private val wake = Channel<Unit>(Channel.CONFLATED)

    @Volatile
    private var paused = false

    /** El usuario actuó (toque, tecla, rueda): los fotogramas vuelven a pedirse a ritmo máximo un rato. */
    fun noteActivity() {
        pacer.noteActivity(nowMs())
        wake.trySend(Unit)
    }

    /** En pausa (app en segundo plano) no se piden fotogramas; al reanudar se retoma el bucle. */
    fun setPaused(value: Boolean) {
        paused = value
        wake.trySend(Unit)
    }

    private fun nowMs() = System.nanoTime() / 1_000_000

    /** Identifica el contenido de un fotograma: dos JPEG iguales dan el mismo valor. */
    private fun frameHash(jpeg: ByteArray): Long {
        val crc = CRC32().also { it.update(jpeg) }.value
        return (crc shl 32) xor jpeg.size.toLong()
    }

    /**
     * Pide un solo fotograma con parámetros explícitos y espera su respuesta. No debe mezclarse con
     * [frames] en la misma sesión, porque ambos leen del mismo socket.
     */
    suspend fun requestFrame(
        monitor: Int = 0,
        x: Int = 0,
        y: Int = 0,
        w: Int = 0,
        h: Int = 0,
        onlyChanges: Boolean = false,
    ): UrFrame {
        connection.send(UrMessages.update(deviceId, monitor, x, y, w, h, onlyChanges))
        return awaitFrame()
    }

    /** Mueve el cursor a (x, y), en píxeles del monitor `display`, y hace clic. */
    suspend fun click(x: Int, y: Int, display: Int = 0, button: String = "Left") {
        connection.send(UrMessages.moveTo(deviceId, x, y, display))
        connection.send(UrMessages.click(deviceId, button))
    }

    /** Envía paquetes ya construidos, en orden y sin que otro envío se cuele en medio. */
    suspend fun sendAll(packets: List<UrObject>) = connection.sendAll(packets)

    /** Mensajes de entrada ya listos para enviar con [sendAll]; llevan el identificador de este cliente. */
    fun keyDownPacket(key: String) = UrMessages.keyDown(deviceId, key)
    fun keyUpPacket(key: String) = UrMessages.keyUp(deviceId, key)
    fun mouseDownPacket(button: String) = UrMessages.mouseDown(deviceId, button)
    fun mouseUpPacket(button: String) = UrMessages.mouseUp(deviceId, button)
    fun scrollPacket(amount: Int) = UrMessages.scroll(deviceId, amount)
    fun moveToPacket(x: Int, y: Int, display: Int) = UrMessages.moveTo(deviceId, x, y, display)
    fun clickPacket(button: String) = UrMessages.click(deviceId, button)

    private val _monitorCount = MutableStateFlow<Int?>(null)

    /** Número de monitores que anunció el servidor (llega tras cargar el remote), o `null` si aún no. */
    val monitorCount: StateFlow<Int?> = _monitorCount.asStateFlow()

    private suspend fun awaitFrame(): UrFrame {
        repeat(MAX_FOREIGN_PACKETS) {
            val packet = connection.receive()
            note(packet)
            UrMessages.parseFrame(packet)?.let { return it }
        }
        throw UrProtocolException("el servidor no envió ningún fotograma")
    }

    private suspend fun awaitPacket(what: String, match: (UrObject) -> Boolean): UrObject {
        repeat(MAX_FOREIGN_PACKETS) {
            val packet = connection.receive()
            note(packet)
            if (match(packet)) return packet
        }
        throw UrProtocolException("no llegó la respuesta esperada: $what")
    }

    /** Recoge de cualquier paquete que pase los datos que no pertenecen a la respuesta esperada. */
    private fun note(packet: UrObject) {
        UrMessages.parseMonitorCount(packet)?.let { _monitorCount.value = it }
    }

    companion object {
        /** Peticiones de fotograma simultáneas por defecto; ver [frames]. */
        const val DEFAULT_IN_FLIGHT = 2

        /** Paquetes que no son el esperado (acuses, estado de monitores...) que se toleran antes de rendirse. */
        private const val MAX_FOREIGN_PACKETS = 256
    }
}
