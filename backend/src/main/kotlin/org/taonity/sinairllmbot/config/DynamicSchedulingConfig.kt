package org.taonity.sinairllmbot.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.Trigger
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.config.ScheduledTaskRegistrar
import org.springframework.scheduling.support.CronTrigger
import org.taonity.sinairllmbot.chat.service.RetentionCleanupService
import org.taonity.sinairllmbot.console.service.AuditLogCleanupService

/**
 * Schedules the retention-cleanup jobs from cron expressions read out of [BotSettings] on every
 * cycle. Because the trigger re-reads the live config each time it computes the next fire time, a
 * cron edited through the console takes effect from the next scheduling cycle without a restart
 * (unlike a static `@Scheduled(cron = ...)`, which is bound once at startup).
 */
@Configuration
class DynamicSchedulingConfig(
    private val settings: BotSettings,
    private val retentionCleanupService: RetentionCleanupService,
    private val auditLogCleanupService: AuditLogCleanupService,
) : SchedulingConfigurer {
    override fun configureTasks(taskRegistrar: ScheduledTaskRegistrar) {
        taskRegistrar.addTriggerTask(
            { retentionCleanupService.cleanupOldRecords() },
            Trigger { ctx -> CronTrigger(settings.retention().chat.cron).nextExecution(ctx) },
        )
        taskRegistrar.addTriggerTask(
            { auditLogCleanupService.cleanupOldAuditLogs() },
            Trigger { ctx -> CronTrigger(settings.retention().audit.cron).nextExecution(ctx) },
        )
    }
}
