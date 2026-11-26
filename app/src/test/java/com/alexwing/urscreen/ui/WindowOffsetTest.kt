package com.alexwing.urscreen.ui

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class WindowOffsetTest {
    // Ventana de 450 de ancho en una vista de 800 x 360, con barra de título de 22 y 72 visibles como mínimo.
    private fun clamp(x: Float, y: Float) = clampWindowOffset(Offset(x, y), 450f, 800f, 360f, 22f, 72f)

    @Test
    fun `una posicion dentro de la vista no cambia`() {
        assertEquals(Offset(100f, 50f), clamp(100f, 50f))
    }

    @Test
    fun `puede salirse por la izquierda pero queda a la vista un trozo`() {
        assertEquals(-378f, clamp(-1000f, 50f).x, 0.001f) // 72 - 450
    }

    @Test
    fun `puede salirse por la derecha pero queda a la vista un trozo`() {
        assertEquals(728f, clamp(5000f, 50f).x, 0.001f) // 800 - 72
    }

    @Test
    fun `no sale por arriba y por abajo deja la barra de titulo a la vista`() {
        assertEquals(0f, clamp(100f, -40f).y, 0.001f)
        assertEquals(338f, clamp(100f, 1000f).y, 0.001f) // 360 - 22
    }

    @Test
    fun `una ventana mas estrecha que el minimo visible cabe entera`() {
        val r = clampWindowOffset(Offset(-500f, 0f), 50f, 800f, 360f, 22f, 72f)
        assertEquals(0f, r.x, 0.001f) // visible = 50, mínimo x = 50 - 50
    }

    @Test
    fun `sin tamano de vista no se toca la posicion`() {
        assertEquals(Offset(-5f, 9999f), clampWindowOffset(Offset(-5f, 9999f), 450f, 0f, 0f, 22f, 72f))
    }

    // Barra de 160 x 40 en una vista de 800 x 360: x en [-320, 320], y en [0, 320].
    private fun clampBar(x: Float, y: Float) = clampBarOffset(Offset(x, y), 160f, 40f, 800f, 360f)

    @Test
    fun `una barra dentro de la vista no cambia`() {
        assertEquals(Offset(100f, 50f), clampBar(100f, 50f))
    }

    @Test
    fun `la barra no sale por la izquierda ni por la derecha`() {
        assertEquals(-320f, clampBar(-1000f, 50f).x, 0.001f) // -(800 - 160) / 2
        assertEquals(320f, clampBar(1000f, 50f).x, 0.001f)
    }

    @Test
    fun `la barra no sale por arriba ni por abajo`() {
        assertEquals(0f, clampBar(100f, -40f).y, 0.001f)
        assertEquals(320f, clampBar(100f, 1000f).y, 0.001f) // 360 - 40
    }

    @Test
    fun `si la barra es mayor que la vista en un eje, ese eje queda a 0`() {
        val ancha = clampBarOffset(Offset(50f, 50f), 900f, 40f, 800f, 360f)
        assertEquals(0f, ancha.x, 0.001f)
        assertEquals(50f, ancha.y, 0.001f)

        val alta = clampBarOffset(Offset(50f, 500f), 160f, 400f, 800f, 360f)
        assertEquals(50f, alta.x, 0.001f)
        assertEquals(0f, alta.y, 0.001f)
    }

    @Test
    fun `sin tamano de vista la barra no se toca`() {
        assertEquals(Offset(-5f, 9999f), clampBarOffset(Offset(-5f, 9999f), 160f, 40f, 0f, 0f))
    }
}
