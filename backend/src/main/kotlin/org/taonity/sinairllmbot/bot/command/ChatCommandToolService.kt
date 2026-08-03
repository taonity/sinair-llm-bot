package org.taonity.sinairllmbot.bot.command

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.taonity.sinairllmbot.bot.client.Tool
import org.taonity.sinairllmbot.bot.entity.OutboundMessageEntity
import org.taonity.sinairllmbot.bot.entity.OutboundStatus
import org.taonity.sinairllmbot.bot.pipeline.PipelineContextTracker
import org.taonity.sinairllmbot.bot.repository.OutboundMessageRepository
import org.taonity.sinairllmbot.bot.tools.LlmToolContributor
import org.taonity.sinairllmbot.bot.tools.ToolCapability
import org.taonity.sinairllmbot.bot.tools.ToolExecutionContext
import org.taonity.sinairllmbot.chat.repository.ChatEventRepository
import org.taonity.sinairllmbot.chat.repository.ChatMessageRepository
import org.taonity.sinairllmbot.config.BotSettings
import org.taonity.sinairllmbot.config.entity.BotConfigOverrideEntity
import org.taonity.sinairllmbot.config.repository.BotConfigOverrideRepository
import tools.jackson.databind.ObjectMapper
import java.time.Instant

@Service
class ChatCommandToolService(
    private val objectMapper: ObjectMapper,
    private val pipelineContextTracker: PipelineContextTracker,
    private val settings: BotSettings,
    private val overrideRepository: BotConfigOverrideRepository,
    private val outboundMessageRepository: OutboundMessageRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val chatEventRepository: ChatEventRepository,
) : LlmToolContributor {
    private companion object {
        private val LOGGER = KotlinLogging.logger {}

        const val TOOL_NAME = "execute_chat_command"
        const val POLL_INTERVAL_MS = 200L
        const val POLL_TIMEOUT_MS = 10_000L

        val COMMANDS = listOf(
            CommandDef(
                name = "help",
                description = "Show the list of available chat commands and their usage. Use this to " +
                    "discover what commands exist, what each one does, and how to use them.",
                usage = "/help",
                minArgs = 0,
            ),
            CommandDef(
                name = "nick",
                description = "Change your own nick/display name. Without a new name, your nick is cleared — to others it looks like you left the room. You won't be able to send messages until you set a new one.",
                usage = "/nick <new nick>",
                minArgs = 0,
            ),
            CommandDef(
                name = "gender",
                description = "Change your gender display (f = female, m = male)",
                usage = "/gender [f|m]",
                minArgs = 1,
            ),
            CommandDef(
                name = "color",
                description = "Change your name color in hex format (e.g. #f00 for red, #f0f000 for pink)",
                usage = "/color <hex color>",
                minArgs = 1,
            ),
            CommandDef(
                name = "me",
                description = "Send an action/emote message describing an action you're performing",
                usage = "/me <message>",
                minArgs = 1,
            ),
            CommandDef(
                name = "n",
                description = "Send an off-topic message (marked as noise)",
                usage = "/n <message>",
                minArgs = 1,
            ),
            CommandDef(
                name = "do",
                description = "Send a message from third-person perspective",
                usage = "/do <message>",
                minArgs = 1,
            ),
            CommandDef(
                name = "msg",
                description = "Send a private message to a user by their nick within the room",
                usage = "/msg <nick> <message>",
                minArgs = 2,
            ),
            CommandDef(
                name = "umsg",
                description = "Send a private message to a user by their room-internal member ID",
                usage = "/umsg <member_id> <message>",
                minArgs = 2,
            ),
            CommandDef(
                name = "myrooms",
                description = "List rooms you have created",
                usage = "/myrooms",
                minArgs = 0,
            ),
            CommandDef(
                name = "addmoder",
                description = "Add a moderator by account UID (room owner only)",
                usage = "/addmoder <uid>",
                minArgs = 1,
            ),
            CommandDef(
                name = "delmoder",
                description = "Remove a moderator by account UID (room owner only)",
                usage = "/delmoder <uid>",
                minArgs = 1,
            ),
            CommandDef(
                name = "moderlist",
                description = "Show the list of moderator account IDs for the room",
                usage = "/moderlist",
                minArgs = 0,
            ),
            CommandDef(
                name = "banlist",
                description = "Show the full ban list",
                usage = "/banlist",
                minArgs = 0,
            ),
            CommandDef(
                name = "bannick",
                description = "Ban a nick from being used",
                usage = "/bannick <nick>",
                minArgs = 1,
            ),
            CommandDef(
                name = "banuid",
                description = "Ban an account by user ID",
                usage = "/banuid <id>",
                minArgs = 1,
            ),
            CommandDef(
                name = "banip",
                description = "Ban an IP address",
                usage = "/banip <ip>",
                minArgs = 1,
            ),
            CommandDef(
                name = "unbannick",
                description = "Remove a nick from the banned list",
                usage = "/unbannick <nick>",
                minArgs = 1,
            ),
            CommandDef(
                name = "unbanuid",
                description = "Unban an account by user ID",
                usage = "/unbanuid <uid>",
                minArgs = 1,
            ),
            CommandDef(
                name = "unbanip",
                description = "Unban an IP address",
                usage = "/unbanip <ip>",
                minArgs = 1,
            ),
            CommandDef(
                name = "kick",
                description = "Kick a user by their nick",
                usage = "/kick <nick>",
                minArgs = 1,
            ),
            CommandDef(
                name = "userlist",
                description = "List clients with IDs and IPs in the room",
                usage = "/userlist",
                minArgs = 0,
            ),
        )

        val COMMAND_NAMES = COMMANDS.map { it.name }.toSet()
    }

    override val capability: ToolCapability = ToolCapability.CHAT_COMMAND

    override fun definitions(context: ToolExecutionContext): List<Tool> = listOf(
        Tool.function(
            name = TOOL_NAME,
            description = "Execute a chat command on your behalf. This sends the command to the " +
                "chat server, waits for the server's response, and returns the actual result. " +
                "Use this when someone asks you to change your nick, color, send a message in a " +
                "special format, or perform any command from the /help listing. After the tool " +
                "executes, report the outcome in your own conversational reply — e.g. 'Done, " +
                "I've changed my nick to NewBot' or 'Sorry, that command failed: <reason>'. " +
                "Do NOT output the raw command text — the tool already sent it. " +
                "/nick without a name clears your nick — to others it looks like you left " +
                "the room, and you won't be able to send replies until you set a new one. " +
                "/help lists every available command. Use it to discover what the bot can do " +
                "and what role it has in the chat. " +
                "/me <text> sends an action/emote, /do <text> sends third-person, /n <text> " +
                "sends off-topic. /msg <nick> <text> sends a private message. /color <hex> " +
                "changes your name color. /gender [f|m] changes gender display. " +
                "Moderator commands: /banlist, /bannick, /banuid, /banip, /unbannick, " +
                "/unbanuid, /unbanip, /kick, /userlist, /moderlist. " +
                "Owner commands: /addmoder <uid>, /delmoder <uid>. " +
                "Other: /myrooms lists your rooms.",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "command" to mapOf(
                        "type" to "string",
                        "enum" to COMMAND_NAMES.toList(),
                        "description" to "The chat command to execute (without the leading slash).",
                    ),
                    "arguments" to mapOf(
                        "type" to "string",
                        "description" to "The arguments for the command. For multi-word args like " +
                            "a nick change, message text, or hex color, pass them as a single " +
                            "space-separated string. For commands needing two arguments " +
                            "(e.g. /msg nick message), separate them with spaces.",
                    ),
                ),
                "required" to listOf("command"),
            ),
        ),
    )

    override fun supports(name: String): Boolean = name == TOOL_NAME

    override fun execute(
        context: ToolExecutionContext,
        name: String,
        argumentsJson: String,
    ): String {
        val command = parseCommand(argumentsJson) ?: return "ERROR: 'command' is required."
        val rawArgs = parseArguments(argumentsJson)

        val def = COMMANDS.firstOrNull { it.name == command }
        if (def == null) {
            return "ERROR: unknown command '$command'. Available commands: ${COMMAND_NAMES.joinToString(", ")}"
        }

        val argCount = if (rawArgs.isBlank()) 0 else rawArgs.split(Regex("\\s+")).size
        if (argCount < def.minArgs) {
            return "ERROR: '$command' requires at least ${def.minArgs} argument(s). Usage: ${def.usage}"
        }

        val commandText = "/$command $rawArgs".trimEnd()

        pipelineContextTracker.recordSource("chat-command://$command")
        LOGGER.info { "Executing chat command: $commandText in room ${context.roomTarget}" }

        // Phase 1: persist the command as an outbound message (in its own transaction so the
        // collector can pick it up and the polling loop below can see the echo).
        val outboundId = persistOutbound(context.roomTarget, commandText, command, rawArgs)

        // Phase 2: poll for the server's response (outside the transaction — no DB connection held).
        val response = pollForResponse(context.roomTarget, outboundId, command, rawArgs)
        return response
    }

    fun execute(name: String, argumentsJson: String): String {
        val command = parseCommand(argumentsJson) ?: return "ERROR: 'command' is required."
        val rawArgs = parseArguments(argumentsJson)

        val def = COMMANDS.firstOrNull { it.name == command }
        if (def == null) return "ERROR: unknown command '$command'."

        val argCount = if (rawArgs.isBlank()) 0 else rawArgs.split(Regex("\\s+")).size
        if (argCount < def.minArgs) return "ERROR: '$command' requires at least ${def.minArgs} argument(s). Usage: ${def.usage}"

        val commandText = "/$command $rawArgs".trimEnd()
        return "SUCCESS: $commandText"
    }

    @Transactional
    fun persistOutbound(roomTarget: String, commandText: String, command: String, rawArgs: String): String {
        val saved = outboundMessageRepository.save(
            OutboundMessageEntity(
                roomTarget = roomTarget,
                messageText = commandText,
            ),
        )

        // When the bot changes its own nick, sync the persona.name config override so all
        // internal mechanisms stay in sync immediately — no manual console edit needed.
        if (command == "nick" && rawArgs.isNotBlank()) {
            syncNickConfig(rawArgs.trim())
        }

        return saved.id ?: throw RuntimeException("Failed to persist outbound message")
    }

    private fun pollForResponse(
        roomTarget: String,
        outboundId: String,
        command: String,
        rawArgs: String,
    ): String {
        val deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS
        val commandText = "/$command $rawArgs".trimEnd()
        val pollStartedAt = Instant.now()

        while (System.currentTimeMillis() < deadline) {
            // 1. Check for an echo message linked to our outbound row.
            val echo = chatMessageRepository.findBySourceOutboundMessageId(outboundId)
            if (echo != null) {
                val echoText = echo.messageText
                LOGGER.info { "Command echo received for $outboundId: $echoText" }
                return "SUCCESS: $commandText — server responded: $echoText"
            }

            // 2. Check for recent events in the room (system messages from commands, or
            //    nick/color/gender change events). Use receivedAt so we catch events that
            //    arrived after the poll started, regardless of their server-side timestamp.
            val recentEvents = chatEventRepository
                .findByRoomTargetAndReceivedAtAfterOrderByReceivedAtDesc(roomTarget, pollStartedAt)
            for (event in recentEvents) {
                LOGGER.info { "Command event detected for $outboundId: status=${event.status} name=${event.memberName} data=${event.eventData}" }
                val detail = buildString {
                    when (event.status) {
                        "system" -> {
                            val msg = event.eventData ?: "command executed"
                            append("SUCCESS: $commandText — $msg")
                        }
                        "nick_change" -> append("SUCCESS: $commandText — nick changed to '${event.memberName}'")
                        "color_change" -> append("SUCCESS: $commandText — color changed to '${event.memberColor}'")
                        "gender_change" -> append("SUCCESS: $commandText — gender changed")
                        else -> append("SUCCESS: $commandText — event: ${event.status} (${event.memberName})")
                    }
                }
                return detail
            }

            // 3. Check if the outbound message was sent (collector delivered it).
            val current = outboundMessageRepository.findById(outboundId).orElse(null)
            if (current != null && current.status == OutboundStatus.SENT) {
                // Message was sent but no echo yet — the server may not echo some commands.
                // Give a bit more time for the echo to arrive.
            }

            try {
                Thread.sleep(POLL_INTERVAL_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return "ERROR: $commandText — polling was interrupted."
            }
        }

        // Timeout — check one last time for an echo or events.
        val echo = chatMessageRepository.findBySourceOutboundMessageId(outboundId)
        if (echo != null) {
            return "SUCCESS: $commandText — server responded: ${echo.messageText}"
        }
        val lateEvents = chatEventRepository
            .findByRoomTargetAndReceivedAtAfterOrderByReceivedAtDesc(roomTarget, pollStartedAt)
        if (lateEvents.isNotEmpty()) {
            val event = lateEvents.first()
            val msg = if (event.status == "system") event.eventData ?: "command executed" else event.status
            return "SUCCESS: $commandText — $msg"
        }

        LOGGER.warn { "Command $commandText timed out waiting for echo (outboundId=$outboundId)" }
        return "SUCCESS: $commandText — command was sent to the chat server but no response was " +
            "received within the timeout. The command may still have been executed."
    }

    private fun parseCommand(argumentsJson: String): String? {
        val args: Map<*, *> = runCatching { objectMapper.readValue(argumentsJson, Map::class.java) }
            .getOrNull() ?: return null
        return (args["command"] as? String)?.trim()?.lowercase()
    }

    private fun parseArguments(argumentsJson: String): String {
        val args: Map<*, *> = runCatching { objectMapper.readValue(argumentsJson, Map::class.java) }
            .getOrNull() ?: return ""
        return (args["arguments"] as? String)?.trim() ?: ""
    }

    private fun syncNickConfig(newNick: String) {
        val now = Instant.now()
        val existing = overrideRepository.findById("app.bot.persona.name").orElse(null)
        if (existing != null) {
            existing.valueJson = objectMapper.writeValueAsString(newNick)
            existing.updatedAt = now
            existing.updatedBy = "bot"
            overrideRepository.save(existing)
        } else {
            overrideRepository.save(
                BotConfigOverrideEntity(
                    configKey = "app.bot.persona.name",
                    valueJson = objectMapper.writeValueAsString(newNick),
                    updatedAt = now,
                    updatedBy = "bot",
                ),
            )
        }
        settings.reload()
        LOGGER.info { "Synced app.bot.persona.name config to '$newNick'" }
    }
}

data class CommandDef(
    val name: String,
    val description: String,
    val usage: String,
    val minArgs: Int,
)