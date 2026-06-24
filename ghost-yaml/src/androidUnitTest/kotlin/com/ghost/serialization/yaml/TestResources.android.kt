package com.ghost.serialization.yaml

internal actual fun readTestResource(path: String): String {
    // For Android Unit Tests running on JVM, we can access resources via JVM class loader.
    val stream = Thread.currentThread().contextClassLoader
        ?.getResourceAsStream(path)
        ?: ClassLoader.getSystemResourceAsStream(path)
        ?: error("Test resource not found: $path")
    return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
}
