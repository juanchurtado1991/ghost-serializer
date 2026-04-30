package com.ghost.serialization

/**
 * Explicitly marks a return value as unused to satisfy the Kotlin 2.3 -Xreturn-value-checker.
 * Use this in tests where the side-effect is important but the return value is not.
 */
fun Any?.unused() {
    // Explicitly do nothing
}
