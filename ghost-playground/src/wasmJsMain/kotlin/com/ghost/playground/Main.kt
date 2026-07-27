package com.ghost.playground

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.ghost.playground.ui.GhostPlaygroundApp
import com.ghost.serialization.Ghost
import com.ghost.serialization.generated.GhostModuleRegistry_playground
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    Ghost.addRegistry(GhostModuleRegistry_playground.INSTANCE)
    ComposeViewport(document.body!!) {
        GhostPlaygroundApp()
    }
}
