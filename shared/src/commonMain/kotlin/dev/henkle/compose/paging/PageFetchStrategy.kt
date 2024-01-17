package dev.henkle.compose.paging

import kotlin.reflect.KClass

sealed interface PageFetchStrategy<T, P: Page<T>> {
    /**
     * Fetches pages from an API that pages using an offset and count
     */
    data class Offset<T>(
        private val startPage: Int = 0,
        private val fetch: (offset: Int, count: Int) -> List<T>,
    ) : PageFetchStrategy<T, Page.OffsetPage<T>>

    /**
     * Fetches pages from an API that pages using the last provided ID and a count
     */
    data class LastID<T>(
        private val fetch: (lastId: String, count: Int) -> List<T>,
    ) : PageFetchStrategy<T, Page.IDPage<T>>
}