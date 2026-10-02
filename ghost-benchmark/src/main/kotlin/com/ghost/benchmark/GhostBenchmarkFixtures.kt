package com.ghost.benchmark

import com.ghost.serialization.integration.model.BenchUser
import com.ghost.serialization.integration.model.Category
import com.ghost.serialization.integration.model.ComplexResponse
import com.ghost.serialization.integration.model.ExtremeMetadata
import com.ghost.serialization.integration.model.UserRole
import kotlinx.serialization.json.Json

/**
 * Synthetic fixture generation for the benchmark harness — building sample payloads has nothing
 * to do with how they're measured, reduced, or printed, so this is split out on its own.
 */

private const val METADATA_HISTORY_SIZE = 1_000
private const val TREE_LEAF_NAME = "L"
private const val TREE_NODE_NAME = "N"
private const val RESPONSE_STATUS_SUCCESS = "success"
private const val RESPONSE_CODE = "42"
private const val SAMPLE_USER_EMAIL = "u@e.com"
private const val SAMPLE_USER_NAME_PREFIX = "User "
private const val SAMPLE_META_TAG = "beta"
private const val SAMPLE_META_SCORE = 1.2e-4
private const val SAMPLE_USER_SCORE = 1.0

/** Builds a Category tree [depth] levels deep for nesting stress tests. */
internal fun createTree(depth: Int): Category = if (depth <= 0) {
    Category(name = TREE_LEAF_NAME)
} else {
    Category(
        name = TREE_NODE_NAME,
        subCategories = listOf(createTree(depth = depth - 1))
    )
}

/** Builds a synthetic ComplexResponse with [count] users and fixed metadata. */
internal fun generateComplexData(count: Int): ComplexResponse {
    val history = IntArray(METADATA_HISTORY_SIZE) { it }
    val meta = ExtremeMetadata(
        lastLogin = System.currentTimeMillis(),
        role = UserRole.EDITOR,
        tags = listOf(SAMPLE_META_TAG),
        precisionScore = SAMPLE_META_SCORE,
        accessHistory = history
    )
    val users = List(count) { i ->
        BenchUser(
            id = i,
            name = "$SAMPLE_USER_NAME_PREFIX$i",
            email = SAMPLE_USER_EMAIL,
            score = SAMPLE_USER_SCORE,
            isActive = true,
            role = UserRole.VIEWER,
            bio = null
        )
    }
    return ComplexResponse(
        status = RESPONSE_STATUS_SUCCESS,
        data = users,
        meta = meta,
        extras = RESPONSE_CODE
    )
}

/** Encodes [data] with KotlinX Serialization for neutral cross-engine JSON fixtures. */
internal fun generateNeutralJson(data: Any): String {
    val json = Json { ignoreUnknownKeys = true }
    return when (data) {
        is ComplexResponse -> json.encodeToString(data)
        is Category -> json.encodeToString(data)
        else -> error("Unsupported benchmark payload type: ${data::class.simpleName}")
    }
}
