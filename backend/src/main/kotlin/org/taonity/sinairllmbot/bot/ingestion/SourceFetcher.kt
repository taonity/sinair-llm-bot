package org.taonity.sinairllmbot.bot.ingestion

import org.springframework.stereotype.Component
import org.taonity.sinairllmbot.bot.ingestion.model.SourceDocument

/** Routes a classified URL to the concrete fetcher. New source types plug in here. */
@Component
class SourceFetcher(
    private val urlClassifier: UrlClassifier,
    private val gitHubReadmeFetcher: GitHubReadmeFetcher,
    private val htmlPageFetcher: HtmlPageFetcher,
    private val imageFetcher: ImageFetcher,
) {
    fun fetch(url: String): SourceDocument = when (val classified = urlClassifier.classify(url)) {
        is ClassifiedUrl.GitHub -> gitHubReadmeFetcher.fetch(classified.owner, classified.repo, classified.url)
        is ClassifiedUrl.Image -> imageFetcher.fetch(classified.url)
        is ClassifiedUrl.Web -> htmlPageFetcher.fetch(classified.url)
    }
}
