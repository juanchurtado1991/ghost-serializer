package com.ghost.serialization.sample.util

/**
 * Wasm clipboard stub — browser Clipboard API wiring can be added later.
 * Sample demos must not crash if the platform cannot copy.
 */
actual fun copyToClipboard(text: String) {
    // No-op on wasm for now.
}
