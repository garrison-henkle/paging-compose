@file:Suppress("KDocUnresolvedReference")

package dev.henkle.compose.paging

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

typealias PageNumber = Int

/**
 * A paging adapter for a datasource.
 *
 * @property pageSize The size of each page.
 * @property data The data to present and the number of items added or removed by the transform
 * @property state The current state of paging operations
 */
interface PagerAdapter<T> {
    val pageSize: Int
    val data: StateFlow<TransformedData<T>>
    val state: StateFlow<PagerState>

    /**
     * Refreshes the pager by loading new data
     */
    fun reset()

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

internal abstract class BasePagerAdapter<T, R, P : Page<T>>(
    private val startPage: Int,
    private val pagePreloadCount: Int,
    private val scope: CoroutineScope,
    protected val transform: (pages: IntRange, items: List<T>) -> TransformedData<R>,
) : PagerAdapter<R> {
    private val actorChannel = Channel<PagerAction>(capacity = 10)
    private val actor: SendChannel<PagerAction> = actorChannel

    protected val pages = MutableStateFlow(emptyList<P>())

    private val _data = MutableStateFlow(TransformedData(emptyList<R>()))
    override val data = _data.asStateFlow()

    @Suppress("ktlint:standard:property-naming")
    protected val _state = MutableStateFlow<PagerState>(PagerState.Idle)
    override val state = _state.asStateFlow()

    init {
        startActor()
        scope.launch {
            actor.send(
                PagerAction.Refresh(
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
                    is PagerAction.Refresh ->
                        refresh(
                            startPage = action.startPage,
                            pagePreloadCount = action.pagePreloadCount,
                        )
                }
            }
        }
    }

    override fun reset() {
        scope.launch {
            refresh(startPage = startPage, pagePreloadCount = pagePreloadCount)
        }
    }

    override fun startFlows() {
        scope.launch {
            pages
                .map { pages ->
                    val flattenedPageData = pages.map { page -> page.data }.flatten()
                    val pageNumbers =
                        pages.firstOrNull()?.page?.let { firstPageNumber ->
                            pages.lastOrNull()?.page?.let { lastPageNumber ->
                                firstPageNumber..lastPageNumber
                            }
                        } ?: IntRange.EMPTY
                    pageNumbers to flattenedPageData
                }
                .collect { (pages, data) ->
                    _data.value = transform(pages, data)
                }
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

    abstract suspend fun refresh(
        startPage: Int,
        pagePreloadCount: Int,
    )

    abstract suspend fun loadPageAtStart(page: Int)

    abstract suspend fun loadPageAtEnd(page: Int)
}

/**
 * Pager that uses ids and counts to retrieve more data
 *
 * @param pageSize The size of each page.
 * @param maxPages The maximum number of pages to keep loaded at once. If the max count is exceeded,
 * a page on the opposite side of the list will be removed i.e. adding a page to the end of the list
 * will trigger the removal of a page at the beginning.
 * @param pagePreloadCount The number of pages to preload on initialization. This applies to both
 * directions relative to the [startPage].
 * @param startPage The page to start on.
 * @param scope A scope for the pager to run jobs in.
 * @param getID A function to retrieve an ID for a given item
 * @param fetch A function that fetches data from the datasource following the last ID provided with
 * a given [pageSize]. The datasource should return as much data as it can, even if it cannot fill a
 * page. If the datasource is unable to fetch any data following an ID, is should return an empty
 * list.
 */
@Suppress("UnnecessaryVariable")
internal class IDPagerAdapter<T, R, ID : Any>(
    override val pageSize: Int = 50,
    private val maxPages: Int = 9,
    pagePreloadCount: Int = 2,
    scope: CoroutineScope,
    private val enableThoroughSafetyCheck: Boolean = false,
    private val getID: (T) -> ID,
    transform: (pages: IntRange, items: List<T>) -> TransformedData<R>,
    private val fetch: suspend (lastID: ID?, pageSize: Int) -> List<T>,
) : BasePagerAdapter<T, R, Page.IDPage<T, ID>>(
        startPage = 0,
        pagePreloadCount = pagePreloadCount,
        scope = scope,
        transform = transform,
    ) {
    private val cache = hashMapOf<PageNumber, PageCache<ID>>()

    override suspend fun loadPageAtStart(page: Int) {
        val currentPages = pages.value.toMutableList()

        cache[page]?.also { cachedPage ->
            val invalidLastID =
                !isValidPageLoad(
                    lastID = cachedPage.previousPageLastID,
                    currentPages = currentPages,
                    thorough = enableThoroughSafetyCheck,
                )
            if (invalidLastID) return

            _state.value = PagerState.LoadingAtStart

            val newPageData = fetch(cachedPage.previousPageLastID, pageSize)
            currentPages.add(
                0,
                Page.IDPage(
                    page = page,
                    previousPageLastID = cachedPage.previousPageLastID,
                    size = pageSize, data = newPageData,
                ),
            )

            cachedPage.setIds(ids = newPageData.map(getID))

            if (currentPages.size >= maxPages) {
                val lastPage = currentPages.removeLast()
                cache[lastPage.page]?.clear()
            }

            withContext(Dispatchers.Main) {
                pages.value = currentPages

                _state.value = PagerState.Idle
            }
        } ?: run {
            if (page == 0) {
                refresh(startPage = 0, pagePreloadCount = 0)
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

        val lastID =
            currentPages
                .lastOrNull()
                ?.takeIf { it.isFull }
                ?.data?.lastOrNull()
                ?.let(getID)
                ?: return

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

        withContext(Dispatchers.Main) {
            pages.value = currentPages

            _state.value = PagerState.Idle
        }
    }

    override suspend fun refresh(
        startPage: Int,
        pagePreloadCount: Int,
    ) {
        val currentPages = mutableListOf<Page.IDPage<T, ID>>()

        _state.value = PagerState.Refresh

        var lastID: ID? = null
        var newPageData: List<T>
        var newPage: Page.IDPage<T, ID>

        for (i in 0..pagePreloadCount) {
            newPageData = fetch(lastID, pageSize)
            newPage = Page.IDPage(page = i, previousPageLastID = lastID, size = pageSize, data = newPageData)
            cache[i] = PageCache(previousPageLastID = lastID, ids = newPageData.map(getID))
            currentPages += newPage
            if (!newPage.isFull) break
            lastID = getID(newPage.data.last())
        }

        withContext(Dispatchers.Main) {
            pages.value = currentPages

            _state.value = PagerState.Idle
        }
    }

    private fun isValidPageLoad(
        lastID: ID?,
        currentPages: List<Page.IDPage<T, ID>>,
        thorough: Boolean,
    ): Boolean {
        val pageAlreadyLoaded = currentPages.any { it.previousPageLastID == lastID }
        if (pageAlreadyLoaded) return false
        if (!thorough) return true
        val newIDNotInExistingPages = cache.none { (_, cache) -> lastID in cache.ids }
        return newIDNotInExistingPages
    }

    class PageCache<ID>(val previousPageLastID: ID?, ids: List<ID>) {
        private val _ids = ids.toMutableList()
        val ids: List<ID> = _ids

        fun clear() {
            _ids.clear()
        }

        fun setIds(ids: List<ID>) {
            _ids += ids
        }

        override fun toString(): String {
            val builder = StringBuilder()
            builder.append("PageCache(")
            builder.append("previousPageLastID=$previousPageLastID")
            builder.append(',')
            builder.append("ids=$ids")
            builder.append(')')
            return builder.toString()
        }
    }
}

/**
 * Pager that uses offsets and counts to retrieve more data
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
internal class OffsetPagerAdapter<T, R>(
    override val pageSize: Int = 50,
    private val maxPages: Int = 9,
    pagePreloadCount: Int = 2,
    startPage: Int = 0,
    scope: CoroutineScope,
    transform: (pages: IntRange, items: List<T>) -> TransformedData<R>,
    private val fetch: suspend (offset: Int, pageSize: Int) -> List<T>,
) : BasePagerAdapter<T, R, Page.OffsetPage<T>>(
        startPage = startPage,
        pagePreloadCount = pagePreloadCount,
        scope = scope,
        transform = transform,
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
        withContext(Dispatchers.Main) {
            pages.value = currentPages

            _state.value = PagerState.Idle
        }
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

        withContext(Dispatchers.Main) {
            pages.value = currentPages

            _state.value = PagerState.Idle
        }
    }

    override suspend fun refresh(
        startPage: Int,
        pagePreloadCount: Int,
    ) {
        val currentPages = mutableListOf<Page.OffsetPage<T>>()

        _state.value = PagerState.Refresh

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

        withContext(Dispatchers.Main) {
            pages.value = currentPages

            _state.value = PagerState.Idle
        }
    }
}
