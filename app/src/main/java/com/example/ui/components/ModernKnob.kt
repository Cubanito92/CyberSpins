package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

/**
 * Perilla rotatoria original (arrastre vertical = giro). Construida 100% con
 * Canvas + gestos propios; no reutiliza layout ni assets de terceros.
 *
 * @param value 0f..1f posición normalizada de la perilla
 * @param accentColor color del arco de progreso (para diferenciar Mic / Música / Master)
 */
@Composable
fun ModernKnob(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = NeonCyan,
    size: Dp = 84.dp,
    valueText: String? = null
) {
    var dragAccum by remember { mutableStateOf(value) }
    LaunchedEffect(value) { dragAccum = value }

    val sweep = 270f
    val startAngle = 135f
    val angle = startAngle + sweep * value.coerceIn(0f, 1f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        // Arrastre vertical: arriba sube el valor, abajo lo baja.
                        val delta = -dragAmount.y / 220f
                        dragAccum = (dragAccum + delta).coerceIn(0f, 1f)
                        onValueChange(dragAccum)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        dragAccum = 0.5f
                        onValueChange(0.5f)
                    })
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val strokeWidth = 8.dp.toPx()
                val radius = (size.toPx() - strokeWidth) / 2f
                val center = Offset(size.toPx() / 2f, size.toPx() / 2f)

                // Pista de fondo
                drawArc(
                    color = StudioMutedAccent,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(strokeWidth, cap = StrokeCap.Round),
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2f, radius * 2f)
                )
                // Arco de progreso
                drawArc(
                    brush = Brush.sweepGradient(listOf(accentColor.copy(alpha = 0.4f), accentColor)),
                    startAngle = startAngle,
                    sweepAngle = sweep * value.coerceIn(0f, 1f),
                    useCenter = false,
                    style = Stroke(strokeWidth, cap = StrokeCap.Round),
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2f, radius * 2f)
                )

                // Cuerpo de la perilla (disco central con degradado)
                val knobRadius = radius - strokeWidth * 1.4f
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(StudioCardSurface, StudioDarkBackground),
                        center = center,
                        radius = knobRadius
                    ),
                    radius = knobRadius,
                    center = center
                )
                drawCircle(
                    color = StudioCardBorder,
                    radius = knobRadius,
                    center = center,
                    style = Stroke(2.dp.toPx())
                )

                // Indicador (línea que marca la posición angular)
                val rad = Math.toRadians(angle.toDouble())
                val indicatorStart = Offset(
                    x = center.x + (knobRadius * 0.35f) * cos(rad).toFloat(),
                    y = center.y + (knobRadius * 0.35f) * sin(rad).toFloat()
                )
                val indicatorEnd = Offset(
                    x = center.x + (knobRadius * 0.9f) * cos(rad).toFloat(),
                    y = center.y + (knobRadius * 0.9f) * sin(rad).toFloat()
                )
                drawLine(
                    color = accentColor,
                    start = indicatorStart,
                    end = indicatorEnd,
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = StudioTextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
        if (valueText != null) {
            Text(text = valueText, color = StudioTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
