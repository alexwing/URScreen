package com.alexwing.urscreen.protocol

/**
 * Valores del formato binario del puerto 9800 de Unified Remote.
 *
 * Cada campo en el cable es `[tipo: 1 byte][clave: texto UTF-8 terminado en 00][valor]`; el tipo decide
 * qué subclase es. Ver [UrCodec] para la tabla completa.
 */
sealed interface UrValue

/** Tipo 03: entero de 4 bytes big-endian con signo (`Version`, `X`, `Y`, `Hash`...). */
data class UrInt(val value: Int) : UrValue

/** Tipo 08: entero de 1 byte sin signo (`Action`, `Request`, `Response`, `Security`, `Type`). */
data class UrByte(val value: Int) : UrValue {
    init {
        require(value in 0..255) { "byte fuera de rango: $value" }
    }
}

/** Tipo 04: booleano de 1 byte. */
data class UrBool(val value: Boolean) : UrValue

/** Tipo 05: texto UTF-8 terminado en 00. */
data class UrString(val value: String) : UrValue

/** Tipo 07: bloque binario con longitud de 4 bytes (`Image`, con el JPEG dentro). */
class UrBinary(val bytes: ByteArray) : UrValue {
    override fun equals(other: Any?) = other is UrBinary && bytes.contentEquals(other.bytes)
    override fun hashCode() = bytes.contentHashCode()
    override fun toString() = "UrBinary(${bytes.size} bytes)"
}

/** Tipo 06: lista de objetos (cada elemento va como `02 00 campos... 00`) terminada en 00. */
data class UrList(val items: List<UrObject>) : UrValue

/**
 * Tipo 02: objeto, o raíz del paquete. Conserva el orden de los campos, porque el servidor responde con
 * un orden propio y así los paquetes recodificados salen byte a byte iguales.
 */
class UrObject(val fields: LinkedHashMap<String, UrValue> = LinkedHashMap()) : UrValue {
    operator fun get(key: String): UrValue? = fields[key]

    fun string(key: String): String? = (fields[key] as? UrString)?.value

    /** Acepta tanto el entero de 4 bytes como el de 1 byte: el servidor usa ambos según el campo. */
    fun int(key: String): Int? = when (val v = fields[key]) {
        is UrInt -> v.value
        is UrByte -> v.value
        else -> null
    }

    fun bool(key: String): Boolean? = (fields[key] as? UrBool)?.value
    fun obj(key: String): UrObject? = fields[key] as? UrObject
    fun list(key: String): List<UrObject>? = (fields[key] as? UrList)?.items
    fun binary(key: String): ByteArray? = (fields[key] as? UrBinary)?.bytes

    override fun equals(other: Any?) = other is UrObject && fields == other.fields
    override fun hashCode() = fields.hashCode()
    override fun toString() = fields.toString()
}

/** Constructor con DSL para armar paquetes en el orden de campos que se quiera. */
class UrObjectBuilder {
    private val fields = LinkedHashMap<String, UrValue>()

    fun byte(key: String, value: Int) { fields[key] = UrByte(value) }
    fun int(key: String, value: Int) { fields[key] = UrInt(value) }
    fun bool(key: String, value: Boolean) { fields[key] = UrBool(value) }
    fun str(key: String, value: String) { fields[key] = UrString(value) }
    fun bin(key: String, value: ByteArray) { fields[key] = UrBinary(value) }

    fun obj(key: String, block: UrObjectBuilder.() -> Unit) {
        fields[key] = urObject(block)
    }

    fun list(key: String, block: UrListBuilder.() -> Unit) {
        fields[key] = UrList(UrListBuilder().apply(block).items)
    }

    internal fun build() = UrObject(fields)
}

class UrListBuilder {
    internal val items = mutableListOf<UrObject>()

    fun item(block: UrObjectBuilder.() -> Unit) {
        items += urObject(block)
    }
}

fun urObject(block: UrObjectBuilder.() -> Unit): UrObject = UrObjectBuilder().apply(block).build()
