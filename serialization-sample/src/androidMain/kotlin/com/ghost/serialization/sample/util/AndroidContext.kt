package com.ghostserializer.sample.util

import android.content.Context
import java.lang.ref.WeakReference

/**
 * Industrial Context Holder for Multiplatform bridges.
 */
object AndroidContext {
    private var contextRef: WeakReference<Context>? = null

    fun init(context: Context) {
        contextRef = WeakReference(context.applicationContext)
    }

    fun get(): Context? = contextRef?.get()
}
