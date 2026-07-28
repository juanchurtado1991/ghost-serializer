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

enum class SpeedTestPhase {
    Idle,
    Loading,
    Warmup,
    RunningKser,
    RunningMoshi,
    RunningGhost,
    Done,
}

data class SpeedSample(
    val phase: SpeedTestPhase,
    val elapsed: Duration,
    val totalDuration: Duration,
    val kserOps: Long,
    val moshiOps: Long,
    val ghostOps: Long,
    val kserOpsPerSec: Double,
    val moshiOpsPerSec: Double,
    val ghostOpsPerSec: Double,
    val kserBytesPerSec: Double,
    val moshiBytesPerSec: Double,
    val ghostBytesPerSec: Double,
    val memBytes: Long?,
)

/**
 * Speed-test-style benchmark: a short shared warmup, then measured phases in order
 * **KSER → Moshi (codegen) → Ghost**. Ghost runs last so its result appears after the others.
 *
 * Kotlin/Wasm executes on the browser's single thread; see [runPhase] for batching and
 * [delay] between batches.
 */
object SpeedTestEngine {
    val WARMUP_DURATION: Duration = 3.seconds
    val PHASE_DURATION: Duration = 15.seconds
    val TOTAL_DURATION: Duration = WARMUP_DURATION + PHASE_DURATION * 3
    private val BATCH_BUDGET: Duration = 30.milliseconds
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun loadPayload(): String =
        Res.readBytes("files/twitter_macro.json").decodeToString()

    suspend fun run(
        payload: String,
        warmupDuration: Duration = WARMUP_DURATION,
        phaseDuration: Duration = PHASE_DURATION,
        onUpdate: suspend (SpeedSample) -> Unit,
    ) {
        val payloadBytes = payload.encodeToByteArray().size.toLong()
        val totalDuration = warmupDuration + phaseDuration * 3

        warmup(payload, warmupDuration, payloadBytes, totalDuration, onUpdate)

        val kser = runPhase(
            duration = phaseDuration,
            op = { json.encodeToString(json.decodeFromString<TwitterResponse>(payload)) },
        ) { elapsed, ops, opsPerSec ->
            onUpdate(
                sample(
                    phase = SpeedTestPhase.RunningKser,
                    elapsed = warmupDuration + elapsed,
                    totalDuration = totalDuration,
                    payloadBytes = payloadBytes,
                    kserOps = ops,
                    kserOpsPerSec = opsPerSec,
                ),
            )
        }

        val moshi = runPhase(
            duration = phaseDuration,
            op = { MoshiBench.roundTrip(payload) },
        ) { elapsed, ops, opsPerSec ->
            onUpdate(
                sample(
                    phase = SpeedTestPhase.RunningMoshi,
                    elapsed = warmupDuration + phaseDuration + elapsed,
                    totalDuration = totalDuration,
                    payloadBytes = payloadBytes,
                    kserOps = kser.ops,
                    kserOpsPerSec = kser.avgOpsPerSec,
                    moshiOps = ops,
                    moshiOpsPerSec = opsPerSec,
                ),
            )
        }

        val ghost = runPhase(
            duration = phaseDuration,
            op = { Ghost.encodeToString(Ghost.deserialize<TwitterResponse>(payload)) },
        ) { elapsed, ops, opsPerSec ->
            onUpdate(
                sample(
                    phase = SpeedTestPhase.RunningGhost,
                    elapsed = warmupDuration + phaseDuration * 2 + elapsed,
                    totalDuration = totalDuration,
                    payloadBytes = payloadBytes,
                    kserOps = kser.ops,
                    kserOpsPerSec = kser.avgOpsPerSec,
                    moshiOps = moshi.ops,
                    moshiOpsPerSec = moshi.avgOpsPerSec,
                    ghostOps = ops,
                    ghostOpsPerSec = opsPerSec,
                ),
            )
        }

        onUpdate(
            sample(
                phase = SpeedTestPhase.Done,
                elapsed = totalDuration,
                totalDuration = totalDuration,
                payloadBytes = payloadBytes,
                kserOps = kser.ops,
                kserOpsPerSec = kser.avgOpsPerSec,
                moshiOps = moshi.ops,
                moshiOpsPerSec = moshi.avgOpsPerSec,
                ghostOps = ghost.ops,
                ghostOpsPerSec = ghost.avgOpsPerSec,
            ),
        )
    }

    private suspend fun warmup(
        payload: String,
        warmupDuration: Duration,
        payloadBytes: Long,
        totalDuration: Duration,
        onUpdate: suspend (SpeedSample) -> Unit,
    ) {
        val warmupStart = TimeSource.Monotonic.markNow()
        while (warmupStart.elapsedNow() < warmupDuration) {
            val batchStart = TimeSource.Monotonic.markNow()
            while (batchStart.elapsedNow() < BATCH_BUDGET && warmupStart.elapsedNow() < warmupDuration) {
                json.encodeToString(json.decodeFromString<TwitterResponse>(payload))
                MoshiBench.roundTrip(payload)
                Ghost.encodeToString(Ghost.deserialize<TwitterResponse>(payload))
            }
            onUpdate(
                sample(
                    phase = SpeedTestPhase.Warmup,
                    elapsed = warmupStart.elapsedNow(),
                    totalDuration = totalDuration,
                    payloadBytes = payloadBytes,
                ),
            )
            delay(1)
        }
    }

    private fun sample(
        phase: SpeedTestPhase,
        elapsed: Duration,
        totalDuration: Duration,
        payloadBytes: Long,
        kserOps: Long = 0,
        kserOpsPerSec: Double = 0.0,
        moshiOps: Long = 0,
        moshiOpsPerSec: Double = 0.0,
        ghostOps: Long = 0,
        ghostOpsPerSec: Double = 0.0,
    ): SpeedSample {
        return SpeedSample(
            phase = phase,
            elapsed = elapsed,
            totalDuration = totalDuration,
            kserOps = kserOps,
            moshiOps = moshiOps,
            ghostOps = ghostOps,
            kserOpsPerSec = kserOpsPerSec,
            moshiOpsPerSec = moshiOpsPerSec,
            ghostOpsPerSec = ghostOpsPerSec,
            kserBytesPerSec = kserOpsPerSec * payloadBytes,
            moshiBytesPerSec = moshiOpsPerSec * payloadBytes,
            ghostBytesPerSec = ghostOpsPerSec * payloadBytes,
            memBytes = currentMemoryUsedBytes(),
        )
    }

    private class PhaseResult(val ops: Long, val avgOpsPerSec: Double)

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
            val instantOpsPerSec =
                if (batchElapsed > Duration.ZERO) batchOps / batchElapsed.toDouble(DurationUnit.SECONDS) else 0.0
            onBatch(phaseStart.elapsedNow(), ops, instantOpsPerSec)
            delay(1)
        }
        val elapsed = phaseStart.elapsedNow()
        val avg = if (elapsed > Duration.ZERO) ops / elapsed.toDouble(DurationUnit.SECONDS) else 0.0
        return PhaseResult(ops, avg)
    }
}
