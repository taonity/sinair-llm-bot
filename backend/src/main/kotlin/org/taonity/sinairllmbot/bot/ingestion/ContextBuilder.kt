package org.taonity.sinairllmbot.bot.ingestion

import org.springframework.stereotype.Component
import org.taonity.sinairllmbot.bot.ingestion.config.IngestionProperties
import org.taonity.sinairllmbot.bot.ingestion.model.LinkKind
import org.taonity.sinairllmbot.bot.ingestion.model.SourceDocument
import org.taonity.sinairllmbot.bot.ingestion.model.SourceType

/**
 * Assembles the compact, source-cited context that grounds the reply. Text sources are rendered in
 * the `Source N:` block format and trimmed to a character budget (long documents get simple
 * keyword/heading-based section selection); image sources are surfaced as attachments whose bytes
 * are carried separately as data URLs for the vision model.
 */
@Component
class ContextBuilder(
    private val properties: IngestionProperties,
) {
    private companion object {
        private val KEYWORD_SPLIT = Regex("\\W+")
        private val PARAGRAPH_SPLIT = Regex("\n{2,}")
        private const val MIN_KEYWORD_LENGTH = 4
        private const val MAX_DOC_LINKS = 5
    }

    data class GroundedContext(
        val contextText: String,
        val imageDataUrls: List<String>,
    ) {
        val hasImages: Boolean get() = imageDataUrls.isNotEmpty()
    }

    fun build(documents: List<SourceDocument>, question: String): GroundedContext {
        val textDocs = documents.filter { it.sourceType != SourceType.IMAGE }
        val imageDocs = documents.filter { it.sourceType == SourceType.IMAGE }

        val builder = StringBuilder()
        var index = 1
        var remaining = properties.maxContextChars

        for (doc in textDocs) {
            if (remaining <= 0) break
            val block = renderTextSource(index, doc, question, minOf(remaining, properties.maxCharsPerSource))
            builder.append(block).append("\n\n")
            remaining -= block.length
            index++
        }
        for (doc in imageDocs) {
            builder.append(renderImageSource(index, doc)).append("\n\n")
            index++
        }

        return GroundedContext(
            contextText = builder.toString().trim(),
            imageDataUrls = imageDocs.mapNotNull { it.imageDataUrl },
        )
    }

    private fun renderTextSource(index: Int, doc: SourceDocument, question: String, charBudget: Int): String {
        val body = selectRelevant(doc.contentText.orEmpty(), question, charBudget)
        return buildString {
            append("Source ").append(index).append(":\n")
            append("Type: ").append(doc.sourceType.wireName).append("\n")
            append("URL: ").append(doc.canonicalUrl ?: doc.url).append("\n")
            doc.title?.let { append("Title: ").append(it).append("\n") }
            doc.metadata["commitSha"]?.let { append("Commit: ").append(it).append("\n") }

            val docLinks = doc.links
                .filter { it.kind == LinkKind.DOCS || it.kind == LinkKind.API }
                .map { it.url }
                .distinct()
                .take(MAX_DOC_LINKS)
            if (docLinks.isNotEmpty()) {
                append("Documentation links (not fetched): ").append(docLinks.joinToString(", ")).append("\n")
            }

            append("Content:\n").append(body.ifBlank { "(no readable text content)" })
        }
    }

    private fun renderImageSource(index: Int, doc: SourceDocument): String = buildString {
        append("Source ").append(index).append(":\n")
        append("Type: image\n")
        append("URL: ").append(doc.url).append("\n")
        append("Content: (image attached below — analyse it directly)")
    }

    /**
     * Fits document text into [charBudget]. When it overflows, keeps the paragraphs that best match
     * the question's keywords (falling back to a head-truncation when nothing matches).
     */
    private fun selectRelevant(text: String, question: String, charBudget: Int): String {
        val clean = text.trim()
        if (clean.length <= charBudget) return clean

        val keywords = question.lowercase().split(KEYWORD_SPLIT).filter { it.length >= MIN_KEYWORD_LENGTH }.toSet()
        if (keywords.isEmpty()) return headTruncate(clean, charBudget)

        val paragraphs = clean.split(PARAGRAPH_SPLIT).filter { it.isNotBlank() }
        val scored = paragraphs.mapIndexed { position, paragraph ->
            val lower = paragraph.lowercase()
            ScoredParagraph(position, paragraph, keywords.count { lower.contains(it) })
        }
        if (scored.none { it.score > 0 }) return headTruncate(clean, charBudget)

        val chosen = mutableListOf<Int>()
        var used = 0
        for (candidate in scored.filter { it.score > 0 }.sortedByDescending { it.score }) {
            val cost = candidate.text.length + 2
            if (used + cost > charBudget) continue
            chosen.add(candidate.position)
            used += cost
        }
        if (chosen.isEmpty()) return headTruncate(clean, charBudget)
        return chosen.sorted().joinToString("\n\n") { paragraphs[it] }
    }

    private fun headTruncate(text: String, charBudget: Int): String =
        text.take(charBudget).trimEnd() + "…"

    private data class ScoredParagraph(val position: Int, val text: String, val score: Int)
}
