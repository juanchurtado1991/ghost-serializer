package com.ghostserializer.sample.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ghostserializer.Ghost
import com.ghostserializer.sample.api.EngineResult
import com.ghostserializer.sample.api.PlatformCapabilities
import com.ghostserializer.sample.api.RickAndMortyApi
import com.ghostserializer.sample.domain.GhostCharacter
import com.ghostserializer.sample.util.copyToClipboard
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
    var ghostMemBytes by remember { mutableStateOf(0L) }
    var engineResults by remember { mutableStateOf<List<EngineResult>>(emptyList()) }

    var pageCount by remember { mutableStateOf(1f) }
    var sessionHistory by remember { mutableStateOf(listOf<String>()) }

    val capabilities = remember { PlatformCapabilities }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DesignSystem.BackgroundGradient)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(top = 24.dp, bottom = 4.dp)
        ) {
            // Header Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        SampleText(
                            text = "GHOST",
                            isBold = true,
                            fontSize = 32,
                            overrideColor = DesignSystem.AccentGlow
                        )
                        SampleText(
                            text = "SERIALIZATION",
                            isBold = true,
                            fontSize = 32,
                            overrideColor = DesignSystem.AccentGlow
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(DesignSystem.AccentGlow.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(DesignSystem.AccentGlow.copy(alpha = 0.2f), CircleShape)
                        )
                    }
                }

                SampleText(
                    text = "Industrial Multiplatform Performance Laboratory",
                    fontSize = 14,
                    isSecondary = true,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Control Panel
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = DesignSystem.SurfaceColor,
                    border = BorderStroke(1.dp, DesignSystem.GlassBorder)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            SampleText(text = "STRESS LOAD (PAGES)", isBold = true, fontSize = 12)
                            SampleText(
                                text = "${pageCount.toInt()} PAGES (~${pageCount.toInt() * 20} ITEMS)",
                                overrideColor = DesignSystem.AccentGlow,
                                isBold = true,
                                fontSize = 12
                            )
                        }

                        Slider(
                            value = pageCount,
                            onValueChange = { pageCount = it },
                            valueRange = 1f..20f,
                            steps = 19,
                            modifier = Modifier.padding(vertical = 12.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = DesignSystem.AccentGlow,
                                activeTrackColor = DesignSystem.AccentGlow,
                                inactiveTrackColor = DesignSystem.GlassColor
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Action Button
            item {
                Button(
                    onClick = {
                        if (isLoading) return@Button
                        isLoading = true
                        errorMessage = null
                        scope.launch {
                            val result = api.getCharacters(pageCount.toInt())
                            result.onSuccess { res ->
                                characters = res.data
                                ghostTimeMs = res.parseTimeMs
                                ghostMemBytes = res.ghostMemoryBytes
                                engineResults = res.engineResults

                                val timestamp =
                                    "${Constants.STR_LOG_PREFIX}${sessionHistory.size + 1}"
                                val logParts = mutableListOf<String>()
                                logParts.add(timestamp)
                                logParts.add(formatMs(ghostTimeMs))
                                res.engineResults.forEach { logParts.add(formatMs(it.timeMs)) }

                                if (capabilities.isMemoryTrackingSupported) {
                                    logParts.add(formatMem(ghostMemBytes))
                                    res.engineResults.forEach { logParts.add(formatMem(it.memoryBytes)) }
                                }

                                sessionHistory = sessionHistory + logParts.joinToString(", ")
                            }.onFailure { err ->
                                errorMessage = err.message
                            }
                            isLoading = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DesignSystem.SurfaceColor),
                    border = BorderStroke(1.dp, DesignSystem.AccentGlow)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = DesignSystem.AccentGlow,
                            strokeWidth = 2.dp
                        )
                    } else {
                        SampleText(
                            text = Constants.STR_BTN_FETCH,
                            overrideColor = DesignSystem.AccentGlow,
                            isBold = true
                        )
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))
            }

            // Benchmark Dashboard
            item {
                if (ghostTimeMs > 0 && !isLoading) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = DesignSystem.SurfaceColor.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, DesignSystem.GlassBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Title row (Centered)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SampleText(
                                    text = Constants.STR_BENCHMARK_TITLE,
                                    isBold = true,
                                    fontSize = 12,
                                    isSecondary = true,
                                    textAlign = TextAlign.Center
                                )
                            }

                            Spacer(modifier = Modifier.height(32.dp))

                            val allEngines = listOf(
                                EngineResult(
                                    "GHOST",
                                    ghostTimeMs,
                                    ghostMemBytes,
                                    true
                                )
                            ) + engineResults

                            // Metrics grid
                            Column(modifier = Modifier.fillMaxWidth()) {
                                allEngines.chunked(2).forEach { rowEngines ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        rowEngines.forEach { res ->
                                            Box(modifier = Modifier.padding(horizontal = 12.dp)) {
                                                MetricItem(
                                                    res.name,
                                                    formatMs(res.timeMs),
                                                    when (res.name) {
                                                        "GHOST" -> DesignSystem.AccentGlow
                                                        "MOSHI" -> DesignSystem.ErrorColor
                                                        "GSON" -> Color(0xFFFACC15)
                                                        else -> Color(0xFF818CF8)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }

                            if (capabilities.isMemoryTrackingSupported) {
                                Spacer(modifier = Modifier.height(8.dp))
                                SampleText(
                                    text = "MEMORY ALLOCATION",
                                    isBold = true,
                                    fontSize = 10,
                                    isSecondary = true,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    allEngines.chunked(2).forEach { rowEngines ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            rowEngines.forEach { res ->
                                                Box(modifier = Modifier.padding(horizontal = 12.dp)) {
                                                    MetricItem(
                                                        "${res.name} MEM",
                                                        formatMem(res.memoryBytes),
                                                        when (res.name) {
                                                            "GHOST" -> DesignSystem.AccentGlow
                                                            "MOSHI" -> DesignSystem.ErrorColor
                                                            "GSON" -> Color(0xFFFACC15)
                                                            else -> Color(0xFF818CF8)
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }
                                }
                            }

                            // New Export Section at the bottom of the card
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                TextButton(
                                    onClick = {
                                        if (sessionHistory.isEmpty()) return@TextButton
                                        val engines = engineResults.map { it.name }
                                        val header = "SESSION METRICS HISTORY (Ghost vs ${
                                            engines.joinToString(" vs ")
                                        })\n"
                                        val columns = mutableListOf("TIMESTAMP", "GHOST (ms)")
                                        engines.forEach { columns.add("$it (ms)") }
                                        if (capabilities.isMemoryTrackingSupported) {
                                            columns.add("GHOST MEM (KB)")
                                            engines.forEach { columns.add("$it MEM (KB)") }
                                        }
                                        val logText =
                                            header + columns.joinToString(", ") + "\n" + sessionHistory.joinToString(
                                                "\n"
                                            )
                                        copyToClipboard(logText)
                                    },
                                    contentPadding = PaddingValues(
                                        horizontal = 16.dp,
                                        vertical = 8.dp
                                    )
                                ) {
                                    SampleText(
                                        text = Constants.STR_BTN_EXPORT,
                                        fontSize = 12,
                                        isBold = true,
                                        overrideColor = DesignSystem.AccentGlow
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }

            // Error Message
            item {
                if (errorMessage != null) {
                    SampleText(
                        text = "HYPER-ENGINE ERROR:\n$errorMessage",
                        overrideColor = DesignSystem.ErrorColor,
                        isBold = true,
                        fontSize = 14,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }

            // API Results Label
            item {
                if (characters.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SampleText(text = "API RESULTS", isBold = true, fontSize = 14)
                        SampleText(
                            text = "${characters.size} ITEMS",
                            isSecondary = true,
                            fontSize = 12
                        )
                    }
                }
            }

            // Characters List
            items(characters, key = { it.id }) { character ->
                CharacterCard(character)
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun CharacterCard(character: GhostCharacter) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DesignSystem.PrimaryDark)
            ) {
                AsyncImage(
                    model = character.image,
                    contentDescription = character.name,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                SampleText(
                    text = character.name,
                    isBold = true,
                    fontSize = 18,
                    overrideColor = DesignSystem.AccentGlow
                )
                StatusIndicator(character.status.name)
                Spacer(modifier = Modifier.height(8.dp))
                SampleRow("Species:", character.species)
                SampleRow("Origin:", character.origin.name)
            }
        }
    }
}

private fun formatMs(value: Double): String {
    if (value < 0) return "N/A"
    return if (value < 0.01 && value > 0) Constants.STR_LOW_LATENCY
    else "${(value * 100).toInt() / 100.0}ms"
}

private fun formatMem(value: Long): String {
    if (value < 0) return "N/A"
    if (value == 0L) return "0 KB"
    return if (value < 1024) "${value}B"
    else if (value < 1024 * 1024) "${value / 1024}KB"
    else "${(value / 1024.0 / 1024.0 * 100.0).toInt() / 100.0}MB"
}

private object Constants {
    const val STR_LOG_PREFIX = "Log #"
    const val STR_BENCHMARK_TITLE = "DYNAMIC MULTIPLATFORM BENCHMARK"
    const val STR_BTN_EXPORT = "EXPORT LOGS"
    const val STR_BTN_FETCH = "FETCH RICK AND MORTY"
    const val STR_LOW_LATENCY = "<0.01ms"
}
