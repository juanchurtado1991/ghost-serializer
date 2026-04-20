package com.ghost.serialization.sample.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.ghost.serialization.Ghost
import com.ghost.serialization.sample.domain.GhostCharacter
import com.ghost.serialization.sample.ui.model.NetworkStack
import com.ghost.serialization.sample.ui.viewmodel.MainViewModel
import com.ghost.serialization.sample.util.copyToClipboard
import com.ghost.serialization.sample.util.formatMem

@Composable
fun GhostSampleApp(vm: MainViewModel = viewModel { MainViewModel() }) {
    val uiState by vm.uiState.collectAsState()
    val jankTracker = rememberJankTracker()

    if (uiState.isStackDialogVisible) {
        StackSelectorDialog(
            current = uiState.selectedStack,
            onSelect = { vm.selectStack(it) },
            onDismiss = { vm.showStackDialog(false) }
        )
    }

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
            contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp)
        ) {
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
                            SampleText(text = "STRESS LOAD", isBold = true, fontSize = 12)
                            SampleText(
                                text = "${uiState.pageCount.toInt()} PAGES (x100)",
                                overrideColor = DesignSystem.AccentGlow,
                                isBold = true,
                                fontSize = 12
                            )
                        }

                        Slider(
                            value = uiState.pageCount,
                            onValueChange = { vm.updatePageCount(it) },
                            valueRange = 1f..10f,
                            steps = 9,
                            modifier = Modifier.padding(top = 8.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = DesignSystem.AccentGlow,
                                activeTrackColor = DesignSystem.AccentGlow,
                                inactiveTrackColor = DesignSystem.GlassBorder
                            )
                        )

                        HorizontalDivider(
                            color = DesignSystem.GlassBorder,
                            thickness = 1.dp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.showStackDialog(true) }
                        ) {
                            SampleText(
                                text = "SELECTED NETWORK STACK",
                                isBold = true,
                                fontSize = 10,
                                isSecondary = true
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    SampleText(
                                        text = uiState.selectedStack.title,
                                        isBold = true,
                                        fontSize = 16,
                                        overrideColor = DesignSystem.AccentGlow
                                    )
                                    SampleText(
                                        text = uiState.selectedStack.description,
                                        fontSize = 10,
                                        isSecondary = true
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                Button(
                    onClick = { vm.runBenchmark(jankTracker) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DesignSystem.SurfaceColor),
                    border = BorderStroke(1.dp, DesignSystem.AccentGlow)
                ) {
                    if (uiState.isLoading) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = DesignSystem.AccentGlow,
                                strokeWidth = 2.dp
                            )
                            SampleText(
                                text = uiState.loadingStatus,
                                fontSize = 10,
                                overrideColor = DesignSystem.AccentGlow
                            )
                        }
                    } else {
                        SampleText(
                            text = "RUN STRESS COMPARISON",
                            isBold = true,
                            fontSize = 14,
                            overrideColor = DesignSystem.AccentGlow
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            if (uiState.errorMessage != null) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFEF4444).copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, Color(0xFFEF4444))
                    ) {
                        SampleText(
                            text = "ERROR: ${uiState.errorMessage}",
                            overrideColor = Color(0xFFEF4444),
                            fontSize = 12,
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            if (uiState.results.isNotEmpty()) {
                item {
                    val selectedEngineName = when (uiState.selectedStack) {
                        NetworkStack.GHOST_KTOR -> "GHOST"
                        else -> "K-SER"
                    }
                    val ghostRes = uiState.results.find { it.name == "GHOST" }
                    val comparisonEngineName = if (selectedEngineName == "GHOST") "KSER" else selectedEngineName
                    val currentRes = uiState.results.find { it.name == comparisonEngineName } ?: uiState.results.find { it.name == "KSER" }
                    
                    val speedFactor =
                        if (ghostRes != null && currentRes != null && ghostRes.timeMs > 0) {
                            (currentRes.timeMs / ghostRes.timeMs)
                        } else 1.0

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
                                text = "PERFORMANCE INSIGHT",
                                isBold = true,
                                fontSize = 12,
                                overrideColor = DesignSystem.AccentGlow
                            )

                            val insightText = if (selectedEngineName == "GHOST") {
                                "You are using the fastest stack. Pure KMP power."
                            } else {
                                "Ghost is ${(speedFactor * 100).toInt() / 100.0}x faster than your current stack."
                            }
                            SampleText(
                                text = insightText,
                                fontSize = 16,
                                isBold = true,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )

                            HorizontalDivider(
                                color = DesignSystem.GlassBorder,
                                thickness = 1.dp,
                                modifier = Modifier.padding(bottom = 24.dp)
                            )

                            SampleText(
                                text = "KMP STRESS RESULTS (ms / JANK)",
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
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Spacer(modifier = Modifier.height(24.dp))

                            val chunkedResults = uiState.results.chunked(2)
                            chunkedResults.forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    row.forEach { res ->
                                        val isCurrent = res.name == selectedEngineName
                                        val color = when (res.name) {
                                            "GHOST" -> DesignSystem.AccentGlow
                                            else -> Color(0xFF818CF8)
                                        }
                                        Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                if (isCurrent) SampleText(
                                                    text = "YOUR STACK",
                                                    fontSize = 8,
                                                    isBold = true,
                                                    overrideColor = color
                                                )
                                                MetricItem(
                                                    title = res.name + " (J:${res.jankCount})",
                                                    value = "${(res.timeMs * 100).toInt() / 100.0}ms",
                                                    overrideColor = if (isCurrent) color else color.copy(
                                                        alpha = 0.6f
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }

                            if (uiState.results.any { it.memoryBytes > 0 }) {
                                Spacer(modifier = Modifier.height(16.dp))
                                SampleText(
                                    text = "MEMORY ALLOCATION",
                                    isBold = true,
                                    fontSize = 10,
                                    isSecondary = true,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                                chunkedResults.forEach { row ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        row.forEach { res ->
                                            val isCurrent = res.name == selectedEngineName
                                            val color = when (res.name) {
                                                "GHOST" -> DesignSystem.AccentGlow
                                                else -> Color(0xFF818CF8)
                                            }
                                            Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                                                MetricItem(
                                                    title = res.name + " MEM",
                                                    value = formatMem(res.memoryBytes),
                                                    overrideColor = if (isCurrent) color else color.copy(
                                                        alpha = 0.6f
                                                    )
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                            TextButton(onClick = {
                                copyToClipboard(
                                    uiState.sessionHistory.joinToString(
                                        "\n"
                                    )
                                )
                            }) {
                                SampleText(
                                    text = "COPY SESSION LOGS",
                                    overrideColor = DesignSystem.AccentGlow,
                                    isBold = true,
                                    fontSize = 12
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }

            items(uiState.characters, key = { it.id }) { character ->
                CharacterCard(character)
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun StackSelectorDialog(
    current: NetworkStack,
    onSelect: (NetworkStack) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = DesignSystem.SurfaceColor,
            border = BorderStroke(1.dp, DesignSystem.GlassBorder)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                SampleText(
                    text = "SELECT KMP STACK",
                    isBold = true,
                    fontSize = 18,
                    overrideColor = DesignSystem.AccentGlow
                )
                SampleText(
                    text = "Choose the multiplatform engine used for fetching.",
                    fontSize = 12,
                    isSecondary = true,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                NetworkStack.entries.forEach { stack ->
                    val isSelected = stack == current
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .clickable { onSelect(stack) },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) DesignSystem.AccentGlow.copy(alpha = 0.1f) else Color.Transparent,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) DesignSystem.AccentGlow else DesignSystem.GlassBorder
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            SampleText(
                                text = stack.title,
                                isBold = true,
                                fontSize = 14,
                                overrideColor = if (isSelected) DesignSystem.AccentGlow else DesignSystem.TextPrimary
                            )
                            SampleText(text = stack.description, fontSize = 10, isSecondary = true)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    SampleText(
                        text = "CLOSE",
                        isBold = true,
                        fontSize = 14,
                        overrideColor = DesignSystem.AccentGlow
                    )
                }
            }
        }
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
                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(12.dp))
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
