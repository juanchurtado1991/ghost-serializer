import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.ghost.serialization.sample.ui.GhostSampleApp
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Manual registration for Wasm environment
    com.ghost.serialization.Ghost.addRegistry(com.ghost.serialization.generated.GhostModuleRegistry_serialization_sample())
    
    ComposeViewport(document.body!!) {
        GhostSampleApp()
    }
}
