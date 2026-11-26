package com.alexwing.urscreen.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class CredentialStoreTest {

    /** [TextStore] en memoria para las pruebas. */
    private class MapTextStore(val map: MutableMap<String, String> = mutableMapOf()) : TextStore {
        override fun get(name: String): String? = map[name]

        override fun set(name: String, value: String?) {
            if (value == null) map.remove(name) else map[name] = value
        }
    }

    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private val key = newKey()
    private val backing = MapTextStore()
    private val store = CredentialStore(backing, SecretBox { key })

    @Test
    fun `guardar y cargar devuelve la clave`() {
        store.savePassword("clave123")

        assertEquals("clave123", store.loadPassword())
    }

    @Test
    fun `sin nada guardado la clave es vacia`() {
        assertEquals("", store.loadPassword())
    }

    @Test
    fun `guardar vacio borra la entrada del almacen`() {
        store.savePassword("clave123")

        store.savePassword("")

        assertNull(backing.get("password"))
        assertEquals("", store.loadPassword())
    }

    @Test
    fun `guardar otra clave sobrescribe la anterior`() {
        store.savePassword("primera")

        store.savePassword("segunda")

        assertEquals("segunda", store.loadPassword())
    }

    @Test
    fun `lo almacenado no contiene la clave en claro`() {
        store.savePassword("secreto123")

        val guardado = backing.get("password")

        assertNotNull(guardado)
        assertFalse(guardado!!.contains("secreto123"))
    }

    @Test
    fun `si el almacen tiene basura la clave es vacia sin lanzar`() {
        backing.set("password", "no es base64!!")

        assertEquals("", store.loadPassword())
    }

    @Test
    fun `si la caja usa otra clave la clave guardada es vacia`() {
        store.savePassword("clave123")
        val otraCaja = CredentialStore(backing, SecretBox { newKey() })

        assertEquals("", otraCaja.loadPassword())
    }

    @Test
    fun `clear borra la clave guardada`() {
        store.savePassword("clave123")

        store.clear()

        assertNull(backing.get("password"))
        assertEquals("", store.loadPassword())
    }
}
