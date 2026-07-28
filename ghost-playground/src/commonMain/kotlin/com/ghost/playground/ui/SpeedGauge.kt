package com.ghost.playground.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghost.playground.bench.formatCompactNumber
import com.ghost.playground.ui.theme.Ink
import com.ghost.playground.ui.theme.InkMuted
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Semicircle speed dial. [value] and [maxValue] drive the needle; the arc, ticks, and needle
 * animate smoothly between updates.
 */
@Composable
fun SpeedGauge(
    title: String,
    value: Double,
    maxValue: Double,
    unit: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val safeMax = if (maxValue <= 0.0) 1.0 else maxValue
    // Animate the raw value (not just the needle fraction) so the big number climbs in lock-step
    // with the needle instead of snapping between live updates — a soft spring reads as a real
    // needle settling into place rather than a mechanical tween.
    val animatedValue by animateFloatAsState(
        targetValue = value.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "gaugeValue",
    )
    val fraction = (animatedValue / safeMax.toFloat()).coerceIn(0f, 1f)
    val trackColor = accent.copy(alpha = 0.16f)
    val needleColor = Ink

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = accent)
        Spacer(Modifier.height(6.dp))
        Box(Modifier.size(176.dp, 106.dp), contentAlignment = Alignment.BottomCenter) {
            Canvas(Modifier.size(176.dp, 106.dp)) {
                val strokeWidth = size.width * 0.09f
                val diameter = size.width - strokeWidth
                val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                val arcSize = Size(diameter, diameter)
                val center = Offset(size.width / 2f, size.width / 2f)
                val radius = diameter / 2f

                drawArc(
                    color = trackColor,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
                if (fraction > 0.001f) {
                    drawArc(
                        color = accent,
                        startAngle = 180f,
                        sweepAngle = 180f * fraction,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                }

                for (i in 0..10) {
                    val angleRad = (180f + i * 18f) * (PI.toFloat() / 180f)
                    val cosA = cos(angleRad)
                    val sinA = sin(angleRad)
                    val inner = radius - strokeWidth * 0.7f
                    val outer = radius + strokeWidth * 0.55f
                    drawLine(
                        color = Color.White,
                        start = Offset(center.x + inner * cosA, center.y + inner * sinA),
                        end = Offset(center.x + outer * cosA, center.y + outer * sinA),
                        strokeWidth = strokeWidth * 0.14f,
                    )
                }

                val needleAngleRad = (180f + 180f * fraction) * (PI.toFloat() / 180f)
                val needleLength = radius * 0.78f
                drawLine(
                    color = needleColor,
                    start = center,
                    end = Offset(
                        center.x + needleLength * cos(needleAngleRad),
                        center.y + needleLength * sin(needleAngleRad),
                    ),
                    strokeWidth = strokeWidth * 0.16f,
                    cap = StrokeCap.Round,
                )
                drawCircle(color = accent, radius = strokeWidth * 0.42f, center = center)
                drawCircle(color = Color.White, radius = strokeWidth * 0.16f, center = center)
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            formatCompactNumber(animatedValue.toDouble()),
            fontWeight = FontWeight.Black,
            fontSize = 26.sp,
            color = Ink
        )
        Text(unit, fontSize = 11.sp, color = InkMuted)
    }
}
