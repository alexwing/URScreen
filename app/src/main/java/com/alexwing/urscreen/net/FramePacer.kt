package com.alexwing.urscreen.net

/**
 * Decide cuánto esperar antes de pedir el siguiente fotograma al servidor de Unified Remote, que pierde
 * un objeto GDI por cada fotograma servido. Si la pantalla no cambia, se pide con menos frecuencia; si el
 * usuario actúa, vuelve a ritmo máximo durante [boostMs].
 *
 * Lógica pura: el reloj llega como parámetro (`nowMs`) para poder probarla sin Android ni corrutinas.
 */
class FramePacer(
    private val stepMs: Long = 40,     // espera añadida por cada paso de reposo
    private val maxSteps: Int = 10,    // tope de pasos: espera máxima = stepMs * maxSteps = 400 ms
    private val boostMs: Long = 2000,  // tiempo a ritmo máximo tras una acción del usuario
) {
    private var idle: Int = 0
    private var lastHash: Long? = null
    private var boostUntil: Long = Long.MIN_VALUE

    /** Registra el fotograma recibido, identificado por un hash de su contenido. */
    @Synchronized
    fun onFrame(hash: Long) {
        val anterior = lastHash
        idle = if (anterior != null && hash == anterior) {
            minOf(idle + 1, maxSteps)
        } else {
            idle / 2
        }
        lastHash = hash
    }

    /** El usuario actuó (toque, tecla, rueda): ritmo máximo durante boostMs. */
    @Synchronized
    fun noteActivity(nowMs: Long) {
        boostUntil = nowMs + boostMs
        idle = 0
    }

    /** Cuánto esperar antes de pedir el siguiente fotograma, en ms. */
    @Synchronized
    fun waitMs(nowMs: Long): Long =
        if (nowMs < boostUntil) 0L else idle * stepMs
}
