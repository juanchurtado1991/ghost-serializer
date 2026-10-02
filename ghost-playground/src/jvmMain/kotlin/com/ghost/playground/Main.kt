package com.ghost.playground

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.ghost.playground.i18n.I18n
import com.ghost.playground.ui.GhostPlaygroundApp
import com.ghost.serialization.Ghost
import com.ghost.serialization.generated.GhostModuleRegistry_playground

fun main() = application {
    Ghost.addRegistry(registry = GhostModuleRegistry_playground.INSTANCE)
    Window(onCloseRequest = ::exitApplication, title = I18n.BRAND) {
        GhostPlaygroundApp()
    }
}
