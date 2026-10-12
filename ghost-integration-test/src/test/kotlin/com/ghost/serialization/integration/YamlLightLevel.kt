package com.ghost.serialization.integration

import com.ghost.serialization.annotations.GhostSerialization
import com.ghost.serialization.annotations.GhostYamlSerialization

@GhostSerialization
@GhostYamlSerialization
enum class YamlLightLevel { DIM, BRIGHT_WHITE }
