package com.ghost.serialization.sample.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object AppDesign {
    val PrimaryDark = Color(0xFF0F172A)
    val SurfaceColor = Color(0xFF1E293B)
    val AccentGlow = Color(0xFFA855F7) // Ghost Purple

    val GlassColor = Color(0xFFFFFFFF).copy(alpha = 0.05f)
    val GlassBorder = Color(0xFFFFFFFF).copy(alpha = 0.1f)
    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFF94A3B8)

    val BackgroundGradient = Brush.verticalGradient(
        colors = listOf(PrimaryDark, Color(0xFF020617))
    )

    val StatusAlive = Color(0xFF10B981)
    val StatusDead = Color(0xFFEF4444)
    val StatusUnknown = Color(0xFF64748B)
}