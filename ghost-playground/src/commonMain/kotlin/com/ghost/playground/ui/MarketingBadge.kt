package com.ghost.playground.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghost.playground.ui.icons.PlaygroundIcon
import com.ghost.playground.ui.icons.PlaygroundIconKind
import com.ghost.playground.ui.theme.CardBg
import com.ghost.playground.ui.theme.CardBorder
import com.ghost.playground.ui.theme.InkSoft
import com.ghost.playground.ui.theme.Teal

/** Marketing pill shown under the header (for example, drop-in compatibility badges). */
@Composable
internal fun MarketingBadge(label: String, icon: PlaygroundIconKind) {
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(CardBg)
            .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaygroundIcon(icon, tint = Teal, size = 12.dp)
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = InkSoft)
    }
}
