package com.ghost.playground.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ghost.playground.ui.icons.PlaygroundIcon
import com.ghost.playground.ui.icons.PlaygroundIconKind
import com.ghost.playground.ui.theme.CardBg
import com.ghost.playground.ui.theme.CardBorder

private val CardCornerRadius = 16.dp
private val CardIconBadgeSize = 32.dp
private val CardAccentBarSize = 6.dp to 20.dp
private const val CardAccentIconBgAlpha = 0.14f
private const val CardHoverBorderAlpha = 0.55f
private const val CardElevationIdleNonInteractive = 3f
private const val CardElevationPressed = 2f
private const val CardElevationHovered = 10f
private const val CardElevationIdleInteractive = 4f
private const val CardAnimationDurationMs = 140

/** A card with a subtle hover/press lift — used across every tab so the whole lab feels alive. */
@Composable
internal fun Card(
    title: String,
    accent: Color,
    leadingIcon: PlaygroundIconKind? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val interactive = onClick != null
    val elevation by animateFloatAsState(
        targetValue = when {
            !interactive -> CardElevationIdleNonInteractive
            pressed -> CardElevationPressed
            hovered -> CardElevationHovered
            else -> CardElevationIdleInteractive
        },
        animationSpec = tween(CardAnimationDurationMs),
        label = "cardElevation",
    )
    val border by animateColorAsState(
        targetValue = if (interactive && hovered) accent.copy(CardHoverBorderAlpha) else CardBorder,
        animationSpec = tween(CardAnimationDurationMs),
        label = "cardBorder",
    )

    Column(
        Modifier
            .fillMaxWidth()
            .shadow(elevation.dp, RoundedCornerShape(CardCornerRadius))
            .clip(RoundedCornerShape(CardCornerRadius))
            .background(CardBg)
            .border(1.dp, border, RoundedCornerShape(CardCornerRadius))
            .then(
                if (onClick != null) {
                    Modifier.hoverable(interactionSource).clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(CardAccentBarSize.first, CardAccentBarSize.second).background(accent, RoundedCornerShape(3.dp)))
            leadingIcon?.let {
                Box(
                    Modifier
                        .size(CardIconBadgeSize)
                        .clip(CircleShape)
                        .background(accent.copy(CardAccentIconBgAlpha)),
                    contentAlignment = Alignment.Center,
                ) {
                    PlaygroundIcon(it, tint = accent, size = 18.dp)
                }
            }
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        content()
    }
}
