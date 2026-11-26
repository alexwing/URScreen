package com.alexwing.urscreen.net

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.CRC32

/**
 * Exploración del servidor real: qué nombres de tecla acepta, qué hacen los botones del ratón, la rueda y los
 * monitores. INYECTA ENTRADA EN EL PC, así que no hace nada salvo que se cumpla todo esto:
 *
 *  - `UR_LIVE_HOST` apunta al servidor (con la contraseña desactivada);
 *  - `UR_LIVE_INPUT` lista las fases, separadas por comas: `keys`, `hold`, `mouse`, `scroll`, `monitors`, `linkloss`;
 *  - `UR_LIVE_ORACLE_LOG` es el registro de oracle.ps1, una ventana de prueba que recibe y anota todo. Las fases que
 *    inyectan teclas, botones o rueda llevan primero el cursor a la ventana (`UR_LIVE_ORACLE_XY`, "x,y" de su centro
 *    en el monitor 0) y hacen clic para darle el foco, y antes de CADA envío comprueban en el registro que la ventana
 *    sigue teniendo el foco; si no, paran. Así nada llega a otra aplicación.
 *
 * Solo imprime (líneas `[live-input]` con la hora en milisegundos, para alinearlas con el registro de la ventana).
 */
class LiveInputTest {

    private fun phaseEnabled(phase: String): Boolean {
        val host = System.getenv("UR_LIVE_HOST")
        val phases = System.getenv("UR_LIVE_INPUT").orEmpty().split(',').map { it.trim() }
        return !host.isNullOrBlank() && phase in phases
    }

    private val oracleLog: File? get() = System.getenv("UR_LIVE_ORACLE_LOG")?.let(::File)?.takeIf { it.exists() }

    private val oracleCenter: Pair<Int, Int>?
        get() = System.getenv("UR_LIVE_ORACLE_XY")?.split(',')?.mapNotNull { it.trim().toIntOrNull() }
            ?.takeIf { it.size == 2 }?.let { it[0] to it[1] }

    /** ¿Tiene el foco la ventana de prueba ahora mismo? Lo dice su último evento de foco. */
    private fun oracleFocused(): Boolean {
        val lines = runCatching { oracleLog?.readLines() }.getOrNull() ?: return false
        val last = lines.lastOrNull { "\"src\":\"focus\"" in it } ?: return false
        return "\"activated\"" in last
    }

    private fun mark(text: String) = println("[live-input] ${System.currentTimeMillis()} $text")

    private fun <T> withSession(block: suspend (UrSession) -> T): T {
        val host = System.getenv("UR_LIVE_HOST")!!
        val port = System.getenv("UR_LIVE_PORT")?.toIntOrNull() ?: UrConnection.DEFAULT_PORT
        val connection = UrConnection(host, port)
        try {
            return runBlocking {
                withTimeout(120_000) {
                    connection.open()
                    val session = UrSession(connection, "android-" + "0123456789abcdef")
                    session.connect()
                    block(session)
                }
            }
        } finally {
            connection.close()
        }
    }

    /** Envía solo si la ventana de prueba tiene el foco; si no, falla y no se envía nada más. */
    private suspend fun guarded(s: UrSession, packets: List<com.alexwing.urscreen.protocol.UrObject>) {
        check(oracleFocused()) { "la ventana de prueba no tiene el foco: se detiene la prueba" }
        s.sendAll(packets)
    }

    /** Lleva el cursor al centro de la ventana de prueba y hace clic para darle el foco. */
    private suspend fun focusOracle(s: UrSession) {
        val (cx, cy) = oracleCenter ?: error("falta UR_LIVE_ORACLE_XY")
        mark("enfocar ventana de prueba en $cx,$cy")
        s.sendAll(listOf(s.moveToPacket(cx, cy, 0)))
        delay(300)
        s.sendAll(listOf(s.clickPacket("Left")))
        delay(600)
        check(oracleFocused()) { "no se pudo dar el foco a la ventana de prueba" }
    }

