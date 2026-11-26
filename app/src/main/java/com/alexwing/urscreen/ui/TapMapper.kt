package com.alexwing.urscreen.ui

/** Posición en píxeles del escritorio remoto. */
data class SourcePoint(val x: Int, val y: Int)

/**
 * Convierte un toque en la vista a una posición del escritorio remoto.
 *
 * La imagen se dibuja centrada y escalada para caber (ContentScale.Fit) en un contenedor de
 * [boxWidth] x [boxHeight]. [imageWidth] x [imageHeight] es el tamaño del bitmap decodificado y
 * [sourceWidth] x [sourceHeight] el de la zona del escritorio que representa (si es 0, se asume igual).
 * [originX] y [originY] son el origen de esa zona en el escritorio.
 *
 * Devuelve `null` si el toque cae en las bandas negras fuera de la imagen.
 */
fun mapTapToSource(
    tapX: Float, tapY: Float,
    boxWidth: Float, boxHeight: Float,
    imageWidth: Int, imageHeight: Int,
    sourceWidth: Int, sourceHeight: Int,
    originX: Int, originY: Int,
): SourcePoint? {
    if (imageWidth <= 0 || imageHeight <= 0 || boxWidth <= 0f || boxHeight <= 0f) return null
    val scale = minOf(boxWidth / imageWidth, boxHeight / imageHeight)
    val left = (boxWidth - imageWidth * scale) / 2f
    val top = (boxHeight - imageHeight * scale) / 2f
    val px = (tapX - left) / scale
    val py = (tapY - top) / scale
    if (px < 0f || py < 0f || px >= imageWidth || py >= imageHeight) return null
    val sx = if (sourceWidth > 0) sourceWidth.toFloat() / imageWidth else 1f
    val sy = if (sourceHeight > 0) sourceHeight.toFloat() / imageHeight else 1f
    return SourcePoint(originX + (px * sx).toInt(), originY + (py * sy).toInt())
}
