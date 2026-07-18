package org.taonity.sinairllmbot.console.service

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.taonity.sinairllmbot.bot.entity.OutboundMessageEntity
import org.taonity.sinairllmbot.bot.entity.PipelineRunEntity
import org.taonity.sinairllmbot.bot.entity.RoomSummaryEntity
import org.taonity.sinairllmbot.bot.repository.OutboundMessageRepository
import org.taonity.sinairllmbot.bot.repository.PipelineRunRepository
import org.taonity.sinairllmbot.bot.repository.RoomSummaryRepository
import org.taonity.sinairllmbot.chat.entity.ChatEventEntity
import org.taonity.sinairllmbot.chat.entity.ChatMessageEntity
import org.taonity.sinairllmbot.chat.entity.IgnoredMessageEntity
import org.taonity.sinairllmbot.chat.repository.ChatEventRepository
import org.taonity.sinairllmbot.chat.repository.ChatMessageRepository
import org.taonity.sinairllmbot.chat.repository.IgnoredMessageRepository
import org.taonity.sinairllmbot.console.dto.AuditLogDto
import org.taonity.sinairllmbot.console.dto.ChatEventDto
import org.taonity.sinairllmbot.console.dto.ChatMessageDto
import org.taonity.sinairllmbot.console.dto.LlmCallUsageDto
import org.taonity.sinairllmbot.console.dto.OutboundMessageDto
import org.taonity.sinairllmbot.console.dto.PageResponse
import org.taonity.sinairllmbot.console.dto.PipelineRunDto
import org.taonity.sinairllmbot.console.dto.PipelineStageDto
import org.taonity.sinairllmbot.console.dto.RoomSummaryDto
import org.taonity.sinairllmbot.console.entity.AuditAction
import org.taonity.sinairllmbot.console.exception.ConsoleNotFoundException
import org.taonity.sinairllmbot.console.repository.AuditLogRepository
import org.taonity.sinairllmbot.security.principal.GoogleUserPrincipal
import tools.jackson.databind.ObjectMapper

/**
 * Read and mutate console data. Reads require VIEWER access, mutations require EDITOR access.
 * Every mutation is recorded in the audit log (without the changed data values).
 */
