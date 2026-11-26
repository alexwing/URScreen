package com.alexwing.urscreen.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class SecretBoxTest {

    private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val box = SecretBox { key }

    private fun otherBox(): SecretBox {
        val otherKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        return SecretBox { otherKey }
    }

    @Test
    fun `ida y vuelta de un texto normal`() {
        val token = box.encrypt("hola mundo")

        assertEquals("hola mundo", box.decrypt(token))
    }

    @Test
    fun `ida y vuelta de un texto vacio`() {
        val token = box.encrypt("")

        assertEquals("", box.decrypt(token))
    }

    @Test
    fun `ida y vuelta de texto con acentos, CJK y emoji`() {
        val texto = "contraseña ñ 日本 🙂"

        assertEquals(texto, box.decrypt(box.encrypt(texto)))
    }

    @Test
    fun `dos cifrados del mismo texto dan tokens distintos y ambos descifran`() {
        val primero = box.encrypt("secreto")
        val segundo = box.encrypt("secreto")

        assertNotEquals(primero, segundo)
        assertEquals("secreto", box.decrypt(primero))
        assertEquals("secreto", box.decrypt(segundo))
    }

    @Test
    fun `el token no contiene el texto en claro`() {
        val token = box.encrypt("secreto123")

        assertFalse(token.contains("secreto123"))
        val bytes = java.util.Base64.getDecoder().decode(token)
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains("secreto123"))
    }

    @Test
    fun `manipular un caracter del token hace que decrypt devuelva null`() {
        val token = box.encrypt("secreto")
        val cambiado = if (token[0] == 'A') "B" + token.substring(1) else "A" + token.substring(1)

        assertNull(box.decrypt(cambiado))
    }

    @Test
    fun `un token cifrado con otra clave devuelve null`() {
        val token = otherBox().encrypt("secreto")

        assertNull(box.decrypt(token))
    }

    @Test
    fun `basura devuelve null`() {
        assertNull(box.decrypt("no es base64!!"))
        assertNull(box.decrypt(""))
        assertNull(box.decrypt("AAAA"))
    }

    @Test
    fun `el token tiene 12 bytes de IV, el texto y 16 de etiqueta`() {
        val token = box.encrypt("abcde")

        val bytes = java.util.Base64.getDecoder().decode(token)

        assertEquals(12 + 5 + 16, bytes.size)
    }
}
