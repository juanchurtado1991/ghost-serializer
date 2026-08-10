package com.ghost.serialization.retrofit

import java.lang.reflect.Type

/** Ghost map unwrap is `Map<String, V>` only — decline other key types. */
internal fun isStringMapKeyType(keyType: Type): Boolean =
    keyType == String::class.java
