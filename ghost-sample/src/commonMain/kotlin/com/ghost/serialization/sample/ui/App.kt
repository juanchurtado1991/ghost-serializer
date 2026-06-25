package com.ghost.serialization.sample.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ghost.serialization.sample.ui.composable.CharacterCard
import com.ghost.serialization.sample.ui.composable.PerformanceResultsCard
import com.ghost.serialization.sample.ui.composable.SampleText
import com.ghost.serialization.sample.ui.composable.YamlPreviewCard
import com.ghost.serialization.sample.ui.composable.YamlResultsCard
import com.ghost.serialization.sample.ui.model.UiState
import com.ghost.serialization.sample.ui.model.YamlUiState
import com.ghost.serialization.sample.ui.viewmodel.MainViewModel
import com.ghost.serialization.sample.ui.viewmodel.YamlViewModel
import com.ghost.serialization.sample.util.copyToClipboard
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

private val COLOR_YAML_ACCENT = Color(0xFF38BDF8)

private val TAB_LABELS = listOf("JSON", "YAML")

@Composable
fun GhostSampleApp(
    mainViewModel: MainViewModel = viewModel { MainViewModel() },
    yamlViewModel: YamlViewModel = viewModel { YamlViewModel() }
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppDesign.BackgroundGradient)
            .statusBarsPadding()
    ) {
        AppTabRow(
            selectedIndex = pagerState.currentPage,
            onTabSelected = { index ->
                coroutineScope.launch {
                    pagerState.animateScrollToPage(index)
                }
            }
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            when (page) {
                TAB_JSON -> JsonBenchmarkScreen(mainViewModel)
                TAB_YAML -> YamlBenchmarkScreen(yamlViewModel)
            }
        }
    }
}

// ── Tab Row ─────────────────────────────────────────────────────────────────

@Composable
private fun AppTabRow(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppDesign.SurfaceColor,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            TAB_LABELS.forEachIndexed { index, label ->
                val isSelected = selectedIndex == index
                val accentColor = if (index == TAB_YAML) COLOR_YAML_ACCENT else AppDesign.AccentGlow

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onTabSelected(index) }
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SampleText(
                        text = label,
                        isBold = isSelected,
                        fontSize = 13,
                        overrideColor = if (isSelected) accentColor else AppDesign.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(2.dp)
                            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                            .background(if (isSelected) accentColor else Color.Transparent)
                    )
                }
            }
        }
    }
}

// ── JSON Tab ─────────────────────────────────────────────────────────────────

