package org.taonity.sinairllmbot.console.entity

/**
 * Kind of auditable action performed through the data console.
 *
 * Audit entries intentionally record only WHAT kind of change happened and on WHICH record,
 * never the changed data itself.
 */
enum class AuditAction {
    DELETE_CHAT_MESSAGE,
    DELETE_CHAT_EVENT,
    DELETE_OUTBOUND_MESSAGE,
    EDIT_SUMMARY,
    REQUEST_ACCESS,
    APPROVE_ACCESS,
    REJECT_ACCESS,
    CHANGE_ROLE,
}
