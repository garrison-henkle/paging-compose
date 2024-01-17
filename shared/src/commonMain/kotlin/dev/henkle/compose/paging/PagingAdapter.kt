@file:Suppress("KDocUnresolvedReference")

package dev.henkle.compose.paging

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

typealias PageNumber = Int

/**
 * A paging adapter for a datasource.
 *
 * @property pageSize The size of each page.
 * @property data The data to present
 * @property state The current state of paging operations
 *
 * @param pageSize The size of each page.
 * @param maxPages The maximum number of pages to keep loaded at once. If the max count is exceeded,
 * a page on the opposite side of the list will be removed i.e. adding a page to the end of the list
 * will trigger the removal of a page at the beginning.
 * @param pagePreloadCount The number of pages to preload on initialization. This applies to both
 * directions relative to the [startPage].
 * @param startPage The page to start on.
 * @param scope A scope for the pager to run jobs in.
 * @param fetch A function that fetches data from the datasource at the provided offset with a
 * given [pageSize]. The datasource should return as much data as it can, even if it cannot fill a
 * page. If the datasource is unable to fetch any data at the given offset, is should return an
 * empty list.
 */
interface PagerAdapter<T> {
    val pageSize: Int
    val data: StateFlow<List<T>>
    val state: StateFlow<PagerState>

    /**
     * Begins population of the [data] output flow from the paged data
     */
    fun startFlows()

    /**
     * Shuts down the [data] output flow, preventing further updates
     */
    fun shutdown()

    /**
     * Loads the next page
     */
    suspend fun loadNext()

    /**
     * Loads the previous page
     */
    suspend fun loadPrevious()
}

internal abstract class BasePagerAdapter<T, P : Page<T>>(
    startPage: Int,
    pagePreloadCount: Int,
    private val scope: CoroutineScope,
) : PagerAdapter<T> {
    protected val actorChannel = Channel<PagerAction>(capacity = 10)
    protected val actor: SendChannel<PagerAction> = actorChannel

    protected val pages = MutableStateFlow<List<P>>(emptyList())
    protected val _data = MutableStateFlow<List<T>>(emptyList())
    override val data = _data.asStateFlow()
    protected val _state = MutableStateFlow<PagerState>(PagerState.Idle)
    override val state = _state.asStateFlow()

    init {
        startActor()
        scope.launch {
            actor.send(
                PagerAction.InitialLoad(
                    startPage = startPage,
                    pagePreloadCount = pagePreloadCount,
                ),
            )
        }
    }

    private fun startActor() {
        scope.launch {
            actorChannel.consumeAsFlow().collect { action ->
                when (action) {
                    is PagerAction.LoadPage -> {
                        when (action.location) {
                            PageLocation.Start -> loadPageAtStart(page = action.page)
                            PageLocation.End -> loadPageAtEnd(page = action.page)
                        }
                    }
                    is PagerAction.InitialLoad ->
                        initialLoad(
                            startPage = action.startPage,
                            pagePreloadCount = action.pagePreloadCount,
                        )
                }
            }
        }
    }

    override fun startFlows() {
        scope.launch {
            pages
                .map { it.map { page -> page.data }.flatten() }
                .collect { data -> _data.value = data }
        }
    }

    override fun shutdown() {
        scope.cancel()
    }

    override suspend fun loadNext() {
        if (state.value.isLoading) return
        val currentLastPage = pages.value.lastOrNull()?.page
        val pageToLoad = currentLastPage?.let { it + 1 } ?: 0
        actor.send(PagerAction.LoadPage(page = pageToLoad, location = PageLocation.End))
    }

    override suspend fun loadPrevious() {
        if (state.value.isLoading) return
        val currentFirstPage = pages.value.firstOrNull()?.page
        val pageToLoad = currentFirstPage?.let { it - 1 }?.coerceAtLeast(0) ?: 0
        actor.send(PagerAction.LoadPage(page = pageToLoad, location = PageLocation.Start))
    }

    abstract suspend fun initialLoad(
        startPage: Int,
        pagePreloadCount: Int,
    )

    abstract suspend fun loadPageAtStart(page: Int)

    abstract suspend fun loadPageAtEnd(page: Int)
}

