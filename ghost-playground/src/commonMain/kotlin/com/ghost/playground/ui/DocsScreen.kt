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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DocsScreen(strings: Strings) {
    Card(title = strings.learnMore, accent = Teal, leadingIcon = PlaygroundIconKind.Book) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DocLink(strings.wikiQuickStart, PlaygroundLinks.WIKI_QUICK_START, PlaygroundIconKind.Wiki, Teal)
            DocLink(strings.wikiAdvanced, PlaygroundLinks.WIKI_ADVANCED, PlaygroundIconKind.Manual, Coral)
            DocLink(strings.wikiArchitecture, PlaygroundLinks.WIKI_ARCHITECTURE, PlaygroundIconKind.Architecture, Sage)
            DocLink(strings.wikiBenchmarks, PlaygroundLinks.WIKI_BENCHMARKS, PlaygroundIconKind.Benchmark, Rose)
            DocLink(strings.manualMd, PlaygroundLinks.MANUAL_MD, PlaygroundIconKind.Manual, Teal)
            DocLink(strings.manualPdf, PlaygroundLinks.MANUAL_PDF, PlaygroundIconKind.Book, Coral)
        }
    }
}
