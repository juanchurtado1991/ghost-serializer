package com.ghost.serialization

import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader

/** Applies the per-request `strict`/`coerce` flags the HTTP integrations resolve from annotations or config. */
@InternalGhostApi
fun GhostJsonFlatReader.applyOptions(
    isStrict: Boolean,
    isCoerce: Boolean
) {
    strictMode = isStrict
    if (isCoerce) {
        coerceStringsToNumbers = true
        coerceBooleans = true
    }
}

/** YAML counterpart of the [GhostJsonFlatReader] overload. */
@InternalGhostApi
fun GhostYamlFlatReader.applyOptions(
    isStrict: Boolean,
    isCoerce: Boolean
) {
    strictMode = isStrict
    if (isCoerce) {
        coerceStringsToNumbers = true
        coerceBooleans = true
    }
}
