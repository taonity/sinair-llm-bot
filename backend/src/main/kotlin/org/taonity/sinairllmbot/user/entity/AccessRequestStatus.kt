package org.taonity.sinairllmbot.user.entity

/**
 * State of a user's request to access the data console.
 *
 * NONE     - the user has never requested access
 * PENDING  - the user requested access and is awaiting an admin decision
 * APPROVED - an admin granted the requested (or bootstrapped) access
 * REJECTED - an admin denied the request
 */
enum class AccessRequestStatus {
    NONE,
    PENDING,
    APPROVED,
    REJECTED,
}
