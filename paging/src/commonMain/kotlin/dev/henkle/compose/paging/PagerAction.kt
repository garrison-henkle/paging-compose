package dev.henkle.compose.paging

sealed interface PagerAction{
    data class LoadPage(val page: Int, val location: PageLocation) : PagerAction
    data class InitialLoad(val startPage: Int, val pagePreloadCount: Int) : PagerAction
}
