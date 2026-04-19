package com.ghostserializer.sample

import androidx.compose.ui.window.ComposeUIViewController
import com.ghostserializer.sample.ui.GhostSampleApp

fun MainViewController() = ComposeUIViewController {
    // Manual registration for iOS (Kotlin/Native)
    com.ghostserializer.Ghost.addRegistry(com.ghostserializer.generated.GhostModuleRegistry_serialization_sample())
    
    GhostSampleApp()
}
