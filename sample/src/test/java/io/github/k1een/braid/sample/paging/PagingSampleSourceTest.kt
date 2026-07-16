package io.github.k1een.braid.sample.paging

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PagingSampleSourceTest {

    @Test
    fun firstPage_hasExpectedItemsAndKeys() = runBlocking {
        val page = source().loadPage(refresh())

        assertEquals((1L..20L).toList(), page.data.map { it.testId })
        assertNull(page.prevKey)
        assertEquals(20, page.nextKey)
        assertEquals(0, page.itemsBefore)
        assertEquals(80, page.itemsAfter)
    }

    @Test
    fun nextPage_hasExpectedPreviousAndNextKeys() = runBlocking {
        val source = source()
        source.loadError(append(PagingSampleSource.DEMO_FAILURE_KEY))

        val page = source.loadPage(append(PagingSampleSource.DEMO_FAILURE_KEY))

        assertEquals((21L..40L).toList(), page.data.map { it.testId })
        assertEquals(0, page.prevKey)
        assertEquals(40, page.nextKey)
        assertEquals(20, page.itemsBefore)
        assertEquals(60, page.itemsAfter)
    }

    @Test
    fun refreshKey_usesPageClosestToAnchor() = runBlocking {
        val source = source()
        val firstPage = source.loadPage(refresh())
        source.loadError(append(PagingSampleSource.DEMO_FAILURE_KEY))
        val secondPage = source.loadPage(
            append(PagingSampleSource.DEMO_FAILURE_KEY),
        )
        val state = PagingState(
            pages = listOf(firstPage, secondPage),
            anchorPosition = 25,
            config = pagingConfig(),
            leadingPlaceholderCount = 0,
        )

        assertEquals(20, source.getRefreshKey(state))
        assertNull(
            source.getRefreshKey(
                PagingState(
                    pages = listOf(firstPage),
                    anchorPosition = null,
                    config = pagingConfig(),
                    leadingPlaceholderCount = 0,
                ),
            ),
        )
    }

    @Test
    fun pageContent_isDeterministicAndHeterogeneous() = runBlocking {
        val page = source().loadPage(refresh())
        val banners = page.data.filterIsInstance<PagingSampleItem.Banner>()
        val users = page.data.filterIsInstance<PagingSampleItem.User>()

        assertEquals(2, banners.size)
        assertEquals(listOf(10L, 20L), banners.map(PagingSampleItem.Banner::id))
        assertEquals(18, users.size)
        assertTrue(page.data.first() is PagingSampleItem.User)
        assertTrue(page.data[9] is PagingSampleItem.Banner)
    }

    @Test
    fun appendFailure_happensOnceAndRetrySucceeds() = runBlocking {
        val source = source()
        val params = append(PagingSampleSource.DEMO_FAILURE_KEY)

        val failure = source.loadError(params)
        val retryPage = source.loadPage(params)
        val followingPage = source.loadPage(append(40))

        assertTrue(failure is IOException)
        assertEquals("Simulated append failure.", failure.message)
        assertEquals(21L, retryPage.data.first().testId)
        assertEquals(41L, followingPage.data.first().testId)
    }

    @Test
    fun refreshAtDemoFailureKey_doesNotConsumeAppendFailure() = runBlocking {
        val source = source()

        val refreshPage = source.loadPage(
            refresh(key = PagingSampleSource.DEMO_FAILURE_KEY),
        )
        val appendFailure = source.loadError(
            append(PagingSampleSource.DEMO_FAILURE_KEY),
        )

        assertEquals(21L, refreshPage.data.first().testId)
        assertTrue(appendFailure is IOException)
    }

    @Test
    fun finalPage_hasNoNextKey() = runBlocking {
        val page = source().loadPage(append(key = 80))

        assertEquals((81L..100L).toList(), page.data.map { it.testId })
        assertEquals(60, page.prevKey)
        assertNull(page.nextKey)
        assertEquals(0, page.itemsAfter)
    }

    @Test
    fun largerRefreshLoadSize_keepsOffsetKeySemantics() = runBlocking {
        val source = source()
        val page = source.loadPage(refresh(loadSize = 60))
        val state = PagingState(
            pages = listOf(page),
            anchorPosition = 30,
            config = pagingConfig(),
            leadingPlaceholderCount = 0,
        )

        assertEquals(60, page.data.size)
        assertEquals(60, page.nextKey)
        assertEquals(0, source.getRefreshKey(state))
        assertFalse(page.data.isEmpty())
    }

    private fun source(): PagingSampleSource =
        PagingSampleSource(loadDelayMillis = 0L)

    private fun pagingConfig(): PagingConfig = PagingConfig(
        pageSize = PagingSampleSource.PAGE_SIZE,
        initialLoadSize = PagingSampleSource.PAGE_SIZE,
        enablePlaceholders = false,
    )

    private fun refresh(
        key: Int? = null,
        loadSize: Int = PagingSampleSource.PAGE_SIZE,
    ): PagingSource.LoadParams.Refresh<Int> = PagingSource.LoadParams.Refresh(
        key = key,
        loadSize = loadSize,
        placeholdersEnabled = false,
    )

    private fun append(
        key: Int,
    ): PagingSource.LoadParams.Append<Int> = PagingSource.LoadParams.Append(
        key = key,
        loadSize = PagingSampleSource.PAGE_SIZE,
        placeholdersEnabled = false,
    )

    private suspend fun PagingSampleSource.loadPage(
        params: PagingSource.LoadParams<Int>,
    ): PagingSource.LoadResult.Page<Int, PagingSampleItem> =
        when (val result = load(params)) {
            is PagingSource.LoadResult.Page -> result
            is PagingSource.LoadResult.Error -> {
                throw AssertionError("Expected Page, got Error", result.throwable)
            }
            is PagingSource.LoadResult.Invalid -> {
                throw AssertionError("Expected Page, got Invalid")
            }
        }

    private suspend fun PagingSampleSource.loadError(
        params: PagingSource.LoadParams<Int>,
    ): Throwable = when (val result = load(params)) {
        is PagingSource.LoadResult.Error -> result.throwable
        is PagingSource.LoadResult.Page -> {
            throw AssertionError("Expected Error, got Page")
        }
        is PagingSource.LoadResult.Invalid -> {
            throw AssertionError("Expected Error, got Invalid")
        }
    }

    private val PagingSampleItem.testId: Long
        get() = when (this) {
            is PagingSampleItem.Banner -> id
            is PagingSampleItem.User -> id
        }
}
