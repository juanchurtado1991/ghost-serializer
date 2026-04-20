package com.ghost.serialization.sample.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ghost.serialization.sample.ui.AppDesign

@Composable
fun MetricItem(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    overrideColor: Color? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(8.dp)
    ) {
        SampleText(
            text = title.uppercase(),
            isSecondary = true,
            fontSize = 10,
            isBold = true,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Box(
            modifier = Modifier
                .background(AppDesign.GlassColor, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            SampleText(
                text = value,
                fontSize = 17,
                isBold = true,
                overrideColor = overrideColor ?: AppDesign.TextPrimary
            )
        }
    }
}