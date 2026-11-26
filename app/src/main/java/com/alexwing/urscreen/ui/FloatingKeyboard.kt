package com.alexwing.urscreen.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.hypot
import kotlin.math.roundToInt

private val KeyWidth = 30.dp
private val KeyHeight = 32.dp
private val TitleHeight = 26.dp
private val WindowPadding = 4.dp

/** Aire extra del Intro con las teclas de su izquierda, además del hueco normal entre teclas. */
private val EnterClearance = 3.dp
private val MinVisible = 72.dp

private val LitKey = Color(0xCC6EA8FF)
private val IdleKey = Color(0x40FFFFFF)

/**
 * Teclado flotante, como una ventana propia del escritorio: se arrastra por la barra de título, puede salirse de
 * la pantalla, es semitransparente y su fondo es un trozo difuminado del propio fotograma que queda debajo.
 *
 * Cada tecla se pulsa al apoyar el dedo y se suelta al levantarlo, y cada una atiende a su dedo por separado, así
 * que se puede mantener Mayús con un dedo y pulsar otra tecla con otro.
 *
 * @param offset posición de la ventana en la vista, en píxeles. Se pide moverla con [onOffsetChange].
 */
@Composable
fun FloatingKeyboard(
    offset: Offset,
    onOffsetChange: (Offset) -> Unit,
    viewSize: IntSize,
    frame: ScreenFrame?,
    transform: ViewTransform,
    latched: Set<String>,
    listener: KeyboardListener,
    onClose: () -> Unit,
) {
    val density = LocalDensity.current
    var windowSize by remember { mutableStateOf(IntSize.Zero) }
    val shape = RoundedCornerShape(10.dp)

    Box(
        Modifier
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .onSizeChanged { windowSize = it }
            .clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.25f), shape)
            // Cubre también los huecos entre teclas: ningún toque debe llegar al escritorio que hay debajo.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
    ) {
        FrostedBackdrop(frame, transform, viewSize, offset, Modifier.matchParentSize())

        Box(Modifier.padding(WindowPadding)) {
            // Intro: una sola tecla en L que ocupa dos filas. Va debajo de las filas para que, en la muesca de la L,
            // el toque sea de la tecla Ç que hay encima.
            EnterKey(
                latched = IsoEnter.key.name in latched,
                listener = listener,
                modifier = Modifier.offset(KeyWidth * IsoEnter.LEFT, TitleHeight + KeyHeight * IsoEnter.FIRST_ROW),
            )
            // Ancho fijo de una fila completa: sin esto, la barra de título (fillMaxWidth) estiraría la ventana a toda la vista.
            Column(Modifier.width(KeyWidth * SpanishKeyboard.ROW_UNITS)) {
                TitleBar(
                    offset = offset,
                    onDrag = { target ->
                        with(density) {
                            onOffsetChange(
                                clampWindowOffset(
                                    target,
                                    windowSize.width.toFloat(), viewSize.width.toFloat(), viewSize.height.toFloat(),
                                    TitleHeight.toPx(), MinVisible.toPx(),
                                ),
                            )
                        }
                    },
                    onClose = onClose,
                )
                for (row in SpanishKeyboard.rows) {
                    Row {
                        for (key in row) KeyCap(key, key.name in latched, listener)
                    }
                }
            }
        }
    }
}

/** Intro del teclado ISO: una sola tecla en forma de L (ver [IsoEnter]), con el toque limitado a esa forma. */
@Composable
private fun EnterKey(latched: Boolean, listener: KeyboardListener, modifier: Modifier = Modifier) {
    var pressed by remember { mutableStateOf(false) }
    val currentListener by rememberUpdatedState(listener)
    val density = LocalDensity.current
    val shape = remember(density) {
        val inset = with(density) { 1.dp.toPx() }
        val clearance = with(density) { EnterClearance.toPx() }
        val radius = with(density) { 5.dp.toPx() }
        GenericShape { size, _ ->
            roundedPolygon(IsoEnter.polygon(size.width / IsoEnter.TOP_WIDTH, size.height / 2f, inset, clearance), radius)
        }
    }
    val key = IsoEnter.key
    Box(
        modifier
            .size(KeyWidth * IsoEnter.TOP_WIDTH, KeyHeight * 2f)
            .clip(shape)
            .background(if (pressed || latched) LitKey else IdleKey)
            .pointerInput(key) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    pressed = true
                    currentListener.onKeyDown(key)
                    try {
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    } finally {
                        pressed = false
                        currentListener.onKeyUp(key)
                    }
                }
            },
    ) {
        // La etiqueta va centrada sobre el tallo bajo de la L.
        Box(Modifier.fillMaxSize().padding(start = KeyWidth * IsoEnter.NOTCH), contentAlignment = Alignment.Center) {
            Text(key.label, color = Color.White, fontSize = 9.sp, lineHeight = 9.sp, maxLines = 1)
        }
    }
}

