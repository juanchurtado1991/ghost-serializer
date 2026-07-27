package com.ghost.playground.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghost.playground.ui.icons.PlaygroundIcon
import com.ghost.playground.ui.icons.PlaygroundIconKind

private const val HeroScalePressed = 0.985f
private const val HeroScaleHovered = 1.01f
private const val HeroScaleIdle = 1f
private const val HeroAnimationDurationMs = 120
private val HeroElevationHovered = 14.dp
private val HeroElevationIdle = 8.dp
private val HeroCornerRadius = 16.dp

/** Big primary call-to-action — gradient fill, used for the marquee actions (run pipeline, start speed test). */
@Composable
internal fun HeroButton(
    label: String,
    icon: PlaygroundIconKind?,
    colors: List<Color>,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) HeroScalePressed else if (hovered) HeroScaleHovered else HeroScaleIdle,
        animationSpec = tween(HeroAnimationDurationMs),
        label = "heroScale",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .scale(scale)
            .shadow(if (hovered) HeroElevationHovered else HeroElevationIdle, RoundedCornerShape(HeroCornerRadius))
            .clip(RoundedCornerShape(HeroCornerRadius))
            .background(Brush.horizontalGradient(colors))
            .hoverable(interactionSource)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            icon?.let { PlaygroundIcon(it, tint = Color.White, size = 22.dp) }
            Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
    }
}
