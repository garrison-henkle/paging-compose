# Paging Compose
A simple paging library for Jetpack Compose and Compose Multiplatform. This was hacked together at
10pm, so expect small bugs :).

Example usage:
```kotlin
PagedLazyColumn(
    modifier = Modifier,
    pageSize = 15,
    maxPages = 5,
    key = { it },
    fetch = { offset, pageSize -> repo.get(offset = offset, count = pageSize) },
){ item ->
    Text(text = item.toString())
}
```

Other than `PagedLazyColumn`, there are also some lower-level tools that provide lower-level access
to the paging implementation. These include:
- `PagerAdapter` - The paging implementation class
- `rememberPager()` - A utility function to create a `PagerAdapter` in a Composable
- `registerForPagingEvents()` - A Composable function that can be used to notify a pager of load
events given a `LazyListState`

For custom implementations, [this hack](https://github.com/garrison-henkle/paging-compose/blob/e0360e61b88884e016bad997334b424ce841c845/shared/src/commonMain/kotlin/dev/henkle/compose/paging/PagedLazyColumn.kt#L86) might be a useful
reference for avoiding jumps when loading pages at the beginning of the page list.

# License
Copyright (c) 2023 Garrison Henkle

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
