package com.ghost.playground.bench

/** Platform-specific Moshi codegen round-trip used by the Speed Test lab. */
expect object MoshiBench {
    fun roundTrip(payload: String)
}
