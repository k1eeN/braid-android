package io.github.k1een.braid.paging

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.paging.LoadState
import androidx.paging.LoadStateAdapter
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.k1een.braid.AdapterDelegate
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BraidPagingDataAdapterInstrumentedTest {

    @Test
    fun varargAndDslFactoriesCreatePagingAdapters() = onMainThread {
        val varargAdapter = braidPagingDataAdapter<PagingInstrumentedItem>(
            FirstPagingDelegate(),
            SecondPagingDelegate(),
        )
        val dslAdapter = braidPagingDataAdapter<PagingInstrumentedItem> {
            delegate(FirstPagingDelegate())
            delegate(SecondPagingDelegate())
        }

        assertEquals(0, varargAdapter.itemCount)
        assertEquals(0, dslAdapter.itemCount)
    }

    @Test
    fun pagingPresentationRoutesHeterogeneousItemsAndExposesSnapshot() {
        val fixture = pagingFixture()
        val items = listOf<PagingInstrumentedItem>(
            FirstPagingItem(id = 1L, value = "First"),
            SecondPagingItem(id = 2L, value = "Second"),
        )
        val owner = fixture.adapter.submitAndAwait(items)

        try {
            assertEquals(2, onMainThread { fixture.adapter.itemCount })
            assertEquals(items, onMainThread { fixture.adapter.snapshot().items })

            val firstViewType = onMainThread {
                fixture.adapter.getItemViewType(0)
            }
            val secondViewType = onMainThread {
                fixture.adapter.getItemViewType(1)
            }
            val firstHolder = onMainThread {
                fixture.adapter.createViewHolder(parent(), firstViewType)
            }
            val secondHolder = onMainThread {
                fixture.adapter.createViewHolder(parent(), secondViewType)
            }

            onMainThread {
                fixture.adapter.onBindViewHolder(firstHolder, 0)
                fixture.adapter.onBindViewHolder(secondHolder, 1)
            }

            assertEquals(0, firstViewType)
            assertEquals(1, secondViewType)
            assertEquals("first", firstHolder.itemView.tag)
            assertEquals("second", secondHolder.itemView.tag)
            assertEquals(1, fixture.firstDelegate.createCalls)
            assertEquals(1, fixture.secondDelegate.createCalls)
            assertEquals(listOf(items[0]), fixture.firstDelegate.boundItems)
            assertEquals(listOf(items[1]), fixture.secondDelegate.boundItems)
        } finally {
            owner.destroyOnMainThread()
        }
    }

    @Test
    fun bindPayloadAndLifecycleCallbacksAreRoutedWithoutMixing() {
        val fixture = pagingFixture(firstFailedToRecycle = true)
        val item = FirstPagingItem(id = 1L, value = "First")
        val owner = fixture.adapter.submitAndAwait(listOf(item))

        try {
            val viewType = onMainThread {
                fixture.adapter.getItemViewType(0)
            }
            val holder = onMainThread {
                fixture.adapter.createViewHolder(parent(), viewType)
            }
            val payloads = mutableListOf<Any>(Any())

            onMainThread {
                fixture.adapter.onBindViewHolder(holder, 0)
                fixture.adapter.onBindViewHolder(
                    holder,
                    0,
                    mutableListOf(),
                )
                fixture.adapter.onBindViewHolder(holder, 0, payloads)
                fixture.adapter.onViewAttachedToWindow(holder)
                fixture.adapter.onViewDetachedFromWindow(holder)
            }
            val failedToRecycle = onMainThread {
                fixture.adapter.onFailedToRecycleView(holder)
            }
            onMainThread {
                fixture.adapter.onViewRecycled(holder)
            }

            assertEquals(3, fixture.firstDelegate.boundItems.size)
            assertTrue(fixture.firstDelegate.boundPayloads[0].isEmpty())
            assertTrue(fixture.firstDelegate.boundPayloads[1].isEmpty())
            assertSame(payloads, fixture.firstDelegate.boundPayloads[2])
            assertEquals(1, fixture.firstDelegate.attachedCalls)
            assertEquals(1, fixture.firstDelegate.detachedCalls)
            assertEquals(1, fixture.firstDelegate.failedToRecycleCalls)
            assertEquals(1, fixture.firstDelegate.recycledCalls)
            assertTrue(failedToRecycle)
            assertTrue(fixture.secondDelegate.boundItems.isEmpty())
        } finally {
            owner.destroyOnMainThread()
        }
    }

    @Test
    fun standardLoadStateFlowReachesNotLoadingAfterPresentation() {
        val fixture = pagingFixture()
        val (owner, loadStates) = runBlocking {
            val loadStates = async(
                context = Dispatchers.Default,
                start = CoroutineStart.UNDISPATCHED,
            ) {
                withTimeout(10_000L) {
                    fixture.adapter.loadStateFlow.first { states ->
                        val refresh = states.source.refresh
                        refresh is LoadState.NotLoading &&
                            refresh.endOfPaginationReached
                    }
                }
            }
            val owner = fixture.adapter.submitAndAwait(
                listOf(FirstPagingItem(id = 1L, value = "First")),
            )

            owner to loadStates.await()
        }

        try {
            val refresh = loadStates.source.refresh
            assertTrue(
                refresh is LoadState.NotLoading &&
                    refresh.endOfPaginationReached,
            )
            assertFalse(onMainThread { fixture.adapter.snapshot().isEmpty() })
        } finally {
            owner.destroyOnMainThread()
        }
    }

    @Test
    fun loadStateCompositionRoutesBindPayloadLifecycleAndRecycledHolder() {
        val fixture = pagingFixture(firstFailedToRecycle = true)
        val items = listOf<PagingInstrumentedItem>(
            FirstPagingItem(id = 1L, value = "First"),
            SecondPagingItem(id = 2L, value = "Second"),
        )
        val owner = fixture.adapter.submitAndAwait(items)

        try {
            val concatAdapter = onMainThread {
                fixture.adapter.withLoadStateHeaderAndFooter(
                    header = AlwaysVisibleLoadStateAdapter("header"),
                    footer = AlwaysVisibleLoadStateAdapter("footer"),
                )
            }
            val viewTypes = onMainThread {
                PagingConcatViewTypes(
                    header = concatAdapter.getItemViewType(0),
                    first = concatAdapter.getItemViewType(1),
                    second = concatAdapter.getItemViewType(2),
                    footer = concatAdapter.getItemViewType(3),
                )
            }
            val localFirstViewType = onMainThread {
                fixture.adapter.getItemViewType(0)
            }
            val localSecondViewType = onMainThread {
                fixture.adapter.getItemViewType(1)
            }

            assertNotEquals(localFirstViewType, viewTypes.first)
            assertNotEquals(localSecondViewType, viewTypes.second)
            assertNotEquals(viewTypes.header, viewTypes.footer)

            val firstHolder = onMainThread {
                concatAdapter.createViewHolder(parent(), viewTypes.first)
            }
            val secondHolder = onMainThread {
                concatAdapter.createViewHolder(parent(), viewTypes.second)
            }
            val payloads = mutableListOf<Any>(Any())

            onMainThread {
                concatAdapter.bindViewHolder(firstHolder, 1)
                concatAdapter.bindViewHolder(secondHolder, 2)
                // ConcatAdapter's inherited three-argument overload does not
                // forward payloads. Exercise the Paging child path with the
                // holder whose visible view type was globalized by ConcatAdapter.
                fixture.adapter.onBindViewHolder(secondHolder, 1, payloads)
                concatAdapter.onViewAttachedToWindow(secondHolder)
                concatAdapter.onViewDetachedFromWindow(secondHolder)
                concatAdapter.onViewRecycled(firstHolder)
                concatAdapter.onViewRecycled(secondHolder)

                // A recycled holder retains the delegate that physically created it.
                concatAdapter.bindViewHolder(secondHolder, 2)
                concatAdapter.onViewRecycled(secondHolder)
            }

            val failedHolder = onMainThread {
                concatAdapter.createViewHolder(parent(), viewTypes.first)
            }
            val failedToRecycle = onMainThread {
                concatAdapter.bindViewHolder(failedHolder, 1)
                concatAdapter.onFailedToRecycleView(failedHolder)
            }

            assertEquals(2, fixture.firstDelegate.boundItems.size)
            assertEquals(1, fixture.firstDelegate.recycledCalls)
            assertEquals(1, fixture.firstDelegate.failedToRecycleCalls)
            assertTrue(failedToRecycle)
            assertEquals(3, fixture.secondDelegate.boundItems.size)
            assertTrue(fixture.secondDelegate.boundPayloads[0].isEmpty())
            assertSame(payloads, fixture.secondDelegate.boundPayloads[1])
            assertTrue(fixture.secondDelegate.boundPayloads[2].isEmpty())
            assertEquals(1, fixture.secondDelegate.attachedCalls)
            assertEquals(1, fixture.secondDelegate.detachedCalls)
            assertEquals(2, fixture.secondDelegate.recycledCalls)
        } finally {
            owner.destroyOnMainThread()
        }
    }

    @Test
    fun recreatedLoadStateCompositionCanAllocateGlobalTypesInAnotherOrder() {
        val fixture = pagingFixture()
        val secondItem = SecondPagingItem(id = 2L, value = "Second")
        val owner = fixture.adapter.submitAndAwait(
            listOf(
                FirstPagingItem(id = 1L, value = "First"),
                secondItem,
            ),
        )

        try {
            val headerFirstConcat = onMainThread {
                fixture.adapter.withLoadStateHeaderAndFooter(
                    header = AlwaysVisibleLoadStateAdapter("header"),
                    footer = AlwaysVisibleLoadStateAdapter("footer"),
                )
            }

            val headerFirstSecondViewType = onMainThread {
                headerFirstConcat.getItemViewType(0)
                headerFirstConcat.getItemViewType(1)
                headerFirstConcat.getItemViewType(2)
            }
            val headerFirstHolder = onMainThread {
                headerFirstConcat.createViewHolder(
                    parent(),
                    headerFirstSecondViewType,
                )
            }
            onMainThread {
                headerFirstConcat.bindViewHolder(headerFirstHolder, 2)
                headerFirstConcat.onViewRecycled(headerFirstHolder)
            }

            val pagingFirstConcat = onMainThread {
                fixture.adapter.withLoadStateHeaderAndFooter(
                    header = AlwaysVisibleLoadStateAdapter("header"),
                    footer = AlwaysVisibleLoadStateAdapter("footer"),
                )
            }

            // Resolve the second Braid type before either load-state adapter.
            val pagingFirstSecondViewType = onMainThread {
                pagingFirstConcat.getItemViewType(2)
            }
            val localSecondViewType = onMainThread {
                fixture.adapter.getItemViewType(1)
            }
            assertNotEquals(localSecondViewType, headerFirstSecondViewType)
            assertNotEquals(localSecondViewType, pagingFirstSecondViewType)
            assertNotEquals(headerFirstSecondViewType, pagingFirstSecondViewType)

            val pagingFirstHolder = onMainThread {
                pagingFirstConcat.createViewHolder(
                    parent(),
                    pagingFirstSecondViewType,
                )
            }
            onMainThread {
                pagingFirstConcat.bindViewHolder(pagingFirstHolder, 2)
                pagingFirstConcat.onViewRecycled(pagingFirstHolder)
            }

            assertEquals(listOf(secondItem, secondItem), fixture.secondDelegate.boundItems)
            assertEquals(2, fixture.secondDelegate.createCalls)
            assertEquals(2, fixture.secondDelegate.recycledCalls)
            assertTrue(fixture.firstDelegate.boundItems.isEmpty())
        } finally {
            owner.destroyOnMainThread()
        }
    }
}

