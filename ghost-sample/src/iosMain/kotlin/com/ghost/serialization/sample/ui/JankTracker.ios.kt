package com.ghost.serialization.sample.ui

import androidx.compose.runtime.*

class IosJankTracker : JankTracker {
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
            // Heuristic: > 12ms on iOS (ProMotion 120Hz/8ms or 60Hz/16ms) = Jank
            if (delta > 12_000_000L) {
                onJankDetected()
            }
        }
        lastFrameTime = frameTimeNanos
    }
}

@Composable
actual fun rememberJankTracker(): JankTracker {
    val tracker = remember { IosJankTracker() }
    
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameTime ->
                tracker.recordFrame(frameTime)
            }
        }
    }
    
    return tracker
}