    private fun gate(phase: String) =
        assumeTrue("fase $phase no activa o sin ventana de prueba", phaseEnabled(phase) && oracleLog != null && oracleCenter != null)

    @Test
    fun `nombres de tecla`() {
        gate("keys")
        val names = System.getenv("UR_LIVE_KEYS")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: DEFAULT_KEYS
        withSession { s ->
            var pressed: String? = null
            try {
                focusOracle(s)
                for (name in names) {
                    mark("key $name")
                    pressed = name
                    guarded(s, listOf(s.keyDownPacket(name)))
                    delay(60)
                    s.sendAll(listOf(s.keyUpPacket(name)))
                    pressed = null
                    delay(80)
                }
            } finally {
                pressed?.let { withContext(NonCancellable) { s.sendAll(listOf(s.keyUpPacket(it))) } }
            }
        }
    }

    /** ¿Se mantiene una tecla pulsada y se repite sola? ¿Se repite si se reenvía KeyDown? */
    @Test
    fun `tecla mantenida y repeticion`() {
        gate("hold")
        withSession { s ->
            try {
                focusOracle(s)
                mark("hold a 1500ms, un solo KeyDown")
                guarded(s, listOf(s.keyDownPacket("a")))
                delay(1500)
                s.sendAll(listOf(s.keyUpPacket("a")))
                delay(500)
                mark("hold a con KeyDown repetido cada 100ms durante 600ms")
                repeat(6) {
                    guarded(s, listOf(s.keyDownPacket("a")))
                    delay(100)
                }
                s.sendAll(listOf(s.keyUpPacket("a")))
                delay(500)
                mark("shift+a a la vez (un solo envío)")
                guarded(s, listOf(s.keyDownPacket("shift"), s.keyDownPacket("a")))
                delay(300)
                s.sendAll(listOf(s.keyUpPacket("a"), s.keyUpPacket("shift")))
                delay(300)
                mark("shift mantenido, luego b, luego soltar shift")
                guarded(s, listOf(s.keyDownPacket("shift")))
                delay(200)
                guarded(s, listOf(s.keyDownPacket("b")))
                delay(80)
                s.sendAll(listOf(s.keyUpPacket("b")))
                delay(200)
                s.sendAll(listOf(s.keyUpPacket("shift")))
            } finally {
                withContext(NonCancellable) {
                    s.sendAll(listOf(s.keyUpPacket("a"), s.keyUpPacket("b"), s.keyUpPacket("shift")))
                }
            }
        }
    }

    /**
     * ¿Qué pasa en el PC si se corta la conexión con una tecla pulsada (se va el Wi-Fi mientras se mantiene Borrar)?
     * Pulsa `a`, cierra el socket sin `KeyUp`, observa 3 s y después suelta la tecla desde otra conexión.
     */
    @Test
    fun `conexion cortada con tecla pulsada`() {
        gate("linkloss")
        val host = System.getenv("UR_LIVE_HOST")!!
        val port = System.getenv("UR_LIVE_PORT")?.toIntOrNull() ?: UrConnection.DEFAULT_PORT
        try {
            val first = UrConnection(host, port)
            runBlocking {
                first.open()
                val s = UrSession(first, "android-" + "0123456789abcdef")
                s.connect()
                focusOracle(s)
                mark("keydown a, y se corta el socket sin KeyUp")
                guarded(s, listOf(s.keyDownPacket("a")))
                delay(300)
                first.close()
                mark("socket cerrado")
                delay(3_000)
                mark("fin de la observación")
            }
        } finally {
            // Pase lo que pase, la tecla se suelta desde una conexión nueva.
            withSession { s ->
                mark("reconectar y KeyUp a")
                s.sendAll(listOf(s.keyUpPacket("a")))
                delay(500)
                mark("fin")
            }
        }
    }

