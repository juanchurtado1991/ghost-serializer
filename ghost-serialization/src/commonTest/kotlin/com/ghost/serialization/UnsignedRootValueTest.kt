package com.ghost.serialization

import com.ghost.serialization.exception.GhostJsonException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** `UByte`/`UShort`/`UInt`/`ULong` decode and encode as top-level values, with range checks. */
class UnsignedRootValueTest {

    @Test
    fun unsignedRootValuesRoundTrip() {
        assertEquals(
            expected = "18446744073709551615",
            actual = Ghost.encodeToString(value = ULong.MAX_VALUE)
        )
        assertEquals(
            expected = ULong.MAX_VALUE,
            actual = Ghost.deserialize<ULong>(json = "18446744073709551615")
        )
        assertEquals(
            expected = UInt.MAX_VALUE,
            actual = Ghost.deserialize<UInt>(bytes = "4294967295".encodeToByteArray())
        )
        assertEquals(
            expected = UShort.MAX_VALUE,
            actual = Ghost.deserialize<UShort>(json = Ghost.encodeToString(value = UShort.MAX_VALUE))
        )
        assertEquals(
            expected = UByte.MAX_VALUE,
            actual = Ghost.deserialize<UByte>(json = "255")
        )
    }

    @Test
    fun outOfRangeUnsignedRootValuesAreRejected() {
        assertFailsWith<GhostJsonException> { Ghost.deserialize<UByte>(json = "256") }
        assertFailsWith<GhostJsonException> { Ghost.deserialize<UShort>(json = "-1") }
        assertFailsWith<GhostJsonException> { Ghost.deserialize<UInt>(json = "4294967296") }
        assertFailsWith<GhostJsonException> { Ghost.deserialize<ULong>(json = "18446744073709551616") }
    }
}
