@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.ghost.serialization.parser.common

actual object GhostHeuristics {
    actual val initialCollectionCapacity: Int = 10
    actual val maxStringPoolLength: Int = 64
    actual val maxCollectionSize: Int = 500_000
    actual val maxDiscriminatorPeekDistance: Int = 1024
    actual val maxWarmWriteBufferCapacity: Int = 1024 * 1024
    actual val maxWarmCharWriteBufferCapacity: Int = 512 * 1024

    /**
     * JSC (Safari / iOS): UTF-8 flat writer + TextDecoder.
     * V8 and other engines: keep [GhostJsonStringWriter] (faster on Chrome).
     * Resolved once at first access.
     */
    actual val encodeToStringViaUtf8Bytes: Boolean
        get() = utf8EncodePreference

    private val utf8EncodePreference: Boolean = ghostJsEnginePrefersUtf8EncodeToString()
}
