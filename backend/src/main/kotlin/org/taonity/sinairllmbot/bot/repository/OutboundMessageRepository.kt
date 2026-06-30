package org.taonity.sinairllmbot.bot.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.stereotype.Repository
import org.taonity.sinairllmbot.bot.entity.OutboundMessageEntity
import org.taonity.sinairllmbot.bot.entity.OutboundStatus
import java.time.Instant

@Repository
interface OutboundMessageRepository : JpaRepository<OutboundMessageEntity, String> {
    fun findByRoomTarget(roomTarget: String, pageable: Pageable): Page<OutboundMessageEntity>

    fun findByRoomTargetAndStatusOrderByCreatedAtAsc(
        roomTarget: String,
        status: OutboundStatus,
        pageable: Pageable,
    ): List<OutboundMessageEntity>

    fun findByStatusOrderByCreatedAtAsc(
        status: OutboundStatus,
        pageable: Pageable,
    ): List<OutboundMessageEntity>

    fun findByIdInAndStatus(ids: Collection<String>, status: OutboundStatus): List<OutboundMessageEntity>

    @Modifying
    fun deleteByCreatedAtBefore(cutoff: Instant): Int
}
