@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.retrofit

import com.ghost.serialization.InternalGhostApi

data class YamlDeviceProfile(val deviceId: Int, val label: String)
