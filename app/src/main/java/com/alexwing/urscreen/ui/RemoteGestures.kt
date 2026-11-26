package com.alexwing.urscreen.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/** Qué hacer con cada gesto sobre la pantalla remota. Todas las posiciones son de la vista, en píxeles. */
interface RemoteGestureHandler {
    /** Un dedo, toque corto. */
    fun onTap(position: Offset, viewSize: IntSize)

    /** Un dedo quieto durante [RemoteGestures.LONG_PRESS_MS]. */
    fun onLongPress(position: Offset, viewSize: IntSize)

    /** Dos dedos: `zoom` es el factor respecto al evento anterior, `pan` el desplazamiento del centro. */
    fun onPinchPan(centroid: Offset, zoom: Float, pan: Offset, viewSize: IntSize)

    /** Se empieza a hacer rueda con tres dedos; `centroid` es el centro de los dedos. La rueda actúa sobre lo que hay bajo el cursor del PC. */
    fun onScrollStart(centroid: Offset, viewSize: IntSize)

    /** Rueda (dos dedos sin zoom, o tres o más): desplazamiento vertical del centro en este evento (positivo = hacia abajo). */
    fun onScroll(deltaY: Float, viewSize: IntSize)

    /**
     * ¿Hacen rueda dos dedos que se arrastran en vertical? Sí cuando la vista no tiene zoom (no hay nada que desplazar,
     * y los tres dedos los suele quitar el sistema para capturar la pantalla); con zoom, dos dedos desplazan la vista.
     * Se consulta al decidir qué gesto es, al empezar.
     */
    val twoFingerScroll: Boolean
}

object RemoteGestures {
    /** Tiempo con el dedo quieto para que cuente como clic derecho. */
    const val LONG_PRESS_MS = 2000L

    /** Cuánto debe cambiar la escala de dos dedos, sin que se muevan, para empezar a contar como zoom. */
    const val ZOOM_START = 0.05f
}

private enum class Phase { Pending, Tap, Drag, Multi }

/**
 * Reconoce los gestos de control del escritorio: toque = clic, pulsación larga (2 s) = clic derecho, dos dedos =
 * zoom y desplazamiento de la vista (o rueda si arrastran en vertical sin zoom, ver
 * [RemoteGestureHandler.twoFingerScroll]), tres dedos = rueda. Un dedo que se arrastra no hace nada (por ahora).
 *
 * Con varios dedos se espera a ver qué hacen: si llega un tercero antes de que los dos primeros se muevan más
 * allá del umbral de toque, es rueda; con dos, [classifyTwoFingers] decide entre zoom/desplazamiento y rueda. Una
 * vez elegido el modo se mantiene hasta que se levantan todos los dedos.
 */
fun Modifier.remoteGestures(handler: RemoteGestureHandler): Modifier = pointerInput(handler) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val slop = viewConfiguration.touchSlop
        var phase = Phase.Pending

        // Fase 1: un dedo apoyado. Termina en toque, arrastre, más dedos, o expira como pulsación larga.
        val finished = withTimeoutOrNull(RemoteGestures.LONG_PRESS_MS) {
            while (phase == Phase.Pending) {
                val event = awaitPointerEvent()
                val pressed = event.changes.count { it.pressed }
                when {
                    pressed == 0 -> phase = Phase.Tap
                    pressed >= 2 -> phase = Phase.Multi
                    else -> {
                        val own = event.changes.firstOrNull { it.id == down.id }
                        if (own != null && (own.position - down.position).getDistance() > slop) phase = Phase.Drag
                    }
                }
            }
        }
        if (finished == null) {
            handler.onLongPress(down.position, size)
            awaitAllUp()
            return@awaitEachGesture
        }

        // Un dedo que se desliza no hace nada, pero si se le une otro pasa a ser un gesto de varios dedos.
        if (phase == Phase.Drag) phase = awaitSecondFingerOrUp()

        when (phase) {
            Phase.Tap -> handler.onTap(down.position, size)
            Phase.Multi -> trackMultiTouch(handler, size, slop)
            else -> Unit
        }
        awaitAllUp()
    }
}

