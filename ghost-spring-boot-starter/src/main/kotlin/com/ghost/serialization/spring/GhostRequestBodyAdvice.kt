package com.ghost.serialization.spring

import com.ghost.serialization.annotations.GhostCoerce
import com.ghost.serialization.annotations.GhostStrict
import org.springframework.core.MethodParameter
import org.springframework.http.HttpInputMessage
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdvice
import java.lang.reflect.Type

@ControllerAdvice
class GhostRequestBodyAdvice : RequestBodyAdvice {

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

    override fun beforeBodyRead(
        inputMessage: HttpInputMessage,
        parameter: MethodParameter,
        targetType: Type,
        converterType: Class<out HttpMessageConverter<*>>
    ): HttpInputMessage {
        GhostSpringConfig.strict.set(parameter.isAnnotatedAnywhere(annotation = GhostStrict::class.java))
        GhostSpringConfig.coerce.set(parameter.isAnnotatedAnywhere(annotation = GhostCoerce::class.java))

        return inputMessage
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

    override fun supports(
        methodParameter: MethodParameter,
        targetType: Type,
        converterType: Class<out HttpMessageConverter<*>>
    ): Boolean {
        return GhostHttpMessageConverter::class.java.isAssignableFrom(converterType) ||
                GhostYamlHttpMessageConverter::class.java.isAssignableFrom(converterType)
    }

    /** True if [annotation] is on the parameter itself, its method, or the declaring controller class. */
    private fun MethodParameter.isAnnotatedAnywhere(annotation: Class<out Annotation>): Boolean =
        hasParameterAnnotation(annotation) ||
                hasMethodAnnotation(annotation) ||
                containingClass.isAnnotationPresent(annotation)
}
