package com.alexwing.urscreen.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TapMapperTest {
    // Imagen 2560x1080 en una vista de 1280x800: escala 0,5; la imagen mide 1280x540 y queda centrada
    // con 130 px de banda negra arriba y abajo.
    private fun map(x: Float, y: Float, originX: Int = 0, originY: Int = 0) = mapTapToSource(
        x, y, 1280f, 800f, 2560, 1080, 2560, 1080, originX, originY,
    )

    @Test
    fun `el centro de la vista es el centro del escritorio`() {
        assertEquals(SourcePoint(1280, 540), map(640f, 400f))
    }

    @Test
    fun `la esquina superior izquierda de la imagen es el origen`() {
        assertEquals(SourcePoint(0, 0), map(0f, 130f))
    }

    @Test
    fun `un toque en la banda negra no hace nada`() {
        assertNull(map(640f, 50f))
        assertNull(map(640f, 760f))
    }

    @Test
    fun `se suma el origen de la zona capturada`() {
        assertEquals(SourcePoint(100, 40), map(0f, 130f, originX = 100, originY = 40))
    }

    @Test
    fun `si el bitmap es mas pequeno que la zona se reescala`() {
        // Bitmap de 1280x540 que representa 2560x1080 del escritorio, en una vista del mismo tamaño.
        val p = mapTapToSource(640f, 270f, 1280f, 540f, 1280, 540, 2560, 1080, 0, 0)
        assertEquals(SourcePoint(1280, 540), p)
    }
}
