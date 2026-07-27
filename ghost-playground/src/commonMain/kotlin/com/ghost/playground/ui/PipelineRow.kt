package com.ghost.playground.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghost.playground.ui.icons.PlaygroundIcon
import com.ghost.playground.ui.icons.PlaygroundIconKind
import com.ghost.playground.ui.theme.CardBorder
import com.ghost.playground.ui.theme.Ink
import com.ghost.playground.ui.theme.InkMuted
import com.ghost.playground.ui.theme.InkSoft
import com.ghost.playground.ui.theme.Rose
import com.ghost.playground.ui.theme.Sage
import com.ghost.playground.ui.theme.Teal
import com.ghost.playground.ui.theme.TealLight

private val StepBadgeSize = 28.dp
private const val StepErrorBgAlpha = 0.12f
private const val StepLitBgAlpha = 0.35f
private const val StepDoneBgAlpha = 0.08f

/** One numbered row inside the "What Ghost just did" pipeline card, lighting up as [activeStep] advances. */
@Composable
internal fun PipelineRow(num: Int, title: String, detail: String, status: StepStatus, lit: Boolean) {
    val bg = when {
        status == StepStatus.Error -> Rose.copy(StepErrorBgAlpha)
        lit -> TealLight.copy(StepLitBgAlpha)
        status == StepStatus.Done -> Sage.copy(StepDoneBgAlpha)
        else -> Color.Transparent
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(StepBadgeSize).background(if (lit) Teal else CardBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (status == StepStatus.Done) {
                PlaygroundIcon(PlaygroundIconKind.Check, tint = Color.White, size = 16.dp)
            } else {
                Text(num.toString(), color = if (lit) Color.White else InkMuted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
        Column {
            Text(title, fontWeight = FontWeight.Bold, color = Ink, fontSize = 14.sp)
            Text(detail, color = InkSoft, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}
