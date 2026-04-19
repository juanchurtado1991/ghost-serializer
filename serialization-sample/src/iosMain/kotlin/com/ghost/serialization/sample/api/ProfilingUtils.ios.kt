package com.ghostserializer.sample.api

import kotlin.native.runtime.NativeRuntimeApi

@OptIn(NativeRuntimeApi::class)
actual fun getCurrentThreadAllocatedBytes(): Long {
    return kotlin.native.Runtime.getUsedMemory()
}
