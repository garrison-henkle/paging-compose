@file:Suppress("KDocUnresolvedReference")

package dev.henkle.compose.paging

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * A paging adapter for a datasource.
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
class PagerAdapter<T>(
    val pageSize: Int = 50,
    private val maxPages: Int = 9,
    pagePreloadCount: Int = 2,
    startPage: Int = 0,
    private val scope: CoroutineScope,
    val fetch: suspend (offset: Int, pageSize: Int) -> List<T>,
) {
    private val actorChannel = Channel<PagerAction>(capacity = 10)
    private val actor: SendChannel<PagerAction> = actorChannel

    private val pages = MutableStateFlow<List<Page<T>>>(emptyList())
    private val _data = MutableStateFlow<List<T>>(emptyList())
    val data = _data.asStateFlow()
    private val _state = MutableStateFlow<PagerState>(PagerState.Idle)
    val state = _state.asStateFlow()

    private fun startActor(){
        scope.launch {
            actorChannel.consumeAsFlow().collect{ action ->
                when(action){
                    is PagerAction.LoadPage -> {
                        when(action.location){
                            PageLocation.Start -> loadPageAtStart(page = action.page)
                            PageLocation.End -> loadPageAtEnd(page = action.page)
                        }
                    }
                    is PagerAction.InitialLoad -> initialLoad(
                        startPage = action.startPage,
                        pagePreloadCount = action.pagePreloadCount,
                    )
                }
            }
        }
    }

    fun startFlows(){
        scope.launch {
            pages
                .map { it.map { page -> page.data }.flatten() }
                .collect{ data -> _data.value = data }
        }
    }

    fun shutdown(){
        scope.cancel()
    }

    private suspend fun loadPageAtStart(page: Int){
        val currentPages = pages.value.toMutableList()

        val offset = page * pageSize
        if(currentPages.any{ it.offset == offset }) return

        _state.value = PagerState.LoadingAtStart

        val newPageData = fetch(offset, pageSize)
        currentPages.add(0, Page(page = page, offset = offset, size = pageSize, data = newPageData))
        if(currentPages.size >= maxPages){
            currentPages.removeLast()
        }
        pages.value = currentPages

        _state.value = PagerState.Idle
    }

    private suspend fun loadPageAtEnd(page: Int){
        val currentPages = pages.value.toMutableList()

        val offset = page * pageSize
        currentPages.lastOrNull()?.takeIf{ it.isFull } ?: return
        if(currentPages.any{ it.offset == offset }) return

        _state.value = PagerState.LoadingAtEnd

        val newPageData = fetch(offset, pageSize)
        currentPages += Page(page = page, offset = offset, size = pageSize, data = newPageData)
        if(currentPages.size >= maxPages){
            currentPages.removeFirst()
        }
        pages.value = currentPages

        _state.value = PagerState.Idle
    }

    private suspend fun initialLoad(startPage: Int, pagePreloadCount: Int){
        val currentPages = pages.value.toMutableList()

        _state.value = PagerState.InitialLoad

        var nextOffset = startPage * pageSize
        var newPageData = fetch(nextOffset, pageSize)
        var newPage = Page(page = startPage, offset = nextOffset, size = pageSize, data = newPageData)
        currentPages += newPage

        if(newPage.isFull){
            for(i in 1..pagePreloadCount){
                nextOffset = (startPage + i) * pageSize
                newPageData = fetch(nextOffset, pageSize)
                newPage = Page(page = i, offset = nextOffset, size = pageSize, data = newPageData)
                currentPages += newPage
                if(!newPage.isFull) break
            }
        }

        val previousPage = startPage - 1
        val lastPreviousPageToLoad = (startPage - pagePreloadCount - 1).coerceAtLeast(0)
        for(i in previousPage downTo lastPreviousPageToLoad){
            nextOffset = i * pageSize
            newPageData = fetch(nextOffset, pageSize)
            newPage = Page(page = i, offset = nextOffset, size = pageSize, data = newPageData)
            currentPages.add(0, newPage)
        }

        pages.value = currentPages

        _state.value = PagerState.Idle
    }

    init{
        startActor()
        scope.launch {
            actor.send(
                PagerAction.InitialLoad(
                    startPage = startPage,
                    pagePreloadCount = pagePreloadCount,
                )
            )
        }
    }

    suspend fun loadNext(){
        if(state.value.isLoading) return
        val currentLastPage = pages.value.lastOrNull()?.page
        val pageToLoad = currentLastPage?.let{ it + 1 } ?: 0
        actor.send(PagerAction.LoadPage(page = pageToLoad, location = PageLocation.End))
    }

    suspend fun loadPrevious(){
        if(state.value.isLoading) return
        val currentFirstPage = pages.value.firstOrNull()?.page
        val pageToLoad = currentFirstPage?.let{ it - 1 }?.coerceAtLeast(0) ?: 0
        actor.send(PagerAction.LoadPage(page = pageToLoad, location = PageLocation.Start))
    }

    sealed interface PagerAction{
        data class LoadPage(val page: Int, val location: PageLocation) : PagerAction
        data class InitialLoad(val startPage: Int, val pagePreloadCount: Int) : PagerAction
    }

    enum class PageLocation{
        Start,
        End;
    }

    sealed class PagerState{
        data object InitialLoad : PagerState()
        data object LoadingAtStart : PagerState()
        data object LoadingAtEnd : PagerState()
        data object Idle : PagerState()

        val isLoading: Boolean get() = this !is Idle
    }

    data class Page<T>(val page: Int, val offset: Int, val size: Int, val data: List<T>){
        val isFull: Boolean get() = size == data.size
    }
}
