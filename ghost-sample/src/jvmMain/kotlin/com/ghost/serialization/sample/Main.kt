package com.ghost.serialization.sample

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.ghost.serialization.Ghost
import com.ghost.serialization.generated.GhostModuleRegistry_serialization_sample
import com.ghost.serialization.sample.ui.GhostSampleApp

fun main() {
    // Manual registration for JVM
    Ghost.addRegistry(GhostModuleRegistry_serialization_sample())

    application {
        val windowState = rememberWindowState(
            size = DpSize(480.dp, 800.dp),
            position = WindowPosition(Alignment.Center)
        )

        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "Ghost Serialization - Desktop Demo"
        ) {
            GhostSampleApp()
        }
    }
}
