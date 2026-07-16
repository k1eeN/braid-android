package io.github.k1een.braid

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewbinding.ViewBinding
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BraidListAdapterFactoryInstrumentedTest {

    @Test
    fun inferredFactoryCreatesBraidListAdapterAndRoutesOneDelegate() {
        val boundItems = mutableListOf<FactoryItem>()
        val adapter: BraidListAdapter<FactoryItem> = onMainThread {
            braidListAdapter(
                inflate = FactoryBinding::inflate,
                keySelector = FactoryItem::id,
            ) { item ->
                label.text = item.title
                boundItems += item
            }
        }
        val items = listOf(
            FactoryItem(id = 1L, title = "First"),
            FactoryItem(id = 2L, title = "Second"),
        )
        adapter.submitAndAwait(items)

        assertEquals(
            listOf(0, 0),
            onMainThread {
                items.indices.map(adapter::getItemViewType)
            },
        )

        val holder = onMainThread {
            adapter.onCreateViewHolder(parent(), adapter.getItemViewType(0))
        }
        onMainThread {
            adapter.onBindViewHolder(holder, 0)
        }

        assertEquals(listOf(items.first()), boundItems)
        assertEquals("First", (holder.itemView as TextView).text.toString())
    }

    @Test
    fun payloadBinderReceivesTheOriginalPayloadList() {
        var fullBindCalls = 0
        var receivedPayloads: List<Any>? = null
        val adapter = onMainThread {
            braidListAdapter(
                inflate = FactoryBinding::inflate,
                keySelector = FactoryItem::id,
                bindPayloads = { _, payloads ->
                    receivedPayloads = payloads
                },
            ) { _ ->
                fullBindCalls += 1
            }
        }
        adapter.submitAndAwait(listOf(FactoryItem(id = 1L, title = "Item")))
        val holder = onMainThread {
            adapter.onCreateViewHolder(parent(), adapter.getItemViewType(0))
        }
        val payloads = mutableListOf<Any>(Any())

        onMainThread {
            adapter.onBindViewHolder(holder, 0, payloads)
        }

        assertEquals(0, fullBindCalls)
        assertSame(payloads, receivedPayloads)
    }

    @Test
    fun nonEmptyPayloadFallsBackToFullBindWithoutPayloadBinder() {
        var fullBindCalls = 0
        val adapter = onMainThread {
            braidListAdapter(
                inflate = FactoryBinding::inflate,
                keySelector = FactoryItem::id,
            ) { _ ->
                fullBindCalls += 1
            }
        }
        adapter.submitAndAwait(listOf(FactoryItem(id = 1L, title = "Item")))
        val holder = onMainThread {
            adapter.onCreateViewHolder(parent(), adapter.getItemViewType(0))
        }

        onMainThread {
            adapter.onBindViewHolder(holder, 0, mutableListOf(Any()))
        }

        assertEquals(1, fullBindCalls)
    }

    @Test
    fun keySelectorControlsIdentityAndDefaultContentsUseStructuralEquality() {
        val adapter = onMainThread {
            braidListAdapter(
                inflate = FactoryBinding::inflate,
                keySelector = FactoryItem::id,
            ) { }
        }
        val observer = TrackingObserver()
        adapter.submitAndAwait(listOf(FactoryItem(id = 1L, title = "Old")))
        onMainThread { adapter.registerAdapterDataObserver(observer) }

        adapter.submitAndAwait(listOf(FactoryItem(id = 1L, title = "Old")))
        assertEquals(0, observer.totalEvents)

        adapter.submitAndAwait(listOf(FactoryItem(id = 1L, title = "New")))
        assertEquals(1, observer.changedItems)
        assertEquals(0, observer.insertedItems)
        assertEquals(0, observer.removedItems)

        observer.reset()
        adapter.submitAndAwait(listOf(FactoryItem(id = 2L, title = "New")))
        assertEquals(0, observer.changedItems)
        assertEquals(1, observer.insertedItems)
        assertEquals(1, observer.removedItems)
    }

    @Test
    fun customContentsComparatorIsUsed() {
        var comparisonCalls = 0
        val adapter = onMainThread {
            braidListAdapter(
                inflate = FactoryBinding::inflate,
                keySelector = FactoryItem::id,
                areContentsTheSame = { _, _ ->
                    comparisonCalls += 1
                    true
                },
            ) { }
        }
        val observer = TrackingObserver()
        adapter.submitAndAwait(listOf(FactoryItem(id = 1L, title = "Old")))
        onMainThread { adapter.registerAdapterDataObserver(observer) }

        adapter.submitAndAwait(listOf(FactoryItem(id = 1L, title = "New")))

        assertTrue(comparisonCalls > 0)
        assertEquals(0, observer.totalEvents)
    }

    @Test
    fun changePayloadIsForwardedWithoutTransformation() {
        val expectedPayload = Any()
        val adapter = onMainThread {
            braidListAdapter(
                inflate = FactoryBinding::inflate,
                keySelector = FactoryItem::id,
                getChangePayload = { _, _ -> expectedPayload },
            ) { }
        }
        val observer = TrackingObserver()
        adapter.submitAndAwait(listOf(FactoryItem(id = 1L, title = "Old")))
        onMainThread { adapter.registerAdapterDataObserver(observer) }

        adapter.submitAndAwait(listOf(FactoryItem(id = 1L, title = "New")))

        assertSame(expectedPayload, observer.lastPayload)
    }

    @Test
    fun existingVarargAndBlockFactoriesReturnBraidListAdapter() = onMainThread {
        val varargAdapter: BraidListAdapter<FactoryItem> =
            braidListAdapter(FactoryDelegate())
        val blockAdapter: BraidListAdapter<FactoryItem> = braidListAdapter {
            viewBinding(
                inflate = FactoryBinding::inflate,
                keySelector = FactoryItem::id,
            ) { }
        }

        assertEquals(0, varargAdapter.itemCount)
        assertEquals(0, blockAdapter.itemCount)
    }
}

