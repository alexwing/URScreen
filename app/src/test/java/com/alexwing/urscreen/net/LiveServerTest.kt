package com.alexwing.urscreen.net

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Prueba de integración contra un servidor de Unified Remote de verdad. No se ejecuta si no se define
 * `UR_LIVE_HOST`, y el servidor debe tener la contraseña desactivada:
 *
 *     $env:UR_LIVE_HOST = "127.0.0.1"; ./gradlew testDebugUnitTest --tests "*LiveServerTest*"
 *
 * Pide fotogramas del monitor 0 y mide; no guarda ni muestra la imagen. No hace clic.
 */
class LiveServerTest {

    @Test
    fun `handshake, carga de Screen y diez fotogramas JPEG`() {
        val host = System.getenv("UR_LIVE_HOST")
        assumeTrue("UR_LIVE_HOST no definido: se omite", !host.isNullOrBlank())
        val port = System.getenv("UR_LIVE_PORT")?.toIntOrNull() ?: UrConnection.DEFAULT_PORT

        val connection = UrConnection(host!!, port)
        try {
            runBlocking {
                withTimeout(30_000) {
                    connection.open()
                    val session = UrSession(connection, "android-" + "0123456789abcdef")
                    session.connect()
                    println("[live] servidor: ${session.serverName}")

                    val started = System.nanoTime()
                    val frames = session.frames(monitor = 0).take(10).toList()
                    val totalMs = (System.nanoTime() - started) / 1_000_000

                    assertEquals(10, frames.size)
                    for (f in frames) {
                        val jpeg = f.frame.jpeg
                        assertTrue("no es un JPEG", jpeg.size > 4 && jpeg[0] == 0xFF.toByte() && jpeg[1] == 0xD8.toByte())
                    }
                    val avgKb = frames.sumOf { it.frame.jpeg.size } / frames.size / 1024
                    val rtts = frames.map { it.roundTripMs }
                    println(
                        "[live] 10 fotogramas en $totalMs ms (${totalMs / 10} ms/fotograma), " +
                            "$avgKb KB de media, ida y vuelta min=${rtts.min()} max=${rtts.max()} ms, " +
                            "tamaño ${frames[0].frame.width}x${frames[0].frame.height}",
                    )
                }
            }
        } finally {
            connection.close()
        }
    }

    /**
     * Compara 1, 2 y 3 peticiones en vuelo. En local el cuello de botella es el servidor, así que la
     * ganancia real (solapar captura con transferencia) solo se ve por Wi-Fi; aquí se comprueba que
     * funciona, que llegan todos los fotogramas y que no es más lento. Solo imprime.
     */
    @Test
    fun `fotogramas por segundo con 1, 2 y 3 peticiones en vuelo`() {
        val host = System.getenv("UR_LIVE_HOST")
        assumeTrue("UR_LIVE_HOST no definido: se omite", !host.isNullOrBlank())
        val port = System.getenv("UR_LIVE_PORT")?.toIntOrNull() ?: UrConnection.DEFAULT_PORT
        val count = 40

        for (inFlight in listOf(1, 2, 3, 1, 2, 3)) {
            // Conexión nueva por prueba: al cortar el flujo quedan respuestas en el socket.
            val connection = UrConnection(host!!, port)
            try {
                runBlocking {
                    withTimeout(60_000) {
                        connection.open()
                        val session = UrSession(connection, "android-" + "0123456789abcdef")
                        session.connect()
                        val started = System.nanoTime()
                        val frames = session.frames(monitor = 0, inFlight = inFlight).take(count).toList()
                        val totalMs = (System.nanoTime() - started) / 1_000_000
                        assertEquals(count, frames.size)
                        println(
                            "[live] inFlight=$inFlight: $count fotogramas en $totalMs ms " +
                                "(${totalMs / count} ms/fotograma, %.1f fps)".format(count * 1000.0 / totalMs),
                        )
                    }
                }
            } finally {
                connection.close()
            }
        }
    }

    /** Exploración: ¿cambian el tamaño del fotograma los parámetros x, y, w, h y update? Solo imprime. */
    @Test
    fun `efecto de los parametros de update en el fotograma`() {
        val host = System.getenv("UR_LIVE_HOST")
        assumeTrue("UR_LIVE_HOST no definido: se omite", !host.isNullOrBlank())
        val port = System.getenv("UR_LIVE_PORT")?.toIntOrNull() ?: UrConnection.DEFAULT_PORT

        val connection = UrConnection(host!!, port)
        try {
            runBlocking {
                withTimeout(60_000) {
                    connection.open()
                    val session = UrSession(connection, "android-" + "0123456789abcdef")
                    session.connect()
                    data class Case(val label: String, val x: Int, val y: Int, val w: Int, val h: Int, val only: Boolean)
                    val cases = listOf(
                        Case("0,0,0,0 completo", 0, 0, 0, 0, false),
                        Case("w=1280 h=540", 0, 0, 1280, 540, false),
                        Case("w=640 h=360", 0, 0, 640, 360, false),
                        Case("x=100 y=100 w=800 h=600", 100, 100, 800, 600, false),
                        Case("w=2560 h=1080", 0, 0, 2560, 1080, false),
                        Case("update=true (1ª)", 0, 0, 0, 0, true),
                        Case("update=true (2ª, sin cambios)", 0, 0, 0, 0, true),
                        Case("update=true w=1280 h=540", 0, 0, 1280, 540, true),
                    )
                    for (c in cases) {
                        val t0 = System.nanoTime()
                        val f = session.requestFrame(0, c.x, c.y, c.w, c.h, c.only)
                        val ms = (System.nanoTime() - t0) / 1_000_000
                        println("[live] ${c.label}: x=${f.x} y=${f.y} ${f.width}x${f.height}, ${f.jpeg.size / 1024} KB, $ms ms")
                    }
                }
            }
        } finally {
            connection.close()
        }
    }
}
