package com.ghost.playground.bench

import com.ghost.playground.bench.moshi.TwitterResponseJsonAdapter
import com.squareup.moshi.Moshi

actual object MoshiBench {
    private val adapter = TwitterResponseJsonAdapter(Moshi.Builder().build())

    actual fun roundTrip(payload: String) {
        adapter.toJson(adapter.fromJson(payload)!!)
    }
}
