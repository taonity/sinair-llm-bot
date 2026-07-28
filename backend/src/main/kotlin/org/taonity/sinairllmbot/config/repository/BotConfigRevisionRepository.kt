package org.taonity.sinairllmbot.config.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.taonity.sinairllmbot.config.entity.BotConfigRevisionEntity

@Repository
interface BotConfigRevisionRepository : JpaRepository<BotConfigRevisionEntity, String> {
    fun findByContentHash(contentHash: String): BotConfigRevisionEntity?
}
