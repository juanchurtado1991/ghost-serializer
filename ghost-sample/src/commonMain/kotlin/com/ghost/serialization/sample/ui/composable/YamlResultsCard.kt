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
import com.ghost.serialization.sample.api.EngineResult
import com.ghost.serialization.sample.ui.AppDesign
import com.ghost.serialization.sample.ui.model.YamlUiState
import com.ghost.serialization.sample.util.format

private val COLOR_KAML = Color(0xFF38BDF8)
private val COLOR_JACKSON = Color(0xFFFB923C)

@Composable
fun YamlResultsCard(
    uiState: YamlUiState,
    onCopyLogs: () -> Unit
) {
    val grouped = uiState.results.groupBy { result ->
        result.name.substringBefore("]").removePrefix("[")
    }

    val ghostResults = uiState.results.filter { it.name.contains("GHOST") }
    val competitorResults = uiState.results.filter { !it.name.contains("GHOST") }
    val avgGhostMs = ghostResults.map { it.timeMs }.average().takeIf { !it.isNaN() } ?: 0.0
    val avgCompetitorMs = competitorResults.map { it.timeMs }.average().takeIf { !it.isNaN() } ?: 0.0
    val speedFactor = if (avgGhostMs > 0) avgCompetitorMs / avgGhostMs else 1.0
    val memGhostBytes = ghostResults.map { it.memoryBytes }.average().takeIf { !it.isNaN() } ?: 0.0
    val memCompetitorBytes = competitorResults.map { it.memoryBytes }.average().takeIf { !it.isNaN() } ?: 0.0
    val memFactor = if (memGhostBytes > 0) memCompetitorBytes / memGhostBytes else 1.0

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
            SampleText(
                text = "YAML PERFORMANCE INSIGHT",
                isBold = true,
                fontSize = 12,
                overrideColor = COLOR_KAML
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

            grouped.forEach { (category, results) ->
                SampleText(
                    text = category,
                    isBold = true,
                    fontSize = 11,
                    isSecondary = true,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    results.forEach { result ->
                        val color = engineColor(result)
                        val engineName = result.name.substringAfter("] ").trim()
                        MetricItem(
                            title = engineName,
                            value = "${"%.2f".format(result.timeMs)}ms",
                            overrideColor = color
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    results.forEach { result ->
                        val color = engineColor(result)
                        val engineName = result.name.substringAfter("] ").trim()
                        MetricItem(
                            title = "$engineName MEM",
                            value = "${result.memoryBytes / 1024} KB",
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

            TextButton(
                onClick = onCopyLogs,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                SampleText(
                    text = "COPY SESSION LOGS",
                    overrideColor = COLOR_KAML,
                    isBold = true,
                    fontSize = 12
                )
            }
        }
    }
}

private fun engineColor(result: EngineResult): Color = when {
    result.name.contains("GHOST") -> AppDesign.AccentGlow
    result.name.contains("KAML") -> COLOR_KAML
    else -> COLOR_JACKSON
}
