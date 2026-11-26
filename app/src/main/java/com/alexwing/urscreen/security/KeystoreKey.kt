package com.alexwing.urscreen.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Clave AES-256 guardada en el Android Keystore: no se puede exportar y no sale del dispositivo. */
object KeystoreKey {
    const val DEFAULT_ALIAS = "urscreen-credentials"

    private const val PROVIDER = "AndroidKeyStore"

    /** Devuelve la clave con ese alias, creándola si no existe. */
    @Synchronized
    fun get(alias: String = DEFAULT_ALIAS): SecretKey {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }

        // Sin autenticación de usuario: la app debe poder abrirse sola.
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
            .apply { init(spec) }
            .generateKey()
    }
}
