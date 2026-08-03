package org.taonity.sinairllmbot.config.service

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.taonity.sinairllmbot.config.BotSettings
import org.taonity.sinairllmbot.config.ConfigRegistry
import org.taonity.sinairllmbot.config.entity.BotConfigRevisionEntity
import org.taonity.sinairllmbot.config.repository.BotConfigRevisionRepository
import tools.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.util.TreeMap

@Service
class ConfigRevisionService(
    private val settings: BotSettings,
    private val registry: ConfigRegistry,
    private val repository: BotConfigRevisionRepository,
    private val objectMapper: ObjectMapper,
) {
    @Transactional
    fun currentRevisionId(): String {
        val json = currentSnapshotJson()
        val hash = sha256(json)
        repository.findByContentHash(hash)?.let { return it.id!! }
        return try {
            repository.saveAndFlush(
                BotConfigRevisionEntity(contentHash = hash, effectiveConfigJson = json),
            ).id!!
        } catch (_: DataIntegrityViolationException) {
            repository.findByContentHash(hash)?.id
                ?: throw IllegalStateException("Configuration revision was created concurrently but cannot be read")
        }
    }

    fun currentSnapshot(): Map<String, Any?> = safeSnapshot(settings.effective())

    fun revisionSnapshot(revisionId: String): Map<String, Any?>? =
        repository.findById(revisionId).orElse(null)?.let(::parseSnapshot)

    fun revisionJson(revisionId: String): String? =
        repository.findById(revisionId).orElse(null)?.effectiveConfigJson

    private fun currentSnapshotJson(): String =
        objectMapper.writeValueAsString(safeSnapshot(settings.effective()))

    private fun safeSnapshot(config: org.taonity.sinairllmbot.config.EffectiveConfig): Map<String, Any?> {
        val values = TreeMap<String, Any?>()
        registry.fields(config.llm.tiers.keys.toList()).forEach { values[it.key] = it.read(config) }
        return linkedMapOf(
            "fields" to values,
            "tiers" to config.llm.tiers.keys.sorted(),
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseSnapshot(entity: BotConfigRevisionEntity): Map<String, Any?> =
        objectMapper.readValue(entity.effectiveConfigJson, Map::class.java) as Map<String, Any?>

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
