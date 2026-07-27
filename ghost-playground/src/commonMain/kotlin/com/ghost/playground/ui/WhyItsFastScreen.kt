package com.ghost.playground.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ghost.playground.features.SpeedPillars
import com.ghost.playground.i18n.Lang
import com.ghost.playground.i18n.Strings

@Composable
internal fun WhyItsFastScreen(strings: Strings, lang: Lang) {
    Text(strings.speedSubtitle, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 8.dp))
    SpeedPillars.all.forEach { pillar ->
        PillarCard(pillar, strings, lang)
    }
}
