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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghost.playground.platform.openUrl
import com.ghost.playground.ui.icons.PlaygroundIcon
import com.ghost.playground.ui.icons.PlaygroundIconKind
import com.ghost.playground.ui.theme.CardBg
import com.ghost.playground.ui.theme.CardBorder
import com.ghost.playground.ui.theme.Ink
import com.ghost.playground.ui.theme.InkMuted

private val DocLinkWidth = 220.dp
private val DocLinkElevationHovered = 6.dp
private val DocLinkElevationIdle = 1.dp
private const val DocLinkAnimationDurationMs = 140
private const val DocLinkHoverBorderAlpha = 0.5f

@Composable
internal fun DocLink(label: String, url: String, icon: PlaygroundIconKind, accent: Color) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val elevation by animateFloatAsState(
        if (hovered) DocLinkElevationHovered.value else DocLinkElevationIdle.value,
        tween(DocLinkAnimationDurationMs),
        label = "docLinkElevation",
    )
    Row(
        Modifier
            .width(DocLinkWidth)
            .shadow(elevation.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .border(
                1.dp,
                if (hovered) accent.copy(DocLinkHoverBorderAlpha) else CardBorder,
                RoundedCornerShape(12.dp)
            )
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { openUrl(url) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(accent.copy(0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            PlaygroundIcon(icon, tint = accent, size = 16.dp)
        }
        Text(
            label,
            color = Ink,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        PlaygroundIcon(PlaygroundIconKind.RoundTrip, tint = InkMuted, size = 14.dp)
    }
}
