package org.taonity.sinairllmbot.observability.logging

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class EndpointLogLevel(val value: LogLevel = LogLevel.INFO)