@Suppress("UnnecessaryVariable")
internal class IDPagerAdapter<T>(
    override val pageSize: Int = 50,
    private val maxPages: Int = 9,
    pagePreloadCount: Int = 2,
    scope: CoroutineScope,
    private val enableThoroughSafetyCheck: Boolean = false,
    private val getID: (T) -> String,
    private val fetch: suspend (lastID: String?, pageSize: Int) -> List<T>,
) : BasePagerAdapter<T, Page.IDPage<T>>(
        startPage = 0,
        pagePreloadCount = pagePreloadCount,
        scope = scope,
    ) {
    private val cache = hashMapOf<PageNumber, PageCache>()

    override suspend fun loadPageAtStart(page: Int) {
        val currentPages = pages.value.toMutableList()

        cache[page]?.also { cachedPage ->
            val newPageData = fetch(cachedPage.previousPageLastID, pageSize)
            currentPages += Page.IDPage(page = page, previousPageLastID = cachedPage.previousPageLastID, size = pageSize, data = newPageData)
            cachedPage.setIds(ids = newPageData.map(getID))

            if (currentPages.size >= maxPages) {
                val lastPage = currentPages.removeLast()
                cache[lastPage.page]?.clear()
            }

            pages.value = currentPages

            _state.value = PagerState.Idle
        } ?: run {
            if (page == 0) {
                initialLoad(startPage = 0, pagePreloadCount = 0)
            } else {
                // TODO: determine what to do when a non-0 page is not found in the cache. Options:
                //  1. start a while loop that keeps adding new pages until it finds a lastID that
                //     is already loaded (this is probably not a great idea)
                //  2. ignore the request and assume the pager is in a bad state (better idea but
                //     not ideal)
                //  3. load another page that is in the cache (this will probably lead to invalid
                //     states, so probably not a great idea)
            }
        }
    }

    override suspend fun loadPageAtEnd(page: Int) {
        val currentPages = pages.value.toMutableList()

        val lastID = currentPages.lastOrNull()?.takeIf { it.isFull }?.previousPageLastID ?: return
        val invalidLastID =
            !isValidPageLoad(
                lastID = lastID,
                currentPages = currentPages,
                thorough = enableThoroughSafetyCheck,
            )
        if (invalidLastID) return

        _state.value = PagerState.LoadingAtEnd

        val newPageData = fetch(lastID, pageSize)
        currentPages += Page.IDPage(page = page, previousPageLastID = lastID, size = pageSize, data = newPageData)
        cache[page] = PageCache(previousPageLastID = lastID, ids = newPageData.map(getID))
        if (currentPages.size >= maxPages) {
            val removedPage = currentPages.removeFirst().page
            cache[removedPage]?.clear()
        }
    }

    override suspend fun initialLoad(
        startPage: Int,
        pagePreloadCount: Int,
    ) {
        val currentPages = pages.value.toMutableList()

        _state.value = PagerState.InitialLoad

        var lastID: String? = null
        var newPageData: List<T>
        var newPage: Page.IDPage<T>

        for (i in 0..pagePreloadCount) {
            newPageData = fetch(lastID, pageSize)
            newPage = Page.IDPage(page = i, previousPageLastID = lastID, size = pageSize, data = newPageData)
            cache[i] = PageCache(previousPageLastID = lastID, ids = newPageData.map(getID))
            currentPages += newPage
            if (!newPage.isFull) break
            lastID = getID(newPage.data.last())
        }

        pages.value = currentPages

        _state.value = PagerState.Idle
    }

    private fun isValidPageLoad(
        lastID: String,
        currentPages: List<Page.IDPage<T>>,
        thorough: Boolean,
    ): Boolean {
        val pageAlreadyLoaded = currentPages.any { it.previousPageLastID == lastID }
        if (pageAlreadyLoaded) return false
        if (!thorough) return true
        val newIDNotInExistingPages = cache.none { (_, cache) -> lastID in cache.ids }
        return newIDNotInExistingPages
    }

    class PageCache(val previousPageLastID: String?, ids: List<String>) {
        private val _ids = ids.toMutableList()
        val ids: List<String> = _ids

        fun clear() {
            _ids.clear()
        }

        fun setIds(ids: List<String>) {
            _ids += ids
        }
    }
}

internal class OffsetPagerAdapter<T>(
    override val pageSize: Int = 50,
    private val maxPages: Int = 9,
    pagePreloadCount: Int = 2,
    startPage: Int = 0,
    scope: CoroutineScope,
    private val fetch: suspend (offset: Int, pageSize: Int) -> List<T>,
) : BasePagerAdapter<T, Page.OffsetPage<T>>(
        startPage = startPage,
        pagePreloadCount = pagePreloadCount,
        scope = scope,
    ) {
    override suspend fun loadPageAtStart(page: Int) {
        val currentPages = pages.value.toMutableList()

        val offset = page * pageSize
        if (currentPages.any { it.offset == offset }) return

        _state.value = PagerState.LoadingAtStart

        val newPageData = fetch(offset, pageSize)
        currentPages.add(0, Page.OffsetPage(page = page, offset = offset, size = pageSize, data = newPageData))
        if (currentPages.size >= maxPages) {
            currentPages.removeLast()
        }
        pages.value = currentPages

        _state.value = PagerState.Idle
    }

    override suspend fun loadPageAtEnd(page: Int) {
        val currentPages = pages.value.toMutableList()

        val offset = page * pageSize
        currentPages.lastOrNull()?.takeIf { it.isFull } ?: return
        if (currentPages.any { it.offset == offset }) return

        _state.value = PagerState.LoadingAtEnd

        val newPageData = fetch(offset, pageSize)
        currentPages += Page.OffsetPage(page = page, offset = offset, size = pageSize, data = newPageData)
        if (currentPages.size >= maxPages) {
            currentPages.removeFirst()
        }
        pages.value = currentPages

        _state.value = PagerState.Idle
    }

    override suspend fun initialLoad(
        startPage: Int,
        pagePreloadCount: Int,
    ) {
        val currentPages = pages.value.toMutableList()

        _state.value = PagerState.InitialLoad

        var nextOffset = startPage * pageSize
        var newPageData: List<T>
        var newPage: Page.OffsetPage<T>

        val lastPageToLoad = startPage + pagePreloadCount
        for (i in startPage..lastPageToLoad) {
            newPageData = fetch(nextOffset, pageSize)
            newPage = Page.OffsetPage(page = i, offset = nextOffset, size = pageSize, data = newPageData)
            currentPages += newPage
            if (!newPage.isFull) break
            nextOffset = (startPage + i + 1) * pageSize
        }

        val previousPage = startPage - 1
        val lastPreviousPageToLoad = (startPage - pagePreloadCount - 1).coerceAtLeast(0)
        for (i in previousPage downTo lastPreviousPageToLoad) {
            nextOffset = i * pageSize
            newPageData = fetch(nextOffset, pageSize)
            newPage = Page.OffsetPage(page = i, offset = nextOffset, size = pageSize, data = newPageData)
            currentPages.add(0, newPage)
        }

        pages.value = currentPages

        _state.value = PagerState.Idle
    }
}
