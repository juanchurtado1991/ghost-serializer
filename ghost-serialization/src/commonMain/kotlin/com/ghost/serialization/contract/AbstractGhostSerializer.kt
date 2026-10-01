package com.ghost.serialization.contract

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.ghostInternalEncodeAndDrainTo
import com.ghost.serialization.ghostInternalEncodeWithWriter
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.common.json.withPreparedUtf8Json
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import okio.BufferedSink
import okio.BufferedSource
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants as SCN

/**
 * Skeletal [GhostSerializer] — extend this instead of implementing [GhostSerializer] directly for
 * the defaults most implementations don't need to customize.
 *
 * [deserialize] on [GhostJsonFlatReader]/[GhostJsonStringReader] bridges through a temporary
 * streaming/flat reader (allocates, loses monomorphy) — KSP-generated and hot-path serializers
 * override these directly instead.
 */
@OptIn(InternalGhostApi::class)
abstract class AbstractGhostSerializer<T> : GhostSerializer<T> {

    override val isResilient: Boolean = false

    override val isProto: Boolean = false

    override fun deserialize(source: BufferedSource): T {
        val bytes = source.readByteArray()
        return withPreparedUtf8Json(bytes = bytes, limit = bytes.size) { data, offset, length ->
            val reader = GhostJsonFlatReader(rawData = data)
            reader.resetSlice(buffer = data, offset = offset, length = length)
            deserialize(reader)
        }
    }

    override fun deserialize(reader: GhostJsonFlatReader): T {
        val delegatedReader = GhostJsonReader(reader.rawData).also {
            it.position = reader.position
            it.limit = reader.limit
            it.strictMode = reader.strictMode
            it.coerceStringsToNumbers = reader.coerceStringsToNumbers
            it.coerceBooleans = reader.coerceBooleans
            it.maxDepth = reader.maxDepth
            it.maxCollectionSize = reader.maxCollectionSize
        }
        val result = deserialize(delegatedReader)
        reader.position = delegatedReader.position
        reader.nextTokenByte = SCN.RESET_TOKEN_BYTE
        return result
    }

    override fun deserialize(reader: GhostJsonStringReader): T {
        val bytes = reader.ensureUtf8Bytes()
        val flatReader = GhostJsonFlatReader(rawData = bytes).also {
            it.position = reader.charPositionToBytePosition(charPos = reader.position)
            it.limit = bytes.size
            it.strictMode = reader.strictMode
            it.coerceStringsToNumbers = reader.coerceStringsToNumbers
            it.coerceBooleans = reader.coerceBooleans
            it.maxDepth = reader.maxDepth
            it.maxCollectionSize = reader.maxCollectionSize
            it.materializeRawJsonCaptures = true
        }
        val result = deserialize(flatReader)
        reader.position = reader.bytePositionToCharPosition(
            targetBytePos = flatReader.position
        )
        reader.nextTokenByte = SCN.RESET_TOKEN_BYTE
        return result
    }

    override fun serialize(sink: BufferedSink, value: T) {
        ghostInternalEncodeAndDrainTo(sink = sink) { writer ->
            serialize(writer, value)
        }
    }

    override fun serialize(writer: GhostJsonStringWriter, value: T) {
        val bytes = ghostInternalEncodeWithWriter { flatWriter ->
            serialize(flatWriter, value)
        }
        writer.rawValue(bytes)
    }

    override fun warmUp() {}
}
