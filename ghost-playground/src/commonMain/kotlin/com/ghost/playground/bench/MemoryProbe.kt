package com.ghost.playground.bench

/** Best-effort used-heap reading. Returns null where the platform exposes no such API. */
expect fun currentMemoryUsedBytes(): Long?
