package org.taonity.sinairllmbot.console.service

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.taonity.sinairllmbot.bot.entity.OutboundMessageEntity
import org.taonity.sinairllmbot.bot.entity.RoomSummaryEntity
import org.taonity.sinairllmbot.bot.repository.OutboundMessageRepository
import org.taonity.sinairllmbot.bot.repository.RoomSummaryRepository
import org.taonity.sinairllmbot.chat.entity.ChatEventEntity
import org.taonity.sinairllmbot.chat.entity.ChatMessageEntity
import org.taonity.sinairllmbot.chat.repository.ChatEventRepository
import org.taonity.sinairllmbot.chat.repository.ChatMessageRepository
import org.taonity.sinairllmbot.console.dto.AuditLogDto
import org.taonity.sinairllmbot.console.dto.ChatEventDto
import org.taonity.sinairllmbot.console.dto.ChatMessageDto
import org.taonity.sinairllmbot.console.dto.OutboundMessageDto
import org.taonity.sinairllmbot.console.dto.PageResponse
import org.taonity.sinairllmbot.console.dto.RoomSummaryDto
import org.taonity.sinairllmbot.console.entity.AuditAction
import org.taonity.sinairllmbot.console.exception.ConsoleNotFoundException
import org.taonity.sinairllmbot.console.repository.AuditLogRepository
import org.taonity.sinairllmbot.security.principal.GoogleUserPrincipal

/**
 * Read and mutate console data. Reads require VIEWER access, mutations require EDITOR access.
 * Every mutation is recorded in the audit log (without the changed data values).
 */
