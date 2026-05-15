package com.ghost.serialization.sample.util

import java.util.Locale

/**
 * JVM implementation using java.util.String.format.
 */
actual fun String.format(vararg args: Any?): String {
    return java.lang.String.format(Locale.US, this, *args)
}
