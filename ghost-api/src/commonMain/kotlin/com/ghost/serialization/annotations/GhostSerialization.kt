package com.ghost.serialization.annotations

/**
 * Annotation used to trigger the KSP compiler plugin to generate
 * a lazy Ghost implementation for the marked class.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class GhostSerialization