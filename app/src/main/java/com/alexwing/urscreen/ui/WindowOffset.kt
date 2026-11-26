package com.alexwing.urscreen.ui

import androidx.compose.ui.geometry.Offset

/**
 * Limita la posición de una ventana flotante: se puede sacar por la izquierda, la derecha y abajo (desbordarse),
 * pero siempre queda a la vista un trozo de [minVisible] píxeles de ancho y su barra de título, para poder volver a
 * agarrarla. Por arriba no se sale, para que la barra de título no desaparezca.
 */
fun clampWindowOffset(
    offset: Offset,
    windowWidth: Float, viewWidth: Float, viewHeight: Float,
    titleHeight: Float, minVisible: Float,
): Offset {
    if (viewWidth <= 0f || viewHeight <= 0f) return offset
    val visible = minOf(minVisible, windowWidth)
    val x = offset.x.coerceIn(visible - windowWidth, viewWidth - visible)
    val y = offset.y.coerceIn(0f, maxOf(0f, viewHeight - titleHeight))
    return Offset(x, y)
}

/**
 * Limita la posición de una barra flotante que por defecto va pegada arriba y centrada. `offset` se mide desde esa
 * posición por defecto. La barra no sale de la vista por ningún lado:
 * x en [-(viewWidth - barWidth)/2, (viewWidth - barWidth)/2] e y en [0, viewHeight - barHeight].
 * Si la barra es más grande que la vista en un eje, ese eje queda a 0. Sin tamaño de vista (<= 0), devuelve el offset tal cual.
 */
fun clampBarOffset(offset: Offset, barWidth: Float, barHeight: Float, viewWidth: Float, viewHeight: Float): Offset {
    if (viewWidth <= 0f || viewHeight <= 0f) return offset
    val maxX = (viewWidth - barWidth) / 2f
    val maxY = viewHeight - barHeight
    val x = if (maxX <= 0f) 0f else offset.x.coerceIn(-maxX, maxX)
    val y = if (maxY <= 0f) 0f else offset.y.coerceIn(0f, maxY)
    return Offset(x, y)
}
