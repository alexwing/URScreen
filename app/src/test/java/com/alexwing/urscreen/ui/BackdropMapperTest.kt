package com.alexwing.urscreen.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackdropMapperTest {

    @Test
    fun `identidad con la imagen del mismo tamano mapea la ventana a si misma`() {
        val region = backdropRegion(
            winX = 10f, winY = 20f, winWidth = 100f, winHeight = 50f,
            transform = ViewTransform(),
            boxWidth = 1000f, boxHeight = 500f,
            imageWidth = 1000, imageHeight = 500,
        )

        assertEquals(BackdropRegion(10, 20, 100, 50, 0, 0, 100, 50), region)
    }

    @Test
    fun `ventana parcialmente fuera por la izquierda solo copia la parte visible`() {
        val region = backdropRegion(
            winX = -50f, winY = 0f, winWidth = 100f, winHeight = 50f,
            transform = ViewTransform(),
            boxWidth = 1000f, boxHeight = 500f,
            imageWidth = 1000, imageHeight = 500,
        )

        assertEquals(0, region!!.srcX)
        assertEquals(50, region.srcWidth)
        assertEquals(50, region.dstX)
        assertEquals(50, region.dstWidth)
    }

    @Test
    fun `ventana totalmente fuera de la vista devuelve null`() {
        val region = backdropRegion(
            winX = 2000f, winY = 0f, winWidth = 100f, winHeight = 50f,
            transform = ViewTransform(),
            boxWidth = 1000f, boxHeight = 500f,
            imageWidth = 1000, imageHeight = 500,
        )

        assertNull(region)
    }

    @Test
    fun `con zoom 2 y sin desplazamiento la mitad de la imagen cabe en la ventana`() {
        val region = backdropRegion(
            winX = 0f, winY = 0f, winWidth = 100f, winHeight = 50f,
            transform = ViewTransform(zoom = 2f),
            boxWidth = 1000f, boxHeight = 500f,
            imageWidth = 1000, imageHeight = 500,
        )

        assertEquals(BackdropRegion(0, 0, 50, 25, 0, 0, 100, 50), region)
    }

    @Test
    fun `con zoom 2 y desplazamiento el origen de la fuente se desplaza con el pan`() {
        // pantalla = vista * zoom + pan  ->  vista = (pantalla - pan) / zoom
        // x=0 -> vista (0 + 500) / 2 = 250
        val region = backdropRegion(
            winX = 0f, winY = 0f, winWidth = 100f, winHeight = 50f,
            transform = ViewTransform(zoom = 2f, panX = -500f, panY = 0f),
            boxWidth = 1000f, boxHeight = 500f,
            imageWidth = 1000, imageHeight = 500,
        )

        assertEquals(250, region!!.srcX)
        assertEquals(50, region.srcWidth)
    }

    @Test
    fun `ventana solo sobre las bandas negras devuelve null`() {
        // Caja 1000x1000 con imagen 1000x500: banda de 250 px arriba y abajo.
        val region = backdropRegion(
            winX = 0f, winY = 0f, winWidth = 100f, winHeight = 100f,
            transform = ViewTransform(),
            boxWidth = 1000f, boxHeight = 1000f,
            imageWidth = 1000, imageHeight = 500,
        )

        assertNull(region)
    }

    @Test
    fun `ventana bajo la imagen recorta solo la parte que hay sobre el fotograma`() {
        val region = backdropRegion(
            winX = 0f, winY = 200f, winWidth = 100f, winHeight = 100f,
            transform = ViewTransform(),
            boxWidth = 1000f, boxHeight = 1000f,
            imageWidth = 1000, imageHeight = 500,
        )

        assertEquals(0, region!!.srcY)
        assertEquals(50, region.srcHeight)
        assertEquals(50, region.dstY)
        assertEquals(50, region.dstHeight)
    }

    @Test
    fun `entradas invalidas devuelven null`() {
        val transform = ViewTransform()

        assertNull(
            backdropRegion(0f, 0f, 0f, 50f, transform, 1000f, 500f, 1000, 500),
        )
        assertNull(
            backdropRegion(0f, 0f, 100f, 50f, transform, 1000f, 500f, 0, 500),
        )
        assertNull(
            backdropRegion(0f, 0f, 100f, 50f, transform, 0f, 500f, 1000, 500),
        )
    }
}
