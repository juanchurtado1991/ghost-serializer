package com.ghost.serialization.sample.ui.composable.shared

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ghost.serialization.sample.ui.AppDesign

@Composable
fun ErrorCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = AppDesign.StatusDead.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, AppDesign.StatusDead)
    ) {
        SampleText(
            text = "ERROR: $message",
            overrideColor = AppDesign.StatusDead,
            fontSize = 12,
            modifier = Modifier.padding(16.dp),
            textAlign = TextAlign.Center
        )
    }
}
