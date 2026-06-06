package org.example.fullstackstarter.common.util

import jakarta.validation.Validator

inline fun <reified T : Any> Validator.validateOrThrow(obj: T) {
    val violations = this.validate(obj)
    require(violations.isEmpty()) {
        violations.joinToString("; ") { "${it.propertyPath}: ${it.message}" }
    }
}

inline fun <reified T : Any> Validator.validateOrThrow(
    obj: T,
    errorFactory: (String) -> Throwable
) {
    val violations = this.validate(obj)
    if (violations.isNotEmpty()) {
        val message = violations.joinToString("; ") { "${it.propertyPath}: ${it.message}" }
        throw errorFactory(message)
    }
}

fun Throwable.hasCause(clazz: Class<out Throwable>): Boolean =
    clazz.isInstance(this) || (cause?.hasCause(clazz) ?: false)
