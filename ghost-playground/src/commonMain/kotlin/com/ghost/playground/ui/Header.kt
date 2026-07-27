package com.ghost.playground.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ghost.playground.i18n.Lang
import com.ghost.playground.i18n.Strings
import com.ghost.playground.ui.icons.PlaygroundIcon
import com.ghost.playground.ui.icons.PlaygroundIconKind
import com.ghost.playground.ui.theme.InkSoft
import com.ghost.playground.ui.theme.Rose
import com.ghost.playground.ui.theme.Sage
import com.ghost.playground.ui.theme.Teal
import com.ghost.playground.ui.theme.TealLight

private const val LangCodeEn = "EN"
private const val LangCodeEs = "ES"
private val HeaderMaxWidth = 960.dp
private val GhostBadgeGlowSize = 18.dp
private const val GhostBadgeGlowAlpha = 0.6f
private const val GhostBadgeTintAlpha = 0.55f
private val GhostBadgeIconSize = 52.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Header(
    strings: Strings,
    lang: Lang,
    dest: PlaygroundDest,
    onLang: (Lang) -> Unit,
    onNav: (PlaygroundDest) -> Unit,
) {
    Column(
        Modifier.widthIn(max = HeaderMaxWidth).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier
                    .shadow(GhostBadgeGlowSize, CircleShape, spotColor = Teal.copy(GhostBadgeGlowAlpha))
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(TealLight.copy(GhostBadgeTintAlpha), Color.Transparent)))
                    .padding(10.dp),
                contentAlignment = Alignment.Center,
            ) {
                PlaygroundIcon(PlaygroundIconKind.Ghost, tint = Teal, size = GhostBadgeIconSize)
            }
            Column {
                Text(strings.brand, style = MaterialTheme.typography.displayLarge)
                Text(strings.tagline, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Spacer(Modifier.height(24.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MarketingBadge(strings.badgeDropIn, PlaygroundIconKind.Bolt)
            MarketingBadge(strings.badgeCoexist, PlaygroundIconKind.RoundTrip)
            MarketingBadge(strings.badgeHotPaths, PlaygroundIconKind.Target)
        }
        Spacer(Modifier.height(22.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TabChip(strings.speedTest, dest == PlaygroundDest.SpeedTest, Sage) { onNav(PlaygroundDest.SpeedTest) }
            TabChip(strings.studio, dest == PlaygroundDest.Studio, Teal) { onNav(PlaygroundDest.Studio) }
            TabChip(strings.underHood, dest == PlaygroundDest.UnderHood, Rose) { onNav(PlaygroundDest.UnderHood) }
            TabChip(strings.learnMore, dest == PlaygroundDest.LearnMore, InkSoft) { onNav(PlaygroundDest.LearnMore) }
            Spacer(Modifier.weight(1f))
            LangToggle(LangCodeEn, lang == Lang.EN) { onLang(Lang.EN) }
            LangToggle(LangCodeEs, lang == Lang.ES) { onLang(Lang.ES) }
        }
    }
}
