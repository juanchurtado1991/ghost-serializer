package com.ghost.playground.bench

import com.ghost.playground.bench.model.TwitterResponse
import com.ghost.serialization.Ghost
import com.ghost.serialization.generated.GhostModuleRegistry_playground
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class SpeedTestEngineJvmTest {

    @BeforeTest
    fun registerModule() {
        Ghost.addRegistry(registry = GhostModuleRegistry_playground.INSTANCE)
    }

    @Test
    fun bundledTwitterDatasetRoundTripsThroughAllEngines() = runBlocking {
        val payload = SpeedTestEngine.loadPayload()
        assertTrue(
            actual = payload.text.length > 5_000,
            message = "expected a non-trivial twitter_macro.json payload, got ${payload.text.length} chars"
        )
        assertEquals(expected = payload.utf8.size.toLong(), actual = payload.sizeBytes)
        assertEquals(expected = payload.text, actual = payload.utf8.decodeToString())

        val ghostDecoded = Ghost.deserialize<TwitterResponse>(payload.text)
        assertTrue(actual = ghostDecoded.statuses.isNotEmpty())
        val ghostEncoded = Ghost.encodeToString(ghostDecoded)
        assertTrue(actual = ghostEncoded.contains("\"statuses\""))
        val ghostRoundTripped = Ghost.deserialize<TwitterResponse>(ghostEncoded)
        assertEquals(expected = ghostDecoded.statuses.size, actual = ghostRoundTripped.statuses.size)

        val json = Json { ignoreUnknownKeys = true }
        val kserDecoded = json.decodeFromString<TwitterResponse>(payload.text)
        assertEquals(
            expected = ghostDecoded.statuses.size,
            actual = kserDecoded.statuses.size,
            message = "Ghost and kser disagree on tweet count"
        )
        assertEquals(expected = ghostDecoded.statuses.first().id, actual = kserDecoded.statuses.first().id)
        assertEquals(
            expected = ghostDecoded.statuses.first().user.screenName,
            actual = kserDecoded.statuses.first().user.screenName
        )

        MoshiBench.roundTrip(payload = payload.text)
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
                assertTrue(actual = sample.kserOpsPerSec > 0.0, message = "kser rate should hold after its phase")
            }
            if (sample.phase == SpeedTestPhase.RunningGhost || sample.phase == SpeedTestPhase.Done) {
                assertTrue(actual = sample.moshiOpsPerSec > 0.0, message = "moshi rate should hold after its phase")
            }
        }

        assertTrue(actual = sawDone, message = "expected a final Done sample")
        assertTrue(actual = SpeedTestPhase.Warmup in phases, message = "expected warmup samples")
        assertTrue(actual = SpeedTestPhase.RunningKser in phases, message = "expected kser phase samples")
        assertTrue(actual = SpeedTestPhase.RunningMoshi in phases, message = "expected moshi phase samples")
        assertTrue(actual = SpeedTestPhase.RunningGhost in phases, message = "expected ghost phase samples")
        assertTrue(actual = lastGhostOps > 0, message = "Ghost should have completed at least one round-trip")
        assertTrue(actual = lastKserOps > 0, message = "kser should have completed at least one round-trip")
        assertTrue(actual = lastMoshiOps > 0, message = "Moshi should have completed at least one round-trip")
    }
}
