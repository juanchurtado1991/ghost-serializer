import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.ghostserializer.sample.ui.GhostSampleApp
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Manual registration for Wasm environment
    com.ghostserializer.Ghost.addRegistry(com.ghostserializer.generated.GhostModuleRegistry_serialization_sample())
    
    ComposeViewport(document.body!!) {
        GhostSampleApp()
    }
}
