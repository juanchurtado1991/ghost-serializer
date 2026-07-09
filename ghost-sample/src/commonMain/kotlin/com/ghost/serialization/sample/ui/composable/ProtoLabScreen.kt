package com.ghost.serialization.sample.ui.composable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.ghost.serialization.sample.api.EngineResult
import com.ghost.serialization.sample.model.OpenLibraryBook
import com.ghost.serialization.sample.ui.AppDesign
import com.ghost.serialization.sample.ui.model.ProtoLabUiState
import com.ghost.serialization.sample.ui.model.BenchmarkUiState
import com.ghost.serialization.sample.ui.viewmodel.ProtoLabViewModel
import com.ghost.serialization.sample.util.copyToClipboard

private val TOPICS = listOf(
    "Kotlin", "gRPC", "Protobuf", "Android", "KMP", "Rust", "WebAssembly", "Distributed Systems"
)

@Composable
fun ProtoLabScreen(viewModel: ProtoLabViewModel = viewModel { ProtoLabViewModel() }) {
    val uiState by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AppDesign.BackgroundGradient)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(top = 24.dp, bottom = 40.dp)
    ) {
        // ── Header ───────────────────────────────────────────────────────────────
        item {
            ProtoLabHeader()
            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── Proto3 explanation banner ─────────────────────────────────────────────
        item {
            Proto3ExplanationBanner()
            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── Topic Selector ───────────────────────────────────────────────────────
        item {
            TopicSelectorRow(
                topics = TOPICS,
                selectedTopic = uiState.selectedTopic,
                onTopicSelected = { viewModel.selectTopic(it) }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── Run Button ───────────────────────────────────────────────────────────
        item {
            RunProtoLabButton(uiState = uiState, onRun = { viewModel.runBenchmark() })
            Spacer(modifier = Modifier.height(20.dp))
        }

        // ── Error ────────────────────────────────────────────────────────────────
        if (uiState.errorMessage != null) {
            item {
                ProtoLabErrorCard(uiState.errorMessage!!)
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        // ── Benchmark Results ────────────────────────────────────────────────────
        if (uiState.benchmarkResults.isNotEmpty()) {
            item {
                PerformanceResultsCard(
                    uiState = BenchmarkUiState(
                        results = uiState.benchmarkResults,
                        sessionHistory = uiState.sessionHistory
                    ),
                    onCopyLogs = {
                        val logs = uiState.sessionHistory.joinToString("\n")
                        if (logs.isNotEmpty()) copyToClipboard(logs)
                    }
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // ── Dual payload viewer ──────────────────────────────────────────────
            item {
                DualJsonViewer(
                    uiState = uiState,
                    onToggleViewer = { viewModel.toggleRawJson() },
                    onToggleProto3 = { viewModel.toggleProto3View() }
                )
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        // ── Book List ────────────────────────────────────────────────────────────
        if (uiState.books.isNotEmpty()) {
            item {
                SampleText(
                    text = "${uiState.books.size} BOOKS FETCHED — \"${uiState.selectedTopic}\"",
                    isBold = true,
                    fontSize = 11,
                    isSecondary = true,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            items(uiState.books, key = { it.key }) { book ->
                BookVolumeCard(book = book)
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

// ── Header ───────────────────────────────────────────────────────────────────────

@Composable
private fun ProtoLabHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            SampleText(text = "PROTO", isBold = true, fontSize = 32, overrideColor = AppDesign.AccentGlow)
            SampleText(text = "LABORATORY", isBold = true, fontSize = 32, overrideColor = AppDesign.AccentGlow)
        }
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(AppDesign.AccentGlow.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.size(40.dp).background(AppDesign.AccentGlow.copy(alpha = 0.2f), CircleShape))
        }
    }
    SampleText(
        text = "Ghost Proto vs Ghost JSON vs KotlinX-Ser",
        fontSize = 13,
        isSecondary = true,
        modifier = Modifier.padding(top = 8.dp)
    )
}

// ── Proto3 Explanation Banner ─────────────────────────────────────────────────────
//
// Explains why the proto3 JSON variant is synthesized and what it proves.

@Composable
private fun Proto3ExplanationBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = AppDesign.AccentGlow.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, AppDesign.AccentGlow.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SampleText(
                text = "HOW PROTO3 JSON COERCION WORKS",
                isBold = true,
                fontSize = 11,
                overrideColor = AppDesign.AccentGlow
            )
            Spacer(modifier = Modifier.height(8.dp))
            SampleText(
                text = "Real gRPC-Gateway APIs (YouTube viewCount, Google Cloud, Firebase) " +
                    "serialize int32/int64/double fields as quoted strings — e.g. \"pageCount\":\"312\" " +
                    "instead of \"pageCount\":312. GhostProtobuf coerces these transparently. " +
                    "Ghost JSON and KotlinX-Ser parse them as 0 or throw.",
                fontSize = 11,
                isSecondary = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            SampleText(
                text = "This benchmark downloads real Google Books data, then synthesizes " +
                    "an equivalent proto3 JSON variant with all numbers quoted as strings, " +
                    "exactly as gRPC-Gateway would emit them.",
                fontSize = 11,
                overrideColor = AppDesign.AccentCompetitor
            )
        }
    }
}

// ── Topic Selector ────────────────────────────────────────────────────────────────

@Composable
private fun TopicSelectorRow(
    topics: List<String>,
    selectedTopic: String,
    onTopicSelected: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = AppDesign.SurfaceColor,
        border = BorderStroke(1.dp, AppDesign.GlassBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SampleText(text = "SELECT TOPIC", isBold = true, fontSize = 11, isSecondary = true)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                topics.forEach { topic ->
                    FilterChip(
                        selected = topic == selectedTopic,
                        onClick = { onTopicSelected(topic) },
                        label = {
                            SampleText(
                                text = topic,
                                fontSize = 12,
                                isBold = topic == selectedTopic,
                                overrideColor = if (topic == selectedTopic) AppDesign.AccentGlow else AppDesign.TextSecondary
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppDesign.AccentGlow.copy(alpha = 0.15f),
                            containerColor = AppDesign.GlassColor
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = AppDesign.GlassBorder,
                            selectedBorderColor = AppDesign.AccentGlow.copy(alpha = 0.5f),
                            enabled = true,
                            selected = topic == selectedTopic
                        )
                    )
                }
            }
        }
    }
}

// ── Run Button ────────────────────────────────────────────────────────────────────

@Composable
private fun RunProtoLabButton(uiState: ProtoLabUiState, onRun: () -> Unit) {
    Button(
        onClick = onRun,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AppDesign.SurfaceColor),
        border = BorderStroke(1.dp, AppDesign.AccentGlow)
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
                text = "FETCH & BENCHMARK  (JSON + PROTO3 JSON)",
                isBold = true,
                fontSize = 14,
                overrideColor = AppDesign.AccentGlow
            )
        }
    }
}

// ── Error Card ────────────────────────────────────────────────────────────────────

@Composable
private fun ProtoLabErrorCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = AppDesign.StatusDead.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, AppDesign.StatusDead)
    ) {
        SampleText(
            text = "ERROR: $message",
            overrideColor = AppDesign.StatusDead,
            fontSize = 12,
            modifier = Modifier.padding(16.dp),
            textAlign = TextAlign.Center
        )
    }
}

// ── Dual JSON Viewer ──────────────────────────────────────────────────────────────
//
// Toggle between the standard REST JSON (as downloaded) and the proto3 JSON variant
// (numbers quoted as strings) to show what a real gRPC-Gateway endpoint would emit.

@Composable
private fun DualJsonViewer(
    uiState: ProtoLabUiState,
    onToggleViewer: () -> Unit,
    onToggleProto3: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = AppDesign.SurfaceColor,
        border = BorderStroke(1.dp, AppDesign.GlassBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SampleText(
                    text = "RAW PAYLOAD VIEWER",
                    isBold = true,
                    fontSize = 11,
                    isSecondary = true
                )
                TextButton(onClick = onToggleViewer) {
                    SampleText(
                        text = if (uiState.showRawJson) "HIDE" else "SHOW",
                        fontSize = 11,
                        isBold = true,
                        overrideColor = AppDesign.AccentGlow
                    )
                }
            }

            if (uiState.showRawJson) {
                HorizontalDivider(
                    color = AppDesign.GlassBorder,
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                // Tab bar: Standard JSON | Proto3 JSON
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(false, true).forEach { isProto3 ->
                        val selected = uiState.showProto3Json == isProto3
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) AppDesign.AccentGlow.copy(alpha = 0.15f)
                            else Color.Transparent,
                            border = BorderStroke(
                                1.dp,
                                if (selected) AppDesign.AccentGlow.copy(alpha = 0.5f)
                                else AppDesign.GlassBorder
                            )
                        ) {
                            TextButton(onClick = { if (!selected) onToggleProto3() }) {
                                SampleText(
                                    text = if (isProto3) "PROTO3 JSON" else "STANDARD JSON",
                                    fontSize = 10,
                                    isBold = selected,
                                    overrideColor = if (selected) AppDesign.AccentGlow
                                    else AppDesign.TextSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Descriptor
                val (label, rawJson) = if (uiState.showProto3Json)
                    "PROTO3 JSON (numbers quoted as strings — gRPC-Gateway format)" to uiState.proto3Json
                else
                    "STANDARD REST JSON (as downloaded from Google Books API)" to uiState.standardJson

                SampleText(
                    text = label,
                    fontSize = 10,
                    overrideColor = if (uiState.showProto3Json) AppDesign.AccentGlow else AppDesign.AccentCompetitor,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                val previewLength = minOf(rawJson.length, 2000)
                SampleText(
                    text = rawJson.take(previewLength) +
                        if (rawJson.length > previewLength) "\n... (${rawJson.length} total chars)" else "",
                    fontSize = 10,
                    isSecondary = true
                )
            }
        }
    }
}

// ── Book Volume Card ──────────────────────────────────────────────────────────────

@Composable
fun BookVolumeCard(book: OpenLibraryBook) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            val thumbnailUrl = if (book.cover_i > 0) "https://covers.openlibrary.org/b/id/${book.cover_i}-M.jpg" else null
            
            if (thumbnailUrl != null) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = "Cover of ${book.title}",
                    modifier = Modifier
                        .size(72.dp, 96.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(72.dp, 96.dp)
                        .background(AppDesign.GlassColor, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    SampleText(text = "📚", fontSize = 28)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                SampleText(
                    text = book.title,
                    isBold = true,
                    fontSize = 14,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                val authorsText = book.author_name.take(2).joinToString(", ")
                if (authorsText.isNotBlank()) {
                    SampleText(
                        text = authorsText,
                        fontSize = 12,
                        isSecondary = true,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (book.first_publish_year > 0) {
                        MetaBadge(
                            text = "${book.first_publish_year}",
                            color = AppDesign.AccentGlow.copy(alpha = 0.7f)
                        )
                    }
                    if (book.language.isNotEmpty()) {
                        MetaBadge(
                            text = book.language.first().uppercase(),
                            color = AppDesign.AccentCompetitor
                        )
                    }
                    if (book.edition_count > 0) {
                        MetaBadge(
                            text = "${book.edition_count} ed",
                            color = AppDesign.StatusAlive
                        )
                    }
                }
                
                if (book.subject.isNotEmpty()) {
                    SampleText(
                        text = book.subject.first(),
                        fontSize = 10,
                        isSecondary = true,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MetaBadge(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.15f),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.4f))
    ) {
        SampleText(
            text = text,
            fontSize = 10,
            overrideColor = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
