@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.retrofit

import com.ghost.serialization.InternalGhostApi

data class RetrofitUser(val id: Int, val name: String, val isActive: Boolean)
