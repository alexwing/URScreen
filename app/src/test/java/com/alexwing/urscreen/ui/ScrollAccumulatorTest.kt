package com.alexwing.urscreen.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrollAccumulatorTest {

    @Test
    fun `los restos fraccionarios se acumulan entre llamadas`() {
        val acumulador = ScrollAccumulator(100f)

        assertEquals(0, acumulador.add(60f))   // 60
        assertEquals(1, acumulador.add(60f))   // 120 -> 1 paso, resto 20
        assertEquals(0, acumulador.add(30f))   // 50
        assertEquals(1, acumulador.add(60f))   // 110 -> 1 paso, resto 10
    }

    @Test
    fun `el signo negativo produce pasos negativos y trunca hacia cero`() {
        val acumulador = ScrollAccumulator(100f)

        assertEquals(-2, acumulador.add(-250f))  // -2.5 -> -2, resto -50
        assertEquals(-1, acumulador.add(-50f))   // -100 -> -1, resto 0
        assertEquals(-1, acumulador.add(-150f))  // -150 -> -1, resto -50
        assertEquals(0, acumulador.add(20f))     // -30 -> 0, resto -30
    }

    @Test
    fun `reset descarta el resto acumulado`() {
        val acumulador = ScrollAccumulator(100f)
        assertEquals(0, acumulador.add(90f))

        acumulador.reset()

        assertEquals(0, acumulador.add(20f))   // sin reset serían 110 -> 1
        assertEquals(1, acumulador.add(80f))   // 100 -> 1
    }

    @Test
    fun `valores no finitos se ignoran sin alterar el resto`() {
        val acumulador = ScrollAccumulator(100f)
        assertEquals(0, acumulador.add(90f))

        assertEquals(0, acumulador.add(Float.NaN))
        assertEquals(0, acumulador.add(Float.POSITIVE_INFINITY))
        assertEquals(0, acumulador.add(Float.NEGATIVE_INFINITY))

        assertEquals(1, acumulador.add(15f))   // 105 -> 1
    }

    @Test(expected = IllegalArgumentException::class)
    fun `pixelsPerNotch cero lanza IllegalArgumentException`() {
        ScrollAccumulator(0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `pixelsPerNotch negativo lanza IllegalArgumentException`() {
        ScrollAccumulator(-5f)
    }
}
