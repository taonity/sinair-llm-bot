package org.taonity.sinairllmbot.bot.ingestion

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.taonity.sinairllmbot.bot.ingestion.config.IngestionProperties
import org.taonity.sinairllmbot.bot.ingestion.config.IngestionSettings

class UrlExtractorTest {
    private val extractor = UrlExtractor()

    @Test
    fun `extracts urls and trims trailing punctuation`() {
        val urls = extractor.extract("глянь https://github.com/owner/repo, и вот тут (https://example.com/docs).")

        assertThat(urls).containsExactly(
            "https://github.com/owner/repo",
            "https://example.com/docs",
        )
    }

    @Test
    fun `returns empty when no url present`() {
        assertThat(extractor.extract("no links here at all")).isEmpty()
    }

    @Test
    fun `deduplicates repeated urls`() {
        val urls = extractor.extract("https://a.com https://a.com")
        assertThat(urls).containsExactly("https://a.com")
    }
}

class UrlClassifierTest {
    private val classifier = UrlClassifier(IngestionSettings { IngestionProperties() })

    @Test
    fun `classifies github repo urls`() {
        val result = classifier.classify("https://github.com/mozilla/readability")

        assertThat(result).isInstanceOf(ClassifiedUrl.GitHub::class.java)
        result as ClassifiedUrl.GitHub
        assertThat(result.owner).isEqualTo("mozilla")
        assertThat(result.repo).isEqualTo("readability")
    }

    @Test
    fun `strips git suffix from repo`() {
        val result = classifier.classify("https://github.com/owner/repo.git") as ClassifiedUrl.GitHub
        assertThat(result.repo).isEqualTo("repo")
    }

    @Test
    fun `treats github site pages as web`() {
        assertThat(classifier.classify("https://github.com/features/actions"))
            .isInstanceOf(ClassifiedUrl.Web::class.java)
    }

    @Test
    fun `classifies direct image links by extension`() {
        assertThat(classifier.classify("https://cdn.site.com/pic/diagram.PNG"))
            .isInstanceOf(ClassifiedUrl.Image::class.java)
        assertThat(classifier.classify("https://cdn.site.com/a/b/shot.webp"))
            .isInstanceOf(ClassifiedUrl.Image::class.java)
    }

    @Test
    fun `defaults to web page`() {
        assertThat(classifier.classify("https://example.com/article/how-to"))
            .isInstanceOf(ClassifiedUrl.Web::class.java)
    }
}
