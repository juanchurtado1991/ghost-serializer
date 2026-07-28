package com.ghost.playground.ui.icons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class PlaygroundIconKind {
    Ghost,
    RoundTrip,
    Shield,
    Flatten,
    Package,
    Fallback,
    Bolt,
    Target,
    Bytes,
    TextChannel,
    Order,
    Pool,
    Nullable,
    Write,
    Warning,
    Check,
    Book,
    Manual,
    Wiki,
    Architecture,
    Benchmark,
    Chevron,
}

@Composable
fun PlaygroundIcon(
    kind: PlaygroundIconKind,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    size: Dp = 24.dp,
) {
    val color = if (tint == Color.Unspecified) Color(0xFF0D9488) else tint
    Box(modifier.size(size)) {
        Canvas(Modifier.fillMaxSize()) {
            when (kind) {
                PlaygroundIconKind.Ghost -> drawGhost(color)
                PlaygroundIconKind.RoundTrip -> drawRoundTrip(color)
                PlaygroundIconKind.Shield -> drawShield(color)
                PlaygroundIconKind.Flatten -> drawFlatten(color)
                PlaygroundIconKind.Package -> drawPackage(color)
                PlaygroundIconKind.Fallback -> drawFallback(color)
                PlaygroundIconKind.Bolt -> drawBolt(color)
                PlaygroundIconKind.Target -> drawTarget(color)
                PlaygroundIconKind.Bytes -> drawBytes(color)
                PlaygroundIconKind.TextChannel -> drawTextChannel(color)
                PlaygroundIconKind.Order -> drawOrder(color)
                PlaygroundIconKind.Pool -> drawPool(color)
                PlaygroundIconKind.Nullable -> drawNullable(color)
                PlaygroundIconKind.Write -> drawWrite(color)
                PlaygroundIconKind.Warning -> drawWarning(color)
                PlaygroundIconKind.Check -> drawCheck(color)
                PlaygroundIconKind.Book -> drawBook(color)
                PlaygroundIconKind.Manual -> drawManual(color)
                PlaygroundIconKind.Wiki -> drawWiki(color)
                PlaygroundIconKind.Architecture -> drawArchitecture(color)
                PlaygroundIconKind.Benchmark -> drawBenchmark(color)
                PlaygroundIconKind.Chevron -> drawChevron(color)
            }
        }
    }
}

private fun DrawScope.drawGhost(color: Color) {
    val w = size.width
    val h = size.height
    val body = Path().apply {
        moveTo(w * 0.5f, h * 0.08f)
        cubicTo(w * 0.82f, h * 0.08f, w * 0.92f, h * 0.38f, w * 0.88f, h * 0.58f)
        lineTo(w * 0.88f, h * 0.78f)
        lineTo(w * 0.72f, h * 0.68f)
        lineTo(w * 0.58f, h * 0.78f)
        lineTo(w * 0.44f, h * 0.68f)
        lineTo(w * 0.28f, h * 0.78f)
        lineTo(w * 0.12f, h * 0.68f)
        lineTo(w * 0.12f, h * 0.58f)
        cubicTo(w * 0.08f, h * 0.38f, w * 0.18f, h * 0.08f, w * 0.5f, h * 0.08f)
        close()
    }
    drawPath(body, color)
    drawCircle(Color.White, w * 0.07f, Offset(w * 0.36f, h * 0.38f))
    drawCircle(Color.White, w * 0.07f, Offset(w * 0.64f, h * 0.38f))
    drawCircle(color.copy(0.85f), w * 0.035f, Offset(w * 0.37f, h * 0.39f))
    drawCircle(color.copy(0.85f), w * 0.035f, Offset(w * 0.65f, h * 0.39f))
}

