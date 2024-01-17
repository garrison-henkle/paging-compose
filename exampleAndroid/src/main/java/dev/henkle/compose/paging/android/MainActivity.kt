package dev.henkle.compose.paging.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.henkle.compose.paging.PagedLazyColumn
import dev.henkle.compose.paging.TransformedData
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val exampleData = List(10_000) { it.toString() }
        val pageSize = 15
        val maxPages = 3

        val transform: (
            pages: IntRange,
            data: List<String>,
        ) -> TransformedData<String> = transform@{ pages, items ->
            if (pages.isEmpty()) return@transform TransformedData(items = items)

            val data: MutableList<String> = items.toMutableList()
            val fullPages = data.size / pageSize
            val partialPage = data.size % pageSize != 0
            val sectionCount = if (partialPage) fullPages + 1 else fullPages - 1
            val lastSectionIndex = sectionCount * pageSize

            var currentPage = pages.last + 1
            for (index in lastSectionIndex downTo 0 step pageSize) {
                data.add(index, "Start of page $currentPage")
                currentPage -= 1
            }

            val sizeChange = sectionCount.coerceAtLeast(0)
            TransformedData(
                items = data,
                totalSizeChange = sizeChange,
                pageSizeChanges = List(size = sizeChange) { 1 },
            )
        }

        setContent {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(color = Color(0xffeaeaea)),
            ) {
                PagedLazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    loadingCirclesEnabled = true,
                    pageSize = pageSize,
                    maxPages = maxPages,
                    key = { it },
                    transform = transform,
                    fetch = { offset, pageSize ->
                        delay(500)
                        when {
                            offset >= exampleData.size -> emptyList()
                            offset + pageSize >= exampleData.size ->
                                exampleData.subList(offset, exampleData.size)
                            else -> exampleData.subList(offset, offset + pageSize)
                        }
                    },
                ) { text ->
                    Surface(
                        modifier =
                            Modifier
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                .fillMaxWidth(),
                        color = Color.White,
                        elevation = 2.dp,
                        shape = RoundedCornerShape(size = 10.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = text)
                        }
                    }
                }

                PagedLazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    loadingCirclesEnabled = true,
                    pageSize = pageSize,
                    maxPages = maxPages,
                    key = { it },
                    getID = { it },
                    transform = transform,
                    fetch = { lastID, pageSize ->
                        delay(500)
                        val index =
                            lastID?.let {
                                exampleData.indexOf(lastID)
                                    .takeIf { it != -1 }
                                    ?.let { it + 1 }
                            }
                        when {
                            index == null -> exampleData.subList(0, pageSize)
                            index >= exampleData.size -> emptyList()
                            index + pageSize >= exampleData.size ->
                                exampleData.subList(index, exampleData.size)
                            else -> exampleData.subList(index, index + pageSize)
                        }
                    },
                ) { text ->
                    Surface(
                        modifier =
                            Modifier
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                .fillMaxWidth(),
                        color = Color.White,
                        elevation = 2.dp,
                        shape = RoundedCornerShape(size = 10.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = text)
                        }
                    }
                }
            }
        }
    }
}
