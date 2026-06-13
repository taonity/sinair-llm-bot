package org.taonity.sinairllmbot

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class SinairLlmBotApplication

fun main(args: Array<String>) {
    runApplication<SinairLlmBotApplication>(*args)
}
