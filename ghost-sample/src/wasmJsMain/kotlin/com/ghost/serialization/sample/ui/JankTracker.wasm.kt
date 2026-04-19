package com.ghost.serialization.sample.ui

import androidx.compose.runtime.*

class WasmJankTracker : JankTracker {
    private var currentJankAccumulator = 0
    private var isTracking = false
    private var lastFrameTime = 0L

    override fun startTracking(engineName: String) {
        currentJankAccumulator = 0
        isTracking = true
        lastFrameTime = 0L
    }

    override fun stopTracking(): Int {
        isTracking = false
        return currentJankAccumulator
    }

    override fun onJankDetected() {
        currentJankAccumulator++
    }

    override fun recordFrame(frameTimeNanos: Long) {
        if (lastFrameTime != 0L && isTracking) {
            val delta = frameTimeNanos - lastFrameTime
            // Heuristic: > 25ms on Web (more lenient due to browser overhead) = Jank
            if (delta > 25_000_000L) {
                onJankDetected()
            }
        }
        lastFrameTime = frameTimeNanos
    }
}

@Composable
actual fun rememberJankTracker(): JankTracker {
    val tracker = remember { WasmJankTracker() }
    
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameTime ->
                tracker.recordFrame(frameTime)
            }
        }
    }
    
    return tracker
}
