package com.ghost.playground.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghost.playground.ui.theme.CardBg
import com.ghost.playground.ui.theme.CardBorder
import com.ghost.playground.ui.theme.InkMuted
import com.ghost.playground.ui.theme.Teal

/** EN/ES switch in the header. */
@Composable
internal fun LangToggle(label: String, on: Boolean, click: () -> Unit) {
    // Border matches the fill (instead of a light CardBorder over a near-black fill) — a 1dp
    // ring with that much contrast on a 10dp corner reads as jagged/pixelated on Skia's wasm
    // canvas. Using the brand accent instead of near-black for the selected state also keeps it
    // consistent with every other "selected" control in the app.
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (on) Teal else CardBg)
            .border(1.dp, if (on) Teal else CardBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = click)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(label, color = if (on) Color.White else InkMuted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}
