package com.alexwing.urscreen.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FramePacerTest {

    @Test
    fun `recien creado la espera es cero`() {
        val pacer = FramePacer()

        assertEquals(0L, pacer.waitMs(0))
    }

    @Test
    fun `un primer fotograma no cuenta como repetido`() {
        val pacer = FramePacer()

        pacer.onFrame(1)

        assertEquals(0L, pacer.waitMs(0))
    }

    @Test
    fun `fotogramas identicos suben la espera de 40 en 40 hasta 400 y ahi se quedan`() {
        val pacer = FramePacer()
        pacer.onFrame(1)

        for (k in 1..10) {
            pacer.onFrame(1)
            assertEquals(40L * k, pacer.waitMs(0))
        }

        repeat(5) { pacer.onFrame(1) }
        assertEquals(400L, pacer.waitMs(0))
    }

    @Test
    fun `un fotograma distinto reduce la espera a la mitad`() {
        val pacer = FramePacer()
        pacer.onFrame(1)
        repeat(10) { pacer.onFrame(1) }
        assertEquals(400L, pacer.waitMs(0))   // idle 10

        pacer.onFrame(2)
        assertEquals(200L, pacer.waitMs(0))   // idle 10 -> 5

        pacer.onFrame(3)
        assertEquals(80L, pacer.waitMs(0))    // idle 5 -> 2 (división entera)
    }

    @Test
    fun `fotogramas que cambian siempre mantienen la espera en cero`() {
        val pacer = FramePacer()

        for (i in 0 until 20) {
            pacer.onFrame(if (i % 2 == 0) 1L else 2L)
            assertEquals(0L, pacer.waitMs(0))
        }
    }

    @Test
    fun `la actividad del usuario pone la espera a cero durante el impulso`() {
        val pacer = FramePacer()
        pacer.onFrame(1)
        repeat(10) { pacer.onFrame(1) }
        assertEquals(400L, pacer.waitMs(0))

        pacer.noteActivity(1000)

        assertEquals(0L, pacer.waitMs(1500))
        assertEquals(0L, pacer.waitMs(2999))
    }

    @Test
    fun `tras el impulso la espera refleja los repetidos recibidos durante el mismo`() {
        // Con 10 o más repetidos durante el impulso, al acabar la espera ya está en el tope.
        val muchos = FramePacer()
        muchos.onFrame(1)
        muchos.noteActivity(1000)
        repeat(12) { muchos.onFrame(1) }
        assertEquals(0L, muchos.waitMs(2999))
        assertEquals(400L, muchos.waitMs(3000))

        // Con n<10 repetidos, la espera es 40*n.
        val pocos = FramePacer()
        pocos.onFrame(1)
        pocos.noteActivity(1000)
        repeat(4) { pocos.onFrame(1) }
        assertEquals(160L, pocos.waitMs(3000))
    }

    @Test
    fun `el parpadeo mantiene la espera acotada y por debajo de 400 de media`() {
        val pacer = FramePacer()
        val patron = listOf(1L, 1L, 1L, 1L, 1L, 2L)   // 5 idénticos y 1 distinto
        var maximo = Long.MIN_VALUE
        var minimo = Long.MAX_VALUE
        var suma = 0L
        var muestras = 0

        repeat(50) {
            for (hash in patron) {
                pacer.onFrame(hash)
                val espera = pacer.waitMs(0)
                maximo = maxOf(maximo, espera)
                minimo = minOf(minimo, espera)
                suma += espera
                muestras++
            }
        }

        assertTrue("la espera nunca debe superar 400, máximo=$maximo", maximo <= 400L)
        assertTrue("la espera nunca debe bajar de 0, mínimo=$minimo", minimo >= 0L)
        assertTrue("la espera debe bajar alguna vez por debajo del máximo", minimo < maximo)
        assertTrue("la media debe quedar por debajo de 400", suma.toDouble() / muestras < 400.0)
    }

    @Test
    fun `parametros personalizados limitan el crecimiento a 30 ms`() {
        val pacer = FramePacer(stepMs = 10, maxSteps = 3, boostMs = 100)
        pacer.onFrame(1)

        pacer.onFrame(1)
        assertEquals(10L, pacer.waitMs(0))
        pacer.onFrame(1)
        assertEquals(20L, pacer.waitMs(0))
        pacer.onFrame(1)
        assertEquals(30L, pacer.waitMs(0))
        pacer.onFrame(1)
        assertEquals(30L, pacer.waitMs(0))
    }
}
