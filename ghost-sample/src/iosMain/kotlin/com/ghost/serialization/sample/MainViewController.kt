package com.ghost.serialization.sample

import androidx.compose.ui.window.ComposeUIViewController
import com.ghost.serialization.Ghost
import com.ghost.serialization.generated.GhostModuleRegistry_serialization_sample
import com.ghost.serialization.sample.ui.GhostSampleApp

@Suppress("FunctionName", "unused")
fun MainViewController() = ComposeUIViewController {
    Ghost.addRegistry(GhostModuleRegistry_serialization_sample.INSTANCE)
    GhostSampleApp()
}
