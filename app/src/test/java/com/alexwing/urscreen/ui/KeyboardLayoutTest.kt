package com.alexwing.urscreen.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutTest {

    private val rows = SpanishKeyboard.rows
    private val keys = SpanishKeyboard.allKeys
    private val names = keys.map { it.name }

    @Test
    fun `hay seis filas`() {
        assertEquals(6, rows.size)
    }

    @Test
    fun `el Intro ISO ocupa la L de las filas 2 y 3 y cuadra el ancho de cada una`() {
        val total = SpanishKeyboard.ROW_UNITS.toDouble()

        listOf(0, 1, 4, 5).forEach { indice ->
            assertEquals("fila $indice", total, rows[indice].sumOf { it.width.toDouble() }, 0.01)
        }

        val fila2 = rows[IsoEnter.FIRST_ROW].sumOf { it.width.toDouble() }
        assertEquals("fila 2 sin la parte alta", IsoEnter.LEFT.toDouble(), fila2, 0.01)
        assertEquals("fila 2 con la parte alta", total, fila2 + IsoEnter.TOP_WIDTH, 0.01)

        val fila3 = rows[IsoEnter.FIRST_ROW + 1].sumOf { it.width.toDouble() }
        assertEquals("fila 3 sin el tallo", (IsoEnter.LEFT + IsoEnter.NOTCH).toDouble(), fila3, 0.01)
        assertEquals("fila 3 con el tallo", total, fila3 + IsoEnter.BOTTOM_WIDTH, 0.01)
    }

    @Test
    fun `todas las teclas tienen name no vacio y width positivo`() {
        keys.forEach { tecla ->
            assertTrue("name vacio en $tecla", tecla.name.isNotEmpty())
            assertTrue("width no positivo en ${tecla.name}", tecla.width > 0f)
        }
    }

    @Test
    fun `todas las teclas tienen label salvo space`() {
        keys.filter { it.name != "space" }.forEach { tecla ->
            assertTrue("label vacio en ${tecla.name}", tecla.label.isNotEmpty())
        }
        assertEquals("", keys.single { it.name == "space" }.label)
    }

    @Test
    fun `las letras de a a z aparecen exactamente una vez`() {
        ('a'..'z').forEach { letra ->
            assertEquals("letra $letra", 1, names.count { it == letra.toString() })
        }
    }

    @Test
    fun `los digitos del 0 al 9 aparecen exactamente una vez`() {
        ('0'..'9').forEach { digito ->
            assertEquals("digito $digito", 1, names.count { it == digito.toString() })
        }
    }

    @Test
    fun `todos los names son unicos, tambien return, que aparece una sola vez`() {
        val repetidos = names.groupingBy { it }.eachCount().filterValues { it > 1 }

        assertEquals(emptyMap<String, Int>(), repetidos)
        assertEquals(1, names.count { it == "return" })
    }

    @Test
    fun `solo los seis modificadores estan marcados como modifier`() {
        val modificadores = keys.filter { it.modifier }.map { it.name }.toSet()

        assertEquals(setOf("lshift", "rshift", "lctrl", "lwin", "lalt", "ralt"), modificadores)
    }

    @Test
    fun `existen las teclas especiales y de puntuacion del teclado espanol`() {
        val esperadas = listOf(
            "space", "back", "tab", "esc", "left", "right", "up", "down",
            "oem_3", "oem_102", "oem_comma", "oem_period", "oem_minus", "oem_plus",
        )

        esperadas.forEach { nombre ->
            assertTrue("falta $nombre", nombre in names)
        }
    }

    @Test
    fun `las teclas F1 a F12 tienen los names f1 a f12`() {
        val funcion = (1..12).map { "f$it" }

        funcion.forEach { nombre ->
            assertEquals(nombre, 1, names.count { it == nombre })
        }
    }

    @Test
    fun `el aire extra del Intro solo mueve el lado izquierdo y la muesca`() {
        val base = IsoEnter.polygon(keyWidth = 30f, keyHeight = 32f, inset = 1f)
        val aire = IsoEnter.polygon(keyWidth = 30f, keyHeight = 32f, inset = 1f, clearance = 3f)

        // Lado derecho, arriba y abajo: igual.
        assertEquals(base[1], aire[1])
        assertEquals(base[2], aire[2])
        // Los vértices de la izquierda se desplazan 3 a la derecha; los dos de la muesca suben 3.
        assertEquals(4f to 1f, aire[0])
        assertEquals(11.5f to 63f, aire[3])
        assertEquals(11.5f to 28f, aire[4])
        assertEquals(4f to 28f, aire[5])
    }

    @Test
    fun `el poligono del Intro ISO tiene 6 vertices dentro de la caja y con la muesca en su sitio`() {
        // W = 1.5*30 = 45, H = 32, N = 0.25*30 = 7.5; hueco del tallo = 1.25*30 - 2*1 = 35.5
        val puntos = IsoEnter.polygon(keyWidth = 30f, keyHeight = 32f, inset = 1f)

        assertEquals(6, puntos.size)
        assertEquals(1f to 1f, puntos[0])

        puntos.forEach { (x, y) ->
            assertTrue("x fuera de la caja: $x", x in 0f..45f)
            assertTrue("y fuera de la caja: $y", y in 0f..64f)
        }

        // Cuarto vértice: (N+1, 2H-1) = (8.5, 63)
        assertEquals(7.5f + 1f, puntos[3].first, 0.001f)
        assertEquals(63f, puntos[3].second, 0.001f)

        // Quinto vértice: (N+1, H-1) = (8.5, 31)
        assertEquals(8.5f, puntos[4].first, 0.001f)
        assertEquals(31f, puntos[4].second, 0.001f)

        // La muesca queda a la izquierda del borde derecho: el hueco del tallo bajo mide 35.5
        assertTrue(puntos[4].first < puntos[2].first)
        assertEquals(35.5f, (puntos[2].first - puntos[3].first), 0.001f)
    }
}
