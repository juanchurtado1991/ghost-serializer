package com.ghost.serialization.sample.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object IndustrialDesignSystem {
    val BackgroundGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF0F172A), Color(0xFF020617))
    )
    val CardGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFF1E293B).copy(alpha = 0.8f), Color(0xFF0F172A).copy(alpha = 0.8f))
    )
    val PrimaryAccent = Color(0xFF10B981) // Neon Mint
    val AccentGlow = Color(0xFF34D399)
    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFF94A3B8)
    val BorderColor = Color(0xFF334155).copy(alpha = 0.5f)
    val ErrorColor = Color(0xFFEF4444) // Red for Moshi metrics
    
    val StatusAlive = Color(0xFF10B981)
    val StatusDead = Color(0xFFEF4444)
    val StatusUnknown = Color(0xFF64748B)
}

@Composable
fun IndustrialCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = Color.Black.copy(alpha = 0.5f)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(IndustrialDesignSystem.CardGradient)
            .border(1.dp, IndustrialDesignSystem.BorderColor, RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        content()
    }
}

@Composable
fun IndustrialButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(IndustrialDesignSystem.PrimaryAccent.copy(alpha = 0.15f), Color.Transparent)
                )
            )
            .border(1.dp, IndustrialDesignSystem.PrimaryAccent.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 32.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            color = IndustrialDesignSystem.AccentGlow,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp,
            fontSize = 14.sp
        )
    }
}

@Composable
fun IndustrialText(
    text: String,
    modifier: Modifier = Modifier,
    isSecondary: Boolean = false,
    isBold: Boolean = false,
    fontSize: Int = 14,
    overrideColor: Color? = null,
    textAlign: androidx.compose.ui.text.style.TextAlign = androidx.compose.ui.text.style.TextAlign.Start
) {
    Text(
        text = text,
        modifier = modifier,
        color = overrideColor ?: if (isSecondary) IndustrialDesignSystem.TextSecondary else IndustrialDesignSystem.TextPrimary,
        fontWeight = if (isBold) FontWeight.ExtraBold else FontWeight.Medium,
        fontSize = fontSize.sp,
        fontFamily = FontFamily.SansSerif,
        textAlign = textAlign
    )
}

@Composable
fun IndustrialRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = IndustrialDesignSystem.TextSecondary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            modifier = Modifier.width(70.dp)
        )
        Text(
            text = value,
            color = IndustrialDesignSystem.TextPrimary,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp
        )
    }
}

@Composable
fun StatusIndicator(status: String) {
    val color = when(status.uppercase()) {
        "ALIVE" -> IndustrialDesignSystem.StatusAlive
        "DEAD" -> IndustrialDesignSystem.StatusDead
        else -> IndustrialDesignSystem.StatusUnknown
    }
    
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
                .shadow(4.dp, CircleShape, spotColor = color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        IndustrialText(text = status, isBold = true, fontSize = 12)
    }
}
