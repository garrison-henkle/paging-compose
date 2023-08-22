package dev.henkle.compose.paging

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
fun <T> PagedLazyColumn(
    modifier: Modifier,
    pageSize: Int = 25,
    maxPages: Int = 9,
    initialPagePreloadCount: Int = 2,
    startPage: Int = 0,
    loadThreshold: Int? = null,
    loadThresholdPercent: Float = 0.33f,
    loadingCirclesEnabled: Boolean = true,
    loadingCircleColor: Color = Color.Blue,
    loadingCircleSize: Dp = 48.dp,
    loadingCircleStrokeWidth: Dp = 3.dp,
    key: (T) -> Any,
    fetch: suspend (offset: Int, pageSize: Int) -> List<T>,
    content: @Composable LazyItemScope.(item: T) -> Unit,
){
    val pager = rememberPager(
        pageSize = pageSize,
        initialPagePreloadCount = initialPagePreloadCount,
        maxPages = maxPages,
        startPage = startPage,
        fetch = fetch,
    )
    val pagerData by pager.data.collectAsState()
    val pagerState by pager.state.collectAsState()
    val state = rememberLazyListState()

    LazyColumn(
        modifier = modifier,
        state = state,
    ) {
        item(key = PAGER_TOP_LOADING_CIRCLE_KEY){
            if(loadingCirclesEnabled && pagerState.shouldShowTopLoadingCircle){
                LoadingCircle(
                    color = loadingCircleColor,
                    size = loadingCircleSize,
                    strokeWidth = loadingCircleStrokeWidth,
                )
            }
        }

        items(items = pagerData, key = key, itemContent = content)

        item(key = PAGER_BOT_LOADING_CIRCLE_KEY){
            if(loadingCirclesEnabled && pagerState.shouldShowBottomLoadingCircle){
                LoadingCircle(
                    color = loadingCircleColor,
                    size = loadingCircleSize,
                    strokeWidth = loadingCircleStrokeWidth,
                )
            }
        }
    }

    //hacky workaround for a LazyColumn bug/weird behavior. When a new index 0 is inserted when the
    //LazyColumn is scrolled all the way up, it will jump up to the new index 0. This keeps the
    //current visible item in place.
    val density = LocalDensity.current
    LaunchedEffect(pagerData){
        var lastFirstItem = pagerData.firstOrNull()
        snapshotFlow { pagerData }
            .distinctUntilChanged()
            .map { it.firstOrNull() }
            .collect{ newFirstItem ->
                if(lastFirstItem != newFirstItem){
                    if(!state.canScrollBackward && lastFirstItem != null){
                        state.scrollToItem(
                            index = pageSize + 1,
                            scrollOffset = if(loadingCirclesEnabled) -loadingCircleSize.px(density) else 0
                        )
                    }
                    lastFirstItem = newFirstItem
                }
            }
    }

    registerForPagingEvents(
        pager = pager,
        state = state,
        loadThreshold = loadThreshold,
        loadThresholdPercent = loadThresholdPercent,
    )
}

@Composable
private fun LoadingCircle(color: Color, size: Dp, strokeWidth: Dp){
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ){
        CircularProgressIndicator(
            modifier = Modifier.size(size),
            color = color,
            strokeWidth = strokeWidth,
            strokeCap = StrokeCap.Round,
        )
    }
}

private const val PAGER_TOP_LOADING_CIRCLE_KEY = "pagerTopLoadingCircleKey"
private const val PAGER_BOT_LOADING_CIRCLE_KEY = "pagerBotLoadingCircleKey"

private fun Dp.px(density: Density): Int = with(density){ roundToPx() }

private val PagerAdapter.PagerState.shouldShowTopLoadingCircle: Boolean get() =
    this is PagerAdapter.PagerState.LoadingAtStart

private val PagerAdapter.PagerState.shouldShowBottomLoadingCircle: Boolean get() =
    this is PagerAdapter.PagerState.LoadingAtEnd || this is PagerAdapter.PagerState.InitialLoad