private sealed interface PagingInstrumentedItem

private data class FirstPagingItem(
    val id: Long,
    val value: String,
) : PagingInstrumentedItem

private data class SecondPagingItem(
    val id: Long,
    val value: String,
) : PagingInstrumentedItem

private class PagingInstrumentedHolder(
    itemView: View,
) : RecyclerView.ViewHolder(itemView)

private abstract class TrackingPagingDelegate<Item : PagingInstrumentedItem>(
    private val kind: String,
    private val failedToRecycleResult: Boolean,
) : AdapterDelegate<
    PagingInstrumentedItem,
    Item,
    PagingInstrumentedHolder,
>() {

    var createCalls: Int = 0
    val boundItems: MutableList<Item> = mutableListOf()
    val boundPayloads: MutableList<List<Any>> = mutableListOf()
    var recycledCalls: Int = 0
    var attachedCalls: Int = 0
    var detachedCalls: Int = 0
    var failedToRecycleCalls: Int = 0

    override fun createViewHolder(parent: ViewGroup): PagingInstrumentedHolder {
        createCalls += 1
        val itemView = View(parent.context).apply {
            tag = kind
        }
        return PagingInstrumentedHolder(
            itemView = itemView,
        )
    }

    override fun bindViewHolder(
        holder: PagingInstrumentedHolder,
        item: Item,
        payloads: List<Any>,
    ) {
        boundItems += item
        boundPayloads += payloads
    }

    override fun onViewRecycled(holder: PagingInstrumentedHolder) {
        recycledCalls += 1
    }

    override fun onViewAttachedToWindow(holder: PagingInstrumentedHolder) {
        attachedCalls += 1
    }

    override fun onViewDetachedFromWindow(holder: PagingInstrumentedHolder) {
        detachedCalls += 1
    }

    override fun onFailedToRecycleView(
        holder: PagingInstrumentedHolder,
    ): Boolean {
        failedToRecycleCalls += 1
        return failedToRecycleResult
    }
}

