package com.ghost.serialization.link

import com.ghost.serialization.contract.AbstractGhostSerializer
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.writer.bytes.GhostJsonWriter

/** Registered explicitly through `Ghost.addRegistry` to prove a registry wins over the compiler-plugin link. */
object LinkedDeviceOverrideSerializer : AbstractGhostSerializer<LinkedDevice>() {
    const val DECODED_ID = 42

    override val typeName: String = "LinkedDevice"

    override fun deserialize(
        reader: GhostJsonReader
    ): LinkedDevice = LinkedDevice(id = DECODED_ID)

    override fun deserialize(
        reader: GhostJsonStringReader
    ): LinkedDevice = LinkedDevice(id = DECODED_ID)

    override fun serialize(
        writer: GhostJsonWriter,
        value: LinkedDevice
    ) = Unit
}
