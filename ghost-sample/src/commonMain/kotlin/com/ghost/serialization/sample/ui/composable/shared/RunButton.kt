package com.ghost.serialization.sample.ui.composable.shared

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ghost.serialization.sample.ui.AppDesign

@Composable
fun RunButton(
    isLoading: Boolean,
    loadingStatus: String,
    text: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AppDesign.SurfaceColor),
        border = BorderStroke(width = 1.dp, color = AppDesign.AccentGlow)
    ) {
        if (isLoading) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = AppDesign.AccentGlow,
                    strokeWidth = 2.dp
                )
                SampleText(
                    text = loadingStatus,
                    fontSize = 10,
                    overrideColor = AppDesign.AccentGlow
                )
            }
        } else {
            SampleText(
                text = text,
                isBold = true,
                fontSize = 14,
                overrideColor = AppDesign.AccentGlow
            )
        }
    }
}
