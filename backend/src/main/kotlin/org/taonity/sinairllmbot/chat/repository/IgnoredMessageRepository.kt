package org.taonity.sinairllmbot.chat.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.stereotype.Repository
import org.taonity.sinairllmbot.chat.entity.IgnoredMessageEntity
import java.time.Instant

@Repository
interface IgnoredMessageRepository : JpaRepository<IgnoredMessageEntity, String> {
    fun existsByDedupKey(dedupKey: String): Boolean

    @Modifying
    fun deleteByCreatedAtBefore(cutoff: Instant): Int
}
