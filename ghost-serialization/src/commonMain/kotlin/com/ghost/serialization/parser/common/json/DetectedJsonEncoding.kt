package com.ghost.serialization.parser.common.json

internal class DetectedJsonEncoding(
    val kind: JsonEncodingKind,
    val bomSize: Int
)