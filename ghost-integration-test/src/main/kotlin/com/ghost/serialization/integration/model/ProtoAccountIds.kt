package com.ghost.serialization.integration.model

import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class ProtoAccountIds(val value: List<Long>)