@Service
class ConsoleDataService(
    private val chatMessageRepository: ChatMessageRepository,
    private val chatEventRepository: ChatEventRepository,
    private val ignoredMessageRepository: IgnoredMessageRepository,
    private val outboundMessageRepository: OutboundMessageRepository,
    private val roomSummaryRepository: RoomSummaryRepository,
    private val pipelineRunRepository: PipelineRunRepository,
    private val auditLogRepository: AuditLogRepository,
    private val accessGuard: AccessGuard,
    private val auditService: AuditService,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private const val MAX_PAGE_SIZE = 100
    }

    private fun pageable(page: Int, size: Int, sortProperty: String, direction: String) = PageRequest.of(
        page.coerceAtLeast(0),
        size.coerceIn(1, MAX_PAGE_SIZE),
        sortDirection(direction).let { dir -> Sort.by(dir, sortProperty).and(Sort.by(dir, "id")) },
    )

    private fun sortDirection(direction: String?): Sort.Direction =
        if (direction.equals("asc", ignoreCase = true)) Sort.Direction.ASC else Sort.Direction.DESC

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
        direction: String,
    ): PageResponse<ChatMessageDto> {
        accessGuard.requireView(principal)
        val pageable = pageable(page, size, "sentAt", direction)
        val result: Page<ChatMessageEntity> = when {
            !q.isNullOrBlank() -> chatMessageRepository.search(q.trim(), field.orAllField(), pageable)
            room.isNullOrBlank() -> chatMessageRepository.findAll(pageable)
            else -> chatMessageRepository.findByRoomTarget(room, pageable)
        }
        return PageResponse.of(result, ChatMessageDto::from)
    }

    fun locateChatMessagePage(principal: GoogleUserPrincipal, id: String, size: Int, direction: String): Int {
        accessGuard.requireView(principal)
        val entity = chatMessageRepository.findById(id)
            .orElseThrow { ConsoleNotFoundException("Chat message not found") }
        val before = if (sortDirection(direction) == Sort.Direction.ASC) {
            chatMessageRepository.countOrderedBeforeAsc(entity.sentAt, entity.id!!)
        } else {
            chatMessageRepository.countOrderedBefore(entity.sentAt, entity.id!!)
        }
        return pageIndexOf(before, size)
    }

    @Transactional
    fun deleteChatMessage(principal: GoogleUserPrincipal, id: String) {
        val actor = accessGuard.requireEdit(principal)
        val entity = chatMessageRepository.findById(id)
            .orElseThrow { ConsoleNotFoundException("Chat message not found") }
        chatMessageRepository.deleteById(id)
        // The pipeline trace(s) this message triggered are meaningless once the message is gone, so
        // drop them too — keeps the Pipelines tab in sync with the Messages tab.
        pipelineRunRepository.deleteByTriggerMessageId(id)
        // Tombstone the dedup key so the same message replayed in the history burst after a
        // reconnect/relogin is recognised and dropped instead of being re-ingested.
        if (!ignoredMessageRepository.existsByDedupKey(entity.dedupKey)) {
            ignoredMessageRepository.save(
                IgnoredMessageEntity(dedupKey = entity.dedupKey, roomTarget = entity.roomTarget),
            )
        }
        auditService.record(AuditAction.DELETE_CHAT_MESSAGE, "chat_message", id, actor)
    }

    fun listChatEvents(
        principal: GoogleUserPrincipal,
        room: String?,
        q: String?,
        field: String?,
        page: Int,
        size: Int,
        direction: String,
    ): PageResponse<ChatEventDto> {
        accessGuard.requireView(principal)
        val pageable = pageable(page, size, "eventTime", direction)
        val result: Page<ChatEventEntity> = when {
            !q.isNullOrBlank() -> chatEventRepository.search(q.trim(), field.orAllField(), pageable)
            room.isNullOrBlank() -> chatEventRepository.findAll(pageable)
            else -> chatEventRepository.findByRoomTarget(room, pageable)
        }
        return PageResponse.of(result, ChatEventDto::from)
    }

    fun locateChatEventPage(principal: GoogleUserPrincipal, id: String, size: Int, direction: String): Int {
        accessGuard.requireView(principal)
        val entity = chatEventRepository.findById(id)
            .orElseThrow { ConsoleNotFoundException("Chat event not found") }
        val before = if (sortDirection(direction) == Sort.Direction.ASC) {
            chatEventRepository.countOrderedBeforeAsc(entity.eventTime, entity.id!!)
        } else {
            chatEventRepository.countOrderedBefore(entity.eventTime, entity.id!!)
        }
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
        direction: String,
    ): PageResponse<OutboundMessageDto> {
        accessGuard.requireView(principal)
        val pageable = pageable(page, size, "createdAt", direction)
        val result: Page<OutboundMessageEntity> = when {
            !q.isNullOrBlank() -> outboundMessageRepository.search(q.trim(), field.orAllField(), pageable)
            room.isNullOrBlank() -> outboundMessageRepository.findAll(pageable)
            else -> outboundMessageRepository.findByRoomTarget(room, pageable)
        }
        return PageResponse.of(result, OutboundMessageDto::from)
    }

    fun locateOutboundMessagePage(principal: GoogleUserPrincipal, id: String, size: Int, direction: String): Int {
        accessGuard.requireView(principal)
        val entity = outboundMessageRepository.findById(id)
            .orElseThrow { ConsoleNotFoundException("Outbound message not found") }
        val before = if (sortDirection(direction) == Sort.Direction.ASC) {
            outboundMessageRepository.countOrderedBeforeAsc(entity.createdAt, entity.id!!)
        } else {
            outboundMessageRepository.countOrderedBefore(entity.createdAt, entity.id!!)
        }
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

    fun listPipelineRuns(
        principal: GoogleUserPrincipal,
        room: String?,
        q: String?,
        field: String?,
        page: Int,
        size: Int,
        direction: String,
    ): PageResponse<PipelineRunDto> {
        accessGuard.requireView(principal)
        val pageable = pageable(page, size, "createdAt", direction)
        val result: Page<PipelineRunEntity> = when {
            !q.isNullOrBlank() -> pipelineRunRepository.search(q.trim(), field.orAllField(), pageable)
            room.isNullOrBlank() -> pipelineRunRepository.findAll(pageable)
            else -> pipelineRunRepository.findByRoomTarget(room, pageable)
        }
        return PageResponse.of(result) {
            PipelineRunDto.from(it, parseStages(it.stagesJson), parseLlmUsage(it.llmUsageJson))
        }
    }

    fun locatePipelineRunPage(principal: GoogleUserPrincipal, id: String, size: Int, direction: String): Int {
        accessGuard.requireView(principal)
        val entity = pipelineRunRepository.findById(id)
            .orElseThrow { ConsoleNotFoundException("Pipeline run not found") }
        val before = if (sortDirection(direction) == Sort.Direction.ASC) {
            pipelineRunRepository.countOrderedBeforeAsc(entity.createdAt, entity.id!!)
        } else {
            pipelineRunRepository.countOrderedBefore(entity.createdAt, entity.id!!)
        }
        return pageIndexOf(before, size)
    }

    @Transactional
    fun deletePipelineRun(principal: GoogleUserPrincipal, id: String) {
        val actor = accessGuard.requireEdit(principal)
        if (!pipelineRunRepository.existsById(id)) {
            throw ConsoleNotFoundException("Pipeline run not found")
        }
        pipelineRunRepository.deleteById(id)
        auditService.record(AuditAction.DELETE_PIPELINE_RUN, "pipeline_run", id, actor)
    }

    private fun parseStages(json: String): List<PipelineStageDto> = runCatching {
        val type = objectMapper.typeFactory.constructCollectionType(List::class.java, PipelineStageDto::class.java)
        val stages: List<PipelineStageDto> = objectMapper.readValue(json, type)
        stages
    }.getOrElse { emptyList() }

    private fun parseLlmUsage(json: String): List<LlmCallUsageDto> = runCatching {
        val type = objectMapper.typeFactory.constructCollectionType(List::class.java, LlmCallUsageDto::class.java)
        val usage: List<LlmCallUsageDto> = objectMapper.readValue(json, type)
        usage
    }.getOrElse { emptyList() }

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
