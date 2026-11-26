package com.alexwing.urscreen.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ViewTransformTest {

    private val tolerancia = 0.001f

    @Test
    fun `toView sin zoom devuelve el mismo punto`() {
        val punto = ViewTransform().toView(100f, 50f)

        assertEquals(100f, punto.x, tolerancia)
        assertEquals(50f, punto.y, tolerancia)
    }

    @Test
    fun `toView con zoom y desplazamiento invierte la transformacion de pantalla`() {
        // pantalla = vista * zoom + pan  ->  vista = (pantalla - pan) / zoom
        val punto = ViewTransform(zoom = 2f, panX = -100f, panY = -50f).toView(300f, 150f)

        assertEquals(200f, punto.x, tolerancia)
        assertEquals(100f, punto.y, tolerancia)
    }

    @Test
    fun `zoom se acota al maximo y al minimo`() {
        val alMaximo = ViewTransform().transformedBy(100f, 500f, 250f, 0f, 0f, 1000f, 500f)
        val alMinimo = ViewTransform().transformedBy(0.1f, 500f, 250f, 0f, 0f, 1000f, 500f)

        assertEquals(ViewTransform.MAX_ZOOM, alMaximo.zoom, tolerancia)
        assertEquals(ViewTransform.MIN_ZOOM, alMinimo.zoom, tolerancia)
    }

    @Test
    fun `pan se acota para no dejar huecos al desplazar`() {
        val inicial = ViewTransform(zoom = 2f, panX = -500f, panY = -250f)

        // Arrastre hacia la derecha y abajo: el pan no puede pasar de 0.
        val haciaDerecha = inicial.transformedBy(1f, 500f, 250f, 2000f, 0f, 1000f, 500f)
        assertEquals(0f, haciaDerecha.panX, tolerancia)

        // Arrastre hacia arriba: el pan no puede bajar de boxHeight * (1 - zoom) = -500.
        val haciaArriba = inicial.transformedBy(1f, 500f, 250f, 0f, -2000f, 1000f, 500f)
        assertEquals(-500f, haciaArriba.panY, tolerancia)
    }

    @Test
    fun `con zoom 1 el pan siempre es cero`() {
        val sinZoom = ViewTransform().transformedBy(1f, 300f, 200f, 150f, -80f, 1000f, 500f)
        val reducido = ViewTransform().transformedBy(0.5f, 300f, 200f, 150f, -80f, 1000f, 500f)

        assertEquals(0f, sinZoom.panX, tolerancia)
        assertEquals(0f, sinZoom.panY, tolerancia)
        assertEquals(1f, sinZoom.zoom, tolerancia)
        assertEquals(0f, reducido.panX, tolerancia)
        assertEquals(0f, reducido.panY, tolerancia)
    }

    @Test
    fun `el punto bajo el centroide se mantiene al hacer zoom`() {
        // Centroide en el centro de una vista de 1000x500: el punto (500, 250) no debe moverse.
        val tras = ViewTransform().transformedBy(2f, 500f, 250f, 0f, 0f, 1000f, 500f)

        assertEquals(2f, tras.zoom, tolerancia)
        assertEquals(-500f, tras.panX, tolerancia)
        assertEquals(-250f, tras.panY, tolerancia)
        val punto = tras.toView(500f, 250f)
        assertEquals(500f, punto.x, tolerancia)
        assertEquals(250f, punto.y, tolerancia)
    }

    @Test
    fun `el punto bajo el centroide se mantiene al hacer zoom desde una transformacion previa`() {
        val previo = ViewTransform(zoom = 2f, panX = -500f, panY = -250f)
        val antes = previo.toView(300f, 200f)

        val tras = previo.transformedBy(1.5f, 300f, 200f, 0f, 0f, 1000f, 500f)
        val despues = tras.toView(300f, 200f)

        assertEquals(3f, tras.zoom, tolerancia)
        assertEquals(antes.x, despues.x, tolerancia)
        assertEquals(antes.y, despues.y, tolerancia)
    }

    @Test
    fun `arrastrar desplaza la imagen con zoomChange 1`() {
        val inicial = ViewTransform(zoom = 2f, panX = -500f, panY = -250f)

        val tras = inicial.transformedBy(1f, 500f, 250f, 30f, -20f, 1000f, 500f)

        assertEquals(2f, tras.zoom, tolerancia)
        assertEquals(-470f, tras.panX, tolerancia)
        assertEquals(-270f, tras.panY, tolerancia)
    }

    @Test
    fun `entradas invalidas devuelven la misma instancia`() {
        val inicial = ViewTransform(zoom = 2f, panX = -500f, panY = -250f)

        assertSame(inicial, inicial.transformedBy(Float.NaN, 500f, 250f, 0f, 0f, 1000f, 500f))
        assertSame(inicial, inicial.transformedBy(1f, Float.POSITIVE_INFINITY, 250f, 0f, 0f, 1000f, 500f))
        assertSame(inicial, inicial.transformedBy(1f, 500f, 250f, Float.NaN, 0f, 1000f, 500f))
        assertSame(inicial, inicial.transformedBy(1f, 500f, 250f, 0f, 0f, 0f, 500f))
        assertSame(inicial, inicial.transformedBy(1f, 500f, 250f, 0f, 0f, 1000f, -1f))
        assertSame(inicial, inicial.transformedBy(0f, 500f, 250f, 0f, 0f, 1000f, 500f))
    }
}