private data class FactoryItem(
    val id: Long,
    val title: String,
)

private class FactoryBinding private constructor(
    val label: TextView,
) : ViewBinding {

    override fun getRoot(): TextView = label

    companion object {
        fun inflate(
            layoutInflater: LayoutInflater,
            parent: ViewGroup,
            attachToParent: Boolean,
        ): FactoryBinding {
            val label = TextView(layoutInflater.context)
            if (attachToParent) {
                parent.addView(label)
            }
            return FactoryBinding(label)
        }
    }
}

private class FactoryDelegate :
    ViewBindingDelegate<FactoryItem, FactoryItem, FactoryBinding>(
        inflate = FactoryBinding::inflate,
    ) {

    override fun isForItem(item: FactoryItem): Boolean = true

    override fun areItemsTheSame(
        oldItem: FactoryItem,
        newItem: FactoryItem,
    ): Boolean = oldItem.id == newItem.id

    override fun bind(
        binding: FactoryBinding,
        item: FactoryItem,
        payloads: List<Any>,
    ): Unit = Unit
}

private class TrackingObserver : RecyclerView.AdapterDataObserver() {
    var changedItems: Int = 0
    var insertedItems: Int = 0
    var removedItems: Int = 0
    var lastPayload: Any? = null

    val totalEvents: Int
        get() = changedItems + insertedItems + removedItems

    override fun onItemRangeChanged(
        positionStart: Int,
        itemCount: Int,
    ) {
        changedItems += itemCount
    }

    override fun onItemRangeChanged(
        positionStart: Int,
        itemCount: Int,
        payload: Any?,
    ) {
        changedItems += itemCount
        lastPayload = payload
    }

    override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
        insertedItems += itemCount
    }

    override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
        removedItems += itemCount
    }

    fun reset() {
        changedItems = 0
        insertedItems = 0
        removedItems = 0
        lastPayload = null
    }
}

private fun BraidListAdapter<FactoryItem>.submitAndAwait(items: List<FactoryItem>) {
    val committed = CountDownLatch(1)

    onMainThread {
        submitList(items) {
            committed.countDown()
        }
    }

    assertTrue(
        "BraidListAdapter did not commit its list.",
        committed.await(10L, TimeUnit.SECONDS),
    )
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
}

private fun parent(): ViewGroup = FrameLayout(
    InstrumentationRegistry.getInstrumentation().targetContext,
)

private fun <Result> onMainThread(block: () -> Result): Result {
    val task = FutureTask(Callable(block))
    InstrumentationRegistry.getInstrumentation().runOnMainSync(task)
    return task.get()
}
