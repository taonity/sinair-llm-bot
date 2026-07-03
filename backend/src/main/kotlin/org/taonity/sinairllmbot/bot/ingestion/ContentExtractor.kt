package org.taonity.sinairllmbot.bot.ingestion

import org.jsoup.Jsoup
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor
import org.springframework.stereotype.Component
import org.taonity.sinairllmbot.bot.ingestion.model.SourceImage
import org.taonity.sinairllmbot.bot.ingestion.model.SourceLink

/**
 * Turns raw HTML (a web page or GitHub's rendered README) into clean readable text plus extracted
 * links and images. Navigation, scripts, styles and other chrome are stripped before text is
 * collected so the model gets the article body rather than the whole DOM.
 */
@Component
class ContentExtractor(
    private val linkClassifier: LinkClassifier,
) {
    private companion object {
        private const val NOISE_SELECTOR =
            "script, style, noscript, template, nav, header, footer, aside, form, iframe, svg, " +
                "button, [role=navigation], [role=banner], [role=contentinfo], .advertisement, " +
                ".ads, .cookie, .cookie-banner, .sidebar"
        private val BLANK_LINES = Regex("\n{3,}")
        private val INLINE_WS = Regex("[\\t ]{2,}")
    }

    data class ExtractedContent(
        val title: String?,
        val canonicalUrl: String?,
        val text: String,
        val links: List<SourceLink>,
        val images: List<SourceImage>,
    )

    fun extract(html: String, baseUrl: String): ExtractedContent {
        val doc = Jsoup.parse(html, baseUrl)
        val title = doc.title().trim().ifBlank { null }
        val canonical = doc.selectFirst("link[rel=canonical]")?.attr("abs:href")?.trim()?.ifBlank { null }

        val links = doc.select("a[href]")
            .mapNotNull { element ->
                val href = element.attr("abs:href").trim()
                if (!href.startsWith("http")) return@mapNotNull null
                val text = element.text().trim().ifBlank { null }
                SourceLink(url = href, text = text, kind = linkClassifier.classify(href, text))
            }
            .distinctBy { it.url }

        val images = doc.select("img[src]")
            .mapNotNull { element ->
                val src = element.attr("abs:src").trim()
                if (!src.startsWith("http")) return@mapNotNull null
                SourceImage(url = src, alt = element.attr("alt").trim().ifBlank { null }, sourceUrl = baseUrl)
            }
            .distinctBy { it.url }

        doc.select(NOISE_SELECTOR).remove()
        val root = doc.selectFirst("article")
            ?: doc.selectFirst("main")
            ?: doc.selectFirst("[role=main]")
            ?: doc.body()
            ?: doc

        val visitor = TextVisitor()
        NodeTraversor.traverse(visitor, root)
        val text = visitor.result()
            .lineSequence()
            .map { INLINE_WS.replace(it, " ").trim() }
            .joinToString("\n")
            .let { BLANK_LINES.replace(it, "\n\n") }
            .trim()

        return ExtractedContent(title, canonical, text, links, images)
    }

    /** Collects visible text while turning block elements and list items into line breaks. */
    private class TextVisitor : NodeVisitor {
        private val accumulator = StringBuilder()
        private val blocks = setOf(
            "p", "div", "section", "article", "tr", "table", "ul", "ol", "pre", "blockquote",
            "h1", "h2", "h3", "h4", "h5", "h6",
        )

        override fun head(node: Node, depth: Int) {
            when {
                node is TextNode -> accumulator.append(node.text())
                node.nodeName() == "li" -> accumulator.append("\n- ")
                node.nodeName() == "br" -> accumulator.append("\n")
                node.nodeName() in blocks -> accumulator.append("\n")
            }
        }

        override fun tail(node: Node, depth: Int) {
            if (node.nodeName() in blocks || node.nodeName() == "li") {
                accumulator.append("\n")
            }
        }

        fun result(): String = accumulator.toString()
    }
}
