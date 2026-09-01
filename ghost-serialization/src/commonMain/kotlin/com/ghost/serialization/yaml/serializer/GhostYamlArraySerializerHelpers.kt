package com.ghost.serialization.yaml.serializer

/**
 * Shared array-loop kernels for the primitive YAML array serializers. Each caller stays
 * specialized per primitive type — no `reified`/generic boxing beyond the existing
 * `ArrayList<T>` intermediate on the read side.
 */

internal inline fun writeYamlArrayCore(
    size: Int,
    beginArray: () -> Unit,
    writeElement: (index: Int) -> Unit,
    endArray: () -> Unit,
) {
    beginArray()
    var index = 0
    while (index < size) {
        writeElement(index)
        index++
    }
    endArray()
}

internal inline fun <T> readYamlArrayCore(
    beginArray: () -> Unit,
    hasNextArrayElement: () -> Boolean,
    readElement: () -> T,
    endArray: () -> Unit,
): ArrayList<T> {
    beginArray()
    val list = ArrayList<T>()
    while (hasNextArrayElement()) {
        list.add(readElement())
    }
    endArray()
    return list
}
