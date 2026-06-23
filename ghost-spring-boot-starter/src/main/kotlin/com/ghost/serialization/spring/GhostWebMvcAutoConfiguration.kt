package com.ghost.serialization.spring

import com.ghost.serialization.Ghost
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@AutoConfiguration
@ConditionalOnClass(Ghost::class, WebMvcConfigurer::class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Configuration(proxyBeanMethods = false)
open class GhostWebMvcAutoConfiguration : WebMvcConfigurer {
    override fun extendMessageConverters(converters: MutableList<HttpMessageConverter<*>>) {
        converters.add(0, GhostHttpMessageConverter())
    }

    @org.springframework.context.annotation.Bean
    open fun ghostRequestBodyAdvice(): GhostRequestBodyAdvice {
        return GhostRequestBodyAdvice()
    }
}
