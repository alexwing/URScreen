package com.alexwing.urscreen.protocol

/** Valores del campo `Action`, comunes a la web y al TCP. */
object UrAction {
    const val HANDSHAKE = 0
    const val AUTHENTICATE = 1
    const val LOAD = 3
    const val UNLOAD = 4
    const val STATE = 5
    const val ACTION = 7
    const val LAYOUT = 8
    const val HASH = 10
}

/** Valores del campo `Security` en la respuesta al handshake y a la autenticación. */
object UrSecurity {
    const val ENABLE = 0
    const val DISABLED = 1
    const val INVALID = 2
    const val ANONYMOUS = 3
    const val USERS = 4
}

/** Fotograma que devuelve `Unified.Screen`: un JPEG y la posición del cursor. */
class UrFrame(
    val jpeg: ByteArray,
    /** Origen y tamaño de la zona capturada, en píxeles del escritorio. */
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val cursorX: Int?,
    val cursorY: Int?,
)

/**
 * Constructores de los paquetes que envía el cliente. Los campos van en orden alfabético, como hace la
 * app oficial, y los tests comprueban que salen byte a byte iguales a los capturados.
 */
object UrMessages {
    const val PROTOCOL_VERSION = 10
    const val SCREEN_REMOTE = "Unified.Screen"
    const val INPUT_REMOTE = "Core.Input"

    fun handshake(source: String, clientNonce: String) = urObject {
        byte("Action", UrAction.HANDSHAKE)
        str("Password", clientNonce)
        str("Platform", "android")
        byte("Request", UrAction.HANDSHAKE)
        str("Source", source)
        int("Version", PROTOCOL_VERSION)
    }

    fun authenticate(source: String, passwordHash: String) = urObject {
        byte("Action", UrAction.AUTHENTICATE)
        obj("Capabilities") {
            bool("Actions", true)
            bool("Encryption2", true)
            bool("Fast", false)
            bool("Grid", true)
            bool("Loading", true)
            bool("Sync", true)
        }
        str("Password", passwordHash)
        byte("Request", UrAction.AUTHENTICATE)
        str("Source", source)
    }

    fun hash(source: String) = urObject {
        byte("Action", UrAction.HASH)
        byte("Request", UrAction.HASH)
        str("Source", source)
    }

    fun load(source: String, remoteId: String) = urObject {
        byte("Action", UrAction.LOAD)
        str("ID", remoteId)
        obj("Layout") {}
        byte("Request", UrAction.LOAD)
        str("Source", source)
    }

    fun state(source: String, remoteId: String) = urObject {
        byte("Action", UrAction.STATE)
        str("ID", remoteId)
        byte("Request", UrAction.STATE)
        str("Source", source)
    }

    /**
     * Ejecuta una acción de un remote. La app oficial repite la acción dentro de `Layout.Controls` y en
     * `Run`; `target` es el remote destino cuando no es el propio (el ratón va a `Core.Input`).
     */
    fun runAction(
        source: String,
        remoteId: String,
        name: String,
        target: String?,
        values: List<Pair<String, String>>,
    ) = urObject {
        byte("Action", UrAction.ACTION)
        str("ID", remoteId)
        obj("Layout") {
            list("Controls") {
                item {
                    obj("OnAction") {
                        extras(values)
                        str("Name", name)
                        if (target != null) str("Target", target)
                    }
                    byte("Type", 8)
                }
            }
        }
        byte("Request", UrAction.ACTION)
        obj("Run") {
            extras(values)
            str("Name", name)
            if (target != null) str("Target", target)
        }
        str("Source", source)
    }

    /** Pide un fotograma. Con `x`, `y`, `w` y `h` a 0 el servidor devuelve el monitor entero. */
    fun update(
        source: String,
        monitor: Int = 0,
        x: Int = 0,
        y: Int = 0,
        w: Int = 0,
        h: Int = 0,
        onlyChanges: Boolean = false,
    ) = runAction(
        source, SCREEN_REMOTE, "update", null,
        listOf(
            "x" to x.toString(), "y" to y.toString(), "w" to w.toString(), "h" to h.toString(),
            "update" to onlyChanges.toString(), "monitor" to monitor.toString(),
        ),
    )

    /** Mueve el cursor a una posición absoluta, en píxeles, del monitor `display`. */
    fun moveTo(source: String, x: Int, y: Int, display: Int = 0) = runAction(
        source, SCREEN_REMOTE, "MoveTo", INPUT_REMOTE,
        listOf("X" to x.toString(), "Y" to y.toString(), "Display" to display.toString()),
    )

    fun click(source: String, button: String = "Left") = runAction(
        source, SCREEN_REMOTE, "Click", INPUT_REMOTE,
        listOf("Button" to button),
    )

    /** Pulsa una tecla y la deja pulsada hasta [keyUp]. `key` es un nombre del servidor ("a", "shift", "oem_comma"...). */
    fun keyDown(source: String, key: String) = runAction(
        source, SCREEN_REMOTE, "KeyDown", INPUT_REMOTE, listOf("Key" to key),
    )

    fun keyUp(source: String, key: String) = runAction(
        source, SCREEN_REMOTE, "KeyUp", INPUT_REMOTE, listOf("Key" to key),
    )

    /** Pulsa un botón del ratón y lo deja pulsado hasta [mouseUp]. */
    fun mouseDown(source: String, button: String) = runAction(
        source, SCREEN_REMOTE, "MouseDown", INPUT_REMOTE, listOf("Button" to button),
    )

    fun mouseUp(source: String, button: String) = runAction(
        source, SCREEN_REMOTE, "MouseUp", INPUT_REMOTE, listOf("Button" to button),
    )

    /** Rueda vertical. Unidad y signo por confirmar contra el servidor. */
    fun scroll(source: String, amount: Int) = runAction(
        source, SCREEN_REMOTE, "Vert", INPUT_REMOTE, listOf("Amount" to amount.toString()),
    )

    /** Número de monitores que anuncia el servidor, o `null` si el paquete no lo lleva. */
    fun parseMonitorCount(packet: UrObject): Int? {
        val controls = packet.obj("Layout")?.list("Controls") ?: return null
        return controls.firstOrNull { it.string("ID") == "monitors" }?.int("Index")
    }

    private fun UrObjectBuilder.extras(values: List<Pair<String, String>>) {
        obj("Extras") {
            list("Values") {
                for ((k, v) in values) item {
                    str("Key", k)
                    str("Value", v)
                }
            }
        }
    }

    /** Extrae el fotograma de un paquete del servidor, o `null` si el paquete no lleva imagen. */
    fun parseFrame(packet: UrObject): UrFrame? {
        val controls = packet.obj("Layout")?.list("Controls") ?: return null
        var back: UrObject? = null
        var cursor: UrObject? = null
        for (c in controls) {
            when (c.string("ID")) {
                "back" -> back = c
                "cursor" -> cursor = c
            }
        }
        val jpeg = back?.binary("Image") ?: return null
        return UrFrame(
            jpeg = jpeg,
            x = back.int("X") ?: 0,
            y = back.int("Y") ?: 0,
            width = back.int("W") ?: 0,
            height = back.int("H") ?: 0,
            cursorX = cursor?.int("X"),
            cursorY = cursor?.int("Y"),
        )
    }
}
