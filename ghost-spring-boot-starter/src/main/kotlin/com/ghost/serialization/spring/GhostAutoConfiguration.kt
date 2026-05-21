package com.ghost.serialization.spring

import com.ghost.serialization.Ghost
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.codec.ServerCodecConfigurer
import org.springframework.web.reactive.config.WebFluxConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.http.converter.HttpMessageConverter

/**
 * Auto-configuration for Ghost Serialization in Spring Boot.
 * Automatically registers converters for WebMVC and WebFlux.
 */
@AutoConfiguration
@ConditionalOnClass(Ghost::class)
@EnableConfigurationProperties(GhostProperties::class)
class GhostAutoConfiguration {

    @Bean
    internal fun ghostPayloadConfiguration(properties: GhostProperties): GhostPayloadConfiguration =
        GhostPayloadConfiguration(properties)

    @Configuration
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(WebMvcConfigurer::class)
    class GhostWebMvcConfiguration : WebMvcConfigurer {
        override fun configureMessageConverters(converters: MutableList<HttpMessageConverter<*>>) {
            converters.add(0, GhostHttpMessageConverter())
        }
    }

    @Configuration
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    @ConditionalOnClass(WebFluxConfigurer::class)
    class GhostWebFluxConfiguration : WebFluxConfigurer {
        override fun configureHttpMessageCodecs(configurer: ServerCodecConfigurer) {
            with(configurer.customCodecs()) {
                register(GhostReactiveDecoder())
                register(GhostReactiveEncoder())
            }
        }
    }
}
