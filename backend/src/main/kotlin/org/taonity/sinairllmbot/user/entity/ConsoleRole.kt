package org.taonity.sinairllmbot.user.entity

/**
 * Level of access a user has to the data console.
 *
 * NONE   - no access (default for newly logged-in users)
 * VIEWER - may read data
 * EDITOR - may read data and mutate it (delete rows, edit summaries)
 * ADMIN  - full access plus the ability to approve/reject access requests and manage users up to EDITOR
 * OWNER  - everything an admin can do, plus promoting/demoting admins
 *
 * The declaration order defines the privilege hierarchy (see [rank]).
 */
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