@Composable
private fun JsonBenchmarkScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp, start = 0.dp, end = 0.dp),
    ) {
        item {
            BenchMarkTitle()
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            BenchmarkConfigCard(
                uiState = uiState,
                viewModel = viewModel
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            RunBenchmarkButton(
                viewModel = viewModel,
                uiState = uiState
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        if (uiState.errorMessage != null) {
            item {
                ErrorItem(
                    message = uiState.errorMessage!!,
                    accentColor = AppDesign.StatusDead
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        if (uiState.results.isNotEmpty()) {
            item {
                PerformanceResultsCard(
                    uiState = uiState,
                    onCopyLogs = {
                        val logs = uiState.sessionHistory.joinToString("\n")
                        if (logs.isNotEmpty()) copyToClipboard(logs)
                    }
                )
                Spacer(modifier = Modifier.height(40.dp))
            }
        }

        items(uiState.characters, key = { it.id }) { character ->
            CharacterCard(character)
            Spacer(modifier = Modifier.height(12.dp))
        }

        item { Spacer(modifier = Modifier.navigationBarsPadding()) }
    }
}

// ── YAML Tab ─────────────────────────────────────────────────────────────────

@Composable
private fun YamlBenchmarkScreen(viewModel: YamlViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp)
    ) {
        item {
            YamlTitle()
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            YamlConfigCard(uiState = uiState, viewModel = viewModel)
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            RunYamlButton(viewModel = viewModel, uiState = uiState)
            Spacer(modifier = Modifier.height(24.dp))
        }

        if (uiState.errorMessage != null) {
            item {
                ErrorItem(
                    message = uiState.errorMessage!!,
                    accentColor = AppDesign.StatusDead
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        if (uiState.previewYaml.isNotEmpty()) {
            item {
                SampleText(
                    text = "GENERATED YAML OUTPUT",
                    isBold = true,
                    fontSize = 11,
                    isSecondary = true,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                YamlPreviewCard(
                    yamlText = uiState.previewYaml,
                    onCopy = { copyToClipboard(uiState.previewYaml) }
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        if (uiState.results.isNotEmpty()) {
            item {
                YamlResultsCard(
                    uiState = uiState,
                    onCopyLogs = {
                        val logs = uiState.sessionHistory.joinToString("\n")
                        if (logs.isNotEmpty()) copyToClipboard(logs)
                    }
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        item { Spacer(modifier = Modifier.navigationBarsPadding()) }
    }
}

// ── Shared / JSON Title ───────────────────────────────────────────────────────

@Composable
private fun BenchMarkTitle() {
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
                overrideColor = AppDesign.AccentGlow
            )
            SampleText(
                text = "SERIALIZATION",
                isBold = true,
                fontSize = 32,
                overrideColor = AppDesign.AccentGlow
            )
        }
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(AppDesign.AccentGlow.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(AppDesign.AccentGlow.copy(alpha = 0.2f), CircleShape)
            )
        }
    }
    SampleText(
        text = "Multiplatform Performance Laboratory",
        fontSize = 14,
        isSecondary = true,
        modifier = Modifier.padding(top = 8.dp)
    )
}

// ── YAML Title ────────────────────────────────────────────────────────────────

@Composable
private fun YamlTitle() {
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
                overrideColor = COLOR_YAML_ACCENT
            )
            SampleText(
                text = "YAML ENGINE",
                isBold = true,
                fontSize = 32,
                overrideColor = COLOR_YAML_ACCENT
            )
        }
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(COLOR_YAML_ACCENT.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(COLOR_YAML_ACCENT.copy(alpha = 0.2f), CircleShape)
            )
        }
    }
    SampleText(
        text = "Ghost vs KAML vs Jackson · 12 scenarios",
        fontSize = 14,
        isSecondary = true,
        modifier = Modifier.padding(top = 8.dp)
    )
}

// ── JSON Config Card ──────────────────────────────────────────────────────────

@Composable
private fun BenchmarkConfigCard(
    uiState: UiState,
    viewModel: MainViewModel
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = AppDesign.SurfaceColor,
        border = BorderStroke(1.dp, AppDesign.GlassBorder)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SampleText(text = "STRESS LOAD", isBold = true, fontSize = 12)
                SampleText(
                    text = "${uiState.pageCount.toInt()} PAGES (x100)",
                    overrideColor = AppDesign.AccentGlow,
                    isBold = true,
                    fontSize = 12
                )
            }

            Slider(
                value = uiState.pageCount,
                onValueChange = { viewModel.updatePageCount(it) },
                valueRange = 1f..20f,
                steps = 19,
                modifier = Modifier.padding(top = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = AppDesign.AccentGlow,
                    activeTrackColor = AppDesign.AccentGlow,
                    inactiveTrackColor = AppDesign.GlassBorder
                )
            )
        }
    }
}

// ── YAML Config Card ──────────────────────────────────────────────────────────

@Composable
private fun YamlConfigCard(
    uiState: YamlUiState,
    viewModel: YamlViewModel
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = AppDesign.SurfaceColor,
        border = BorderStroke(1.dp, AppDesign.GlassBorder)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SampleText(text = "DATASET SIZE", isBold = true, fontSize = 12)
                SampleText(
                    text = "${uiState.userCount.toInt()} USERS",
                    overrideColor = COLOR_YAML_ACCENT,
                    isBold = true,
                    fontSize = 12
                )
            }

            Slider(
                value = uiState.userCount,
                onValueChange = { viewModel.updateUserCount(it) },
                valueRange = 50f..2000f,
                steps = 38,
                modifier = Modifier.padding(top = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = COLOR_YAML_ACCENT,
                    activeTrackColor = COLOR_YAML_ACCENT,
                    inactiveTrackColor = AppDesign.GlassBorder
                )
            )

            HorizontalDivider(
                color = AppDesign.GlassBorder,
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            SampleText(
                text = "Compares: Ghost (YAML) · KAML · Jackson",
                fontSize = 11,
                isSecondary = true,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            SampleText(
                text = "12 scenarios · encode/decode × string/bytes",
                fontSize = 11,
                isSecondary = true,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
            )
        }
    }
}

// ── Buttons ───────────────────────────────────────────────────────────────────

@Composable
private fun RunBenchmarkButton(
    viewModel: MainViewModel,
    uiState: UiState
) {
    Button(
        onClick = { viewModel.runBenchmark() },
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AppDesign.SurfaceColor),
        border = BorderStroke(width = 1.dp, color = AppDesign.AccentGlow)
    ) {
        if (uiState.isLoading) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = AppDesign.AccentGlow,
                    strokeWidth = 2.dp
                )
                SampleText(
                    text = uiState.loadingStatus,
                    fontSize = 10,
                    overrideColor = AppDesign.AccentGlow
                )
            }
        } else {
            SampleText(
                text = "RUN STRESS COMPARISON",
                isBold = true,
                fontSize = 14,
                overrideColor = AppDesign.AccentGlow
            )
        }
    }
}

@Composable
private fun RunYamlButton(
    viewModel: YamlViewModel,
    uiState: YamlUiState
) {
    Button(
        onClick = { viewModel.runBenchmark() },
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AppDesign.SurfaceColor),
        border = BorderStroke(width = 1.dp, color = COLOR_YAML_ACCENT)
    ) {
        if (uiState.isLoading) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = COLOR_YAML_ACCENT,
                    strokeWidth = 2.dp
                )
                SampleText(
                    text = uiState.loadingStatus,
                    fontSize = 10,
                    overrideColor = COLOR_YAML_ACCENT
                )
            }
        } else {
            SampleText(
                text = "RUN YAML COMPARISON",
                isBold = true,
                fontSize = 14,
                overrideColor = COLOR_YAML_ACCENT
            )
        }
    }
}

// ── Error Item ────────────────────────────────────────────────────────────────

@Composable
private fun ErrorItem(
    message: String,
    accentColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = accentColor.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, accentColor)
    ) {
        SampleText(
            text = "ERROR: $message",
            overrideColor = accentColor,
            fontSize = 12,
            modifier = Modifier.padding(16.dp),
            textAlign = TextAlign.Center
        )
    }
}

// ── Constants ─────────────────────────────────────────────────────────────────

private const val TAB_JSON = 0
private const val TAB_YAML = 1
