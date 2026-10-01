package com.ghost.playground.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.ghost.playground.i18n.Strings
import com.ghost.playground.ui.icons.PlaygroundIconKind
import com.ghost.playground.ui.theme.Coral
import com.ghost.playground.ui.theme.Rose
import com.ghost.playground.ui.theme.Sage
import com.ghost.playground.ui.theme.Teal
import com.ghost.playground.ui.theme.TealDark

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DocsScreen(strings: Strings) {
    Card(title = strings.learnMore, accent = Teal, leadingIcon = PlaygroundIconKind.Book) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DocLink(
                label = strings.wikiQuickStart,
                url = PlaygroundLinks.WIKI_QUICK_START,
                icon = PlaygroundIconKind.Wiki,
                accent = Teal
            )
            DocLink(
                label = strings.wikiAdvanced,
                url = PlaygroundLinks.WIKI_ADVANCED,
                icon = PlaygroundIconKind.Manual,
                accent = Coral
            )
            DocLink(
                label = strings.wikiArchitecture,
                url = PlaygroundLinks.WIKI_ARCHITECTURE,
                icon = PlaygroundIconKind.Architecture,
                accent = Sage
            )
            DocLink(
                label = strings.wikiBenchmarks,
                url = PlaygroundLinks.WIKI_BENCHMARKS,
                icon = PlaygroundIconKind.Benchmark,
                accent = Rose
            )
            DocLink(
                label = strings.wikiUsageYaml,
                url = PlaygroundLinks.WIKI_USAGE_YAML,
                icon = PlaygroundIconKind.RoundTrip,
                accent = Sage
            )
            DocLink(
                label = strings.wikiUsageProtobuf,
                url = PlaygroundLinks.WIKI_USAGE_PROTOBUF,
                icon = PlaygroundIconKind.Bytes,
                accent = TealDark
            )
            DocLink(
                label = strings.manualMd,
                url = PlaygroundLinks.MANUAL_MD,
                icon = PlaygroundIconKind.Manual,
                accent = Teal
            )
            DocLink(
                label = strings.manualPdf,
                url = PlaygroundLinks.MANUAL_PDF,
                icon = PlaygroundIconKind.Book,
                accent = Coral
            )
        }
    }
}
