package com.alexwing.urscreen.ui

/**
 * Convierte el arrastre continuo de los dedos (píxeles) en pasos enteros de rueda, guardando el resto
 * fraccionario entre llamadas para que arrastres lentos también acaben dando pasos.
 */
class ScrollAccumulator(private val pixelsPerNotch: Float) {

    /** Resto en píxeles que aún no forma un paso completo. */
    private var resto = 0f

    init {
        require(pixelsPerNotch > 0f) { "pixelsPerNotch debe ser > 0, era $pixelsPerNotch" }
    }

    /** Suma `deltaPixels` y devuelve los pasos completos acumulados (con signo, truncando hacia 0); conserva el resto. */
    fun add(deltaPixels: Float): Int {
        if (!deltaPixels.isFinite()) return 0
        val total = resto + deltaPixels
        val pasos = (total / pixelsPerNotch).toInt()
        resto = total - pasos * pixelsPerNotch
        return pasos
    }

    /** Descarta el resto acumulado. */
    fun reset() {
        resto = 0f
    }
}
