package org.taonity.sinairllmbot.console.entity

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
