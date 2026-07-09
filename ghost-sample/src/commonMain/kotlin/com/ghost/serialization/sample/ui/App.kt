package com.ghost.serialization.sample.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ghost.serialization.sample.ui.composable.CharacterCard
import com.ghost.serialization.sample.ui.composable.ProtoLabScreen
import com.ghost.serialization.sample.ui.composable.shared.ErrorCard
import com.ghost.serialization.sample.ui.composable.shared.PerformanceResultsCard
import com.ghost.serialization.sample.ui.composable.shared.RawPayloadViewer
import com.ghost.serialization.sample.ui.composable.shared.RunButton
import com.ghost.serialization.sample.ui.composable.shared.SampleText
import com.ghost.serialization.sample.ui.model.BenchmarkUiState
import com.ghost.serialization.sample.ui.viewmodel.MainViewModel
import com.ghost.serialization.sample.ui.viewmodel.ProtoLabViewModel
import com.ghost.serialization.sample.util.copyToClipboard
import kotlinx.coroutines.launch

private val TAB_LABELS = listOf("⚡  BENCHMARK", "🔬  PROTO LAB")

@Composable
fun GhostSampleApp(
    mainViewModel: MainViewModel = viewModel { MainViewModel() },
    protoLabViewModel: ProtoLabViewModel = viewModel { ProtoLabViewModel() }
) {
    val pagerState = rememberPagerState(pageCount = { TAB_LABELS.size })
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppDesign.BackgroundGradient)
            .statusBarsPadding()
    ) {
        // ── Tab Row ──────────────────────────────────────────────────────────────
        GhostTabRow(
            selectedIndex = pagerState.currentPage,
            onTabSelected = { index ->
                coroutineScope.launch { pagerState.animateScrollToPage(index) }
            }
        )

        // ── Pager ────────────────────────────────────────────────────────────────
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            when (pageIndex) {
                0 -> BenchmarkScreen(viewModel = mainViewModel)
                1 -> ProtoLabScreen(viewModel = protoLabViewModel)
                else -> Box(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

// ── Custom Tab Row ────────────────────────────────────────────────────────────────

@Composable
private fun GhostTabRow(selectedIndex: Int, onTabSelected: (Int) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppDesign.SurfaceColor.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, AppDesign.GlassBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TAB_LABELS.forEachIndexed { index, label ->
                val isSelected = index == selectedIndex
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onTabSelected(index) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) AppDesign.AccentGlow.copy(alpha = 0.15f)
                    else Color.Transparent,
                    border = if (isSelected) BorderStroke(1.dp, AppDesign.AccentGlow.copy(alpha = 0.5f))
                    else BorderStroke(1.dp, Color.Transparent)
                ) {
                    SampleText(
                        text = label,
                        isBold = isSelected,
                        fontSize = 12,
                        overrideColor = if (isSelected) AppDesign.AccentGlow else AppDesign.TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

// ── Benchmark Screen (original screen extracted as composable) ────────────────────

@Composable
private fun BenchmarkScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp)
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
            RunButton(
                isLoading = uiState.isLoading,
                loadingStatus = uiState.loadingStatus,
                text = "RUN STRESS COMPARISON",
                onClick = { viewModel.runBenchmark() }
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        if (uiState.rawJson.isNotEmpty()) {
            item {
                RawPayloadViewer(
                    showRawJson = uiState.showRawJson,
                    standardJson = uiState.rawJson,
                    proto3Json = null,
                    showProto3Json = false,
                    onToggleViewer = { viewModel.toggleRawJsonViewer() },
                    onToggleProto3 = {}
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        if (uiState.errorMessage != null) {
            item {
                ErrorCard(uiState.errorMessage!!)
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        if (uiState.results.isNotEmpty()) {
            item {
                PerformanceResultsCard(
                    uiState = uiState,
                    onCopyLogs = {
                        val logs = uiState
                            .sessionHistory
                            .joinToString("\n")
                        if (logs.isNotEmpty()) {
                            copyToClipboard(logs)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(40.dp))
            }
        }

        items(uiState.characters, key = { it.id }) { character ->
            CharacterCard(character)
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

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
                .background(
                    AppDesign
                        .AccentGlow
                        .copy(alpha = 0.1f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        AppDesign
                            .AccentGlow
                            .copy(alpha = 0.2f),
                        CircleShape
                    )
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

@Composable
private fun BenchmarkConfigCard(
    uiState: BenchmarkUiState,
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

// (Replaced by SharedComponents)
