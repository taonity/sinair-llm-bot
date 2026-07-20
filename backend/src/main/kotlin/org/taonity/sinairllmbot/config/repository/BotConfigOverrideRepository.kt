package org.taonity.sinairllmbot.config.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.taonity.sinairllmbot.config.entity.BotConfigOverrideEntity

@Repository
interface BotConfigOverrideRepository : JpaRepository<BotConfigOverrideEntity, String>
