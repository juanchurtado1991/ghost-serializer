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
    fun bundledTwitterDatasetRoundTripsThroughAllEngines() = runBlocking {
        val payload = SpeedTestEngine.loadPayload()
        assertTrue(payload.length > 5_000, "expected a non-trivial twitter_macro.json payload, got ${payload.length} chars")

        val ghostDecoded = Ghost.deserialize<TwitterResponse>(payload)
        assertTrue(ghostDecoded.statuses.isNotEmpty())
        val ghostEncoded = Ghost.encodeToString(ghostDecoded)
        assertTrue(ghostEncoded.contains("\"statuses\""))
        val ghostRoundTripped = Ghost.deserialize<TwitterResponse>(ghostEncoded)
        assertEquals(ghostDecoded.statuses.size, ghostRoundTripped.statuses.size)

        val json = Json { ignoreUnknownKeys = true }
        val kserDecoded = json.decodeFromString<TwitterResponse>(payload)
        assertEquals(ghostDecoded.statuses.size, kserDecoded.statuses.size, "Ghost and kser disagree on tweet count")
        assertEquals(ghostDecoded.statuses.first().id, kserDecoded.statuses.first().id)
        assertEquals(ghostDecoded.statuses.first().user.screenName, kserDecoded.statuses.first().user.screenName)

        MoshiBench.roundTrip(payload)
    }

    @Test
    fun runProgressesThroughWarmupAndThreePhases() = runBlocking {
        val payload = SpeedTestEngine.loadPayload()
        val phases = mutableListOf<SpeedTestPhase>()
        var lastGhostOps = 0L
        var lastKserOps = 0L
        var lastMoshiOps = 0L
        var sawDone = false

        SpeedTestEngine.run(
            payload,
            warmupDuration = 20.milliseconds,
            phaseDuration = 60.milliseconds,
        ) { sample ->
            phases += sample.phase
            lastGhostOps = sample.ghostOps
            lastKserOps = sample.kserOps
            lastMoshiOps = sample.moshiOps
            if (sample.phase == SpeedTestPhase.Done) sawDone = true

            if (sample.phase == SpeedTestPhase.RunningMoshi || sample.phase == SpeedTestPhase.RunningGhost || sample.phase == SpeedTestPhase.Done) {
                assertTrue(sample.kserOpsPerSec > 0.0, "kser rate should hold after its phase")
            }
            if (sample.phase == SpeedTestPhase.RunningGhost || sample.phase == SpeedTestPhase.Done) {
                assertTrue(sample.moshiOpsPerSec > 0.0, "moshi rate should hold after its phase")
            }
        }

        assertTrue(sawDone, "expected a final Done sample")
        assertTrue(SpeedTestPhase.Warmup in phases, "expected warmup samples")
        assertTrue(SpeedTestPhase.RunningKser in phases, "expected kser phase samples")
        assertTrue(SpeedTestPhase.RunningMoshi in phases, "expected moshi phase samples")
        assertTrue(SpeedTestPhase.RunningGhost in phases, "expected ghost phase samples")
        assertTrue(lastGhostOps > 0, "Ghost should have completed at least one round-trip")
        assertTrue(lastKserOps > 0, "kser should have completed at least one round-trip")
        assertTrue(lastMoshiOps > 0, "Moshi should have completed at least one round-trip")
    }
}
