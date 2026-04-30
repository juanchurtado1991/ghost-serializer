import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.ghost.serialization.Ghost
import com.ghost.serialization.generated.GhostModuleRegistry_ghost_serialization
import com.ghost.serialization.sample.ui.GhostSampleApp
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Manual registration for Wasm environment
    Ghost.addRegistry(GhostModuleRegistry_ghost_serialization.INSTANCE)
    
    ComposeViewport(document.body!!) {
        GhostSampleApp()
    }
}
