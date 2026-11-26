package com.alexwing.urscreen.security

import android.content.Context

/** Guarda la contraseña del servidor cifrada. Lo que se escribe en el almacén nunca es la contraseña en claro. */
class CredentialStore(
    private val store: TextStore,
    private val box: SecretBox,
    private val name: String = "password"
) {

    /** La contraseña guardada, o "" si no hay ninguna o no se puede descifrar (clave perdida, datos dañados): nunca lanza. */
    fun loadPassword(): String {
        val token = store.get(name) ?: return ""
        return box.decrypt(token) ?: ""
    }

    /** Guarda la contraseña cifrada; una contraseña vacía borra la guardada. Si el cifrado falla, borra la entrada y no lanza. */
    fun savePassword(password: String) {
        if (password.isEmpty()) {
            clear()
            return
        }
        val token = try {
            box.encrypt(password)
        } catch (e: Exception) {
            null
        }
        store.set(name, token)
    }

    /** Borra la contraseña guardada. */
    fun clear() {
        store.set(name, null)
    }
}

/** [CredentialStore] de la app: SharedPreferences privadas y clave en el Android Keystore. */
fun credentialStore(context: Context): CredentialStore =
    CredentialStore(
        PrefsTextStore(context.getSharedPreferences("urscreen_secure", 0)),
        SecretBox { KeystoreKey.get() }
    )
