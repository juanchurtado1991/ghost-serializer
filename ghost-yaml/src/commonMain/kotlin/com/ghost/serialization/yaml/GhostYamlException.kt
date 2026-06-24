package com.ghost.serialization.yaml

/**
 * Exception thrown when Ghost encounters invalid or unsupported YAML content.
 *
 * @param message Human-readable description including byte position.
 */
class GhostYamlException(message: String) : RuntimeException(message)
