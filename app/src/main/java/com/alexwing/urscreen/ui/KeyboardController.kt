package com.alexwing.urscreen.ui

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Quién recibe las pulsaciones del teclado en pantalla. */
interface KeyboardListener {
    fun onKeyDown(key: KeyDef)
    fun onKeyUp(key: KeyDef)
}

/**
 * Une el teclado en pantalla con el PC: una tecla se pulsa al apoyar el dedo y se suelta al levantarlo, de modo que
 * se mantiene pulsada mientras se mantenga el dedo (Windows se encarga de repetirla). Los modificadores admiten además
 * doble toque para quedarse fijados ([KeyLatch]).
 */
class KeyboardController(private val vm: ViewerViewModel) : KeyboardListener {
    private val latch = KeyLatch()

    /** Teclas fijadas ahora mismo, para dibujarlas encendidas. */
    var latched: Set<String> by mutableStateOf(emptySet())
        private set

    override fun onKeyDown(key: KeyDef) {
        if (latch.onDown(key.name, key.modifier, SystemClock.uptimeMillis())) vm.keyDown(key.name)
        latched = latch.latchedKeys()
    }

    override fun onKeyUp(key: KeyDef) {
        if (latch.onUp(key.name, SystemClock.uptimeMillis())) vm.keyUp(key.name)
        latched = latch.latchedKeys()
    }

    /** Suelta todo lo que haya pulsado o fijado, para no dejar teclas atascadas en el PC. */
    fun releaseAll() {
        latch.clear()
        latched = emptySet()
        vm.releaseAllKeys()
    }
}
