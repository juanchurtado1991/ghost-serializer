package com.ghost.serialization.sample.ui

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
import androidx.compose.foundation.layout.heightIn
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
import com.ghost.serialization.Ghost
import com.ghost.serialization.sample.api.EngineResult
import com.ghost.serialization.sample.api.PlatformCapabilities
import com.ghost.serialization.sample.api.RickAndMortyApi
import com.ghost.serialization.sample.domain.GhostCharacter
import com.ghost.serialization.sample.util.copyToClipboard
import kotlinx.coroutines.launch

@Composable
fun GhostSampleApp() {
    val api = remember { RickAndMortyApi() }
    val scope = rememberCoroutineScope()
    val jankTracker = rememberJankTracker()

    LaunchedEffect(Unit) { Ghost.prewarm() }

    var characters by remember { mutableStateOf<List<GhostCharacter>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var ghostTimeMs by remember { mutableStateOf(0.0) }
    var ghostMemBytes by remember { mutableStateOf(0L) }
    var ghostJankCount by remember { mutableStateOf(0) }
    var engineResults by remember { mutableStateOf<List<EngineResult>>(emptyList()) }
    var loadingStatus by remember { mutableStateOf("") }

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
                            valueRange = 1f..10f,
                            steps = 9,
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

            // Benchmark Trigger
            item {
                Button(
                    onClick = {
                        isLoading = true
                        errorMessage = null
                        loadingStatus = "Initiating..."
                        scope.launch {
                            api.getCharacters(pageCount.toInt(), jankTracker) { status ->
                                loadingStatus = status
                            }
                                .onSuccess { result ->
                                    characters = result.data
                                    ghostTimeMs = result.parseTimeMs
                                    ghostMemBytes = result.ghostMemoryBytes
                                    ghostJankCount = result.ghostJankCount
                                    engineResults = result.engineResults
                                    isLoading = false

                                    // Add to history
                                    val timestamp = "Session ${sessionHistory.size + 1}"
                                    val historyLine = "$timestamp, ${result.parseTimeMs}ms, ${
                                        engineResults.joinToString { "${it.name}: ${it.timeMs}ms" }
                                    }"
                                    sessionHistory = sessionHistory + historyLine
                                }
                                .onFailure {
                                    errorMessage = it.message
                                    isLoading = false
                                }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DesignSystem.SurfaceColor),
                    border = BorderStroke(1.dp, DesignSystem.AccentGlow)
                ) {
                    if (isLoading) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = DesignSystem.AccentGlow,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            SampleText(
                                text = loadingStatus,
                                overrideColor = DesignSystem.AccentGlow,
                                fontSize = 10
                            )
                        }
                    } else {
                        SampleText(
                            text = "RUN STRESS COMPARISON",
                            overrideColor = DesignSystem.AccentGlow,
                            isBold = true
                        )
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))
            }

            // Error Message Section
            item {
                errorMessage?.let { msg ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = DesignSystem.ErrorColor.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, DesignSystem.ErrorColor)
                    ) {
                        SampleText(
                            text = "ERROR: $msg",
                            overrideColor = DesignSystem.ErrorColor,
                            fontSize = 12,
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            item {
                if (!isLoading && (engineResults.isNotEmpty() || ghostTimeMs > 0)) {
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
                            SampleText(
                                text = "STRESS TEST RESULTS (ms / JANK)",
                                isBold = true,
                                fontSize = 12,
                                isSecondary = true,
                                textAlign = TextAlign.Center
                            )
                            
                            SampleText(
                                text = "JANK = UI stutters. Lower is better (0 = Perfect)",
                                fontSize = 10,
                                isSecondary = true,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp)
                            )

                            Spacer(modifier = Modifier.height(32.dp))

                            // Main Comparison Grid
                            val allEngines = listOf(
                                EngineResult(
                                    "GHOST",
                                    ghostTimeMs,
                                    ghostMemBytes,
                                    true,
                                    ghostJankCount
                                ) // Ghost is always supported
                            ) + engineResults

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
                                                    "${res.name} (J:${res.jankCount})",
                                                    "${(res.timeMs * 100).toInt() / 100.0}ms",
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

                            // Export Section
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
                                        // Add Jank columns
                                        columns.add("GHOST JANK")
                                        engines.forEach { columns.add("$it JANK") }
                                        
                                        val logText =
                                            header + columns.joinToString(", ") + "\n" + sessionHistory.joinToString(
                                                "\n"
                                            )
                                        copyToClipboard(logText)
                                    }
                                ) {
                                    SampleText(
                                        text = "COPY SESSION LOGS",
                                        overrideColor = DesignSystem.AccentGlow,
                                        isBold = true,
                                        fontSize = 12
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }

            // Character List Section
            items(characters, key = { it.id }) { character ->
                CharacterCard(character)
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

private fun formatMem(bytes: Long): String {
    val b = if (bytes < 0) 0L else bytes
    return when {
        b >= 1024 * 1024 -> "${(b / (1024 * 1024.0) * 100).toInt() / 100.0} MB"
        b >= 1024 -> "${(b / 1024.0 * 100).toInt() / 100.0} KB"
        else -> "$b B"
    }
}

@Composable
private fun CharacterCard(character: GhostCharacter) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = DesignSystem.SurfaceColor,
        border = BorderStroke(1.dp, DesignSystem.GlassBorder)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
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
