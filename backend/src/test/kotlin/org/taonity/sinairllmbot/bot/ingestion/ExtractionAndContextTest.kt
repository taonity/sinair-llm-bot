package org.taonity.sinairllmbot.bot.ingestion

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.taonity.sinairllmbot.bot.ingestion.config.IngestionProperties
import org.taonity.sinairllmbot.bot.ingestion.model.LinkKind
import org.taonity.sinairllmbot.bot.ingestion.model.SourceDocument
import org.taonity.sinairllmbot.bot.ingestion.model.SourceType

class LinkClassifierTest {
    private val classifier = LinkClassifier()

    @Test
    fun `flags badges and social and docs links`() {
        assertThat(classifier.classify("https://img.shields.io/badge/build-passing.svg", "build"))
            .isEqualTo(LinkKind.BADGE)
        assertThat(classifier.classify("https://twitter.com/project", "follow us"))
            .isEqualTo(LinkKind.SOCIAL)
        assertThat(classifier.classify("https://example.com/docs/quickstart", "Quickstart"))
            .isEqualTo(LinkKind.DOCS)
        assertThat(classifier.classify("https://project.io/reference", "API reference"))
            .isEqualTo(LinkKind.API)
    }
}

class ContentExtractorTest {
    private val extractor = ContentExtractor(LinkClassifier())

    @Test
    fun `extracts readable text links and images and strips chrome`() {
        val html = """
            <html><head><title>Hello Docs</title>
            <link rel="canonical" href="https://example.com/canonical"/></head>
            <body>
              <nav><a href="https://example.com/nav">nav</a></nav>
              <script>var x = 1;</script>
              <main>
                <h1>Title</h1>
                <p>First paragraph of content.</p>
                <a href="https://example.com/docs/guide">Guide</a>
                <img src="https://example.com/pic.png" alt="a picture"/>
              </main>
              <footer>footer junk</footer>
            </body></html>
        """.trimIndent()

        val result = extractor.extract(html, "https://example.com/page")

        assertThat(result.title).isEqualTo("Hello Docs")
        assertThat(result.canonicalUrl).isEqualTo("https://example.com/canonical")
        assertThat(result.text).contains("First paragraph of content.")
        assertThat(result.text).doesNotContain("var x = 1", "footer junk")
        assertThat(result.links.map { it.url }).contains("https://example.com/docs/guide")
        assertThat(result.images).singleElement()
        assertThat(result.images.first().url).isEqualTo("https://example.com/pic.png")
        assertThat(result.images.first().alt).isEqualTo("a picture")
    }
}

class ContextBuilderTest {
    private val builder = ContextBuilder(IngestionProperties())

    @Test
    fun `renders source blocks and collects image data urls`() {
        val docs = listOf(
            SourceDocument(
                sourceId = "github:owner/repo",
                sourceType = SourceType.GITHUB_README,
                url = "https://github.com/owner/repo",
                title = "owner/repo",
                contentText = "This project does something useful.",
                metadata = mapOf("commitSha" to "abc1234"),
            ),
            SourceDocument(
                sourceId = "image:x",
                sourceType = SourceType.IMAGE,
                url = "https://cdn.com/diagram.png",
                imageDataUrl = "data:image/png;base64,AAAA",
            ),
        )

        val result = builder.build(docs, "what does this project do")

        assertThat(result.contextText).contains("Source 1:", "Type: github_readme", "Commit: abc1234")
        assertThat(result.contextText).contains("This project does something useful.")
        assertThat(result.contextText).contains("Type: image")
        assertThat(result.hasImages).isTrue()
        assertThat(result.imageDataUrls).containsExactly("data:image/png;base64,AAAA")
    }

    @Test
    fun `no images yields empty data url list`() {
        val docs = listOf(
            SourceDocument(
                sourceId = "web:x",
                sourceType = SourceType.WEB_PAGE,
                url = "https://example.com",
                contentText = "hello",
            ),
        )

        val result = builder.build(docs, "hello")

        assertThat(result.hasImages).isFalse()
        assertThat(result.imageDataUrls).isEmpty()
    }
}