/** Con un dedo arrastrándose: espera a que se levante (`Tap` no, `Drag` sí) o a que llegue otro dedo (`Multi`). */
private suspend fun AwaitPointerEventScope.awaitSecondFingerOrUp(): Phase {
    while (true) {
        val pressed = awaitPointerEvent().changes.count { it.pressed }
        if (pressed == 0) return Phase.Drag
        if (pressed >= 2) return Phase.Multi
    }
}

/** Varios dedos: zoom y desplazamiento (dos) o rueda (tres). Ver [remoteGestures]. */
private suspend fun AwaitPointerEventScope.trackMultiTouch(
    handler: RemoteGestureHandler,
    viewSize: IntSize,
    slop: Float,
) {
    var mode = MultiMode.Undecided
    var zoomAccumulated = 1f
    var panAccumulated = Offset.Zero
    // El evento que nos trajo hasta aquí ya tiene dos dedos; no hay desplazamiento acumulado todavía.
    while (true) {
        val event = awaitPointerEvent()
        val pressed = event.changes.count { it.pressed }
        if (pressed == 0) return
        val zoom = event.calculateZoom()
        val pan = event.calculatePan()
        when (mode) {
            MultiMode.Undecided -> if (pressed >= 3) {
                mode = MultiMode.Scroll
                handler.onScrollStart(event.calculateCentroid(useCurrent = true), viewSize)
                handler.onScroll(pan.y, viewSize)
            } else {
                zoomAccumulated *= zoom
                panAccumulated += pan
                when (classifyTwoFingers(zoomAccumulated, panAccumulated, slop, handler.twoFingerScroll)) {
                    MultiMode.Scroll -> {
                        mode = MultiMode.Scroll
                        handler.onScrollStart(event.calculateCentroid(useCurrent = true), viewSize)
                        handler.onScroll(panAccumulated.y, viewSize)
                    }
                    MultiMode.Pinch -> {
                        mode = MultiMode.Pinch
                        handler.onPinchPan(event.calculateCentroid(useCurrent = true), zoomAccumulated, panAccumulated, viewSize)
                    }
                    else -> Unit
                }
            }
            MultiMode.Pinch -> if (pressed >= 2) {
                handler.onPinchPan(event.calculateCentroid(useCurrent = true), zoom, pan, viewSize)
            }
            MultiMode.Scroll -> handler.onScroll(pan.y, viewSize)
        }
        event.changes.forEach { it.consume() }
    }
}

internal enum class MultiMode { Undecided, Pinch, Scroll }

/**
 * Qué son dos dedos que llevan [zoomAccumulated] de cambio de escala y [panAccumulated] de desplazamiento del centro
 * desde que se juntaron: `null` mientras no se mueven lo bastante, [MultiMode.Scroll] si arrastran en vertical sin
 * cambiar de distancia y [scrollEnabled] (vista sin zoom), y [MultiMode.Pinch] (zoom y desplazamiento) en los demás casos.
 */
internal fun classifyTwoFingers(zoomAccumulated: Float, panAccumulated: Offset, slop: Float, scrollEnabled: Boolean): MultiMode? {
    val zoomed = abs(zoomAccumulated - 1f) > RemoteGestures.ZOOM_START
    val moved = panAccumulated.getDistance() > slop
    if (!zoomed && !moved) return null
    if (zoomed) return MultiMode.Pinch
    val clearlyVertical = abs(panAccumulated.y) > 2f * abs(panAccumulated.x)
    return if (scrollEnabled && clearlyVertical) MultiMode.Scroll else MultiMode.Pinch
}

/** Espera a que no quede ningún dedo apoyado, para no reconocer a medias el siguiente gesto. */
private suspend fun AwaitPointerEventScope.awaitAllUp() {
    while (currentEvent.changes.any { it.pressed }) {
        awaitPointerEvent()
    }
}
