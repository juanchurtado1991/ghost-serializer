package com.ghost.serialization.sample.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * Android actual implementation of Clipboard service.
 */
actual fun copyToClipboard(text: String) {
    val ctx = AndroidContext.get() ?: return
    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Ghost Metrics", text)
    clipboard.setPrimaryClip(clip)
}