private fun DrawScope.drawRoundTrip(color: Color) {
    val stroke = Stroke(width = size.minDimension * 0.1f, cap = StrokeCap.Round)
    val r = size.minDimension * 0.28f
    val c = Offset(size.width * 0.5f, size.height * 0.5f)
    val arcSize = Size(r * 2f, r * 2f)
    val topLeft = Offset(c.x - r, c.y - r)
    drawArc(color, 200f, 140f, false, topLeft, arcSize, style = stroke)
    drawArc(color, 20f, 140f, false, topLeft, arcSize, style = stroke)
    drawArrowHead(color, Offset(c.x + r * 0.55f, c.y - r * 0.75f), 210f)
    drawArrowHead(color, Offset(c.x - r * 0.55f, c.y + r * 0.75f), 30f)
}

private fun DrawScope.drawShield(color: Color) {
    val path = Path().apply {
        moveTo(size.width * 0.5f, size.height * 0.08f)
        lineTo(size.width * 0.88f, size.height * 0.22f)
        lineTo(size.width * 0.82f, size.height * 0.62f)
        quadraticTo(size.width * 0.5f, size.height * 0.92f, size.width * 0.18f, size.height * 0.62f)
        lineTo(size.width * 0.12f, size.height * 0.22f)
        close()
    }
    drawPath(path, color.copy(0.18f))
    drawPath(path, color, style = Stroke(size.minDimension * 0.08f))
    drawLine(
        color,
        Offset(size.width * 0.35f, size.height * 0.48f),
        Offset(size.width * 0.47f, size.height * 0.6f),
        size.minDimension * 0.08f,
        StrokeCap.Round
    )
    drawLine(
        color,
        Offset(size.width * 0.47f, size.height * 0.6f),
        Offset(size.width * 0.68f, size.height * 0.36f),
        size.minDimension * 0.08f,
        StrokeCap.Round
    )
}

private fun DrawScope.drawFlatten(color: Color) {
    val stroke = Stroke(size.minDimension * 0.08f, cap = StrokeCap.Round)
    drawRoundRect(
        color.copy(0.35f),
        Offset(size.width * 0.12f, size.height * 0.1f),
        Size(size.width * 0.76f, size.height * 0.18f),
        CornerRadius(4f, 4f)
    )
    drawRoundRect(
        color.copy(0.55f),
        Offset(size.width * 0.18f, size.height * 0.32f),
        Size(size.width * 0.64f, size.height * 0.18f),
        CornerRadius(4f, 4f)
    )
    drawRoundRect(
        color,
        Offset(size.width * 0.24f, size.height * 0.68f),
        Size(size.width * 0.52f, size.height * 0.18f),
        CornerRadius(4f, 4f)
    )
    drawLine(
        color,
        Offset(size.width * 0.5f, size.height * 0.52f),
        Offset(size.width * 0.5f, size.height * 0.64f),
        size.minDimension * 0.06f,
        StrokeCap.Round
    )
}

private fun DrawScope.drawPackage(color: Color) {
    drawRoundRect(
        color.copy(0.2f),
        Offset(size.width * 0.16f, size.height * 0.28f),
        Size(size.width * 0.68f, size.height * 0.58f),
        CornerRadius(6f, 6f)
    )
    drawRoundRect(
        color,
        Offset(size.width * 0.16f, size.height * 0.28f),
        Size(size.width * 0.68f, size.height * 0.58f),
        CornerRadius(6f, 6f),
        style = Stroke(size.minDimension * 0.08f)
    )
    drawLine(
        color,
        Offset(size.width * 0.16f, size.height * 0.42f),
        Offset(size.width * 0.84f, size.height * 0.42f),
        size.minDimension * 0.08f
    )
    drawLine(
        color,
        Offset(size.width * 0.5f, size.height * 0.14f),
        Offset(size.width * 0.5f, size.height * 0.42f),
        size.minDimension * 0.08f
    )
    drawLine(
        color,
        Offset(size.width * 0.28f, size.height * 0.14f),
        Offset(size.width * 0.72f, size.height * 0.14f),
        size.minDimension * 0.08f
    )
}

