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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import com.ghost.serialization.sample.api.RickAndMortyApi
import com.ghost.serialization.sample.api.BenchmarkUtils
import com.ghost.serialization.sample.domain.CharacterResponse
import com.ghost.serialization.sample.domain.Character
import coil3.compose.AsyncImage
import com.ghost.serialization.Ghost
import com.ghost.serialization.sample.util.copyToClipboard
import kotlinx.coroutines.launch

@Composable
fun GhostSampleApp() {
    val api = remember { RickAndMortyApi() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { Ghost.prewarm() }

    var characters by remember { mutableStateOf<List<Character>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var networkTimeMs by remember { mutableStateOf(0.0) }
    var parseTimeMs by remember { mutableStateOf(0.0) }
    var moshiTimeMs by remember { mutableStateOf(-1.0) }
    var ghostMemBytes by remember { mutableStateOf(0L) }
    var moshiMemBytes by remember { mutableStateOf(0L) }
    var lastResponse by remember { mutableStateOf<CharacterResponse?>(null) }
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
                text = "Industrial Multiplatform Architecture Demo",
                isSecondary = true,
                fontSize = 14,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // Action Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                IndustrialButton(
                    text = "FETCH COMPLEX API",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (isLoading) return@IndustrialButton
                        scope.launch {
                            isLoading = true
                            errorMessage = null
                            val result = api.getCharacters()

                            result.onSuccess { res ->
                                characters = res.data.results
                                lastResponse = res.data
                                networkTimeMs = res.networkTimeMs
                                parseTimeMs = res.parseTimeMs
                                moshiTimeMs = res.moshiTimeMs
                                ghostMemBytes = res.ghostMemoryBytes
                                moshiMemBytes = res.moshiMemoryBytes
                                
                                // Record in session history
                                val timestamp = (System.currentTimeMillis() % 100000).toString()
                                val logEntry = "$timestamp, ${formatMs(parseTimeMs)}, ${formatMs(moshiTimeMs)}, ${res.ghostMemoryBytes/1024}K, ${res.moshiMemoryBytes/1024}K"
                                sessionHistory = sessionHistory + logEntry
                            }.onFailure { err ->
                                errorMessage = err.message
                            }

                            isLoading = false
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Metrics Dashboard
            AnimatedVisibility(
                visible = parseTimeMs > 0 && !isLoading,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(IndustrialDesignSystem.PrimaryAccent.copy(alpha = 0.1f))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Spacer(modifier = Modifier.width(40.dp)) // Equalizer
                            IndustrialText(
                                text = "⚡ HYPER-PERFORMANCE METRICS",
                                isBold = true,
                                fontSize = 12
                            )
                            // Discreet Log Copy
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    if (sessionHistory.isEmpty()) return@TextButton
                                    val logText = "SESSION METRICS HISTORY (Ghost vs Moshi)\n" +
                                            "TIMESTAMP, GHOST (ms), MOSHI (ms), GHOST MEM (KB), MOSHI MEM (KB)\n" +
                                            sessionHistory.joinToString("\n")
                                    copyToClipboard(logText)
                                },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                IndustrialText(
                                    text = "COPY LOGS",
                                    fontSize = 10,
                                    isSecondary = true
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            MetricItem("NETWORK", formatMs(networkTimeMs))
                            MetricItem("GHOST", formatMs(parseTimeMs), IndustrialDesignSystem.AccentGlow)
                            if (moshiTimeMs >= 0) {
                                MetricItem("MOSHI", formatMs(moshiTimeMs), IndustrialDesignSystem.ErrorColor)
                            }
                        }

                        if (ghostMemBytes > 0 || moshiMemBytes > 0) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                val ghostKb = ghostMemBytes / 1024
                                MetricItem("GHOST MEM", "${ghostKb}KB", IndustrialDesignSystem.AccentGlow)
                                
                                if (moshiMemBytes > 0) {
                                    val moshiKb = moshiMemBytes / 1024
                                    MetricItem("MOSHI MEM", "${moshiKb}KB", IndustrialDesignSystem.ErrorColor)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Content Area
            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = IndustrialDesignSystem.AccentGlow,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                } else if (errorMessage != null) {
                    IndustrialText(
                        text = "NETWORK ERROR: \n$errorMessage",
                        isBold = true,
                        fontSize = 14,
                        modifier = Modifier.align(Alignment.Center).padding(32.dp)
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
private fun CharacterCard(character: Character) {
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
