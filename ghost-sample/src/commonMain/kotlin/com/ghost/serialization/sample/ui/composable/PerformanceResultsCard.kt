package com.ghost.serialization.sample.ui.composable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ghost.serialization.sample.ui.AppDesign
import com.ghost.serialization.sample.ui.model.UiState
import com.ghost.serialization.sample.util.format

private val ACCENT_COMPETITOR = Color(0xFF818CF8)

@Composable
fun PerformanceResultsCard(
    uiState: UiState,
    onCopyLogs: () -> Unit
) {
    // Group results by category prefix, e.g. "[NETWORK]", "[PARSE_STRING]"
    val grouped = uiState.results.groupBy { result ->
        result.name.substringBefore("]").removePrefix("[")
    }

    // Summary insight: compare Ghost vs best competitor per category
    val ghostResults = uiState.results.filter { it.name.contains("GHOST") }
    val competitorResults = uiState.results.filter { !it.name.contains("GHOST") }
    val avgGhostMs = ghostResults.map { it.timeMs }.average().takeIf { !it.isNaN() } ?: 0.0
    val avgCompetitorMs = competitorResults.map { it.timeMs }.average().takeIf { !it.isNaN() } ?: 0.0
    val speedFactor = if (avgGhostMs > 0) avgCompetitorMs / avgGhostMs else 1.0
    val memGhostKb = ghostResults.map { it.memoryBytes }.average().takeIf { !it.isNaN() } ?: 0.0
    val memCompetitorKb = competitorResults.map { it.memoryBytes }.average().takeIf { !it.isNaN() } ?: 0.0
    val memFactor = if (memGhostKb > 0) memCompetitorKb / memGhostKb else 1.0

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = AppDesign.SurfaceColor.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, AppDesign.GlassBorder)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Header ───────────────────────────────────────────────────────────
            SampleText(
                text = "PERFORMANCE INSIGHT",
                isBold = true,
                fontSize = 12,
                overrideColor = AppDesign.AccentGlow
            )
            SampleText(
                text = "Ghost is ${(speedFactor * 10).toLong() / 10.0}x faster overall.",
                fontSize = 16,
                isBold = true,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp)
            )
            SampleText(
                text = "Ghost allocates ${(memFactor * 10).toLong() / 10.0}x less memory on average.",
                fontSize = 12,
                isSecondary = true,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            HorizontalDivider(
                color = AppDesign.GlassBorder,
                thickness = 1.dp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // ── Results by category ──────────────────────────────────────────────
            grouped.forEach { (category, results) ->
                SampleText(
                    text = category,
                    isBold = true,
                    fontSize = 11,
                    isSecondary = true,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Time row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    results.forEach { res ->
                        val color = if (res.name.contains("GHOST")) AppDesign.AccentGlow else ACCENT_COMPETITOR
                        val engineName = res.name.substringAfter("] ").trim()
                        MetricItem(
                            title = engineName,
                            value = "${"%.2f".format(res.timeMs)}ms",
                            overrideColor = color
                        )
                    }
                }

                // Memory row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    results.forEach { res ->
                        val color = if (res.name.contains("GHOST")) AppDesign.AccentGlow else ACCENT_COMPETITOR
                        val engineName = res.name.substringAfter("] ").trim()
                        MetricItem(
                            title = "$engineName MEM",
                            value = "${res.memoryBytes / 1024} KB",
                            overrideColor = color
                        )
                    }
                }

                HorizontalDivider(
                    color = AppDesign.GlassBorder,
                    thickness = 1.dp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // ── Copy logs button ─────────────────────────────────────────────────
            TextButton(
                onClick = onCopyLogs,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                SampleText(
                    text = "COPY SESSION LOGS",
                    overrideColor = AppDesign.AccentGlow,
                    isBold = true,
                    fontSize = 12
                )
            }
        }
    }
}