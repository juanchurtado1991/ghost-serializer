package com.ghost.playground.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghost.playground.ui.theme.CardBg
import com.ghost.playground.ui.theme.CardBorder
import com.ghost.playground.ui.theme.Ink

private const val TabChipHoverFillAlpha = 0.1f

/** One top-level nav tab in the header. */
@Composable
internal fun TabChip(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) accent else if (hovered) accent.copy(TabChipHoverFillAlpha) else CardBg)
            .border(1.dp, if (selected || hovered) accent else CardBorder, RoundedCornerShape(12.dp))
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(label, color = if (selected) Color.White else Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}
