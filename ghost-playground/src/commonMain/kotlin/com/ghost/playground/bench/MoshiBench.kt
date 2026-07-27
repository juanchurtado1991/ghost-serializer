package com.ghost.playground.bench

/** Platform Moshi (codegen adapters) round-trip used by the Speed Test lab. */
expect object MoshiBench {
    fun roundTrip(payload: String)
}
