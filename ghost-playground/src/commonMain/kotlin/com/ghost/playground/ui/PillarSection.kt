package com.ghost.playground.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ghost.playground.ui.theme.Coral
import com.ghost.playground.ui.theme.Ink
import com.ghost.playground.ui.theme.InkMuted
import com.ghost.playground.ui.theme.InkSoft

@Composable
internal fun PillarSection(label: String, body: String, muted: Boolean = false) {
    Column {
        Text(
            label.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (muted) InkMuted else Coral
        )
        Text(body, style = MaterialTheme.typography.bodyLarge, color = if (muted) InkSoft else Ink)
    }
}
