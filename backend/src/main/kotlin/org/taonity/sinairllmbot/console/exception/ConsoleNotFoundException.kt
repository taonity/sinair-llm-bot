package org.taonity.sinairllmbot.console.exception

/** Thrown when a console operation targets a record that does not exist. */
class ConsoleNotFoundException(message: String) : RuntimeException(message)
