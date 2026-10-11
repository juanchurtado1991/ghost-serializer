package com.ghost.serialization.retrofit

/** Non-final on purpose: Kotlin emits `List<? extends RetrofitAnimal>` for it in parameter positions. */
open class RetrofitAnimal(val name: String) {
    override fun equals(other: Any?): Boolean = other is RetrofitAnimal && other.name == name

    override fun hashCode(): Int = name.hashCode()
}