/** Añade al camino un polígono con las esquinas redondeadas (curvas que usan cada vértice como punto de control). */
private fun Path.roundedPolygon(points: List<Pair<Float, Float>>, radius: Float) {
    val n = points.size
    for (i in 0 until n) {
        val (px, py) = points[(i + n - 1) % n]
        val (cx, cy) = points[i]
        val (nx, ny) = points[(i + 1) % n]
        val inLen = hypot(px - cx, py - cy)
        val outLen = hypot(nx - cx, ny - cy)
        val r = minOf(radius, inLen / 2f, outLen / 2f)
        val startX = cx + (px - cx) / inLen * r
        val startY = cy + (py - cy) / inLen * r
        val endX = cx + (nx - cx) / outLen * r
        val endY = cy + (ny - cy) / outLen * r
        if (i == 0) moveTo(startX, startY) else lineTo(startX, startY)
        quadraticTo(cx, cy, endX, endY)
    }
    close()
}

/** Fondo esmerilado: la parte del fotograma que queda bajo la ventana, difuminada y oscurecida. */
@Composable
private fun FrostedBackdrop(
    frame: ScreenFrame?,
    transform: ViewTransform,
    viewSize: IntSize,
    windowOffset: Offset,
    modifier: Modifier,
) {
    Box(modifier.background(Color(0xFF0B0C10))) {
        Canvas(Modifier.matchParentSize().blur(14.dp, BlurredEdgeTreatment.Rectangle)) {
            drawFrameUnder(frame, transform, viewSize, windowOffset)
        }
        Box(Modifier.matchParentSize().background(Color(0x99101218)))
    }
}

private fun DrawScope.drawFrameUnder(frame: ScreenFrame?, transform: ViewTransform, viewSize: IntSize, windowOffset: Offset) {
    val f = frame ?: return
    val region = backdropRegion(
        windowOffset.x, windowOffset.y, size.width, size.height,
        transform, viewSize.width.toFloat(), viewSize.height.toFloat(),
        f.image.width, f.image.height,
    ) ?: return
    drawImage(
        image = f.image,
        srcOffset = IntOffset(region.srcX, region.srcY),
        srcSize = IntSize(region.srcWidth, region.srcHeight),
        dstOffset = IntOffset(region.dstX, region.dstY),
        dstSize = IntSize(region.dstWidth, region.dstHeight),
    )
}

@Composable
private fun TitleBar(offset: Offset, onDrag: (Offset) -> Unit, onClose: () -> Unit) {
    val currentOffset by rememberUpdatedState(offset)
    val currentOnDrag by rememberUpdatedState(onDrag)
    Row(
        Modifier
            .fillMaxWidth()
            .height(TitleHeight)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    currentOnDrag(currentOffset + dragAmount)
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.45f)),
            )
        }
        // Botón de cerrar: una celda cuadrada en la esquina, con la cruz dibujada y centrada en ella.
        Box(
            Modifier
                .size(TitleHeight)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                        onClose()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(9.dp)) {
                val stroke = 1.7.dp.toPx()
                drawLine(Color.White, Offset(0f, 0f), Offset(size.width, size.height), stroke, StrokeCap.Round)
                drawLine(Color.White, Offset(size.width, 0f), Offset(0f, size.height), stroke, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun KeyCap(key: KeyDef, latched: Boolean, listener: KeyboardListener) {
    var pressed by remember { mutableStateOf(false) }
    val currentListener by rememberUpdatedState(listener)
    Box(
        Modifier
            .width(KeyWidth * key.width)
            .height(KeyHeight)
            .padding(1.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(if (pressed || latched) LitKey else IdleKey)
            .pointerInput(key) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    pressed = true
                    currentListener.onKeyDown(key)
                    try {
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    } finally {
                        // También si el gesto se cancela (por ejemplo, al cerrar el teclado con la tecla apoyada).
                        pressed = false
                        currentListener.onKeyUp(key)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        key.shiftLabel?.let {
            Text(
                it,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 7.sp,
                lineHeight = 8.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 3.dp, top = 1.dp),
            )
        }
        val labelSize = if (key.label.length > 2) 9.sp else 12.sp
        Text(
            key.label,
            color = Color.White,
            fontSize = labelSize,
            lineHeight = labelSize,
            maxLines = 1,
        )
    }
}
