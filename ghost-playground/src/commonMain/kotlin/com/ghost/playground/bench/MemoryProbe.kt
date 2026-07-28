package com.ghost.playground.bench

/** Best-effort used-heap reading; returns null when the platform exposes no such API. */
expect fun currentMemoryUsedBytes(): Long?
