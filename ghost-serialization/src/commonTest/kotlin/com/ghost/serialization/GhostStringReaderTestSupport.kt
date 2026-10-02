@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.parser.strings.GhostJsonStringReader

internal fun stringReaderOf(
    json: String
): GhostJsonStringReader = GhostJsonStringReader(rawData = json)