@Service
class ConsoleDataService(
    private val chatMessageRepository: ChatMessageRepository,
    private val chatEventRepository: ChatEventRepository,
    private val outboundMessageRepository: OutboundMessageRepository,
    private val roomSummaryRepository: RoomSummaryRepository,
    private val auditLogRepository: AuditLogRepository,
    private val accessGuard: AccessGuard,
    private val auditService: AuditService,
) {
    companion object {
        private const val MAX_PAGE_SIZE = 100
    }

    private fun pageable(page: Int, size: Int, sortProperty: String) = PageRequest.of(
        page.coerceAtLeast(0),
        size.coerceIn(1, MAX_PAGE_SIZE),
        Sort.by(Sort.Direction.DESC, sortProperty).and(Sort.by(Sort.Direction.DESC, "id")),
    )

    private fun pageIndexOf(rowsBefore: Long, size: Int): Int {
        val safeSize = size.coerceIn(1, MAX_PAGE_SIZE)
        return (rowsBefore / safeSize).toInt()
    }

    fun listChatMessages(
        principal: GoogleUserPrincipal,
        room: String?,
        q: String?,
        field: String?,
        page: Int,
        size: Int,
    ): PageResponse<ChatMessageDto> {
        accessGuard.requireView(principal)
        val pageable = pageable(page, size, "sentAt")
        val result: Page<ChatMessageEntity> = when {
            !q.isNullOrBlank() -> chatMessageRepository.search(q.trim(), field.orAllField(), pageable)
            room.isNullOrBlank() -> chatMessageRepository.findAll(pageable)
            else -> chatMessageRepository.findByRoomTarget(room, pageable)
        }
        return PageResponse.of(result, ChatMessageDto::from)
    }

    fun locateChatMessagePage(principal: GoogleUserPrincipal, id: String, size: Int): Int {
        accessGuard.requireView(principal)
        val entity = chatMessageRepository.findById(id)
            .orElseThrow { ConsoleNotFoundException("Chat message not found") }
        val before = chatMessageRepository.countOrderedBefore(entity.sentAt, entity.id!!)
        return pageIndexOf(before, size)
    }

    @Transactional
    fun deleteChatMessage(principal: GoogleUserPrincipal, id: String) {
        val actor = accessGuard.requireEdit(principal)
        if (!chatMessageRepository.existsById(id)) {
            throw ConsoleNotFoundException("Chat message not found")
        }
        chatMessageRepository.deleteById(id)
        auditService.record(AuditAction.DELETE_CHAT_MESSAGE, "chat_message", id, actor)
    }

    fun listChatEvents(
        principal: GoogleUserPrincipal,
        room: String?,
        q: String?,
        field: String?,
        page: Int,
        size: Int,
    ): PageResponse<ChatEventDto> {
        accessGuard.requireView(principal)
        val pageable = pageable(page, size, "eventTime")
        val result: Page<ChatEventEntity> = when {
            !q.isNullOrBlank() -> chatEventRepository.search(q.trim(), field.orAllField(), pageable)
            room.isNullOrBlank() -> chatEventRepository.findAll(pageable)
            else -> chatEventRepository.findByRoomTarget(room, pageable)
        }
        return PageResponse.of(result, ChatEventDto::from)
    }

    fun locateChatEventPage(principal: GoogleUserPrincipal, id: String, size: Int): Int {
        accessGuard.requireView(principal)
        val entity = chatEventRepository.findById(id)
            .orElseThrow { ConsoleNotFoundException("Chat event not found") }
        val before = chatEventRepository.countOrderedBefore(entity.eventTime, entity.id!!)
        return pageIndexOf(before, size)
    }

    @Transactional
    fun deleteChatEvent(principal: GoogleUserPrincipal, id: String) {
        val actor = accessGuard.requireEdit(principal)
        if (!chatEventRepository.existsById(id)) {
            throw ConsoleNotFoundException("Chat event not found")
        }
        chatEventRepository.deleteById(id)
        auditService.record(AuditAction.DELETE_CHAT_EVENT, "chat_event", id, actor)
    }

    fun listOutboundMessages(
        principal: GoogleUserPrincipal,
        room: String?,
        q: String?,
        field: String?,
        page: Int,
        size: Int,
    ): PageResponse<OutboundMessageDto> {
        accessGuard.requireView(principal)
        val pageable = pageable(page, size, "createdAt")
        val result: Page<OutboundMessageEntity> = when {
            !q.isNullOrBlank() -> outboundMessageRepository.search(q.trim(), field.orAllField(), pageable)
            room.isNullOrBlank() -> outboundMessageRepository.findAll(pageable)
            else -> outboundMessageRepository.findByRoomTarget(room, pageable)
        }
        return PageResponse.of(result, OutboundMessageDto::from)
    }

    fun locateOutboundMessagePage(principal: GoogleUserPrincipal, id: String, size: Int): Int {
        accessGuard.requireView(principal)
        val entity = outboundMessageRepository.findById(id)
            .orElseThrow { ConsoleNotFoundException("Outbound message not found") }
        val before = outboundMessageRepository.countOrderedBefore(entity.createdAt, entity.id!!)
        return pageIndexOf(before, size)
    }

    @Transactional
    fun deleteOutboundMessage(principal: GoogleUserPrincipal, id: String) {
        val actor = accessGuard.requireEdit(principal)
        if (!outboundMessageRepository.existsById(id)) {
            throw ConsoleNotFoundException("Outbound message not found")
        }
        outboundMessageRepository.deleteById(id)
        auditService.record(AuditAction.DELETE_OUTBOUND_MESSAGE, "outbound_message", id, actor)
    }

    fun listSummaries(principal: GoogleUserPrincipal): List<RoomSummaryDto> {
        accessGuard.requireView(principal)
        return roomSummaryRepository.findAll()
            .sortedByDescending { it.updatedAt }
            .map(RoomSummaryDto::from)
    }

    @Transactional
    fun updateSummary(principal: GoogleUserPrincipal, id: String, summary: String): RoomSummaryDto {
        val actor = accessGuard.requireEdit(principal)
        val entity: RoomSummaryEntity = roomSummaryRepository.findById(id)
            .orElseThrow { ConsoleNotFoundException("Summary not found") }
        entity.summary = summary
        val saved = roomSummaryRepository.save(entity)
        auditService.record(AuditAction.EDIT_SUMMARY, "room_summary", id, actor)
        return RoomSummaryDto.from(saved)
    }

    fun listAuditLogs(principal: GoogleUserPrincipal, q: String?, field: String?, page: Int, size: Int): PageResponse<AuditLogDto> {
        accessGuard.requireAdmin(principal)
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, MAX_PAGE_SIZE))
        val result = if (q.isNullOrBlank()) {
            auditLogRepository.findAllByOrderByOccurredAtDesc(pageable)
        } else {
            auditLogRepository.search(q.trim(), field.orAllField(), pageable)
        }
        return PageResponse.of(result, AuditLogDto::from)
    }

    private fun String?.orAllField(): String = this?.takeIf { it.isNotBlank() } ?: "all"
}
