package org.example.fullstackstarter.observability.logging

enum class LogLevel {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR;

    fun demote(): LogLevel = entries.getOrElse(ordinal - 1) { TRACE }
}
