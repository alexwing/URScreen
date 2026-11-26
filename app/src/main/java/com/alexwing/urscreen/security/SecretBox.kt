package com.alexwing.urscreen.security

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Cifra y descifra textos con AES-GCM. La clave la da `key` en cada operación, así se puede probar sin Keystore. */
class SecretBox(private val key: () -> SecretKey) {

    /** Devuelve Base64 (sin saltos de línea, java.util.Base64) de: IV de 12 bytes aleatorio + texto cifrado con su etiqueta GCM de 128 bits. Cada llamada usa un IV nuevo. El texto se pasa a bytes en UTF-8. */
    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // Sin IV explícito: el Keystore de Android genera el IV al cifrar, así que se lee del Cipher.
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return java.util.Base64.getEncoder().encodeToString(iv + cipherText)
    }

    /** Descifra lo que dio [encrypt]. Devuelve null (nunca lanza) si no es Base64 válido, es demasiado corto, ha sido manipulado, o la clave no es la que lo cifró. */
    fun decrypt(token: String): String? {
        return try {
            val raw = java.util.Base64.getDecoder().decode(token)
            if (raw.size < IV_BYTES + TAG_BYTES) return null
            val iv = raw.copyOfRange(0, IV_BYTES)
            val cipherText = raw.copyOfRange(IV_BYTES, raw.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val TAG_BYTES = TAG_BITS / 8
    }
}
