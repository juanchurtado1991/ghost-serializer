package com.ghost.playground.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghost.playground.bench.SpeedSample
import com.ghost.playground.bench.SpeedTestEngine
import com.ghost.playground.bench.SpeedTestPhase
import com.ghost.playground.bench.formatBytes
import com.ghost.playground.bench.formatSeconds
import com.ghost.playground.bench.roundTo
import com.ghost.playground.i18n.Strings
import com.ghost.playground.ui.icons.PlaygroundIcon
import com.ghost.playground.ui.icons.PlaygroundIconKind
import com.ghost.playground.ui.theme.Coral
import com.ghost.playground.ui.theme.CoralLight
import com.ghost.playground.ui.theme.Ink
import com.ghost.playground.ui.theme.InkMuted
import com.ghost.playground.ui.theme.InkSoft
import com.ghost.playground.ui.theme.Rose
import com.ghost.playground.ui.theme.Sage
import com.ghost.playground.ui.theme.Teal
import com.ghost.playground.ui.theme.TealDark
import com.ghost.playground.ui.theme.TealLight
import kotlin.time.DurationUnit

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeedTestScreen(strings: Strings) {
    var payload by remember { mutableStateOf<String?>(null) }
    var payloadBytes by remember { mutableStateOf(0L) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }
    var sample by remember { mutableStateOf<SpeedSample?>(null) }
    var phase by remember { mutableStateOf(SpeedTestPhase.Idle) }
    var gaugeMax by remember { mutableDoubleStateOf(1.0) }

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        loadError = null
        sample = null
        phase = SpeedTestPhase.Loading
        try {
            val json = payload ?: SpeedTestEngine.loadPayload().also {
                payload = it
                payloadBytes = it.encodeToByteArray().size.toLong()
            }
            gaugeMax = 1.0
            SpeedTestEngine.run(json) { s ->
                sample = s
                phase = s.phase
                val peak = maxOf(s.ghostOpsPerSec, s.kserOpsPerSec)
                if (peak * 1.2 > gaugeMax) gaugeMax = peak * 1.2
            }
        } catch (e: Throwable) {
            loadError = e.message ?: strings.speedTestLoadFailed
            phase = SpeedTestPhase.Idle
        }
        running = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(title = strings.speedTest, accent = Teal, leadingIcon = PlaygroundIconKind.Benchmark) {
            Text(strings.speedTestIntro, style = MaterialTheme.typography.bodyLarge)
            Text(strings.speedTestPayloadNote, fontSize = 12.sp, color = InkMuted)

            loadError?.let { err ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlaygroundIcon(PlaygroundIconKind.Warning, tint = Rose, size = 16.dp)
                    Text(err, color = Rose)
                }
            }

            HeroButton(
                label = when (phase) {
                    SpeedTestPhase.Loading -> strings.speedTestLoading
                    SpeedTestPhase.RunningGhost -> strings.speedTestPhaseGhost
                    SpeedTestPhase.RunningKser -> strings.speedTestPhaseKser
                    else -> strings.speedTestStart
                },
                icon = PlaygroundIconKind.Ghost,
                colors = listOf(Teal, TealDark),
                enabled = !running,
                onClick = { running = true },
            )

            val localSample = sample
            if (localSample != null && (phase == SpeedTestPhase.RunningGhost || phase == SpeedTestPhase.RunningKser)) {
                Spacer(Modifier.height(2.dp))
                val progress = (localSample.elapsed.toDouble(DurationUnit.SECONDS) /
                        localSample.totalDuration.toDouble(DurationUnit.SECONDS))
                    .toFloat().coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = Teal,
                    trackColor = TealLight.copy(0.3f),
                )
                Text(
                    "${strings.speedTestElapsed}: ${formatSeconds(localSample.elapsed.toDouble(DurationUnit.SECONDS))} / " +
                            formatSeconds(localSample.totalDuration.toDouble(DurationUnit.SECONDS)),
                    fontSize = 12.sp,
                    color = InkMuted,
                )
            }
        }

        Card(title = "Ghost vs kotlinx.serialization", accent = Coral) {
            val s = sample
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SpeedGauge(
                    strings.speedTestKserLabel,
                    s?.kserOpsPerSec ?: 0.0,
                    gaugeMax,
                    strings.speedTestOpsPerSec,
                    Coral
                )
                Spacer(
                    Modifier.fillMaxHeight()
                        .width(48.dp)
                )
                SpeedGauge(
                    strings.speedTestGhostLabel,
                    s?.ghostOpsPerSec ?: 0.0,
                    gaugeMax,
                    strings.speedTestOpsPerSec,
                    Teal
                )
            }

            Spacer(Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatColumn(strings.speedTestRoundTrips, "${s?.kserOps ?: 0}", "${s?.ghostOps ?: 0}")
                StatColumn(
                    strings.speedTestDataProcessed,
                    formatBytes((s?.kserOps ?: 0) * payloadBytes),
                    formatBytes((s?.ghostOps ?: 0) * payloadBytes),
                )
                Column {
                    Text(
                        strings.speedTestMemory.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = InkMuted
                    )
                    Text(
                        s?.memBytes?.let { formatBytes(it) } ?: strings.speedTestMemoryNA,
                        fontWeight = FontWeight.SemiBold,
                        color = Ink,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        AnimatedVisibility(phase == SpeedTestPhase.Done && sample != null) {
            val s = sample
            if (s != null) {
                val ghostFaster = s.ghostOpsPerSec >= s.kserOpsPerSec
                val winnerName =
                    if (ghostFaster) strings.speedTestGhostLabel else strings.speedTestKserLabel
                val loserName =
                    if (ghostFaster) strings.speedTestKserLabel else strings.speedTestGhostLabel
                val winnerOps = if (ghostFaster) s.ghostOpsPerSec else s.kserOpsPerSec
                val loserOps = if (ghostFaster) s.kserOpsPerSec else s.ghostOpsPerSec
                val pct = if (loserOps > 0.0) roundTo(winnerOps / loserOps, 1) else "—"

                Card(
                    title = strings.speedTestResultTitle,
                    accent = Sage,
                    leadingIcon = PlaygroundIconKind.Check
                ) {
                    Text(
                        strings.speedTestWinnerFmt
                            .replace("{winner}", winnerName)
                            .replace("{loser}", loserName)
                            .replace("{pct}", pct),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Ink,
                    )
                    Text(
                        strings.speedTestCta,
                        style = MaterialTheme.typography.bodyLarge,
                        color = InkSoft
                    )
                }
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, kserValue: String, ghostValue: String) {
    Column {
        Text(label.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = InkMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(kserValue, fontWeight = FontWeight.SemiBold, color = CoralLight, fontSize = 13.sp)
            Text("·", color = InkMuted, fontSize = 13.sp)
            Text(ghostValue, fontWeight = FontWeight.SemiBold, color = TealDark, fontSize = 13.sp)
        }
    }
}
