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
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val exampleData = List(10_000) { it.toString() }

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
                    pageSize = 15,
                    maxPages = 5,
                    key = { it },
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
                    pageSize = 15,
                    maxPages = 3,
                    key = { it },
                    getID = { it },
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
