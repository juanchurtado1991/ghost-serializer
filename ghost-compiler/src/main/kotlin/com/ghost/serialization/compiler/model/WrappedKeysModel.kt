package com.ghost.serialization.compiler.model

/**
 * Everything `@GhostWrappedKeys` contributes to a property: the wire keys collapsed into it, the
 * omit rules, and the per-key unwrap targets. Grouped (rather than four loose fields on
 * [GhostPropertyModel]) so a property either has all of it or none — and so emitters that only
 * deal with wrapped keys depend on this, not on the whole property model.
 *
 * @property sourceKeys Wire keys collapsed into the property.
 * @property omitIfEmpty When true, absent/null-only captures yield a null wrapper property.
 * @property omitIfAbsent Keys that force a null wrapper when absent or JSON null.
 * @property unwrapFields Where each source key lives inside the wrapped type.
 */
internal data class WrappedKeysModel(
    val sourceKeys: List<String>,
    val omitIfEmpty: Boolean,
    val omitIfAbsent: List<String>,
    val unwrapFields: List<WrappedUnwrapFieldModel>,
)
