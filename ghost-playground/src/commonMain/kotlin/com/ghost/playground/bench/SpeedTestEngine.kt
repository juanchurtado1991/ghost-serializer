package com.ghost.playground.bench

import com.ghost.playground.bench.model.TwitterResponse
import com.ghost.serialization.Ghost
import com.ghostserializer.ghost_playground.generated.resources.Res
import kotlinx.coroutines.delay
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

enum class SpeedTestPhase { Idle, Loading, RunningGhost, RunningKser, Done }

data class SpeedSample(
    val phase: SpeedTestPhase,
    val elapsed: Duration,
    val totalDuration: Duration,
    val ghostOps: Long,
    val kserOps: Long,
    /** Live during Ghost's phase; holds steady at its final average once kser's phase starts. */
    val ghostOpsPerSec: Double,
    /** Zero until kser's phase starts, then live; holds steady at the final average once done. */
    val kserOpsPerSec: Double,
    val ghostBytesPerSec: Double,
    val kserBytesPerSec: Double,
    /** Process-wide used-heap sample — shared by both engines (no attribution split; see MemoryProbe). */
    val memBytes: Long?,
)

/**
 * speedtest.net-style: Ghost runs its full phase first (gauge live, kser gauge idle at 0), then
 * kser runs its phase (Ghost gauge holds its final number, kser gauge goes live). The needle
 * starts moving from the first batch, a few tens of milliseconds after Start is pressed.
 *
 * Kotlin/Wasm runs on the browser's single thread, and `yield()` only reorders the coroutine
 * scheduler's own queue — on JS/Wasm that resolves via microtasks, which the browser drains
 * *before* it gets to repaint or process input, so a tight loop of yields alone can still freeze
 * the tab (confirmed in practice: the "Page Unresponsive" dialog kept appearing even after every
 * single operation was followed by a yield). [delay] forces a real timer callback, which *does*
 * hand control back to the browser's event loop. So: do a short batch of work bounded by
 * [BATCH_BUDGET], report progress, then always `delay(1)` before the next batch.
 */
object SpeedTestEngine {
    val PHASE_DURATION: Duration = 15.seconds
    val TOTAL_DURATION: Duration = PHASE_DURATION * 2
    private val BATCH_BUDGET: Duration = 30.milliseconds
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun loadPayload(): String =
        Res.readBytes("files/twitter_macro.json").decodeToString()

    /** kser (the slower engine) runs first, then Ghost — so Ghost's win is the last thing you see. */
    suspend fun run(payload: String, phaseDuration: Duration = PHASE_DURATION, onUpdate: suspend (SpeedSample) -> Unit) {
        val payloadBytes = payload.encodeToByteArray().size.toLong()
        val totalDuration = phaseDuration * 2

        val kser = runPhase(
            duration = phaseDuration,
            op = { json.encodeToString(json.decodeFromString<TwitterResponse>(payload)) },
        ) { elapsed, ops, opsPerSec ->
            onUpdate(
                SpeedSample(
                    phase = SpeedTestPhase.RunningKser,
                    elapsed = elapsed,
                    totalDuration = totalDuration,
                    ghostOps = 0,
                    kserOps = ops,
                    ghostOpsPerSec = 0.0,
                    kserOpsPerSec = opsPerSec,
                    ghostBytesPerSec = 0.0,
                    kserBytesPerSec = opsPerSec * payloadBytes,
                    memBytes = currentMemoryUsedBytes(),
                ),
            )
        }

        val ghost = runPhase(
            duration = phaseDuration,
            op = { Ghost.encodeToString(Ghost.deserialize<TwitterResponse>(payload)) },
        ) { elapsed, ops, opsPerSec ->
            onUpdate(
                SpeedSample(
                    phase = SpeedTestPhase.RunningGhost,
                    elapsed = phaseDuration + elapsed,
                    totalDuration = totalDuration,
                    ghostOps = ops,
                    kserOps = kser.ops,
                    ghostOpsPerSec = opsPerSec,
                    kserOpsPerSec = kser.avgOpsPerSec,
                    ghostBytesPerSec = opsPerSec * payloadBytes,
                    kserBytesPerSec = kser.avgOpsPerSec * payloadBytes,
                    memBytes = currentMemoryUsedBytes(),
                ),
            )
        }

        onUpdate(
            SpeedSample(
                phase = SpeedTestPhase.Done,
                elapsed = totalDuration,
                totalDuration = totalDuration,
                ghostOps = ghost.ops,
                kserOps = kser.ops,
                ghostOpsPerSec = ghost.avgOpsPerSec,
                kserOpsPerSec = kser.avgOpsPerSec,
                ghostBytesPerSec = ghost.avgOpsPerSec * payloadBytes,
                kserBytesPerSec = kser.avgOpsPerSec * payloadBytes,
                memBytes = currentMemoryUsedBytes(),
            ),
        )
    }

    private class PhaseResult(val ops: Long, val avgOpsPerSec: Double)

    /**
     * Runs [op] repeatedly for [duration], in short [BATCH_BUDGET]-bounded batches, calling
     * [onBatch] with (elapsed-so-far, total ops-so-far, this-batch's ops/sec) after every batch —
     * always followed by a real `delay(1)` so the browser gets a genuine turn between batches.
     */
    private suspend fun runPhase(
        duration: Duration,
        op: () -> Unit,
        onBatch: suspend (elapsed: Duration, ops: Long, instantOpsPerSec: Double) -> Unit,
    ): PhaseResult {
        val phaseStart = TimeSource.Monotonic.markNow()
        var ops = 0L
        while (phaseStart.elapsedNow() < duration) {
            val batchStart = TimeSource.Monotonic.markNow()
            var batchOps = 0L
            while (batchStart.elapsedNow() < BATCH_BUDGET && phaseStart.elapsedNow() < duration) {
                op()
                batchOps++
            }
            ops += batchOps
            val batchElapsed = batchStart.elapsedNow()
            val instantOpsPerSec = if (batchElapsed > Duration.ZERO) batchOps / batchElapsed.toDouble(DurationUnit.SECONDS) else 0.0
            onBatch(phaseStart.elapsedNow(), ops, instantOpsPerSec)
            delay(1)
        }
        val elapsed = phaseStart.elapsedNow()
        val avg = if (elapsed > Duration.ZERO) ops / elapsed.toDouble(DurationUnit.SECONDS) else 0.0
        return PhaseResult(ops, avg)
    }
}
