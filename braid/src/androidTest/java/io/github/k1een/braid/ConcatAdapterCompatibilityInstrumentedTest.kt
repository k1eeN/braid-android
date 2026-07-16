package io.github.k1een.braid

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConcatAdapterCompatibilityInstrumentedTest {

    @Test
    fun isolatedViewTypesRouteBindPayloadLifecycleAndRecycledHolder() {
        val fixture = concatFixture(firstFailedToRecycle = true)
        val items = listOf<ConcatItem>(
            FirstConcatItem(id = 1L),
            SecondConcatItem(id = 2L)
        )
        fixture.adapter.submitAndAwait(items)
        val concatAdapter = onMainThread {
            ConcatAdapter(
                ExternalHeaderAdapter(),
                fixture.adapter
            )
        }

        val viewTypes = onMainThread {
            ConcatViewTypes(
                header = concatAdapter.getItemViewType(0),
                first = concatAdapter.getItemViewType(1),
                second = concatAdapter.getItemViewType(2)
            )
        }
        val localSecondViewType = onMainThread {
            fixture.adapter.getItemViewType(1)
        }

        assertNotEquals(viewTypes.header, viewTypes.first)
        assertNotEquals(viewTypes.first, viewTypes.second)
        assertNotEquals(localSecondViewType, viewTypes.second)

        val secondHolder = onMainThread {
            concatAdapter.createViewHolder(parent(), viewTypes.second)
        }
        val payloads = mutableListOf<Any>(Any())

        onMainThread {
            concatAdapter.bindViewHolder(secondHolder, 2)
            // ConcatAdapter's inherited three-argument overload does not
            // forward payloads. Exercise Braid's payload path with the holder
            // whose visible view type was globalized by ConcatAdapter.
            fixture.adapter.onBindViewHolder(secondHolder, 1, payloads)
            concatAdapter.onViewAttachedToWindow(secondHolder)
            concatAdapter.onViewDetachedFromWindow(secondHolder)
            concatAdapter.onViewRecycled(secondHolder)

            // RecyclerView may reuse the same physical holder without calling create again.
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

        assertEquals("second", secondHolder.itemView.tag)
        assertEquals(1, fixture.secondDelegate.createCalls)
        assertEquals(3, fixture.secondDelegate.boundItems.size)
        assertTrue(fixture.secondDelegate.boundPayloads[0].isEmpty())
        assertSame(payloads, fixture.secondDelegate.boundPayloads[1])
        assertTrue(fixture.secondDelegate.boundPayloads[2].isEmpty())
        assertEquals(1, fixture.secondDelegate.attachedCalls)
        assertEquals(1, fixture.secondDelegate.detachedCalls)
        assertEquals(2, fixture.secondDelegate.recycledCalls)
        assertTrue(failedToRecycle)
        assertEquals(1, fixture.firstDelegate.failedToRecycleCalls)
    }

    @Test
    fun recreatedConcatAdapterCanAllocateGlobalTypesInAnotherOrder() {
        val fixture = concatFixture()
        val secondItem = SecondConcatItem(id = 2L)
        fixture.adapter.submitAndAwait(
            listOf(
                FirstConcatItem(id = 1L),
                secondItem
            )
        )
        val headerFirstConcat = onMainThread {
            ConcatAdapter(
                ExternalHeaderAdapter(),
                fixture.adapter
            )
        }

        val headerFirstSecondViewType = onMainThread {
            headerFirstConcat.getItemViewType(0)
            headerFirstConcat.getItemViewType(1)
            headerFirstConcat.getItemViewType(2)
        }
        val headerFirstHolder = onMainThread {
            headerFirstConcat.createViewHolder(parent(), headerFirstSecondViewType)
        }
        onMainThread {
            headerFirstConcat.bindViewHolder(headerFirstHolder, 2)
            headerFirstConcat.onViewRecycled(headerFirstHolder)
        }

        val braidFirstConcat = onMainThread {
            ConcatAdapter(
                ExternalHeaderAdapter(),
                fixture.adapter
            )
        }

        // Resolve the second Braid delegate first. The isolated global type is
        // then 0 even though its local Braid type is 1.
        val braidFirstSecondViewType = onMainThread {
            braidFirstConcat.getItemViewType(2)
        }
        val localSecondViewType = onMainThread {
            fixture.adapter.getItemViewType(1)
        }
        assertNotEquals(localSecondViewType, headerFirstSecondViewType)
        assertNotEquals(localSecondViewType, braidFirstSecondViewType)
        assertNotEquals(headerFirstSecondViewType, braidFirstSecondViewType)

        val braidFirstHolder = onMainThread {
            braidFirstConcat.createViewHolder(parent(), braidFirstSecondViewType)
        }
        onMainThread {
            braidFirstConcat.bindViewHolder(braidFirstHolder, 2)
            braidFirstConcat.onViewRecycled(braidFirstHolder)
        }

        assertEquals(listOf(secondItem, secondItem), fixture.secondDelegate.boundItems)
        assertEquals(2, fixture.secondDelegate.createCalls)
        assertEquals(2, fixture.secondDelegate.recycledCalls)
        assertTrue(fixture.firstDelegate.boundItems.isEmpty())
    }
}

private sealed interface ConcatItem

private data class FirstConcatItem(val id: Long) : ConcatItem

private data class SecondConcatItem(val id: Long) : ConcatItem

private class ConcatHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

private abstract class TrackingConcatDelegate<Item : ConcatItem>(
    private val kind: String,
    private val failedToRecycleResult: Boolean
) : AdapterDelegate<ConcatItem, Item, ConcatHolder>() {

    var createCalls: Int = 0
    val boundItems: MutableList<Item> = mutableListOf()
    val boundPayloads: MutableList<List<Any>> = mutableListOf()
    var recycledCalls: Int = 0
    var attachedCalls: Int = 0
    var detachedCalls: Int = 0
    var failedToRecycleCalls: Int = 0

    override fun createViewHolder(parent: ViewGroup): ConcatHolder {
        createCalls += 1
        return ConcatHolder(
            itemView = View(parent.context).apply {
                tag = kind
            }
        )
    }

    override fun bindViewHolder(holder: ConcatHolder, item: Item, payloads: List<Any>) {
        boundItems += item
        boundPayloads += payloads
    }

    override fun onViewRecycled(holder: ConcatHolder) {
        recycledCalls += 1
    }

    override fun onViewAttachedToWindow(holder: ConcatHolder) {
        attachedCalls += 1
    }

    override fun onViewDetachedFromWindow(holder: ConcatHolder) {
        detachedCalls += 1
    }

    override fun onFailedToRecycleView(holder: ConcatHolder): Boolean {
        failedToRecycleCalls += 1
        return failedToRecycleResult
    }
}

private class FirstConcatDelegate(failedToRecycleResult: Boolean = false) :
    TrackingConcatDelegate<FirstConcatItem>(
        kind = "first",
        failedToRecycleResult = failedToRecycleResult
    ) {

    override fun isForItem(item: ConcatItem): Boolean = item is FirstConcatItem

    override fun areItemsTheSame(oldItem: FirstConcatItem, newItem: FirstConcatItem): Boolean = oldItem.id == newItem.id
}

private class SecondConcatDelegate :
    TrackingConcatDelegate<SecondConcatItem>(
        kind = "second",
        failedToRecycleResult = false
    ) {

    override fun isForItem(item: ConcatItem): Boolean = item is SecondConcatItem

    override fun areItemsTheSame(oldItem: SecondConcatItem, newItem: SecondConcatItem): Boolean =
        oldItem.id == newItem.id
}

private data class ConcatFixture(
    val adapter: BraidListAdapter<ConcatItem>,
    val firstDelegate: FirstConcatDelegate,
    val secondDelegate: SecondConcatDelegate
)

private data class ConcatViewTypes(val header: Int, val first: Int, val second: Int)

private fun concatFixture(firstFailedToRecycle: Boolean = false): ConcatFixture {
    val firstDelegate = FirstConcatDelegate(firstFailedToRecycle)
    val secondDelegate = SecondConcatDelegate()
    return ConcatFixture(
        adapter = onMainThread {
            braidListAdapter(
                firstDelegate,
                secondDelegate
            )
        },
        firstDelegate = firstDelegate,
        secondDelegate = secondDelegate
    )
}

private class ExternalHeaderAdapter : RecyclerView.Adapter<ExternalHeaderHolder>() {

    override fun getItemCount(): Int = 1

    override fun getItemViewType(position: Int): Int = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExternalHeaderHolder =
        ExternalHeaderHolder(View(parent.context))

    override fun onBindViewHolder(holder: ExternalHeaderHolder, position: Int): Unit = Unit
}

private class ExternalHeaderHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

private fun BraidListAdapter<ConcatItem>.submitAndAwait(items: List<ConcatItem>) {
    val committed = CountDownLatch(1)

    onMainThread {
        submitList(items) {
            committed.countDown()
        }
    }

    assertTrue(
        "ListAdapter did not commit its list.",
        committed.await(10L, TimeUnit.SECONDS)
    )
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
}

private fun parent(): ViewGroup = FrameLayout(
    InstrumentationRegistry.getInstrumentation().targetContext
)

private fun <Result> onMainThread(block: () -> Result): Result {
    val task = FutureTask(Callable(block))
    InstrumentationRegistry.getInstrumentation().runOnMainSync(task)
    return task.get()
}
