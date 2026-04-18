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
    val PrimaryDark = Color(0xFF0F172A)
    val SurfaceColor = Color(0xFF1E293B)
    val AccentGlow = Color(0xFF34D399) // Back to Emerald Zenith
    val PrimaryAccent = Color(0xFF10B981)
    
    val GlassColor = Color(0xFFFFFFFF).copy(alpha = 0.05f)
    val GlassBorder = Color(0xFFFFFFFF).copy(alpha = 0.1f)
    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFF94A3B8)
    val ErrorColor = Color(0xFFEF4444)

    val BackgroundGradient = Brush.verticalGradient(
        colors = listOf(PrimaryDark, Color(0xFF020617))
    )

    val CardGradient = Brush.linearGradient(
        colors = listOf(
            SurfaceColor.copy(alpha = 0.7f),
            PrimaryDark.copy(alpha = 0.7f)
        )
    )
    
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
            .clip(RoundedCornerShape(20.dp))
            .background(DesignSystem.CardGradient)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        DesignSystem.GlassBorder,
                        Color.Transparent
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(20.dp)
    ) {
        content()
    }
}

@Composable
fun MetricItem(
    title: String,
    value: String,
    overrideColor: Color? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(8.dp)
    ) {
        SampleText(
            text = title.uppercase(),
            isSecondary = true,
            fontSize = 10,
            isBold = true,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Box(
            modifier = Modifier
                .background(DesignSystem.GlassColor, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            SampleText(
                text = value,
                fontSize = 17,
                isBold = true,
                overrideColor = overrideColor ?: DesignSystem.TextPrimary
            )
        }
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
