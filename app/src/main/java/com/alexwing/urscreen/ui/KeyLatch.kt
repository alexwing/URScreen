package com.alexwing.urscreen.ui

/**
 * Lógica de "dejar una tecla pulsada" sin dedo encima: un doble toque rápido sobre un modificador (Mayús, Ctrl,
 * Alt...) lo deja fijado hasta el siguiente toque. Mantener el dedo apoyado sigue pulsando la tecla mientras dure,
 * para cualquier tecla; esto solo añade el fijado.
 *
 * Es una máquina de estados pura: el llamador pasa la hora y envía lo que se le indica.
 */
class KeyLatch(private val doubleTapMs: Long = DOUBLE_TAP_MS) {
    private val latched = mutableSetOf<String>()
    private val lastUp = mutableMapOf<String, Long>()
    private val secondTap = mutableSetOf<String>()
    private val releaseOnUp = mutableSetOf<String>()

    /** Teclas que están fijadas ahora mismo. */
    fun latchedKeys(): Set<String> = latched.toSet()

    fun isLatched(key: String): Boolean = key in latched

    /** Un dedo se apoya en la tecla. Devuelve `true` si hay que enviar `KeyDown`. */
    fun onDown(key: String, latchable: Boolean, now: Long): Boolean {
        if (key in latched) {
            releaseOnUp += key // ya está pulsada; se suelta al levantar el dedo
            return false
        }
        val previous = lastUp[key]
        if (latchable && previous != null && now - previous <= doubleTapMs) secondTap += key
        return true
    }

    /** El dedo se levanta. Devuelve `true` si hay que enviar `KeyUp`. */
    fun onUp(key: String, now: Long): Boolean {
        if (releaseOnUp.remove(key)) {
            latched -= key
            lastUp.remove(key)
            return true
        }
        if (secondTap.remove(key)) {
            latched += key
            lastUp.remove(key)
            return false // se queda pulsada
        }
        lastUp[key] = now
        return true
    }

    /** Olvida todo (por ejemplo al soltar todas las teclas). */
    fun clear() {
        latched.clear()
        lastUp.clear()
        secondTap.clear()
        releaseOnUp.clear()
    }

    companion object {
        const val DOUBLE_TAP_MS = 350L
    }
}
