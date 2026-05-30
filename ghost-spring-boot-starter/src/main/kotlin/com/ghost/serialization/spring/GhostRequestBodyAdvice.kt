package com.ghost.serialization.spring

import com.ghost.serialization.annotations.GhostCoerce
import com.ghost.serialization.annotations.GhostStrict
import org.springframework.core.MethodParameter
import org.springframework.http.HttpInputMessage
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdvice
import java.io.IOException
import java.lang.reflect.Type

@ControllerAdvice
class GhostRequestBodyAdvice : RequestBodyAdvice {

    override fun supports(
        methodParameter: MethodParameter,
        targetType: Type,
        converterType: Class<out HttpMessageConverter<*>>
    ): Boolean {
        return GhostHttpMessageConverter::class.java.isAssignableFrom(converterType)
    }

    override fun beforeBodyRead(
        inputMessage: HttpInputMessage,
        parameter: MethodParameter,
        targetType: Type,
        converterType: Class<out HttpMessageConverter<*>>
    ): HttpInputMessage {
        val hasStrict = parameter.hasParameterAnnotation(GhostStrict::class.java) ||
                parameter.hasMethodAnnotation(GhostStrict::class.java) ||
                parameter.containingClass.isAnnotationPresent(GhostStrict::class.java)

        val hasCoerce = parameter.hasParameterAnnotation(GhostCoerce::class.java) ||
                parameter.hasMethodAnnotation(GhostCoerce::class.java) ||
                parameter.containingClass.isAnnotationPresent(GhostCoerce::class.java)

        GhostSpringConfig.strict.set(hasStrict)
        GhostSpringConfig.coerce.set(hasCoerce)

        return inputMessage
    }

    override fun afterBodyRead(
        body: Any,
        inputMessage: HttpInputMessage,
        parameter: MethodParameter,
        targetType: Type,
        converterType: Class<out HttpMessageConverter<*>>
    ): Any {
        GhostSpringConfig.strict.remove()
        GhostSpringConfig.coerce.remove()
        return body
    }

    override fun handleEmptyBody(
        body: Any?,
        inputMessage: HttpInputMessage,
        parameter: MethodParameter,
        targetType: Type,
        converterType: Class<out HttpMessageConverter<*>>
    ): Any? {
        GhostSpringConfig.strict.remove()
        GhostSpringConfig.coerce.remove()
        return body
    }
}
