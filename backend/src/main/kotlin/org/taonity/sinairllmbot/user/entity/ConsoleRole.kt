package org.taonity.sinairllmbot.user.entity

enum class ConsoleRole {
    NONE,
    VIEWER,
    EDITOR,
    ADMIN,
    OWNER;

    fun canView(): Boolean = this != NONE

    fun canEdit(): Boolean = this == EDITOR || this == ADMIN || this == OWNER

    fun isAdmin(): Boolean = this == ADMIN || this == OWNER

    fun isOwner(): Boolean = this == OWNER

    fun rank(): Int = ordinal
}
