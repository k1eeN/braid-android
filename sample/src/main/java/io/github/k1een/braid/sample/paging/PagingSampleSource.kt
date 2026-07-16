package io.github.k1een.braid.sample.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay

internal class PagingSampleSource(
    private val loadDelayMillis: Long = DEFAULT_LOAD_DELAY_MILLIS,
) : PagingSource<Int, PagingSampleItem>() {

    private val didEmitDemoFailure = AtomicBoolean(false)
    private val items = createItems()

    override suspend fun load(
        params: LoadParams<Int>,
    ): LoadResult<Int, PagingSampleItem> {
        val startIndex = params.key ?: 0
        if (startIndex !in 0..items.size) {
            return LoadResult.Error(
                IllegalArgumentException(
                    "Paging key must be between 0 and ${items.size}: $startIndex",
                ),
            )
        }

        if (loadDelayMillis > 0L) {
            delay(loadDelayMillis)
        }

        if (
            params is LoadParams.Append &&
            startIndex == DEMO_FAILURE_KEY &&
            didEmitDemoFailure.compareAndSet(false, true)
        ) {
            return LoadResult.Error(
                IOException("Simulated append failure."),
            )
        }

        val endIndex = (startIndex + params.loadSize).coerceAtMost(items.size)
        return LoadResult.Page(
            data = items.subList(startIndex, endIndex),
            prevKey = if (startIndex == 0) {
                null
            } else {
                (startIndex - PAGE_SIZE).coerceAtLeast(0)
            },
            nextKey = endIndex.takeIf { it < items.size },
            itemsBefore = startIndex,
            itemsAfter = items.size - endIndex,
        )
    }

    override fun getRefreshKey(
        state: PagingState<Int, PagingSampleItem>,
    ): Int? {
        val anchorPosition = state.anchorPosition ?: return null
        val anchorPage = state.closestPageToPosition(anchorPosition) ?: return null

        return anchorPage.prevKey?.plus(PAGE_SIZE)
            ?: anchorPage.nextKey?.minus(anchorPage.data.size)
    }

    private fun createItems(): List<PagingSampleItem> =
        List(TOTAL_ITEM_COUNT) { index ->
            val itemNumber = index + 1
            if (itemNumber % BANNER_INTERVAL == 0) {
                PagingSampleItem.Banner(
                    id = itemNumber.toLong(),
                    title = "Featured collection ${itemNumber / BANNER_INTERVAL}",
                )
            } else {
                PagingSampleItem.User(
                    id = itemNumber.toLong(),
                    name = "User $itemNumber",
                    description = "Deterministic in-memory item #$itemNumber",
                )
            }
        }

    internal companion object {
        const val PAGE_SIZE: Int = 20
        const val TOTAL_ITEM_COUNT: Int = 100
        const val DEMO_FAILURE_KEY: Int = PAGE_SIZE

        private const val BANNER_INTERVAL: Int = 10
        private const val DEFAULT_LOAD_DELAY_MILLIS: Long = 300L
    }
}
