package com.ghost.playground

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.ghost.playground.ui.GhostPlaygroundApp
import com.ghost.serialization.Ghost
import com.ghost.serialization.generated.GhostModuleRegistry_playground

fun main() = application {
    Ghost.addRegistry(GhostModuleRegistry_playground.INSTANCE)
    Window(onCloseRequest = ::exitApplication, title = "Ghost Serializer Playground") {
        GhostPlaygroundApp()
    }
}
