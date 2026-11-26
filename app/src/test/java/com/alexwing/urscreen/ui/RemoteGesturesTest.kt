package com.alexwing.urscreen.ui

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteGesturesTest {
    private val slop = 20f

    private fun clasifica(zoom: Float, pan: Offset, scroll: Boolean) = classifyTwoFingers(zoom, pan, slop, scroll)

    @Test
    fun `mientras no se muevan lo bastante no se decide`() {
        assertNull(clasifica(1f, Offset(0f, 0f), scroll = true))
        assertNull(clasifica(1.02f, Offset(5f, 12f), scroll = true))
        assertNull(clasifica(1f, Offset(5f, 12f), scroll = false))
    }

    @Test
    fun `sin zoom, arrastrar en vertical es rueda`() {
        assertEquals(MultiMode.Scroll, clasifica(1f, Offset(0f, 25f), scroll = true))
        assertEquals(MultiMode.Scroll, clasifica(1.01f, Offset(3f, -40f), scroll = true)) // hacia arriba también
    }

    @Test
    fun `con zoom en la vista, arrastrar en vertical desplaza la vista`() {
        assertEquals(MultiMode.Pinch, clasifica(1f, Offset(0f, 25f), scroll = false))
    }

    @Test
    fun `cambiar de distancia es zoom aunque los dedos suban`() {
        assertEquals(MultiMode.Pinch, clasifica(1.2f, Offset(0f, 30f), scroll = true))
        assertEquals(MultiMode.Pinch, clasifica(0.8f, Offset(0f, 0f), scroll = true))
    }

    @Test
    fun `arrastrar en horizontal o en diagonal no es rueda`() {
        assertEquals(MultiMode.Pinch, clasifica(1f, Offset(30f, 0f), scroll = true))
        assertEquals(MultiMode.Pinch, clasifica(1f, Offset(20f, 25f), scroll = true))
    }
}
