package com.ghost.serialization.sample.ui

import androidx.compose.runtime.*

class JvmJankTracker : JankTracker {
    private var currentJankAccumulator = 0
    private var isTracking = false
    private var lastFrameTime = 0L

    override fun startTracking(engineName: String) {
        currentJankAccumulator = 0
        isTracking = true
        lastFrameTime = 0L
    }

    override fun stopTracking(): Int {
        val count = currentJankAccumulator
        isTracking = false
        currentJankAccumulator = 0
        return count
    }

    override fun onJankDetected() {
        if (isTracking) {
            currentJankAccumulator++
        }
    }
    
    override fun recordFrame(frameTimeNanos: Long) {
        if (lastFrameTime != 0L && isTracking) {
            val delta = frameTimeNanos - lastFrameTime
            // Heuristic: > 25ms on Desktop = Jank (Be a bit more lenient to avoid false positives)
            if (delta > 25_000_000L) {
                onJankDetected()
            }
        }
        lastFrameTime = frameTimeNanos
    }
}

@Composable
actual fun rememberJankTracker(): JankTracker {
    val tracker = remember { JvmJankTracker() }
    
    LaunchedEffect(tracker) {
        while (true) {
            withFrameNanos { frameTime ->
                tracker.recordFrame(frameTime)
            }
        }
    }
    
    return tracker
}
