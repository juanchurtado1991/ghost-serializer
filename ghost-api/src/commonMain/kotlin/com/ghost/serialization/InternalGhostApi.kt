package com.ghost.serialization

import kotlin.RequiresOptIn

@RequiresOptIn(level = RequiresOptIn.Level.WARNING, message = "This is an internal Ghost API. Do not use it directly.")
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.TYPEALIAS)
annotation class InternalGhostApi
