package com.ghost.playground.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghost.playground.ui.icons.PlaygroundIcon
import com.ghost.playground.ui.icons.PlaygroundIconKind
import com.ghost.playground.ui.theme.Teal
import com.ghost.playground.ui.theme.TealDark
import com.ghost.playground.ui.theme.TealLight

private const val PresetHoverScale = 1.04f
private const val PresetIdleScale = 1f
private const val PresetAnimationDurationMs = 120
private const val PresetUnselectedFillAlpha = 0.4f
private const val PresetUnselectedBorderAlpha = 0.3f

/** Preset pill button in Studio; highlights when its lab is active. */
@Composable
internal fun PresetButton(label: String, selected: Boolean = false, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val scale by animateFloatAsState(
        if (hovered) PresetHoverScale else PresetIdleScale,
        tween(PresetAnimationDurationMs),
        label = "presetScale",
    )
    Box(
        Modifier
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Teal else TealLight.copy(PresetUnselectedFillAlpha))
            .border(1.dp, if (selected) Teal else Teal.copy(PresetUnselectedBorderAlpha), RoundedCornerShape(20.dp))
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selected) PlaygroundIcon(PlaygroundIconKind.Check, tint = Color.White, size = 11.dp)
            Text(
                label,
                color = if (selected) Color.White else TealDark,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
            )
        }
    }
}
