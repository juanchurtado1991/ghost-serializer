package com.ghost.serialization.spring

import com.ghost.serialization.Ghost
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Configuration
import org.springframework.http.codec.ServerCodecConfigurer
import org.springframework.web.reactive.config.WebFluxConfigurer

@AutoConfiguration
@ConditionalOnClass(Ghost::class, WebFluxConfigurer::class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@Configuration(proxyBeanMethods = false)
open class GhostWebFluxAutoConfiguration : WebFluxConfigurer {
    override fun configureHttpMessageCodecs(configurer: ServerCodecConfigurer) {
        with(configurer.customCodecs()) {
            register(GhostReactiveDecoder())
            register(GhostReactiveEncoder())
        }
    }
}
