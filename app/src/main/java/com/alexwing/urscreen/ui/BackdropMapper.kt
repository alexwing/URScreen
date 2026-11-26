package com.alexwing.urscreen.ui

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Trozo del fotograma que queda bajo una ventana flotante: qué rectángulo del bitmap (`src*`, en píxeles del
 * bitmap) hay que dibujar y dónde (`dst*`, en píxeles locales de la ventana). Sirve para pintar el fondo
 * esmerilado del teclado copiando solo la parte visible bajo él, en vez de difuminar la pantalla entera.
 */
data class BackdropRegion(
    val srcX: Int, val srcY: Int, val srcWidth: Int, val srcHeight: Int,
    val dstX: Int, val dstY: Int, val dstWidth: Int, val dstHeight: Int,
)

/**
 * @param winX posición de la ventana en pantalla (píxeles), [winWidth] x [winHeight] su tamaño.
 * @param transform zoom y desplazamiento con que se dibuja el fotograma (ver [ViewTransform]).
 * @param boxWidth tamaño de la vista que contiene el fotograma, donde éste se ajusta con `ContentScale.Fit`.
 *
 * Devuelve `null` si bajo la ventana no hay nada del fotograma (todo bandas negras o fuera de pantalla).
 */
fun backdropRegion(
    winX: Float, winY: Float, winWidth: Float, winHeight: Float,
    transform: ViewTransform,
    boxWidth: Float, boxHeight: Float,
    imageWidth: Int, imageHeight: Int,
): BackdropRegion? {
    if (winWidth <= 0f || winHeight <= 0f || boxWidth <= 0f || boxHeight <= 0f ||
        imageWidth <= 0 || imageHeight <= 0 || transform.zoom <= 0f
    ) return null
    val fit = minOf(boxWidth / imageWidth, boxHeight / imageHeight)
    val left = (boxWidth - imageWidth * fit) / 2f
    val top = (boxHeight - imageHeight * fit) / 2f

    // pantalla -> píxeles del bitmap
    fun imageX(screenX: Float) = ((screenX - transform.panX) / transform.zoom - left) / fit
    fun imageY(screenY: Float) = ((screenY - transform.panY) / transform.zoom - top) / fit

    val x0 = max(0f, imageX(winX))
    val x1 = min(imageWidth.toFloat(), imageX(winX + winWidth))
    val y0 = max(0f, imageY(winY))
    val y1 = min(imageHeight.toFloat(), imageY(winY + winHeight))
    if (x1 <= x0 || y1 <= y0) return null

    val srcX = floor(x0).toInt()
    val srcY = floor(y0).toInt()
    val srcRight = min(imageWidth, ceil(x1).toInt())
    val srcBottom = min(imageHeight, ceil(y1).toInt())

    // píxeles del bitmap -> locales de la ventana
    fun windowX(imageX: Int) = ((imageX * fit + left) * transform.zoom + transform.panX) - winX
    fun windowY(imageY: Int) = ((imageY * fit + top) * transform.zoom + transform.panY) - winY

    val dstX = windowX(srcX).roundToInt()
    val dstY = windowY(srcY).roundToInt()
    val dstWidth = windowX(srcRight).roundToInt() - dstX
    val dstHeight = windowY(srcBottom).roundToInt() - dstY
    if (srcRight <= srcX || srcBottom <= srcY || dstWidth <= 0 || dstHeight <= 0) return null
    return BackdropRegion(srcX, srcY, srcRight - srcX, srcBottom - srcY, dstX, dstY, dstWidth, dstHeight)
}