private class FirstPagingDelegate(
    failedToRecycleResult: Boolean = false,
) : TrackingPagingDelegate<FirstPagingItem>(
    kind = "first",
    failedToRecycleResult = failedToRecycleResult,
) {

    override fun isForItem(item: PagingInstrumentedItem): Boolean =
        item is FirstPagingItem

    override fun areItemsTheSame(
        oldItem: FirstPagingItem,
        newItem: FirstPagingItem,
    ): Boolean = oldItem.id == newItem.id
}

private class SecondPagingDelegate :
    TrackingPagingDelegate<SecondPagingItem>(
        kind = "second",
        failedToRecycleResult = false,
    ) {

    override fun isForItem(item: PagingInstrumentedItem): Boolean =
        item is SecondPagingItem

    override fun areItemsTheSame(
        oldItem: SecondPagingItem,
        newItem: SecondPagingItem,
    ): Boolean = oldItem.id == newItem.id
}

private data class PagingFixture(
    val adapter: BraidPagingDataAdapter<PagingInstrumentedItem>,
    val firstDelegate: FirstPagingDelegate,
    val secondDelegate: SecondPagingDelegate,
)

private data class PagingConcatViewTypes(
    val header: Int,
    val first: Int,
    val second: Int,
    val footer: Int,
)

