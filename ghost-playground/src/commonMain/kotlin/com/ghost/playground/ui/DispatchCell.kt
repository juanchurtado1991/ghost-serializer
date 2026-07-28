package com.ghost.playground.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghost.playground.ui.theme.CardBorder
import com.ghost.playground.ui.theme.InkMuted
import com.ghost.playground.ui.theme.Teal
import com.ghost.playground.ui.theme.TealDark

private val CellSize = 52.dp to 36.dp
private const val OccupiedBgAlpha = 0.2f
private const val EmptyBgAlpha = 0.5f
private const val OccupiedFieldFontSizeSp = 9
private const val EmptySlotFontSizeSp = 14
private const val EmptySlotGlyph = "·"
private const val DispatchCellMaxLines = 2

/** One cell in the perfect-hash dispatch table preview; occupied cells show the hashed field name. */
@Composable
internal fun DispatchCell(index: Int, field: String?, occupied: Boolean) {
    val bg = if (occupied) Teal.copy(OccupiedBgAlpha) else CardBorder.copy(EmptyBgAlpha)
    Box(
        Modifier
            .size(CellSize.first, CellSize.second)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, if (occupied) Teal else CardBorder, RoundedCornerShape(6.dp))
            .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            field ?: EmptySlotGlyph,
            fontSize = if (field != null) OccupiedFieldFontSizeSp.sp else EmptySlotFontSizeSp.sp,
            color = if (occupied) TealDark else InkMuted,
            fontFamily = if (field != null) FontFamily.Monospace else FontFamily.Default,
            textAlign = TextAlign.Center,
            maxLines = DispatchCellMaxLines,
        )
    }
}
