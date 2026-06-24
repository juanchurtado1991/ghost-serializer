package com.ghost.serialization.yaml

internal actual fun readTestResource(path: String): String {
    val stream = Thread.currentThread().contextClassLoader
        ?.getResourceAsStream(path)
        ?: ClassLoader.getSystemResourceAsStream(path)
        ?: error("Test resource not found: $path")
    return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
}
