package com.ghost.playground.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghost.playground.ui.theme.CodeBg
import com.ghost.playground.ui.theme.CodeText

/** Read-only monospace block for curated DTO source and sample payload text. */
@Composable
internal fun CodeArea(value: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CodeBg)
            .padding(10.dp),
    ) {
        Text(value, color = CodeText, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp)
    }
}
