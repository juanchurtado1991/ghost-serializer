package com.ghost.playground.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Playground color palette: lavender canvas with deep purple and magenta accent colors. */
val CanvasTop = Color(0xFFF6F3FF)
val CanvasMid = Color(0xFFECE4FF)
val CanvasBottom = Color(0xFFE0D1FE)

val Teal = Color(0xFF7C3AED)
val TealDark = Color(0xFF5B21B6)
val TealLight = Color(0xFFC4B5FD)
val Coral = Color(0xFFC026D3)
val CoralLight = Color(0xFFF0ABFC)
val Rose = Color(0xFFE11D48)
val Sage = Color(0xFF059669)

val Ink = Color(0xFF221733)
val InkSoft = Color(0xFF4C3B63)
val InkMuted = Color(0xFF7C6F94)

val CardBg = Color(0xFFFCFAFF)
val CardBorder = Color(0xFFE6DCFA)
val CodeBg = Color(0xFF2E1065)
val CodeText = Color(0xFF6EE7B7)
val CodeAccent = Color(0xFFFDE68A)

val PageGradient = Brush.verticalGradient(listOf(CanvasTop, CanvasMid, CanvasBottom))

private val colors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    secondary = Coral,
    onSecondary = Color.White,
    background = CanvasMid,
    onBackground = Ink,
    surface = CardBg,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF5F5F4),
    onSurfaceVariant = InkSoft,
    outline = CardBorder,
    error = Rose,
)

private val typography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 46.sp,
        lineHeight = 50.sp,
        color = Ink,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        color = Ink,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        color = Ink,
    ),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, color = InkSoft),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = InkMuted),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Ink),
)

@Composable
fun GhostPlaygroundTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
