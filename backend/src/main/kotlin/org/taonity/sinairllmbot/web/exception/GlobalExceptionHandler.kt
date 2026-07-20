package org.taonity.sinairllmbot.web.exception

import tools.jackson.databind.exc.MismatchedInputException
import tools.jackson.module.kotlin.KotlinInvalidNullException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.resource.NoResourceFoundException
import org.taonity.sinairllmbot.console.exception.ConsoleForbiddenException
import org.taonity.sinairllmbot.console.exception.ConsoleNotFoundException
import org.taonity.sinairllmbot.config.ConfigValidationException

@RestControllerAdvice
class GlobalExceptionHandler {
    companion object {
        private val LOGGER = KotlinLogging.logger {}
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ServerErrorResponse> {
        LOGGER.error(e) { "Unhandled exception" }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ServerErrorResponse(ServerErrorCode.UNKNOWN))
    }

    @ExceptionHandler(ConsoleForbiddenException::class)
    fun handleConsoleForbidden(e: ConsoleForbiddenException): ResponseEntity<ClientErrorResponse> {
        LOGGER.debug(e) { "Console access forbidden" }
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ClientErrorResponse(ClientErrorCode.FORBIDDEN, e.message ?: "Forbidden"))
    }

    @ExceptionHandler(ConsoleNotFoundException::class)
    fun handleConsoleNotFound(e: ConsoleNotFoundException): ResponseEntity<ClientErrorResponse> {
        LOGGER.debug(e) { "Console resource not found" }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ClientErrorResponse(ClientErrorCode.NOT_FOUND, e.message ?: "Not found"))
    }

    @ExceptionHandler(ConfigValidationException::class)
    fun handleConfigValidation(e: ConfigValidationException): ResponseEntity<ClientErrorResponse> {
        LOGGER.debug(e) { "Config validation failed" }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ClientErrorResponse(ClientErrorCode.VALIDATION_ERROR, e.message ?: "Invalid config value"))
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNotFound(e: NoResourceFoundException) {
        LOGGER.debug(e) {}
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMissingFieldExceptions(e: HttpMessageNotReadableException): ResponseEntity<ClientErrorResponse> {
        val cause = e.cause
        return when (cause) {
            is KotlinInvalidNullException, is MismatchedInputException -> {
                ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ClientErrorResponse(ClientErrorCode.MISSING_FIELD, cause.message ?: ""))
            }
            else -> {
                ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ClientErrorResponse(ClientErrorCode.MISSING_FIELD, e.message ?: ""))
            }
        }
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationExceptions(e: MethodArgumentNotValidException): ResponseEntity<ClientErrorResponse> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ClientErrorResponse(ClientErrorCode.VALIDATION_ERROR, e.message))
    }
}
