package com.ghost.serialization.sample.ui

import androidx.compose.runtime.Composable

interface JankTracker {
    fun startTracking(engineName: String)
    fun stopTracking(): Int
    fun onJankDetected()
    fun recordFrame(frameTimeNanos: Long)
}

@Composable
expect fun rememberJankTracker(): JankTracker
