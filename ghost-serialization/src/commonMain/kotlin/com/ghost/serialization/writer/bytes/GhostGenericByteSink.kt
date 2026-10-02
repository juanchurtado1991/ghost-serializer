package com.ghost.serialization.writer.bytes

import okio.ByteString

/**
 * Generic byte-sink operations shared by every Ghost writer, with no JSON-specific intrinsics.
 * `GhostYamlWriter` depends on this interface alone — it never needs [GhostByteSink]'s literal/
 * quoting fast paths, so typing its backing store this narrowly keeps it from depending on
 * methods it never calls.
 */
interface GhostGenericByteSink {
    fun flush()
    fun write(bytes: ByteArray)
    fun write(bytes: ByteArray, offset: Int, length: Int)
    fun write(byteString: ByteString)
    fun write2Bytes(firstByte: Int, secondByte: Int)
    fun writeByte(byteAsInt: Int)
    fun writeUtf8(text: String)
    fun writeUtf8(text: String, beginIndex: Int, endIndex: Int)
}
