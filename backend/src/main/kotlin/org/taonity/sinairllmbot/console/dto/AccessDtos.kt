package org.taonity.sinairllmbot.console.dto

import org.taonity.sinairllmbot.user.entity.AccessRequestStatus
import org.taonity.sinairllmbot.user.entity.ConsoleRole

/** The current user's console access state, used by the frontend to decide what to render. */
data class AccessInfoResponse(
    val email: String,
    val displayName: String,
    val role: ConsoleRole,
    val accessStatus: AccessRequestStatus,
    val requestedRole: ConsoleRole?,
    val canView: Boolean,
    val canEdit: Boolean,
    val isAdmin: Boolean,
    val isOwner: Boolean,
)

/** Body for requesting access; the user asks for either VIEWER or EDITOR. */
data class AccessRequestBody(
    val requestedRole: ConsoleRole,
)

/** Body for an admin approving a request with a granted role. */
data class ApproveAccessBody(
    val role: ConsoleRole,
)

/** A pending access request shown to admins. */
data class PendingRequestDto(
    val googleId: String,
    val email: String,
    val displayName: String,
    val requestedRole: ConsoleRole?,
)

/** A user row shown in the admin user-management table. */
data class UserSummaryDto(
    val googleId: String,
    val email: String,
    val displayName: String,
    val role: ConsoleRole,
    val accessStatus: AccessRequestStatus,
    val requestedRole: ConsoleRole?,
)

/** Body for an admin/owner changing another user's role. */
data class ChangeRoleBody(
    val role: ConsoleRole,
)
