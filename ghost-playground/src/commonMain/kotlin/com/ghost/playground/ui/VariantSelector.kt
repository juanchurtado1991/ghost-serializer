package com.ghost.playground.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghost.playground.features.LabVariant
import com.ghost.playground.ui.icons.PlaygroundIcon
import com.ghost.playground.ui.icons.PlaygroundIconKind
import com.ghost.playground.ui.theme.CardBg
import com.ghost.playground.ui.theme.Ink
import com.ghost.playground.ui.theme.InkMuted
import com.ghost.playground.ui.theme.Teal
import com.ghost.playground.ui.theme.TealDark
import com.ghost.playground.ui.theme.TealLight

private val DropdownMinWidth = 260.dp
private val DropdownCornerRadius = 12.dp
private val DropdownBorderWidth = 1.dp
private const val DropdownFillAlpha = 0.3f
private const val DropdownBorderAlphaIdle = 0.35f

/** Trigger + popup for choosing among a [FeatureLab]'s [LabVariant]s. Hidden entirely for single-variant labs. */
@Composable
fun VariantSelector(
    variants: List<LabVariant>,
    selected: LabVariant,
    isEnglish: Boolean,
    label: String,
    onSelect: (LabVariant) -> Unit,
) {
    if (variants.size <= 1) return
    var expanded by remember(selected.id) { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    Box {
        Row(
            Modifier
                .widthIn(min = DropdownMinWidth)
                .clip(RoundedCornerShape(DropdownCornerRadius))
                .background(TealLight.copy(DropdownFillAlpha))
                .border(DropdownBorderWidth, if (hovered) Teal else Teal.copy(DropdownBorderAlphaIdle), RoundedCornerShape(DropdownCornerRadius))
                .hoverable(interactionSource)
                .clickable(interactionSource = interactionSource, indication = null) { expanded = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label.uppercase(), color = InkMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (isEnglish) selected.labelEn else selected.labelEs,
                    color = TealDark,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            PlaygroundIcon(PlaygroundIconKind.Chevron, tint = Teal, size = 14.dp)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = CardBg,
        ) {
            variants.forEach { variant ->
                val isSelected = variant.id == selected.id
                DropdownMenuItem(
                    text = {
                        Text(
                            if (isEnglish) variant.labelEn else variant.labelEs,
                            color = if (isSelected) TealDark else Ink,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        onSelect(variant)
                        expanded = false
                    },
                )
            }
        }
    }
}
