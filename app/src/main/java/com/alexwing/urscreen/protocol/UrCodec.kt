package com.alexwing.urscreen.protocol

import java.io.ByteArrayOutputStream
import java.io.IOException

class UrProtocolException(message: String) : IOException(message)

/**
 * Codec del formato binario del puerto TCP 9800, deducido de capturas reales (ver README).
 *
 * ```
 * [longitud: 4 bytes big-endian = bytes que siguen a este campo]
 * [flags: 00][versión: 01]
 * [campo]... [00]                      <- objeto raíz, terminado en 00
 *
 * campo = [tipo][clave\0][valor]
 *   02 objeto   campos... 00
 *   03 entero   4 bytes big-endian con signo
 *   04 booleano 1 byte
 *   05 texto    UTF-8 terminado en 00
 *   06 lista    elementos "02 00 campos... 00" y un 00 final
 *   07 binario  longitud (4 bytes big-endian) + datos
 *   08 byte     1 byte sin signo
 * ```
 *
 * El byte de flags fue siempre 00 en las capturas; si algún día indica compresión o cifrado, se rechaza
 * con un error claro en vez de interpretar basura.
 */
object UrCodec {
    private const val T_END = 0
    private const val T_OBJ = 2
    private const val T_INT = 3
    private const val T_BOOL = 4
    private const val T_STR = 5
    private const val T_LIST = 6
    private const val T_BIN = 7
    private const val T_BYTE = 8

    const val FLAGS = 0
    const val VERSION = 1

    /** Tope de seguridad para el cuerpo de un paquete: un fotograma 4K en JPEG pesa bastante menos. */
    const val MAX_BODY = 32 * 1024 * 1024

    /** Devuelve el paquete completo, con el prefijo de longitud. */
    fun encode(root: UrObject): ByteArray {
        val body = ByteArrayOutputStream(512)
        body.write(FLAGS)
        body.write(VERSION)
        writeFields(body, root)
        body.write(T_END)
        val bytes = body.toByteArray()
        val out = ByteArray(4 + bytes.size)
        writeInt(out, 0, bytes.size)
        System.arraycopy(bytes, 0, out, 4, bytes.size)
        return out
    }

    /** Decodifica un paquete completo, con el prefijo de longitud. */
    fun decode(packet: ByteArray): UrObject {
        if (packet.size < 4) throw UrProtocolException("paquete de ${packet.size} bytes: falta la longitud")
        val declared = readInt(packet, 0)
        if (declared != packet.size - 4) {
            throw UrProtocolException("la longitud declarada ($declared) no coincide con los ${packet.size - 4} bytes recibidos")
        }
        return decodeBody(packet.copyOfRange(4, packet.size))
    }

    /** Decodifica el cuerpo de un paquete: lo que sigue al prefijo de longitud. */
    fun decodeBody(body: ByteArray): UrObject {
        if (body.size < 3) throw UrProtocolException("cuerpo demasiado corto: ${body.size} bytes")
        if (body[0].toInt() != FLAGS) {
            throw UrProtocolException("flags ${body[0].toInt() and 0xFF} no soportados (¿compresión o cifrado?)")
        }
        if (body[1].toInt() != VERSION) {
            throw UrProtocolException("versión de formato ${body[1].toInt() and 0xFF} no soportada")
        }
        val reader = Reader(body, 2)
        val root = readFields(reader)
        if (reader.pos != body.size) {
            throw UrProtocolException("sobran ${body.size - reader.pos} bytes tras el objeto raíz")
        }
        return root
    }

    // --- escritura ---

    private fun writeFields(out: ByteArrayOutputStream, obj: UrObject) {
        for ((key, value) in obj.fields) writeField(out, key, value)
    }

