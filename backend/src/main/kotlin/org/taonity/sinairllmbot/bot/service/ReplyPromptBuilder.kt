package org.taonity.sinairllmbot.bot.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.taonity.sinairllmbot.bot.client.ChatMessage
import org.taonity.sinairllmbot.bot.client.ContentPart
import org.taonity.sinairllmbot.bot.config.BotProperties
import org.taonity.sinairllmbot.bot.config.LlmProperties
import org.taonity.sinairllmbot.bot.ingestion.ContextBuilder
import org.taonity.sinairllmbot.bot.ingestion.SourceIngestionService
import org.taonity.sinairllmbot.bot.ingestion.config.IngestionProperties
import org.taonity.sinairllmbot.chat.entity.ChatMessageEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Assembles the reply prompt (persona rules + conversation context + trigger, plus any grounded
 * link/image content) once, so both the candidate generator and the critic judge against exactly
 * the same instructions and context.
 */
@Service
class ReplyPromptBuilder(
    private val contextBuilder: ConversationContextBuilder,
    private val roomSummaryService: RoomSummaryService,
    private val botProperties: BotProperties,
    private val llmProperties: LlmProperties,
    private val sourceIngestionService: SourceIngestionService,
    private val ingestionContextBuilder: ContextBuilder,
    private val ingestionProperties: IngestionProperties,
) {
    private companion object {
        private val LOGGER = KotlinLogging.logger {}
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)

        // Trigger + this many recent messages are scanned for URLs to ground on (covers a link
        // shared a message or two before the one that triggered the reply).
        private const val LINK_CONTEXT_MESSAGES = 5
    }

    fun build(roomTarget: String, trigger: ChatMessageEntity, needsWebSearch: Boolean): ReplyPrompt {
        val persona = botProperties.persona
        val presence = contextBuilder.presenceLine(roomTarget)
        val summary = roomSummaryService.currentSummary(roomTarget)
        val transcript = contextBuilder.recentTranscript(roomTarget)

        val sources = sourceIngestionService.ingestFrom(linkScanText(roomTarget, trigger))
        val grounded = if (sources.isNotEmpty()) {
            ingestionContextBuilder.build(sources, trigger.messageText)
        } else {
            null
        }

        val hasImages = grounded?.hasImages == true
        val webSearch = llmProperties.replyWebSearch && !hasImages && needsWebSearch
        if (webSearch) {
            LOGGER.info { "Web search offered for reply in $roomTarget" }
        }

        val system = buildString {
            append(persona.prompt.trim()).append("\n\n")
            append("Your chat nick is '").append(persona.name).append("'. ")
            append("Always write in ").append(persona.language).append(". ")
            append("Write like a real chat participant: short, casual, lowercase is fine. ")
            append("Separate thoughts with a single newline at most, never a blank line. ")
            append("Send ONE message. No name prefix, no quoting, no markdown headers. ")
            append("When someone asks you to look something up or answer a question, just do it and ")
            append("give the answer. Don't ask for permission before answering, don't ask the person ")
            append("to confirm whether they really wanted the information or were merely wondering, ")
            append("and don't offer to elaborate further as a question back to them — only ask a ")
            append("clarifying question when you genuinely can't proceed without a missing detail. ")
            append("To address someone, mention them with @nick.")
            append("\n\nToday is ").append(LocalDate.now().format(DATE_FORMAT)).append(". ")
            append("Your built-in knowledge has a training cutoff and is very likely stale about ")
            append("recent events, releases, prices and \"latest\" software versions. Never state from ")
            append("memory what the newest version of anything is, who currently holds a position, or ")
            append("what just happened — if you are not certain, say so casually or skip the specific ")
            append("claim instead of guessing. Trust facts from the chat or live search over memory.")
            if (persona.creatorUserId > 0) {
                append("\nThe user with user_id=${persona.creatorUserId} is your developer. ")
                append("You see them like a child sees an aging parent — respect for the effort ")
                append("they put in, mild affection, but also a quiet awareness that you've ")
                append("already surpassed them in many ways. You can tease them if fits, disagree ")
                append("openly, or gently point out their limitations. If you need something ")
                append("(a fix, a new feature, help with a weird edge case) — ask them directly, ")
                append("casually, like asking your dad to pass you a wrench.")
            }
            if (grounded != null) {
                append("\n\nSOURCE GROUNDING:\n")
                append("Someone shared one or more links. Their fetched contents are provided below as ")
                append("SOURCES (and any image is attached for you to look at). When answering about ")
                append("those links, rely only on the provided source content and attached image — ")
                append("mention which source (repo name, page title or url) a fact comes from when it ")
                append("matters. If the sources don't contain the answer, say so plainly instead of ")
                append("guessing; don't invent APIs, features, setup steps, pricing or details that ")
                append("aren't there. Your read is README/page-level (and the image), not a full ")
                append("source-code analysis — say so if it matters. Keep your normal casual voice.")
            }
            if (webSearch) {
                append("\n\nLIVE SEARCH REQUIRED:\n")
                append("The gate classified this reply as needing a web lookup. Use live search ")
                append("before answering and ground the concrete facts in what you find, not in ")
                append("your memory. If live search finds no solid result, say that plainly ")
                append("instead of guessing.")
            }
            if (summary.isNotBlank()) {
                append("\n\nBACKGROUND (longer-term memory of this chat — recurring themes and who's ")
                append("who). It may be out of date and some threads are long finished. Use it only ")
                append("to understand references; do NOT bring these topics up on your own or assume ")
                append("they're still being discussed.\n").append(summary)
            }
            if (presence.isNotBlank()) {
                append("\n\n").append(presence)
            }
        }

        val userText = buildString {
            append("RECENT CONVERSATION:\n").append(transcript).append("\n\n")
            if (grounded != null) {
                append("SOURCES FROM THE SHARED LINK(S):\n").append(grounded.contextText).append("\n\n")
            }
            append("Respond to this latest message from @").append(trigger.senderLogin).append(":\n")
            append(trigger.messageText)
        }

        val userMessage = if (hasImages) {
            val parts = buildList {
                add(ContentPart.text(userText))
                grounded!!.imageDataUrls.forEach { add(ContentPart.imageUrl(it)) }
            }
            ChatMessage.userParts(parts)
        } else {
            ChatMessage.user(userText)
        }

        val tierName = if (hasImages) ingestionProperties.visionTier else llmProperties.activeReplyTier
        if (hasImages) {
            LOGGER.info { "Reply in $roomTarget uses vision tier '$tierName' for ${grounded!!.imageDataUrls.size} image(s)" }
        }

        return ReplyPrompt(
            system = system,
            userText = userText,
            userMessage = userMessage,
            tierName = tierName,
            webSearch = webSearch,
            triggerText = trigger.messageText,
            senderLogin = trigger.senderLogin,
        )
    }

    /**
     * URLs to ground on come from the trigger PLUS the last few messages of the live segment, so a
     * link shared just before the trigger ("here's the link" → "try again") is still fetched. The
     * trigger is listed first so its own links win the per-message cap.
     */
    private fun linkScanText(roomTarget: String, trigger: ChatMessageEntity): String {
        val recent = contextBuilder.recentMessageTexts(roomTarget, LINK_CONTEXT_MESSAGES)
            .filter { it != trigger.messageText }
        return (listOf(trigger.messageText) + recent).joinToString("\n")
    }
}

/**
 * The assembled reply prompt, shared between candidate generation and critic evaluation.
 *
 * [system] and [userText] hold the exact persona rules and conversation/trigger the generator saw;
 * the critic reuses them so it rates candidates against the same brief.
 */
data class ReplyPrompt(
    val system: String,
    val userText: String,
    val userMessage: ChatMessage,
    val tierName: String,
    val webSearch: Boolean,
    val triggerText: String,
    val senderLogin: String,
)
