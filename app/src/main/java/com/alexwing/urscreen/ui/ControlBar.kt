package com.alexwing.urscreen.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt

private val ButtonWidth = 36.dp
private val ButtonHeight = 34.dp
private val IconSize = 20.dp

private val IdleIcon = Color.White.copy(alpha = 0.9f)
private val ActiveIcon = Color(0xFF8FBBFF)

/**
 * Barra de controles compacta: solo iconos, centrada y pegada arriba, y se puede mover agarrándola por el asa de la
 * izquierda (los puntos y los fps). [offset] se mide desde su posición por defecto (arriba, centrada).
 */
@Composable
fun ControlBar(
    fps: Float,
    monitor: Int,
    monitorCount: Int,
    keyboardOpen: Boolean,
    offset: Offset,
    onOffsetChange: (Offset) -> Unit,
    viewSize: IntSize,
    onMonitor: (Int) -> Unit,
    onToggleKeyboard: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var barSize by remember { mutableStateOf(IntSize.Zero) }
    val currentOffset by rememberUpdatedState(offset)
    val currentViewSize by rememberUpdatedState(viewSize)
    val currentOnOffsetChange by rememberUpdatedState(onOffsetChange)

    Row(
        modifier
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .onSizeChanged { barSize = it }
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x99000000))
            // Los toques sobre la barra no deben llegar al escritorio.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Asa: arrastrar para mover la barra.
        Row(
            Modifier
                .height(ButtonHeight)
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        currentOnOffsetChange(
                            clampBarOffset(
                                currentOffset + drag,
                                barSize.width.toFloat(), barSize.height.toFloat(),
                                currentViewSize.width.toFloat(), currentViewSize.height.toFloat(),
                            ),
                        )
                    }
                }
                .semantics { contentDescription = "Mover barra" }
                .padding(start = 8.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GripIcon()
            Spacer(Modifier.width(6.dp))
            Text(
                text = String.format(Locale.ROOT, "%.0f fps", fps),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 10.sp,
            )
        }
        if (monitorCount > 1) MonitorButton(monitor, monitorCount, onMonitor)
        BarButton("Teclado", onClick = onToggleKeyboard) {
            KeyboardIcon(if (keyboardOpen) ActiveIcon else IdleIcon)
        }
        BarButton("Salir", onClick = onDisconnect) { PowerIcon(IdleIcon) }
    }
}

@Composable
private fun MonitorButton(monitor: Int, monitorCount: Int, onMonitor: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        BarButton("Pantalla ${monitor + 1}", onClick = { open = true }, width = 50.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MonitorIcon(IdleIcon)
                Spacer(Modifier.width(3.dp))
                Text("${monitor + 1}", color = IdleIcon, fontSize = 12.sp)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (i in 0 until monitorCount) {
                DropdownMenuItem(
                    text = { Text("Pantalla ${i + 1}") },
                    onClick = {
                        open = false
                        onMonitor(i)
                    },
                )
            }
        }
    }
}

@Composable
private fun BarButton(
    description: String,
    onClick: () -> Unit,
    width: androidx.compose.ui.unit.Dp = ButtonWidth,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        Modifier
            .size(width, ButtonHeight)
            .clickable(onClickLabel = description, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
private fun GripIcon() {
    Canvas(Modifier.size(8.dp, 14.dp)) {
        val r = 1.1.dp.toPx()
        for (x in listOf(0.25f, 0.75f)) {
            for (y in listOf(0.15f, 0.5f, 0.85f)) {
                drawCircle(Color.White.copy(alpha = 0.5f), r, Offset(size.width * x, size.height * y))
            }
        }
    }
}

/** Pantalla: un monitor con su pie. Las medidas van en una cuadrícula de 20 x 20. */
@Composable
private fun MonitorIcon(color: Color) {
    Canvas(Modifier.size(IconSize)) {
        val u = size.width / 20f
        val w = 1.6f * u
        drawRoundRect(color, Offset(1.5f * u, 2.5f * u), Size(17f * u, 11f * u), CornerRadius(2f * u), style = Stroke(w))
        drawLine(color, Offset(10f * u, 13.5f * u), Offset(10f * u, 17f * u), w, StrokeCap.Round)
        drawLine(color, Offset(6f * u, 17.5f * u), Offset(14f * u, 17.5f * u), w, StrokeCap.Round)
    }
}

/** Teclado: la caja con dos filas de teclas y la barra espaciadora. */
@Composable
private fun KeyboardIcon(color: Color) {
    Canvas(Modifier.size(IconSize)) {
        val u = size.width / 20f
        drawRoundRect(color, Offset(1f * u, 4f * u), Size(18f * u, 12f * u), CornerRadius(2.2f * u), style = Stroke(1.6f * u))
        for (i in 0..3) {
            drawCircle(color, 0.95f * u, Offset((5f + i * 3.3f) * u, 8f * u))
            drawCircle(color, 0.95f * u, Offset((5f + i * 3.3f) * u, 11f * u))
        }
        drawLine(color, Offset(6.5f * u, 13.6f * u), Offset(13.5f * u, 13.6f * u), 1.3f * u, StrokeCap.Round)
    }
}

/** Salir: el símbolo de apagado, un círculo abierto por arriba con una raya. */
@Composable
private fun PowerIcon(color: Color) {
    Canvas(Modifier.size(IconSize)) {
        val u = size.width / 20f
        val w = 1.7f * u
        drawArc(
            color, startAngle = -60f, sweepAngle = 300f, useCenter = false,
            topLeft = Offset(3.5f * u, 4.5f * u), size = Size(13f * u, 13f * u),
            style = Stroke(w, cap = StrokeCap.Round),
        )
        drawLine(color, Offset(10f * u, 2.5f * u), Offset(10f * u, 9.5f * u), w, StrokeCap.Round)
    }
}
