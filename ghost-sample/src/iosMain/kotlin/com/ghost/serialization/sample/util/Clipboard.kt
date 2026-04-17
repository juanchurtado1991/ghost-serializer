package com.ghost.serialization.sample.util

import platform.UIKit.UIPasteboard

/**
 * iOS Implementation for Clipboard interaction.
 * Uses the native [UIPasteboard] to store text safely.
 */
actual fun copyToClipboard(text: String) {
    UIPasteboard.generalPasteboard.string = text
}
