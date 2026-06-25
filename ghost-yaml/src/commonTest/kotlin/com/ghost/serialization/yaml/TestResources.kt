package com.ghost.serialization.yaml
import com.ghost.serialization.yaml.parser.GhostYamlFlatReader
import com.ghost.serialization.yaml.writer.GhostYamlFlatWriter
import com.ghost.serialization.yaml.writer.GhostYamlWriter

/**
 * Reads a test resource file by path (relative to commonTest/resources).
 * Implemented per-platform via expect/actual.
 */
internal expect fun readTestResource(path: String): String
