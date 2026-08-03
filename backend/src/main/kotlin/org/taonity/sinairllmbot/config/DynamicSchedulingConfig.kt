package org.taonity.sinairllmbot.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.Trigger
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.config.ScheduledTaskRegistrar
import org.springframework.scheduling.support.CronTrigger
import org.taonity.sinairllmbot.chat.service.RetentionCleanupService
import org.taonity.sinairllmbot.console.service.AuditLogCleanupService

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
