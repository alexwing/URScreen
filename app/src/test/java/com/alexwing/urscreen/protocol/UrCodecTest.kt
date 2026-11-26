package com.alexwing.urscreen.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Pruebas contra paquetes reales capturados entre la app oficial de Android y el servidor. */
class UrCodecTest {

    private fun hex(s: String): ByteArray = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private fun fixture(name: String): ByteArray =
        hex(UrFixtures.all.first { it.first == name }.second)

    private fun decoded(name: String) = UrCodec.decode(fixture(name))

    @Test
    fun `todos los paquetes reales sobreviven a decodificar y recodificar byte a byte`() {
        for ((name, h) in UrFixtures.all) {
            val original = hex(h)
            val again = UrCodec.encode(UrCodec.decode(original))
            assertArrayEquals("paquete $name", original, again)
        }
    }

    @Test
    fun `el handshake del servidor se interpreta`() {
        val p = decoded("s_handshake")
        assertEquals(UrAction.HANDSHAKE, p.int("Action"))
        assertEquals(UrSecurity.DISABLED, p.int("Security"))
        assertEquals(1, p.int("Version"))
        assertNotNull(p.string("Password"))
        assertNotNull(p.string("Session"))
        assertNotNull(p.obj("Capabilities"))
    }

    @Test
    fun `el layout de Unified Screen se interpreta con su lista de acciones`() {
        val p = decoded("s_load_layout")
        assertEquals("Unified.Screen", p.string("ID"))
        val layout = p.obj("Layout")!!
        assertEquals("update", layout.list("Actions")!!.single().string("Name"))
        assertEquals(20, layout.obj("Default")!!.int("Type"))
    }

    @Test
    fun `los mensajes del cliente salen identicos a los de la app oficial`() {
        val source = decoded("c_handshake").string("Source")!!
        assertArrayEquals(fixture("c_handshake"), UrCodec.encode(UrMessages.handshake(source, decoded("c_handshake").string("Password")!!)))
        assertArrayEquals(fixture("c_authenticate"), UrCodec.encode(UrMessages.authenticate(source, decoded("c_authenticate").string("Password")!!)))
        assertArrayEquals(fixture("c_hash"), UrCodec.encode(UrMessages.hash(source)))
        assertArrayEquals(fixture("c_load"), UrCodec.encode(UrMessages.load(source, UrMessages.SCREEN_REMOTE)))
        assertArrayEquals(fixture("c_state"), UrCodec.encode(UrMessages.state(source, UrMessages.SCREEN_REMOTE)))
        assertArrayEquals(fixture("c_update"), UrCodec.encode(UrMessages.update(source, monitor = 0)))
        assertArrayEquals(fixture("c_click"), UrCodec.encode(UrMessages.click(source)))

        val move = decoded("c_moveto").obj("Run")!!.obj("Extras")!!.list("Values")!!
            .associate { it.string("Key")!! to it.string("Value")!! }
        assertArrayEquals(
            fixture("c_moveto"),
            UrCodec.encode(UrMessages.moveTo(source, move.getValue("X").toInt(), move.getValue("Y").toInt(), move.getValue("Display").toInt())),
        )
    }

    @Test
    fun `un binario y un entero negativo hacen el viaje de ida y vuelta`() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0x10, 'J'.code.toByte())
        val packet = urObject {
            str("ID", "Unified.Screen")
            byte("Action", UrAction.STATE)
            obj("Layout") {
                list("Controls") {
                    item { str("ID", "cursor"); int("X", 4049); int("Y", -3) }
                    item { str("ID", "back"); bin("Image", jpeg); int("W", 2560); int("H", 1080); int("X", 0); int("Y", 0) }
                }
            }
        }
        val back = UrCodec.decode(UrCodec.encode(packet))
        assertEquals(packet, back)

        val frame = UrMessages.parseFrame(back)!!
        assertArrayEquals(jpeg, frame.jpeg)
        assertEquals(2560, frame.width)
        assertEquals(1080, frame.height)
        assertEquals(4049, frame.cursorX)
        assertEquals(-3, frame.cursorY)
    }

    @Test
    fun `un paquete sin imagen no es un fotograma`() {
        assertNull(UrMessages.parseFrame(decoded("s_state_monitors")))
        assertNull(UrMessages.parseFrame(decoded("s_hash")))
    }

    @Test
    fun `un paquete truncado se rechaza con un error claro`() {
        val full = fixture("s_handshake")
        try {
            UrCodec.decode(full.copyOf(full.size - 5))
            fail("debía fallar")
        } catch (e: UrProtocolException) {
            assertTrue(e.message!!.contains("longitud"))
        }
    }

    @Test
    fun `flags desconocidos se rechazan en vez de interpretarse`() {
        val tampered = fixture("c_hash").also { it[4] = 1 }
        try {
            UrCodec.decode(tampered)
            fail("debía fallar")
        } catch (e: UrProtocolException) {
            assertTrue(e.message!!.contains("flags"))
        }
    }

    @Test
    fun `un tipo de campo desconocido se rechaza`() {
        // [len][00 01] tipo 0x7F, clave "k", y un 00 de cierre
        val body = byteArrayOf(0, 1, 0x7F, 'k'.code.toByte(), 0, 0)
        val packet = byteArrayOf(0, 0, 0, body.size.toByte()) + body
        try {
            UrCodec.decode(packet)
            fail("debía fallar")
        } catch (e: UrProtocolException) {
            assertTrue(e.message!!.contains("desconocido"))
        }
    }
}
