package com.alexwing.urscreen.ui

/** Punto en coordenadas de la vista sin zoom. */
data class ViewPoint(val x: Float, val y: Float)

/**
 * Zoom y desplazamiento de la imagen. La imagen se dibuja llenando la vista y luego se aplica
 * graphicsLayer con origen arriba-izquierda: pantalla = vista * zoom + (panX, panY).
 */
data class ViewTransform(val zoom: Float = 1f, val panX: Float = 0f, val panY: Float = 0f) {

    /** Pasa un punto de pantalla a la vista sin zoom: ((x - panX) / zoom, (y - panY) / zoom). */
    fun toView(x: Float, y: Float): ViewPoint =
        ViewPoint((x - panX) / zoom, (y - panY) / zoom)

    /**
     * Aplica un gesto de dos dedos: `zoomChange` es un factor multiplicativo (1 = sin cambio) alrededor del punto
     * (centroidX, centroidY) en pantalla, y (dx, dy) el desplazamiento del centroide en píxeles de pantalla.
     * El zoom resultante se acota a [MIN_ZOOM, MAX_ZOOM] y el desplazamiento se acota para que la imagen
     * siempre cubra la vista completa (boxWidth x boxHeight): panX en [boxWidth * (1 - zoom), 0], igual con Y.
     * El punto de la imagen bajo el centroide debe quedar bajo el centroide (más dx, dy) salvo por el acotado.
     */
    fun transformedBy(
        zoomChange: Float,
        centroidX: Float,
        centroidY: Float,
        dx: Float,
        dy: Float,
        boxWidth: Float,
        boxHeight: Float,
    ): ViewTransform {
        val entradas = floatArrayOf(zoomChange, centroidX, centroidY, dx, dy, boxWidth, boxHeight)
        // zoomChange <= 0 no tiene sentido como factor de zoom: se trata como entrada inválida.
        if (entradas.any { !it.isFinite() } || zoomChange <= 0f || boxWidth <= 0f || boxHeight <= 0f) return this

        val nuevoZoom = (zoom * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
        // Punto de la imagen (en vista sin zoom) que está bajo el centroide antes del gesto.
        val imagenX = (centroidX - panX) / zoom
        val imagenY = (centroidY - panY) / zoom
        // Ese punto debe acabar en el centroide desplazado por (dx, dy).
        val nuevoPanX = (centroidX + dx - nuevoZoom * imagenX).coerceIn(boxWidth * (1f - nuevoZoom), 0f)
        val nuevoPanY = (centroidY + dy - nuevoZoom * imagenY).coerceIn(boxHeight * (1f - nuevoZoom), 0f)
        return ViewTransform(nuevoZoom, nuevoPanX, nuevoPanY)
    }

    companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 6f
    }
}
