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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object DesignSystem {
    val BackgroundGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF0F172A),
            Color(0xFF020617))
    )
    val CardGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFF1E293B).copy(alpha = 0.8f),
            Color(0xFF0F172A).copy(alpha = 0.8f))
    )
    val PrimaryAccent = Color(0xFF10B981)
    val AccentGlow = Color(0xFF34D399)
    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFF94A3B8)
    val BorderColor = Color(0xFF334155).copy(alpha = 0.5f)
    val ErrorColor = Color(0xFFEF4444)
    
    val StatusAlive = Color(0xFF10B981)
    val StatusDead = Color(0xFFEF4444)
    val StatusUnknown = Color(0xFF64748B)
}

@Composable
fun Card(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(DesignSystem.CardGradient)
            .border(
                1.dp,
                DesignSystem.BorderColor.copy(alpha = 0.8f),
                RoundedCornerShape(16.dp)
            )
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
                    colors = listOf(
                        DesignSystem.PrimaryAccent.copy(alpha = 0.15f),
                        Color.Transparent
                    )
                )
            )
            .border(
                1.dp,
                DesignSystem.PrimaryAccent.copy(alpha = 0.6f),
                RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 32.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            color = DesignSystem.AccentGlow,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp,
            fontSize = 14.sp
        )
    }
}

@Composable
fun SampleText(
    text: String,
    modifier: Modifier = Modifier,
    isSecondary: Boolean = false,
    isBold: Boolean = false,
    fontSize: Int = 14,
    overrideColor: Color? = null,
    textAlign: TextAlign = TextAlign.Start
) {
    Text(
        text = text,
        modifier = modifier,
        color = overrideColor ?: if (isSecondary) DesignSystem.TextSecondary else DesignSystem.TextPrimary,
        fontWeight = if (isBold) FontWeight.ExtraBold else FontWeight.Medium,
        fontSize = fontSize.sp,
        fontFamily = FontFamily.SansSerif,
        textAlign = textAlign
    )
}

@Composable
fun SampleRow(
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
            color = DesignSystem.TextSecondary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            modifier = Modifier.width(70.dp)
        )
        Text(
            text = value,
            color = DesignSystem.TextPrimary,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp
        )
    }
}

@Composable
fun StatusIndicator(status: String) {
    val color = when(status.uppercase()) {
        "ALIVE" -> DesignSystem.StatusAlive
        "DEAD" -> DesignSystem.StatusDead
        else -> DesignSystem.StatusUnknown
    }
    
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        SampleText(text = status, isBold = true, fontSize = 12)
    }
}