private class PagingLoadStateHolder(itemView: View) :
    RecyclerView.ViewHolder(itemView)

private class AlwaysVisibleLoadStateAdapter(
    private val kind: String,
) : LoadStateAdapter<PagingLoadStateHolder>() {

    override fun displayLoadStateAsItem(loadState: LoadState): Boolean = true

    override fun onCreateViewHolder(
        parent: ViewGroup,
        loadState: LoadState,
    ): PagingLoadStateHolder = PagingLoadStateHolder(
        View(parent.context).apply {
            tag = kind
        },
    )

    override fun onBindViewHolder(
        holder: PagingLoadStateHolder,
        loadState: LoadState,
    ): Unit = Unit
}

private fun pagingFixture(
    firstFailedToRecycle: Boolean = false,
): PagingFixture {
    val firstDelegate = FirstPagingDelegate(firstFailedToRecycle)
    val secondDelegate = SecondPagingDelegate()
    return PagingFixture(
        adapter = onMainThread {
            braidPagingDataAdapter(
                firstDelegate,
                secondDelegate,
            )
        },
        firstDelegate = firstDelegate,
        secondDelegate = secondDelegate,
    )
}

private class PagingTestLifecycleOwner : LifecycleOwner {

    private val registry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle
        get() = registry

    fun resume() {
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}

private fun BraidPagingDataAdapter<PagingInstrumentedItem>.submitAndAwait(
    items: List<PagingInstrumentedItem>,
): PagingTestLifecycleOwner {
    val owner = onMainThread { PagingTestLifecycleOwner() }
    val updated = CountDownLatch(1)
    val listener: () -> Unit = { updated.countDown() }

    onMainThread {
        addOnPagesUpdatedListener(listener)
        owner.resume()
        submitData(
            owner.lifecycle,
            PagingData.from(
                data = items,
                sourceLoadStates = LoadStates(
                    refresh = LoadState.NotLoading(
                        endOfPaginationReached = true,
                    ),
                    prepend = LoadState.NotLoading(
                        endOfPaginationReached = true,
                    ),
                    append = LoadState.NotLoading(
                        endOfPaginationReached = true,
                    ),
                ),
            ),
        )
    }

    try {
        assertTrue(
            "PagingData was not presented.",
            updated.await(10L, TimeUnit.SECONDS),
        )
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    } finally {
        onMainThread {
            removeOnPagesUpdatedListener(listener)
        }
    }

    return owner
}

private fun PagingTestLifecycleOwner.destroyOnMainThread() {
    onMainThread {
        destroy()
    }
}

private fun parent(): ViewGroup = FrameLayout(
    InstrumentationRegistry.getInstrumentation().targetContext,
)

private fun <Result> onMainThread(block: () -> Result): Result {
    val task = FutureTask(Callable(block))
    InstrumentationRegistry.getInstrumentation().runOnMainSync(task)
    return task.get()
}
