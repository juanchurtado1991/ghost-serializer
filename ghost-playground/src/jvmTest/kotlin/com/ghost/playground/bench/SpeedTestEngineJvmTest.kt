package com.ghost.playground.bench

import com.ghost.playground.bench.model.TwitterResponse
import com.ghost.serialization.Ghost
import com.ghost.serialization.generated.GhostModuleRegistry_playground
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class SpeedTestEngineJvmTest {

    @BeforeTest
    fun registerModule() {
        Ghost.addRegistry(GhostModuleRegistry_playground.INSTANCE)
    }

    @Test
    fun bundledTwitterDatasetRoundTripsThroughBothEngines() = runBlocking {
        val payload = SpeedTestEngine.loadPayload()
        // Trimmed down from the full twitter_macro.json (631KB) so a single decode+encode round
        // stays fast enough not to freeze the single-threaded Wasm/browser tab mid-test.
        assertTrue(payload.length > 5_000, "expected a non-trivial twitter_macro.json payload, got ${payload.length} chars")

        val ghostDecoded = Ghost.deserialize<TwitterResponse>(payload)
        assertTrue(ghostDecoded.statuses.isNotEmpty())
        val ghostEncoded = Ghost.encodeToString(ghostDecoded)
        assertTrue(ghostEncoded.contains("\"statuses\""))
        // Re-decoding Ghost's own output must reproduce the same tweet count — catches @GhostName
        // typos or nullability mismatches that a single decode alone wouldn't surface.
        val ghostRoundTripped = Ghost.deserialize<TwitterResponse>(ghostEncoded)
        assertEquals(ghostDecoded.statuses.size, ghostRoundTripped.statuses.size)

        val json = Json { ignoreUnknownKeys = true }
        val kserDecoded = json.decodeFromString<TwitterResponse>(payload)
        assertEquals(ghostDecoded.statuses.size, kserDecoded.statuses.size, "Ghost and kser disagree on tweet count for the same payload")
        assertEquals(ghostDecoded.statuses.first().id, kserDecoded.statuses.first().id)
        assertEquals(ghostDecoded.statuses.first().user.screenName, kserDecoded.statuses.first().user.screenName)
    }

    @Test
    fun runProgressesThroughBothPhasesAndReportsPlausibleThroughput() = runBlocking {
        val payload = SpeedTestEngine.loadPayload()
        val phases = mutableListOf<SpeedTestPhase>()
        var lastGhostOps = 0L
        var lastKserOps = 0L
        var sawDone = false

        // A tiny phase duration keeps this test fast while still exercising the full
        // kser-phase -> Ghost-phase -> Done state machine and the batch/delay loop inside runPhase.
        SpeedTestEngine.run(payload, phaseDuration = 60.milliseconds) { sample ->
            phases += sample.phase
            lastGhostOps = sample.ghostOps
            lastKserOps = sample.kserOps
            if (sample.phase == SpeedTestPhase.Done) sawDone = true

            // kser runs first; once Ghost's phase starts, kser's number must hold steady.
            if (sample.phase == SpeedTestPhase.RunningGhost || sample.phase == SpeedTestPhase.Done) {
                assertTrue(sample.kserOpsPerSec > 0.0, "kser's rate should hold at its final value, not reset")
            }
        }

        assertTrue(sawDone, "expected a final Done sample")
        assertTrue(SpeedTestPhase.RunningGhost in phases, "expected at least one RunningGhost sample")
        assertTrue(SpeedTestPhase.RunningKser in phases, "expected at least one RunningKser sample")
        assertTrue(lastGhostOps > 0, "Ghost should have completed at least one round-trip")
        assertTrue(lastKserOps > 0, "kser should have completed at least one round-trip")
    }
}
