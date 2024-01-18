package dev.henkle.compose.paging

class PagedLazyColumnController<T> {
    private var pager: PagerAdapter<T>? = null

    internal fun bind(pager: PagerAdapter<T>) {
        this.pager = pager
    }

    internal fun unbind() {
        this.pager = null
    }

    fun reset() {
        pager?.reset()
    }

    fun shutdown() {
        pager?.shutdown()
    }

    fun restart() {
        pager?.startFlows()
    }

    suspend fun loadNext() {
        pager?.loadNext()
    }

    suspend fun loadPrevious() {
        pager?.loadPrevious()
    }
}
