package com.ghost.serialization.sample.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ghost.serialization.Ghost
import com.ghost.serialization.sample.api.RickAndMortyApi
import com.ghost.serialization.sample.domain.GhostCharacter
import com.ghost.serialization.sample.util.copyToClipboard
import kotlinx.coroutines.launch

@Composable
fun GhostSampleApp() {
    val api = remember { RickAndMortyApi() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { Ghost.prewarm() }

    var characters by remember { mutableStateOf<List<GhostCharacter>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var networkTimeMs by remember { mutableStateOf(0.0) }
    var ghostTimeMs by remember { mutableStateOf(0.0) }
    var moshiTimeMs by remember { mutableStateOf(-1.0) }
    var kserTimeMs by remember { mutableStateOf(-1.0) }
    var ghostMemBytes by remember { mutableStateOf(0L) }
    var moshiMemBytes by remember { mutableStateOf(0L) }
    var kserMemBytes by remember { mutableStateOf(0L) }
    var pageCount by remember { mutableStateOf(1f) }
    var lastResponse by remember { mutableStateOf<List<GhostCharacter>>(emptyList()) }
    var sessionHistory by remember { mutableStateOf(listOf<String>()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IndustrialDesignSystem.BackgroundGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Header Section
            IndustrialText(
                text = "GHOST SERIALIZATION",
                isBold = true,
                fontSize = 28,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            IndustrialText(
                text = "Industrial Multiplatform Performance Laboratory",
                isSecondary = true,
                fontSize = 14,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // Stress Test Controller
            IndustrialCard(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IndustrialText(text = "STRESS LOAD (PAGES)", isBold = true, fontSize = 12)
                        IndustrialText(
                            text = "${pageCount.toInt()} PAGES (~${pageCount.toInt() * 20} ITEMS)",
                            overrideColor = IndustrialDesignSystem.AccentGlow,
                            isBold = true,
                            fontSize = 12
                        )
                    }
                    Slider(
                        value = pageCount,
                        onValueChange = { pageCount = it },
                        valueRange = 1f..20f,
                        steps = 18,
                        colors = SliderDefaults.colors(
                            thumbColor = IndustrialDesignSystem.AccentGlow,
                            activeTrackColor = IndustrialDesignSystem.AccentGlow,
                            inactiveTrackColor = IndustrialDesignSystem.BorderColor
                        )
                    )
                }
            }

            // Action Section
            IndustrialButton(
                text = "FETCH RICK AND MORTY",
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (isLoading) return@IndustrialButton
                    scope.launch {
                        isLoading = true
                        errorMessage = null
                        val result = api.getCharacters(pageCount.toInt())

                        result.onSuccess { res ->
                            characters = res.data
                            networkTimeMs = res.networkTimeMs
                            ghostTimeMs = res.parseTimeMs
                            moshiTimeMs = res.moshiTimeMs
                            kserTimeMs = res.kserTimeMs
                            ghostMemBytes = res.ghostMemoryBytes
                            moshiMemBytes = res.moshiMemoryBytes
                            kserMemBytes = res.kserMemoryBytes

                            // Record in session history
                            val timestamp = "Log #${sessionHistory.size + 1}"
                            val logEntry =
                                "$timestamp, ${formatMs(ghostTimeMs)}, ${formatMs(moshiTimeMs)}, ${
                                    formatMs(kserTimeMs)
                                }, ${ghostMemBytes / 1024}K, ${moshiMemBytes / 1024}K, ${kserMemBytes / 1024}K"
                            sessionHistory = sessionHistory + logEntry
                        }.onFailure { err ->
                            errorMessage = err.message
                        }

                        isLoading = false
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Metrics Dashboard
            AnimatedVisibility(
                visible = ghostTimeMs > 0 && !isLoading,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                IndustrialCard(modifier = Modifier.fillMaxWidth()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IndustrialText(
                                text = "TRIPLE-CORE BENCHMARK",
                                isBold = true,
                                fontSize = 11,
                                isSecondary = true
                            )
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    if (sessionHistory.isEmpty()) return@TextButton
                                    val logText =
                                        "SESSION METRICS HISTORY (Ghost vs Moshi vs KSer)\n" +
                                                "TIMESTAMP, GHOST (ms), MOSHI (ms), KSER (ms), GHOST MEM (KB), MOSHI MEM (KB), KSER MEM (KB)\n" +
                                                sessionHistory.joinToString("\n")
                                    copyToClipboard(logText)
                                },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                IndustrialText(
                                    text = "EXPORT LOGS",
                                    fontSize = 10,
                                    isSecondary = true
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Performance Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            MetricItem(
                                "GHOST",
                                formatMs(ghostTimeMs),
                                IndustrialDesignSystem.AccentGlow
                            )
                            MetricItem(
                                "MOSHI",
                                formatMs(moshiTimeMs),
                                IndustrialDesignSystem.ErrorColor
                            )
                            MetricItem(
                                "K-SER",
                                formatMs(kserTimeMs),
                                androidx.compose.ui.graphics.Color(0xFF818CF8)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Memory Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            MetricItem(
                                "GHOST MEM",
                                "${ghostMemBytes} B",
                                IndustrialDesignSystem.AccentGlow
                            )
                            MetricItem(
                                "MOSHI MEM",
                                "${moshiMemBytes} B",
                                IndustrialDesignSystem.ErrorColor
                            )
                            MetricItem(
                                "K-SER MEM",
                                "${kserMemBytes} B",
                                androidx.compose.ui.graphics.Color(0xFF818CF8)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Content Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = IndustrialDesignSystem.AccentGlow,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(48.dp)
                    )
                } else if (errorMessage != null) {
                    IndustrialText(
                        text = "HYPER-ENGINE ERROR:\n$errorMessage",
                        isBold = true,
                        fontSize = 14,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .padding(horizontal = 48.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        items(characters, key = { it.id }) { character ->
                            CharacterCard(character)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CharacterCard(character: GhostCharacter) {
    IndustrialCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Industrial Image Loading with Coil
            AsyncImage(
                model = character.image,
                contentDescription = character.name,
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(IndustrialDesignSystem.BorderColor)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                IndustrialText(
                    text = character.name,
                    isBold = true,
                    fontSize = 18,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                StatusIndicator(character.status.name)
                Spacer(modifier = Modifier.height(10.dp))
                IndustrialRow("Species:", character.species)
                IndustrialRow("Origin:", character.origin.name)
            }
        }
    }
}

@Composable
private fun MetricItem(
    title: String,
    value: String,
    overrideColor: androidx.compose.ui.graphics.Color? = null
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IndustrialText(
            text = title,
            isSecondary = true,
            fontSize = 11,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        IndustrialText(
            text = value,
            isSecondary = false,
            fontSize = 16,
            overrideColor = overrideColor
        )
    }
}

private fun formatMs(value: Double): String {
    return if (value < 0.01 && value > 0) "<0.01ms"
    else "${(value * 100).toInt() / 100.0}ms"
}
