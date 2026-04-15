package com.ghost.serialization.core

import okio.ByteString

/**
 * Base interface for all generated Ghost classes.
 * It provides access to the raw data buffer and the field mapping table.
 */
interface GhostObject {
    val __buffer: ByteString
    val __offsets: IntArray
}