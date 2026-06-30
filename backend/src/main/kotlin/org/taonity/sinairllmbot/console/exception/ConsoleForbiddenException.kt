package org.taonity.sinairllmbot.console.exception

/** Thrown when an authenticated user lacks the console role required for an action. */
class ConsoleForbiddenException(message: String) : RuntimeException(message)
