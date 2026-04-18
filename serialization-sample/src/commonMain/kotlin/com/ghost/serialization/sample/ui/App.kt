package com.ghost.serialization.sample.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
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
    var ghostTimeMs by remember { mutableStateOf(0.0) }
    var moshiTimeMs by remember { mutableStateOf(-1.0) }
    var kserTimeMs by remember { mutableStateOf(-1.0) }
    var ghostMemBytes by remember { mutableStateOf(0L) }
    var moshiMemBytes by remember { mutableStateOf(0L) }
    var kserMemBytes by remember { mutableStateOf(0L) }
    var pageCount by remember { mutableStateOf(1f) }
    var sessionHistory by remember { mutableStateOf(listOf<String>()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DesignSystem.BackgroundGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(64.dp))

            Box(contentAlignment = Alignment.Center) {
                // Background Glow
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(DesignSystem.GlassGlow, CircleShape)
                        .clip(CircleShape)
                )
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SampleText(
                        text = Constants.STR_APP_TITLE,
                        isBold = true,
                        fontSize = 32,
                        overrideColor = DesignSystem.AccentGlow,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    SampleText(
                        text = Constants.STR_APP_SUBTITLE,
                        isSecondary = true,
                        fontSize = 12,
                        modifier = Modifier.padding(bottom = 40.dp)
                    )
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SampleText(
                            text = Constants.STR_STRESS_LOAD,
                            isBold = true,
                            fontSize = 12
                        )
                        SampleText(
                            text = "${pageCount.toInt()} ${Constants.STR_PAGES} (~${pageCount.toInt() * 20} ${Constants.STR_ITEMS})",
                            overrideColor = DesignSystem.AccentGlow,
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
                            thumbColor = DesignSystem.AccentGlow,
                            activeTrackColor = DesignSystem.AccentGlow,
                            inactiveTrackColor = DesignSystem.BorderColor
                        )
                    )
                }
            }

            IndustrialButton(
                text = Constants.STR_BTN_FETCH,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (isLoading) return@IndustrialButton
                    scope.launch {
                        isLoading = true
                        errorMessage = null
                        val result = api.getCharacters(pageCount.toInt())

                        result.onSuccess { res ->
                            characters = res.data
                            ghostTimeMs = res.parseTimeMs
                            moshiTimeMs = res.moshiTimeMs
                            kserTimeMs = res.kserTimeMs
                            ghostMemBytes = res.ghostMemoryBytes
                            moshiMemBytes = res.moshiMemoryBytes
                            kserMemBytes = res.kserMemoryBytes

                            val timestamp = "${Constants.STR_LOG_PREFIX}${sessionHistory.size + 1}"
                            val logEntry =
                                "$timestamp, ${formatMs(ghostTimeMs)}, ${formatMs(moshiTimeMs)}, ${
                                    formatMs(kserTimeMs)
                                }, ${formatMem(ghostMemBytes)}, ${formatMem(moshiMemBytes)}, ${formatMem(kserMemBytes)}"
                            sessionHistory = sessionHistory + logEntry

                        }.onFailure { err ->
                            errorMessage = err.message
                        }

                        isLoading = false
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedVisibility(
                visible = ghostTimeMs > 0 && !isLoading,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SampleText(
                                text = Constants.STR_BENCHMARK_TITLE,
                                isBold = true,
                                fontSize = 11,
                                isSecondary = true
                            )
                            TextButton(
                                onClick = {
                                    if (sessionHistory.isEmpty()) return@TextButton
                                    val logText =
                                        Constants.STR_EXPORT_HEADER +
                                                Constants.STR_EXPORT_COLUMNS +
                                                sessionHistory.joinToString("\n")
                                    copyToClipboard(logText)
                                },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                SampleText(
                                    text = Constants.STR_BTN_EXPORT,
                                    fontSize = 10,
                                    isSecondary = true
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            MetricItem(
                                "GHOST",
                                formatMs(ghostTimeMs),
                                DesignSystem.AccentGlow
                            )
                            MetricItem(
                                "MOSHI",
                                formatMs(moshiTimeMs),
                                DesignSystem.ErrorColor
                            )
                            MetricItem(
                                "K-SER",
                                formatMs(kserTimeMs),
                                Color(0xFF818CF8)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            MetricItem(
                                "GHOST MEM",
                                formatMem(ghostMemBytes),
                                DesignSystem.AccentGlow
                            )
                            MetricItem(
                                "MOSHI MEM",
                                formatMem(moshiMemBytes),
                                DesignSystem.ErrorColor
                            )
                            MetricItem(
                                "K-SER MEM",
                                formatMem(kserMemBytes),
                                Color(0xFF818CF8)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = DesignSystem.AccentGlow,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(48.dp)
                    )
                } else if (errorMessage != null) {
                    SampleText(
                        text = "${Constants.STR_ERR_PREFIX}\n$errorMessage",
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
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DesignSystem.GlassColor)
                    .border(1.dp, DesignSystem.GlassBorder, RoundedCornerShape(12.dp))
            ) {
                AsyncImage(
                    model = character.image,
                    contentDescription = character.name,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(20.dp))

            Column {
                SampleText(
                    text = character.name,
                    isBold = true,
                    fontSize = 18,
                    overrideColor = DesignSystem.AccentGlow,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                StatusIndicator(character.status.name)
                Spacer(modifier = Modifier.height(12.dp))
                SampleRow("Species:", character.species)
                SampleRow("Origin:", character.origin.name)
            }
        }
    }
}

@Composable
private fun MetricItem(
    title: String,
    value: String,
    overrideColor: Color? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(8.dp)
    ) {
        SampleText(
            text = title.uppercase(),
            isSecondary = true,
            fontSize = 10,
            isBold = true,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Box(
            modifier = Modifier
                .background(DesignSystem.GlassColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            SampleText(
                text = value,
                isSecondary = false,
                fontSize = 17,
                isBold = true,
                overrideColor = overrideColor ?: DesignSystem.TextPrimary
            )
        }
    }
}

private fun formatMs(value: Double): String {
    if (value < 0) return "N/A"
    return if (value < 0.01 && value > 0) Constants.STR_LOW_LATENCY
    else "${(value * 100).toInt() / 100.0}ms"
}

private fun formatMem(value: Long): String {
    if (value <= 0) return "PLATFORM"
    return if (value < 1024) "${value}B"
    else if (value < 1024 * 1024) "${value / 1024}KB"
    else "${(value / 1024.0 / 1024.0 * 100.0).toInt() / 100.0}MB"
}

private object Constants {
    const val STR_APP_TITLE = "GHOST SERIALIZATION"
    const val STR_APP_SUBTITLE = "Industrial Multiplatform Performance Laboratory"
    const val STR_STRESS_LOAD = "STRESS LOAD (PAGES)"
    const val STR_PAGES = "PAGES"
    const val STR_ITEMS = "ITEMS"
    const val STR_BTN_FETCH = "FETCH RICK AND MORTY"
    const val STR_LOG_PREFIX = "Log #"
    const val STR_BENCHMARK_TITLE = "TRIPLE-CORE BENCHMARK"
    const val STR_EXPORT_HEADER = "SESSION METRICS HISTORY (Ghost vs Moshi vs KSer)\n"
    const val STR_EXPORT_COLUMNS =
        "TIMESTAMP, GHOST (ms), MOSHI (ms), KSER (ms), GHOST MEM (KB), MOSHI MEM (KB), KSER MEM (KB)\n"
    const val STR_BTN_EXPORT = "EXPORT LOGS"
    const val STR_ERR_PREFIX = "HYPER-ENGINE ERROR:"
    const val STR_LOW_LATENCY = "<0.01ms"
}
