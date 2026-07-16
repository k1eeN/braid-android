package io.github.k1een.braid.paging

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.k1een.braid.AdapterDelegate
import io.github.k1een.braid.braidDelegateRegistry
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

private fun pagingFixture(
    firstFailedToRecycle: Boolean = false,
): PagingFixture {
    val firstDelegate = FirstPagingDelegate(firstFailedToRecycle)
    val secondDelegate = SecondPagingDelegate()
    val registry = braidDelegateRegistry<PagingInstrumentedItem>(
        firstDelegate,
        secondDelegate,
    )

    return PagingFixture(
        adapter = onMainThread { BraidPagingDataAdapter(registry) },
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
