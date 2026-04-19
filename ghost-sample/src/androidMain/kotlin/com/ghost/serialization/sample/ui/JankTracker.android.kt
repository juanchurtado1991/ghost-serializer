package com.ghost.serialization.sample.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.metrics.performance.JankStats

class AndroidJankTracker(private val activity: Activity) : JankTracker {
    private var jankStats: JankStats? = null
    private var currentJankAccumulator = 0
    private var isTracking = false

    init {
        val window = activity.window
        val decorView = window.peekDecorView()
        if (decorView != null) {
            setupJankStats(activity)
        } else {
            // Wait for attach if decorView is null
            activity.findViewById<View>(android.R.id.content)?.post {
                setupJankStats(activity)
            }
        }
    }

    private fun setupJankStats(activity: Activity) {
        try {
            jankStats = JankStats.createAndTrack(activity.window) { frameData ->
                if (isTracking && frameData.isJank) {
                    onJankDetected()
                }
            }
        } catch (e: Exception) {
            // Fallback if JankStats fails to initialize
        }
    }

    override fun startTracking(engineName: String) {
        currentJankAccumulator = 0
        isTracking = true
        jankStats?.isTrackingEnabled = true
    }

    override fun stopTracking(): Int {
        val count = currentJankAccumulator
        isTracking = false
        jankStats?.isTrackingEnabled = false
        currentJankAccumulator = 0
        return count
    }

    override fun onJankDetected() {
        currentJankAccumulator++
    }

    override fun recordFrame(frameTimeNanos: Long) {
        // Not needed as JankStats handles this natively on Android
    }
}

@Composable
actual fun rememberJankTracker(): JankTracker {
    val view = LocalView.current
    val context = view.context
    val activity = remember(context) { context.findActivity() }
    
    return remember(activity) {
        if (activity != null) {
            AndroidJankTracker(activity)
        } else {
            // No-op tracker if no activity found
            object : JankTracker {
                override fun startTracking(engineName: String) {}
                override fun stopTracking(): Int = 0
                override fun onJankDetected() {}
                override fun recordFrame(frameTimeNanos: Long) {}
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
