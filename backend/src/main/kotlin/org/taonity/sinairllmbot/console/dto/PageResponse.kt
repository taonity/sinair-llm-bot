package org.taonity.sinairllmbot.console.dto

import org.springframework.data.domain.Page

data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasMore: Boolean,
) {
    companion object {
        fun <E : Any, T : Any> of(page: Page<E>, mapper: (E) -> T): PageResponse<T> = PageResponse(
            content = page.content.map(mapper),
            page = page.number,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            hasMore = page.hasNext(),
        )
    }
}
