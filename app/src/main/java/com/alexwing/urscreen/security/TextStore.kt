package com.alexwing.urscreen.security

import android.content.SharedPreferences

/** Almacén clave-texto mínimo: lo implementan las SharedPreferences y, en las pruebas, un mapa en memoria. */
interface TextStore {
    fun get(name: String): String?

    /** `null` borra la entrada. */
    fun set(name: String, value: String?)
}

/** [TextStore] sobre SharedPreferences (usa apply()). */
class PrefsTextStore(private val prefs: SharedPreferences) : TextStore {

    override fun get(name: String): String? = prefs.getString(name, null)

    override fun set(name: String, value: String?) {
        val editor = prefs.edit()
        if (value == null) {
            editor.remove(name)
        } else {
            editor.putString(name, value)
        }
        editor.apply()
    }
}
