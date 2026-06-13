package org.taonity.sinairllmbot.web.config

import org.taonity.sinairllmbot.observability.logging.ControllerLoggingInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(
    private val controllerLoggingInterceptor: ControllerLoggingInterceptor,
    private val optionalInterceptors: List<HandlerInterceptor>
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(controllerLoggingInterceptor)
        optionalInterceptors
            .filter { it !== controllerLoggingInterceptor }
            .forEach { registry.addInterceptor(it) }
    }
}
