package com.ghost.serialization.sample

import androidx.compose.ui.window.ComposeUIViewController
import com.ghost.serialization.sample.ui.GhostSampleApp

fun MainViewController() = ComposeUIViewController {
    // Manual registration for iOS (Kotlin/Native)
    com.ghost.serialization.Ghost.addRegistry(com.ghost.serialization.generated.GhostModuleRegistry_ghost_serialization.INSTANCE)
    
    GhostSampleApp()
}
