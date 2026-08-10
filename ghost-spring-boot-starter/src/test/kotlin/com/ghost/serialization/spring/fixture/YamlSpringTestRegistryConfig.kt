package com.ghost.serialization.spring.fixture

import com.ghost.serialization.Ghost
import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
open class YamlSpringTestRegistryConfig {
    @PostConstruct
    fun registerYamlSerializers() {
        Ghost.addRegistry(YamlSpringTestRegistry)
    }
}