private fun DrawScope.drawFallback(color: Color) {
    drawCircle(
        color.copy(0.25f),
        size.minDimension * 0.22f,
        Offset(size.width * 0.38f, size.height * 0.42f)
    )
    drawCircle(
        color,
        size.minDimension * 0.22f,
        Offset(size.width * 0.38f, size.height * 0.42f),
        style = Stroke(size.minDimension * 0.07f)
    )
    drawCircle(
        color.copy(0.25f),
        size.minDimension * 0.18f,
        Offset(size.width * 0.66f, size.height * 0.58f)
    )
    drawCircle(
        color,
        size.minDimension * 0.18f,
        Offset(size.width * 0.66f, size.height * 0.58f),
        style = Stroke(size.minDimension * 0.07f)
    )
    drawLine(
        color,
        Offset(size.width * 0.52f, size.height * 0.48f),
        Offset(size.width * 0.58f, size.height * 0.52f),
        size.minDimension * 0.06f,
        StrokeCap.Round
    )
}

private fun DrawScope.drawBolt(color: Color) {
    val path = Path().apply {
        moveTo(size.width * 0.58f, size.height * 0.06f)
        lineTo(size.width * 0.28f, size.height * 0.52f)
        lineTo(size.width * 0.48f, size.height * 0.52f)
        lineTo(size.width * 0.38f, size.height * 0.94f)
        lineTo(size.width * 0.78f, size.height * 0.4f)
        lineTo(size.width * 0.54f, size.height * 0.4f)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawTarget(color: Color) {
    val c = Offset(size.width * 0.5f, size.height * 0.5f)
    listOf(0.42f, 0.28f, 0.14f).forEach { f ->
        drawCircle(color, size.minDimension * f, c, style = Stroke(size.minDimension * 0.07f))
    }
    drawCircle(color, size.minDimension * 0.05f, c)
}

private fun DrawScope.drawBytes(color: Color) {
    repeat(3) { row ->
        repeat(4) { col ->
            val alpha = 0.35f + (col * 0.15f)
            drawRoundRect(
                color.copy(alpha),
                Offset(size.width * (0.1f + col * 0.22f), size.height * (0.18f + row * 0.26f)),
                Size(size.width * 0.16f, size.height * 0.18f),
                CornerRadius(3f, 3f),
            )
        }
    }
}

private fun DrawScope.drawTextChannel(color: Color) {
    drawRoundRect(
        color.copy(0.2f),
        Offset(size.width * 0.12f, size.height * 0.14f),
        Size(size.width * 0.76f, size.height * 0.72f),
        CornerRadius(8f, 8f)
    )
    drawLine(
        color,
        Offset(size.width * 0.24f, size.height * 0.34f),
        Offset(size.width * 0.76f, size.height * 0.34f),
        size.minDimension * 0.08f,
        StrokeCap.Round
    )
    drawLine(
        color,
        Offset(size.width * 0.24f, size.height * 0.5f),
        Offset(size.width * 0.62f, size.height * 0.5f),
        size.minDimension * 0.08f,
        StrokeCap.Round
    )
    drawLine(
        color,
        Offset(size.width * 0.24f, size.height * 0.66f),
        Offset(size.width * 0.48f, size.height * 0.66f),
        size.minDimension * 0.08f,
        StrokeCap.Round
    )
}

private fun DrawScope.drawOrder(color: Color) {
    val stroke = Stroke(size.minDimension * 0.08f, cap = StrokeCap.Round)
    listOf(0.22f, 0.42f, 0.62f).forEachIndexed { i, y ->
        drawLine(
            color.copy(0.35f + i * 0.2f),
            Offset(size.width * 0.14f, size.height * y),
            Offset(size.width * 0.86f, size.height * y),
            size.minDimension * 0.08f,
            StrokeCap.Round
        )
    }
    drawLine(
        color,
        Offset(size.width * 0.72f, size.height * 0.18f),
        Offset(size.width * 0.86f, size.height * 0.32f),
        size.minDimension * 0.08f,
        StrokeCap.Round
    )
    drawLine(
        color,
        Offset(size.width * 0.72f, size.height * 0.18f),
        Offset(size.width * 0.58f, size.height * 0.32f),
        size.minDimension * 0.08f,
        StrokeCap.Round
    )
}

private fun DrawScope.drawPool(color: Color) {
    val stroke = Stroke(size.minDimension * 0.08f, cap = StrokeCap.Round)
    val arcSize = Size(size.width * 0.64f, size.height * 0.64f)
    val topLeft = Offset(size.width * 0.18f, size.height * 0.18f)
    drawArc(color, 30f, 300f, false, topLeft, arcSize, style = stroke)
    drawArc(color, 210f, 300f, false, topLeft, arcSize, style = stroke)
    drawCircle(color, size.minDimension * 0.08f, Offset(size.width * 0.5f, size.height * 0.5f))
}

private fun DrawScope.drawNullable(color: Color) {
    drawCircle(
        color,
        size.minDimension * 0.34f,
        Offset(size.width * 0.5f, size.height * 0.5f),
        style = Stroke(size.minDimension * 0.08f)
    )
    drawLine(
        color,
        Offset(size.width * 0.22f, size.height * 0.78f),
        Offset(size.width * 0.78f, size.height * 0.22f),
        size.minDimension * 0.08f,
        StrokeCap.Round
    )
}

private fun DrawScope.drawWrite(color: Color) {
    val path = Path().apply {
        moveTo(size.width * 0.72f, size.height * 0.12f)
        lineTo(size.width * 0.88f, size.height * 0.28f)
        lineTo(size.width * 0.38f, size.height * 0.78f)
        lineTo(size.width * 0.16f, size.height * 0.82f)
        lineTo(size.width * 0.2f, size.height * 0.58f)
        close()
    }
    drawPath(path, color.copy(0.25f))
    drawPath(path, color, style = Stroke(size.minDimension * 0.08f))
    drawLine(
        color,
        Offset(size.width * 0.72f, size.height * 0.12f),
        Offset(size.width * 0.88f, size.height * 0.28f),
        size.minDimension * 0.08f
    )
}

private fun DrawScope.drawWarning(color: Color) {
    val path = Path().apply {
        moveTo(size.width * 0.5f, size.height * 0.08f)
        lineTo(size.width * 0.92f, size.height * 0.88f)
        lineTo(size.width * 0.08f, size.height * 0.88f)
        close()
    }
    drawPath(path, color.copy(0.2f))
    drawPath(path, color, style = Stroke(size.minDimension * 0.08f))
    drawLine(
        color,
        Offset(size.width * 0.5f, size.height * 0.34f),
        Offset(size.width * 0.5f, size.height * 0.58f),
        size.minDimension * 0.08f,
        StrokeCap.Round
    )
    drawCircle(color, size.minDimension * 0.05f, Offset(size.width * 0.5f, size.height * 0.72f))
}

private fun DrawScope.drawCheck(color: Color) {
    drawLine(
        color,
        Offset(size.width * 0.18f, size.height * 0.52f),
        Offset(size.width * 0.42f, size.height * 0.76f),
        size.minDimension * 0.12f,
        StrokeCap.Round
    )
    drawLine(
        color,
        Offset(size.width * 0.42f, size.height * 0.76f),
        Offset(size.width * 0.84f, size.height * 0.24f),
        size.minDimension * 0.12f,
        StrokeCap.Round
    )
}

private fun DrawScope.drawBook(color: Color) {
    drawRoundRect(
        color.copy(0.2f),
        Offset(size.width * 0.18f, size.height * 0.12f),
        Size(size.width * 0.64f, size.height * 0.76f),
        CornerRadius(4f, 4f)
    )
    drawLine(
        color,
        Offset(size.width * 0.5f, size.height * 0.12f),
        Offset(size.width * 0.5f, size.height * 0.88f),
        size.minDimension * 0.07f
    )
    drawRoundRect(
        color,
        Offset(size.width * 0.18f, size.height * 0.12f),
        Size(size.width * 0.64f, size.height * 0.76f),
        CornerRadius(4f, 4f),
        style = Stroke(size.minDimension * 0.07f)
    )
}

private fun DrawScope.drawManual(color: Color) {
    drawBook(color)
    drawLine(
        color,
        Offset(size.width * 0.28f, size.height * 0.34f),
        Offset(size.width * 0.44f, size.height * 0.34f),
        size.minDimension * 0.06f,
        StrokeCap.Round
    )
    drawLine(
        color,
        Offset(size.width * 0.28f, size.height * 0.48f),
        Offset(size.width * 0.44f, size.height * 0.48f),
        size.minDimension * 0.06f,
        StrokeCap.Round
    )
}

private fun DrawScope.drawWiki(color: Color) {
    drawCircle(
        color.copy(0.2f),
        size.minDimension * 0.38f,
        Offset(size.width * 0.5f, size.height * 0.5f)
    )
    drawCircle(
        color,
        size.minDimension * 0.38f,
        Offset(size.width * 0.5f, size.height * 0.5f),
        style = Stroke(size.minDimension * 0.07f)
    )
    drawLine(
        color,
        Offset(size.width * 0.5f, size.height * 0.24f),
        Offset(size.width * 0.5f, size.height * 0.58f),
        size.minDimension * 0.07f,
        StrokeCap.Round
    )
    drawCircle(color, size.minDimension * 0.05f, Offset(size.width * 0.5f, size.height * 0.7f))
}

private fun DrawScope.drawArchitecture(color: Color) {
    drawRect(
        color.copy(0.25f),
        Offset(size.width * 0.12f, size.height * 0.58f),
        Size(size.width * 0.76f, size.height * 0.28f)
    )
    drawRect(
        color,
        Offset(size.width * 0.12f, size.height * 0.58f),
        Size(size.width * 0.76f, size.height * 0.28f),
        style = Stroke(size.minDimension * 0.07f)
    )
    drawRect(
        color.copy(0.25f),
        Offset(size.width * 0.24f, size.height * 0.34f),
        Size(size.width * 0.52f, size.height * 0.2f)
    )
    drawRect(
        color,
        Offset(size.width * 0.24f, size.height * 0.34f),
        Size(size.width * 0.52f, size.height * 0.2f),
        style = Stroke(size.minDimension * 0.07f)
    )
    drawLine(
        color,
        Offset(size.width * 0.5f, size.height * 0.54f),
        Offset(size.width * 0.5f, size.height * 0.34f),
        size.minDimension * 0.07f
    )
}

private fun DrawScope.drawBenchmark(color: Color) {
    val bars = listOf(0.62f to 0.28f, 0.48f to 0.42f, 0.72f to 0.18f)
    bars.forEach { (h, x) ->
        drawRoundRect(
            color,
            Offset(size.width * x, size.height * (1f - h)),
            Size(size.width * 0.14f, size.height * h),
            CornerRadius(3f, 3f)
        )
    }
    drawLine(
        color,
        Offset(size.width * 0.12f, size.height * 0.86f),
        Offset(size.width * 0.88f, size.height * 0.86f),
        size.minDimension * 0.06f,
        StrokeCap.Round
    )
}

private fun DrawScope.drawChevron(color: Color) {
    drawLine(
        color,
        Offset(size.width * 0.22f, size.height * 0.38f),
        Offset(size.width * 0.5f, size.height * 0.64f),
        size.minDimension * 0.12f,
        StrokeCap.Round
    )
    drawLine(
        color,
        Offset(size.width * 0.5f, size.height * 0.64f),
        Offset(size.width * 0.78f, size.height * 0.38f),
        size.minDimension * 0.12f,
        StrokeCap.Round
    )
}

private fun DrawScope.drawArrowHead(color: Color, tip: Offset, degrees: Float) {
    rotate(degrees, tip) {
        val path = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(tip.x - size.minDimension * 0.08f, tip.y - size.minDimension * 0.05f)
            lineTo(tip.x - size.minDimension * 0.02f, tip.y)
            lineTo(tip.x - size.minDimension * 0.08f, tip.y + size.minDimension * 0.05f)
            close()
        }
        drawPath(path, color)
    }
}
