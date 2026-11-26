package com.alexwing.urscreen.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyLatchTest {

    private val shift = "lshift"

    @Test
    fun `un toque simple envia down y up y no fija la tecla`() {
        val latch = KeyLatch()

        assertTrue(latch.onDown(shift, latchable = true, now = 0))
        assertTrue(latch.onUp(shift, now = 50))

        assertFalse(latch.isLatched(shift))
        assertTrue(latch.latchedKeys().isEmpty())
    }

    @Test
    fun `un doble toque rapido sobre un modificador lo deja fijado`() {
        val latch = KeyLatch()

        assertTrue(latch.onDown(shift, latchable = true, now = 0))
        assertTrue(latch.onUp(shift, now = 50))
        assertTrue(latch.onDown(shift, latchable = true, now = 200))
        assertFalse(latch.onUp(shift, now = 250))

        assertTrue(latch.isLatched(shift))
        assertTrue(shift in latch.latchedKeys())
    }

    @Test
    fun `el primer up de un doble toque rapido se envia y el segundo no`() {
        val latch = KeyLatch()

        assertTrue(latch.onDown(shift, latchable = true, now = 0))
        assertTrue(latch.onUp(shift, now = 50))
        assertTrue(latch.onDown(shift, latchable = true, now = 200))
        assertFalse(latch.onUp(shift, now = 250))
    }

    @Test
    fun `un segundo toque lento no fija la tecla`() {
        val latch = KeyLatch()

        assertTrue(latch.onDown(shift, latchable = true, now = 0))
        assertTrue(latch.onUp(shift, now = 50))
        assertTrue(latch.onDown(shift, latchable = true, now = 50 + 351))
        assertTrue(latch.onUp(shift, now = 50 + 351 + 50))

        assertFalse(latch.isLatched(shift))
        assertTrue(latch.latchedKeys().isEmpty())
    }

    @Test
    fun `un segundo toque exactamente a 350 ms si fija la tecla`() {
        val latch = KeyLatch()

        assertTrue(latch.onDown(shift, latchable = true, now = 0))
        assertTrue(latch.onUp(shift, now = 50))
        assertTrue(latch.onDown(shift, latchable = true, now = 50 + 350))
        assertFalse(latch.onUp(shift, now = 50 + 350 + 50))

        assertTrue(latch.isLatched(shift))
    }

    @Test
    fun `tocar una tecla fijada la suelta al levantar el dedo`() {
        val latch = KeyLatch()
        latch.onDown(shift, latchable = true, now = 0)
        latch.onUp(shift, now = 50)
        latch.onDown(shift, latchable = true, now = 200)
        latch.onUp(shift, now = 250)
        assertTrue(latch.isLatched(shift))

        // Ya está pulsada: no se vuelve a enviar KeyDown.
        assertFalse(latch.onDown(shift, latchable = true, now = 400))
        // Al levantar el dedo se envía KeyUp y deja de estar fijada.
        assertTrue(latch.onUp(shift, now = 450))

        assertFalse(latch.isLatched(shift))
        assertTrue(latch.latchedKeys().isEmpty())
    }

    @Test
    fun `tras soltar una tecla fijada un toque inmediato no cuenta como segundo toque`() {
        val latch = KeyLatch()
        latch.onDown(shift, latchable = true, now = 0)
        latch.onUp(shift, now = 50)
        latch.onDown(shift, latchable = true, now = 200)
        latch.onUp(shift, now = 250)
        latch.onDown(shift, latchable = true, now = 400)
        latch.onUp(shift, now = 450)

        // Toque inmediato tras soltar: es un primer toque, no fija.
        assertTrue(latch.onDown(shift, latchable = true, now = 460))
        assertTrue(latch.onUp(shift, now = 470))

        assertFalse(latch.isLatched(shift))
    }

    @Test
    fun `una tecla no fijable nunca se fija aunque sean dos toques rapidos`() {
        val latch = KeyLatch()

        assertTrue(latch.onDown("a", latchable = false, now = 0))
        assertTrue(latch.onUp("a", now = 50))
        assertTrue(latch.onDown("a", latchable = false, now = 200))
        assertTrue(latch.onUp("a", now = 250))

        assertFalse(latch.isLatched("a"))
        assertTrue(latch.latchedKeys().isEmpty())
    }

    @Test
    fun `mantener pulsada mucho tiempo envia down y up y no fija`() {
        val latch = KeyLatch()

        assertTrue(latch.onDown(shift, latchable = true, now = 0))
        assertTrue(latch.onUp(shift, now = 2000))

        assertFalse(latch.isLatched(shift))
    }

    @Test
    fun `fijar una tecla no afecta a otra`() {
        val latch = KeyLatch()
        latch.onDown("lshift", latchable = true, now = 0)
        latch.onUp("lshift", now = 50)
        latch.onDown("lshift", latchable = true, now = 200)
        latch.onUp("lshift", now = 250)

        assertTrue(latch.isLatched("lshift"))
        assertFalse(latch.isLatched("lctrl"))
        assertTrue(latch.onDown("lctrl", latchable = true, now = 300))
        assertTrue(latch.onUp("lctrl", now = 310))
        assertEquals(setOf("lshift"), latch.latchedKeys())
    }

    @Test
    fun `clear olvida las teclas fijadas y el primer toque posterior se comporta como primero`() {
        val latch = KeyLatch()
        latch.onDown(shift, latchable = true, now = 0)
        latch.onUp(shift, now = 50)
        latch.onDown(shift, latchable = true, now = 200)
        latch.onUp(shift, now = 250)
        assertTrue(latch.isLatched(shift))

        latch.clear()

        assertTrue(latch.latchedKeys().isEmpty())
        assertFalse(latch.isLatched(shift))
        // Primer toque tras clear: se envía down y up normales, sin fijar.
        assertTrue(latch.onDown(shift, latchable = true, now = 300))
        assertTrue(latch.onUp(shift, now = 350))
        assertFalse(latch.isLatched(shift))
    }
}
