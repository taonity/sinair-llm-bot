package org.taonity.sinairllmbot.bot.repository

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.taonity.sinairllmbot.bot.entity.OutboundMessageEntity
import org.taonity.sinairllmbot.bot.entity.OutboundStatus

@Repository
interface OutboundMessageRepository : JpaRepository<OutboundMessageEntity, String> {
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
}
