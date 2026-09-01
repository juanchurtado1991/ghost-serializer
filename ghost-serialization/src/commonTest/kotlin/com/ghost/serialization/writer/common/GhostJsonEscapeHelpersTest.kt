package com.ghost.serialization.writer.common

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies [GhostJsonEscapeHelpers.isPlainAsciiSafe] against the two independent checks it
 * replaced (bitmask in [GhostJsonWriter]/here, range-check in [GhostJsonFlatWriter]) — both must
 * agree exactly: unsafe iff the code is a control char (<0x20), `"` (34), `\` (92), or >=128.
 */
class GhostJsonEscapeHelpersTest {

    private fun referenceIsSafe(code: Int): Boolean {
        if (code < 0x20 || code >= 128) return false
        if (code == '"'.code || code == '\\'.code) return false
        return true
    }

    @Test
    fun isPlainAsciiSafe_matchesReferenceForAllCodeUnits() {
        for (code in 0..200) {
            assertEquals(
                referenceIsSafe(code),
                GhostJsonEscapeHelpers.isPlainAsciiSafe(code),
                "mismatch for code $code",
            )
        }
    }
}
