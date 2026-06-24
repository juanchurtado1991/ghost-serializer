package com.ghost.serialization.yaml

/**
 * Reads a test resource file by path (relative to commonTest/resources).
 * Implemented per-platform via expect/actual.
 */
internal expect fun readTestResource(path: String): String
