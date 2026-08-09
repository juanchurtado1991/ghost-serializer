package com.ghost.benchmark

import okio.Buffer

/** Thread-local encode sinks so streaming serialization reuses buffers across batched samples. */
internal object StreamingEncodeSinks {

    private val okioBuffer = ThreadLocal.withInitial { Buffer() }

    fun okioBuffer(): Buffer = okioBuffer.get().also { it.clear() }
}
