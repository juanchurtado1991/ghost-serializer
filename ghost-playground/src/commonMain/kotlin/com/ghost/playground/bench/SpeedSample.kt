package com.ghost.playground.bench

import kotlin.time.Duration

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
