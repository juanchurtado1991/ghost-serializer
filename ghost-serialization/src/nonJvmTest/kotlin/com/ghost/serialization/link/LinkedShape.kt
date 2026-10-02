package com.ghost.serialization.link

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
sealed class LinkedShape {
    data class Circle(val radius: Int) : LinkedShape()
}
