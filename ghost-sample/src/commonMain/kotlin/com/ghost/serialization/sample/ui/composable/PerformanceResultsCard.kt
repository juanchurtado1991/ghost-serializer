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
import kotlin.collections.chunked
import kotlin.collections.forEach

@Composable
fun PerformanceResultsCard(
    uiState: UiState,
    onCopyLogs: () -> Unit
) {
    val ghostPure = uiState.results.find { it.name == "GHOST PURE" }
    val kserPure = uiState.results.find { it.name == "KSER PURE" }
    val ghostFull = uiState.results.find { it.name == "GHOST + KTOR" }
    val kserFull = uiState.results.find { it.name == "KTOR + KSER" }

    val engineSpeedFactor =
        if (ghostPure != null && kserPure != null && ghostPure.timeMs > 0) {
            (kserPure.timeMs / ghostPure.timeMs)
        } else 1.0

    val stackSpeedFactor =
        if (ghostFull != null && kserFull != null && ghostFull.timeMs > 0) {
            (kserFull.timeMs / ghostFull.timeMs)
        } else 1.0

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
                text = "PERFORMANCE INSIGHT",
                isBold = true,
                fontSize = 12,
                overrideColor = AppDesign.AccentGlow
            )

            val insightText = "Ghost Engine is ${(engineSpeedFactor * 100).toInt() / 100.0}x faster."
            SampleText(
                text = insightText,
                fontSize = 16,
                isBold = true,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp)
            )

            val stackText = "Ghost + Ktor is ${(stackSpeedFactor * 100).toInt() / 100.0}x faster E2E."
            SampleText(
                text = stackText,
                fontSize = 12,
                isBold = false,
                isSecondary = true,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            HorizontalDivider(
                color = AppDesign.GlassBorder,
                thickness = 1.dp,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            SampleText(
                text = "JSON ➡️ OBJECTS PERFORMANCE (ms / JANK)",
                isBold = true,
                fontSize = 12,
                isSecondary = true,
                textAlign = TextAlign.Center
            )

            SampleText(
                text = "Jank represents dropped frames. 0 means perfect fluidity.",
                fontSize = 10,
                isSecondary = true,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            val chunkedResults = uiState.results.chunked(2)
            chunkedResults.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(
                        16.dp,
                        Alignment.CenterHorizontally
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    row.forEach { res ->
                        val color = when {
                            res.name.contains("GHOST") -> AppDesign.AccentGlow
                            else -> Color(0xFF818CF8)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            MetricItem(
                                title = res.name + " (JANK:${res.jankCount})",
                                value = "${(res.timeMs * 100).toInt() / 100.0}ms",
                                overrideColor = color
                            )
                        }
                    }
                }
            }

            if (uiState.results.any { it.memoryBytes > 0 }) {
                SampleText(
                    text = "MEMORY ALLOCATION",
                    isBold = true,
                    fontSize = 10,
                    isSecondary = true,
                    modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
                )
                chunkedResults.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(
                            16.dp,
                            Alignment.CenterHorizontally
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        row.forEach { res ->
                            val color = when {
                                res.name.contains("GHOST") -> AppDesign.AccentGlow
                                else -> Color(0xFF818CF8)
                            }

                            MetricItem(
                                title = res.name + " MEM",
                                value = (res.memoryBytes / 1024).toString() + " KB",
                                overrideColor = color
                            )
                        }
                    }
                }
            }

            TextButton(
                onClick = onCopyLogs,
                modifier = Modifier.padding(top = 24.dp)
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