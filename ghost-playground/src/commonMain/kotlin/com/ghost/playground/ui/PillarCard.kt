package com.ghost.playground.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghost.playground.features.SpeedPillar
import com.ghost.playground.i18n.Lang
import com.ghost.playground.i18n.Strings
import com.ghost.playground.ui.theme.Sage
import com.ghost.playground.ui.theme.Teal

/** One collapsible "why it's fast" pillar — expands to what/why/vs sections on tap. */
@Composable
internal fun PillarCard(pillar: SpeedPillar, strings: Strings, lang: Lang) {
    var open by remember(pillar.titleEn) { mutableStateOf(false) }
    val title = if (lang == Lang.EN) pillar.titleEn else pillar.titleEs

    Card(title = title, accent = Sage, leadingIcon = pillar.icon, onClick = { open = !open }) {
        AnimatedVisibility(open) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PillarSection(strings.whatItIs, if (lang == Lang.EN) pillar.whatEn else pillar.whatEs)
                PillarSection(strings.whyFast, if (lang == Lang.EN) pillar.whyEn else pillar.whyEs)
                PillarSection(strings.vsOthers, if (lang == Lang.EN) pillar.vsEn else pillar.vsEs, muted = true)
            }
        }
        if (!open) {
            Text(strings.tapToLearnMore, color = Teal, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}
