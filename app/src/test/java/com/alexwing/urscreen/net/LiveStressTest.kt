package com.alexwing.urscreen.net

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Busca qué hace que el servidor de Unified se quede colgado. Solo corre con `UR_LIVE_HOST` y `UR_LIVE_STRESS=<ciclos>`.
 *
 * Cada ciclo hace lo que la app: conecta, pide fotogramas con 2 en vuelo durante un rato y CORTA el socket sin esperar
 * a que lleguen los pedidos (como «Salir» o como perder el Wi-Fi). Después, desde otra conexión, comprueba que el
 * servidor sigue contestando; si no contesta en 20 s, lo da por colgado y para.
 *
 * `UR_LIVE_STRESS_MODE`: `abrupt` (por defecto: corta con pedidos en vuelo), `graceful` (deja de pedir, espera a
 * que lleguen los pedidos y entonces cierra).
 */
class LiveStressTest {

    private fun env(name: String) = System.getenv(name)?.takeIf { it.isNotBlank() }

    private fun mark(text: String) = println("[live-stress] ${System.currentTimeMillis()} $text")

    /** ¿Responde el servidor a un cliente nuevo con un fotograma? Devuelve los ms, o `null` si no contesta (15 s). */
    private fun probe(host: String, port: Int): Long? {
        val t0 = System.nanoTime()
        val ok = runCatching {
            runBlocking {
                val c = UrConnection(host, port)
                try {
                    c.open()
                    val s = UrSession(c, "android-bbbbbbbbbbbbbbbb")
                    s.connect()
                    s.requestFrame(monitor = 0)
                } finally {
                    c.close()
                }
            }
        }
        return if (ok.isSuccess) (System.nanoTime() - t0) / 1_000_000 else null
    }

    /**
     * Un cliente que pide 2 fotogramas y deja de leer SIN cerrar el socket (se va el Wi-Fi, Android congela la app).
     * Mientras tanto otro cliente comprueba cada pocos segundos si el servidor sigue sirviendo fotogramas, y al final se
     * cierra el primero y se ve si se recupera. `UR_LIVE_STALL=<segundos>`.
     */
    @Test
    fun `cliente que deja de leer sin cerrar`() {
        val host = env("UR_LIVE_HOST")
        val stall = env("UR_LIVE_STALL")?.toIntOrNull()
        assumeTrue("sin UR_LIVE_HOST y UR_LIVE_STALL", host != null && stall != null)
        val port = env("UR_LIVE_PORT")?.toIntOrNull() ?: UrConnection.DEFAULT_PORT
        val idA = "android-aaaaaaaaaaaaaaaa"

        mark("base: ${probe(host!!, port)?.let { "responde en $it ms" } ?: "NO RESPONDE"}")
        runBlocking {
            val a = UrConnection(host, port)
            a.open()
            UrSession(a, idA).connect()
            a.send(com.alexwing.urscreen.protocol.UrMessages.update(idA, 0))
            a.send(com.alexwing.urscreen.protocol.UrMessages.update(idA, 0))
            mark("A: dos peticiones enviadas; no se lee más y no se cierra")
            val end = System.currentTimeMillis() + stall!! * 1000L
            var hung = false
            while (System.currentTimeMillis() < end) {
                val ms = probe(host, port)
                mark("  B con A parado: ${ms?.let { "responde en $it ms" } ?: "NO RESPONDE"}")
                if (ms == null) hung = true
                kotlinx.coroutines.delay(2_000)
            }
            mark("cierro A (hung=$hung)")
            a.close()
            repeat(4) {
                val ms = probe(host, port)
                mark("  B tras cerrar A: ${ms?.let { "responde en $it ms" } ?: "NO RESPONDE"}")
                if (ms != null) return@runBlocking
                kotlinx.coroutines.delay(2_000)
            }
            mark("TRAS CERRAR A SIGUE COLGADO")
        }
    }

    /** ¿Un escritorio quieto devuelve JPEG idénticos? Pide `UR_LIVE_REPEAT` fotogramas seguidos y cuenta los distintos. */
    @Test
    fun `fotogramas repetidos de un escritorio quieto`() {
        val host = env("UR_LIVE_HOST")
        val n = env("UR_LIVE_REPEAT")?.toIntOrNull()
        assumeTrue("sin UR_LIVE_HOST y UR_LIVE_REPEAT", host != null && n != null)
        val port = env("UR_LIVE_PORT")?.toIntOrNull() ?: UrConnection.DEFAULT_PORT
        runBlocking {
            val c = UrConnection(host!!, port)
            try {
                c.open()
                val s = UrSession(c, "android-cccccccccccccccc")
                s.connect()
                val crcs = ArrayList<Long>()
                repeat(n!!) {
                    val f = s.requestFrame(monitor = 0)
                    crcs += java.util.zip.CRC32().also { it.update(f.jpeg) }.value
                    mark("frame $it: ${f.jpeg.size} bytes crc=${crcs.last()} cursor=${f.cursorX},${f.cursorY}")
                }
                mark("distintos: ${crcs.toSet().size} de ${crcs.size}")
            } finally {
                c.close()
            }
        }
    }

    @Test
    fun `cortes con fotogramas en vuelo`() {
        val host = env("UR_LIVE_HOST")
        val cycles = env("UR_LIVE_STRESS")?.toIntOrNull()
        assumeTrue("sin UR_LIVE_HOST y UR_LIVE_STRESS", host != null && cycles != null)
        val port = env("UR_LIVE_PORT")?.toIntOrNull() ?: UrConnection.DEFAULT_PORT
        val graceful = env("UR_LIVE_STRESS_MODE") == "graceful"

        repeat(cycles!!) { i ->
            val runMs = env("UR_LIVE_STRESS_MS")?.toLongOrNull() ?: (500L + Random.nextLong(2500))
            runBlocking {
                val c = UrConnection(host!!, port)
                c.open()
                val s = UrSession(c, "android-0123456789abcdef")
                s.connect()
                var frames = 0
                if (graceful) {
                    // Dos en vuelo, y para cerrar: se recibe lo pedido y no se pide más.
                    withTimeoutOrNull(runMs) { s.frames(inFlight = 1).collect { frames++ } }
                } else {
                    withTimeoutOrNull(runMs) { s.frames().collect { frames++ } }
                }
                mark("ciclo ${i + 1}/$cycles modo=${if (graceful) "graceful" else "abrupt"} fotogramas=$frames, se corta")
                c.close()
            }
            // ¿Sigue vivo el servidor?
            val t0 = System.nanoTime()
            val ok = runCatching {
                runBlocking {
                    withTimeout(20_000) {
                        val c = UrConnection(host!!, port)
                        try {
                            c.open()
                            val s = UrSession(c, "android-0123456789abcdef")
                            s.connect()
                            s.requestFrame(monitor = 0)
                        } finally {
                            c.close()
                        }
                    }
                }
            }
            val ms = (System.nanoTime() - t0) / 1_000_000
            if (ok.isFailure) {
                mark("SERVIDOR COLGADO tras el ciclo ${i + 1}: ${ok.exceptionOrNull()?.javaClass?.simpleName} (${ms} ms)")
                error("el servidor dejó de contestar tras el ciclo ${i + 1}")
            }
            mark("  servidor responde (${ms} ms)")
        }
    }
}
