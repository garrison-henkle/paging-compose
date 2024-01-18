package dev.henkle.compose.paging

sealed interface Page<T> {
    val page: Int
    val size: Int
    val data: List<T>
    val isFull: Boolean get() = size == data.size

    data class OffsetPage<T>(
        override val page: Int,
        val offset: Int,
        override val size: Int,
        override val data: List<T>,
    ) : Page<T>

    data class IDPage<T, ID>(
        override val page: Int,
        val previousPageLastID: ID?,
        override val size: Int,
        override val data: List<T>,
    ) : Page<T>
}