    @Test
    fun `botones del raton`() {
        gate("mouse")
        withSession { s ->
            try {
                focusOracle(s)
                for (button in listOf("Left", "left", "Right", "right", "Middle", "middle")) {
                    mark("click $button")
                    guarded(s, listOf(s.clickPacket(button)))
                    delay(300)
                }
                mark("mousedown/up Right 600ms")
                guarded(s, listOf(s.mouseDownPacket("Right")))
                delay(600)
                s.sendAll(listOf(s.mouseUpPacket("Right")))
                delay(300)
                mark("mousedown/up Left 600ms")
                guarded(s, listOf(s.mouseDownPacket("Left")))
                delay(600)
                s.sendAll(listOf(s.mouseUpPacket("Left")))
            } finally {
                withContext(NonCancellable) {
                    s.sendAll(listOf(s.mouseUpPacket("Right"), s.mouseUpPacket("Left")))
                }
            }
        }
    }

    @Test
    fun `rueda`() {
        gate("scroll")
        withSession { s ->
            focusOracle(s)
            for (amount in listOf(1, -1, 3, -3, 10, 120, -120)) {
                mark("scroll $amount")
                guarded(s, listOf(s.scrollPacket(amount)))
                delay(350)
            }
        }
    }

    /**
     * Monitores: un fotograma de cada índice (tamaño, origen, cursor y CRC para saber si son distintos) y dónde
     * deja el cursor `MoveTo` con cada `Display` (lo registra la ventana de prueba con las posiciones absolutas).
     * No inyecta teclas ni clics: solo mueve el cursor.
     */
    @Test
    fun `monitores`() {
        assumeTrue("fase monitors no activa", phaseEnabled("monitors"))
        // Pedir un monitor que no existe deja al servidor sin responder, así que solo se piden los que anuncia.
        val count = withSession { s ->
            s.requestFrame(monitor = 0)
            s.monitorCount.value ?: 1
        }
        mark("monitorCount=$count")
        for (monitor in 0 until count) {
            try {
                withSession { s ->
                    withTimeout(20_000) {
                        mark("frame monitor=$monitor, monitorCount=${s.monitorCount.value}")
                        val f = s.requestFrame(monitor = monitor)
                        val crc = CRC32().also { it.update(f.jpeg) }.value
                        mark(
                            "  -> x=${f.x} y=${f.y} ${f.width}x${f.height} ${f.jpeg.size / 1024} KB crc=$crc " +
                                "cursor=${f.cursorX},${f.cursorY} monitorCount=${s.monitorCount.value}",
                        )
                    }
                }
            } catch (e: Exception) {
                mark("frame monitor=$monitor FALLÓ: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
        if (oracleLog != null) {
            withSession { s ->
                val moves = listOf(Triple(100, 100, 0), Triple(100, 100, 1), Triple(0, 0, 1), Triple(100, 100, 0))
                for ((x, y, display) in moves.filter { it.third < count }) {
                    mark("moveto x=$x y=$y display=$display")
                    s.sendAll(listOf(s.moveToPacket(x, y, display)))
                    delay(300)
                    val f = s.requestFrame(monitor = display)
                    mark("  -> frame monitor=$display x=${f.x} y=${f.y} cursor=${f.cursorX},${f.cursorY} size=${f.width}x${f.height}")
                }
            }
        }
    }

    private companion object {
        /** Sin F1-F24, Win ni bloqueos: en una ventana de prueba no harían daño, pero no hace falta correr el riesgo. */
        val DEFAULT_KEYS = listOf(
            "a", "z", "A", "0", "9",
            "return", "back", "tab", "space", "esc",
            "left", "right", "up", "down", "home", "end", "delete", "insert", "pageup", "pagedown",
            "shift", "control", "alt", "altgr",
            "oem_1", "oem_2", "oem_3", "oem_4", "oem_5", "oem_6", "oem_7", "oem_8", "oem_102",
            "oem_comma", "oem_period", "oem_plus", "oem_minus",
        )
    }
}
