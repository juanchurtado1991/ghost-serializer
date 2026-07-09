package com.ghost.serialization.sample.ui.composable.shared

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.ghost.serialization.sample.ui.AppDesign

@Composable
fun SampleText(
    text: String,
    modifier: Modifier = Modifier,
    isSecondary: Boolean = false,
    isBold: Boolean = false,
    fontSize: Int = 14,
    overrideColor: Color? = null,
    textAlign: TextAlign = TextAlign.Start,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    Text(
        text = text,
        modifier = modifier,
        color = overrideColor
            ?: if (isSecondary) AppDesign.TextSecondary else AppDesign.TextPrimary,
        fontWeight = if (isBold) FontWeight.ExtraBold else FontWeight.Medium,
        fontSize = fontSize.sp,
        fontFamily = FontFamily.SansSerif,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow
    )
}