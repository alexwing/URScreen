package com.alexwing.urscreen.net

import com.alexwing.urscreen.protocol.UrProtocolException
import com.alexwing.urscreen.protocol.UrSecurity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * La prueba de autenticación sigue la fórmula de la interfaz web del servidor. Los valores esperados están calculados
 * aparte con SHA-256 sobre UTF-8 (hexadecimal en minúsculas), no con este código.
 */
class UrAuthTest {
    private val serverNonce = "srv-nonce"
    private val clientNonce = "cli-nonce"

    @Test
    fun `con contrasena es sha256 de nonce servidor, contrasena y nonce cliente`() {
        val esperado = "916a5d47a18b44fea4252e5eaa36a3ce4d6284e127a1addced94366f2beebf6b"
        assertEquals(esperado, authProof(UrSecurity.ANONYMOUS, serverNonce, clientNonce, "secreto", null))
        // Con la seguridad desactivada se calcula igual (el servidor la ignora).
        assertEquals(esperado, authProof(UrSecurity.DISABLED, serverNonce, clientNonce, "secreto", null))
    }

    @Test
    fun `la contrasena vacia es la del servidor sin contrasena`() {
        assertEquals(
            "a54e4288eebf698636006398440c18ec1f891563bc7e913ff4195ba2bc7a1a29",
            authProof(UrSecurity.DISABLED, serverNonce, clientNonce, "", null),
        )
    }

    @Test
    fun `en modo usuarios el usuario y dos puntos van delante de la contrasena`() {
        assertEquals(
            "dc9dcdeec94dd82b8280d537be617d10c1708e2f2b41490537300e596929ca30",
            authProof(UrSecurity.USERS, serverNonce, clientNonce, "secreto", "ana"),
        )
    }

    @Test
    fun `los caracteres no ASCII se codifican en UTF-8`() {
        assertEquals(
            "e013685b49668ff3f9e5f51f4a1b8ba65fb936e5f3f1652fedebb2903688a083",
            authProof(UrSecurity.ANONYMOUS, serverNonce, clientNonce, "contraseña", null),
        )
    }

    @Test
    fun `en modo usuarios sin usuario se pide el usuario`() {
        try {
            authProof(UrSecurity.USERS, serverNonce, clientNonce, "secreto", null)
            fail("debía pedir el usuario")
        } catch (e: UrUserRequiredException) {
            assertTrue(e is UrAuthException)
        }
    }

    @Test
    fun `los modos que no se soportan fallan con un error claro`() {
        for (modo in listOf(UrSecurity.INVALID, UrSecurity.ENABLE)) {
            try {
                authProof(modo, serverNonce, clientNonce, "", null)
                fail("modo $modo debía fallar")
            } catch (e: UrAuthException) {
                assertTrue(e.message!!.isNotEmpty())
            }
        }
        try {
            authProof(999, serverNonce, clientNonce, "", null)
            fail("un modo desconocido debía fallar")
        } catch (e: UrProtocolException) {
            assertTrue(e.message!!.contains("999"))
        }
    }
}
