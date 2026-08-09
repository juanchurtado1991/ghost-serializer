@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.ktor

import com.ghost.serialization.InternalGhostApi
import io.ktor.http.ContentType

private val YAML_MEDIA_TYPE = ContentType(CONTENT_TYPE_APPLICATION, CONTENT_TYPE_YAML)

data class YamlKtorUser(val id: Int, val name: String, val isActive: Boolean)
