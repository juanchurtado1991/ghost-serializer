package com.ghost.serialization.sample.util

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * JVM actual implementation of Clipboard service.
 */
actual fun copyToClipboard(text: String) {
    val selection = StringSelection(text)
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    clipboard.setContents(selection, selection)
}
