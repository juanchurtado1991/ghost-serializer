package com.ghost.serialization.parser.common

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants
import com.ghost.serialization.parser.common.constants.GhostJsonTokens

/**
 * Skeletal [GhostSource] — extend this instead of implementing [GhostSource] directly for the
 * defaults most implementations don't need to customize: [byteOrEof] delegates to [GhostSource.get]
 * with an EOF sentinel, [decodeJsonStringRange] ignores the 7-bit hint and delegates to
 * [GhostSource.decodeToString], [rawSourceData] reports [GhostJsonTokens.EMPTY_BYTES].
 */
@InternalGhostApi
abstract class AbstractGhostSource : GhostSource {

    override fun byteOrEof(
        index: Int
    ): Int = if (index < size) get(index = index) else GhostJsonScanConstants.MATCH_END

    override fun decodeJsonStringRange(
        start: Int,
        end: Int,
        isKnown7BitContent: Boolean
    ): String = decodeToString(start = start, end = end)

    override val rawSourceData: ByteArray get() = GhostJsonTokens.EMPTY_BYTES
}