    private fun writeField(out: ByteArrayOutputStream, key: String, value: UrValue) {
        when (value) {
            is UrObject -> {
                header(out, T_OBJ, key)
                writeFields(out, value)
                out.write(T_END)
            }
            is UrInt -> {
                header(out, T_INT, key)
                writeInt(out, value.value)
            }
            is UrByte -> {
                header(out, T_BYTE, key)
                out.write(value.value)
            }
            is UrBool -> {
                header(out, T_BOOL, key)
                out.write(if (value.value) 1 else 0)
            }
            is UrString -> {
                header(out, T_STR, key)
                writeCString(out, value.value)
            }
            is UrBinary -> {
                header(out, T_BIN, key)
                writeInt(out, value.bytes.size)
                out.write(value.bytes)
            }
            is UrList -> {
                header(out, T_LIST, key)
                for (item in value.items) {
                    out.write(T_OBJ)
                    out.write(0) // clave vacía del elemento
                    writeFields(out, item)
                    out.write(T_END)
                }
                out.write(T_END)
            }
        }
    }

    private fun header(out: ByteArrayOutputStream, type: Int, key: String) {
        out.write(type)
        writeCString(out, key)
    }

    private fun writeCString(out: ByteArrayOutputStream, s: String) {
        val bytes = s.toByteArray(Charsets.UTF_8)
        require(bytes.none { it.toInt() == 0 }) { "el texto contiene un byte 00" }
        out.write(bytes)
        out.write(0)
    }

    private fun writeInt(out: ByteArrayOutputStream, v: Int) {
        out.write(v ushr 24)
        out.write(v ushr 16)
        out.write(v ushr 8)
        out.write(v)
    }

    private fun writeInt(dst: ByteArray, at: Int, v: Int) {
        dst[at] = (v ushr 24).toByte()
        dst[at + 1] = (v ushr 16).toByte()
        dst[at + 2] = (v ushr 8).toByte()
        dst[at + 3] = v.toByte()
    }

    private fun readInt(src: ByteArray, at: Int): Int =
        ((src[at].toInt() and 0xFF) shl 24) or
            ((src[at + 1].toInt() and 0xFF) shl 16) or
            ((src[at + 2].toInt() and 0xFF) shl 8) or
            (src[at + 3].toInt() and 0xFF)

    // --- lectura ---

    private class Reader(val b: ByteArray, var pos: Int) {
        fun u8(): Int {
            if (pos >= b.size) throw UrProtocolException("paquete truncado en $pos")
            return b[pos++].toInt() and 0xFF
        }

        fun i32(): Int {
            if (pos + 4 > b.size) throw UrProtocolException("paquete truncado en $pos (entero)")
            val v = ((b[pos].toInt() and 0xFF) shl 24) or ((b[pos + 1].toInt() and 0xFF) shl 16) or
                ((b[pos + 2].toInt() and 0xFF) shl 8) or (b[pos + 3].toInt() and 0xFF)
            pos += 4
            return v
        }

        fun cstring(): String {
            var end = pos
            while (end < b.size && b[end].toInt() != 0) end++
            if (end >= b.size) throw UrProtocolException("texto sin terminar en $pos")
            val s = String(b, pos, end - pos, Charsets.UTF_8)
            pos = end + 1
            return s
        }

        fun bytes(n: Int): ByteArray {
            if (n < 0 || pos + n > b.size) throw UrProtocolException("bloque binario de $n bytes fuera del paquete en $pos")
            val out = b.copyOfRange(pos, pos + n)
            pos += n
            return out
        }
    }

    private fun readFields(r: Reader): UrObject {
        val fields = LinkedHashMap<String, UrValue>()
        while (true) {
            val type = r.u8()
            if (type == T_END) return UrObject(fields)
            val key = r.cstring()
            fields[key] = when (type) {
                T_OBJ -> readFields(r)
                T_INT -> UrInt(r.i32())
                T_BYTE -> UrByte(r.u8())
                T_BOOL -> UrBool(r.u8() != 0)
                T_STR -> UrString(r.cstring())
                T_BIN -> UrBinary(r.bytes(r.i32()))
                T_LIST -> readList(r)
                else -> throw UrProtocolException("tipo de campo desconocido $type (clave \"$key\")")
            }
        }
    }

    private fun readList(r: Reader): UrList {
        val items = mutableListOf<UrObject>()
        while (true) {
            val type = r.u8()
            if (type == T_END) return UrList(items)
            if (type != T_OBJ) throw UrProtocolException("elemento de lista de tipo $type: solo se esperan objetos")
            r.cstring() // clave vacía
            items += readFields(r)
        }
    }
}
