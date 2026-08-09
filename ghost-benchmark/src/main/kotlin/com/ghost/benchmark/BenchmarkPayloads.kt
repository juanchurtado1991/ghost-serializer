package com.ghost.benchmark

import com.ghost.serialization.integration.model.ComplexResponse
import okio.ByteString.Companion.encodeUtf8

/**
 * Pre-generated JSON payloads reused across synthetic suites.
 *
 * @property smallComplex in-memory model for the smallest LIST workload ([SMALL_USER_COUNT] users).
 * @property smallBytes UTF-8 JSON for [smallComplex].
 * @property listMediumBytes JSON for a [LIST_MEDIUM_USER_COUNT]-user ComplexResponse list.
 * @property syncLargeBytes JSON for a [SYNC_LARGE_USER_COUNT]-user ComplexResponse list.
 * @property writingComplex in-memory model for the WRITING encode workload ([WRITING_USER_COUNT] users).
 * @property writingBytes pre-encoded JSON for [writingComplex]; used for encode GB/s reporting.
 * @property stressTreeBytes deeply nested Category JSON ([STRESS_TREE_DEPTH] levels) for stress parsing.
 * @property failureMalformed truncated JSON used to exercise error paths.
 * @property failureBytes UTF-8 bytes of [failureMalformed].
 */
internal data class BenchmarkPayloads(
    val smallComplex: ComplexResponse,
    val smallBytes: okio.ByteString,
    val listMediumBytes: okio.ByteString,
    val syncLargeBytes: okio.ByteString,
    val writingComplex: ComplexResponse,
    val writingBytes: okio.ByteString,
    val stressTreeBytes: okio.ByteString,
    val failureMalformed: String,
    val failureBytes: okio.ByteString,
) {
    companion object {
        private const val SMALL_USER_COUNT = 20
        private const val LIST_MEDIUM_USER_COUNT = 200
        private const val SYNC_LARGE_USER_COUNT = 2_000
        private const val WRITING_USER_COUNT = 1_000
        private const val STRESS_TREE_DEPTH = 20

        /** Builds every synthetic payload from generated in-memory models. */
        fun create(): BenchmarkPayloads {
            val smallComplex = generateComplexData(SMALL_USER_COUNT)
            val smallBytes = generateNeutralJson(smallComplex).encodeUtf8()
            val listMediumBytes =
                generateNeutralJson(generateComplexData(LIST_MEDIUM_USER_COUNT)).encodeUtf8()
            val syncLargeBytes =
                generateNeutralJson(generateComplexData(SYNC_LARGE_USER_COUNT)).encodeUtf8()
            val writingComplex = generateComplexData(WRITING_USER_COUNT)
            val writingBytes = generateNeutralJson(writingComplex).encodeUtf8()
            val stressTreeBytes = generateNeutralJson(createTree(STRESS_TREE_DEPTH)).encodeUtf8()
            val failureMalformed = smallBytes.utf8().substring(0, smallBytes.size / 2)
            return BenchmarkPayloads(
                smallComplex = smallComplex,
                smallBytes = smallBytes,
                listMediumBytes = listMediumBytes,
                syncLargeBytes = syncLargeBytes,
                writingComplex = writingComplex,
                writingBytes = writingBytes,
                stressTreeBytes = stressTreeBytes,
                failureMalformed = failureMalformed,
                failureBytes = failureMalformed.encodeUtf8(),
            )
        }
    }
}
