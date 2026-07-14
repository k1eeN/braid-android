package io.github.k1een.braid

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DelegateItemCallbackTest {

    @Test
    fun `items from different delegate instances are never the same item`() {
        val firstDelegate = RecordingDelegate<TextItem>(
            matcher = { item -> item is TextItem },
        )
        val secondDelegate = RecordingDelegate<ImageItem>(
            matcher = { item -> item is ImageItem },
        )
        val callback = callback(firstDelegate, secondDelegate)

        val result = callback.areItemsTheSame(
            TextItem(id = 1L, text = "Text"),
            ImageItem(id = 1L, url = "image.png"),
        )

        assertFalse(result)
        assertEquals(0, firstDelegate.itemsComparisonCount)
        assertEquals(0, secondDelegate.itemsComparisonCount)
    }

    @Test
    fun `items from the same delegate use its identity comparison`() {
        val delegate = RecordingDelegate<TextItem>(
            matcher = { item -> item is TextItem },
            itemComparator = { oldItem, newItem -> oldItem.id == newItem.id },
        )
        val callback = callback(delegate)

        val sameItemResult = callback.areItemsTheSame(
            TextItem(id = 1L, text = "Old"),
            TextItem(id = 1L, text = "New"),
        )
        val differentItemResult = callback.areItemsTheSame(
            TextItem(id = 1L, text = "Old"),
            TextItem(id = 2L, text = "New"),
        )

        assertTrue(sameItemResult)
        assertFalse(differentItemResult)
        assertEquals(2, delegate.itemsComparisonCount)
    }

    @Test
    fun `contents comparison is delegated`() {
        val delegate = RecordingDelegate<TextItem>(
            matcher = { item -> item is TextItem },
            contentComparator = { oldItem, newItem -> oldItem == newItem },
        )
        val callback = callback(delegate)

        val result = callback.areContentsTheSame(
            TextItem(id = 1L, text = "Old"),
            TextItem(id = 1L, text = "New"),
        )

        assertFalse(result)
        assertEquals(1, delegate.contentsComparisonCount)
    }

    @Test
    fun `change payload is delegated without transformation`() {
        val expectedPayload = Any()
        val delegate = RecordingDelegate<TextItem>(
            matcher = { item -> item is TextItem },
            payloadProvider = { _, _ -> expectedPayload },
        )
        val callback = callback(delegate)

        val actualPayload = callback.getChangePayload(
            TextItem(id = 1L, text = "Old"),
            TextItem(id = 1L, text = "New"),
        )

        assertSame(expectedPayload, actualPayload)
        assertEquals(1, delegate.payloadRequestCount)
    }

    @Test
    fun `different delegates have no change payload`() {
        val firstDelegate = RecordingDelegate<TextItem>(
            matcher = { item -> item is TextItem },
            payloadProvider = { _, _ -> Any() },
        )
        val secondDelegate = RecordingDelegate<ImageItem>(
            matcher = { item -> item is ImageItem },
            payloadProvider = { _, _ -> Any() },
        )
        val callback = callback(firstDelegate, secondDelegate)

        val payload = callback.getChangePayload(
            TextItem(id = 1L, text = "Text"),
            ImageItem(id = 1L, url = "image.png"),
        )

        assertNull(payload)
        assertEquals(0, firstDelegate.payloadRequestCount)
        assertEquals(0, secondDelegate.payloadRequestCount)
    }

    @Test
    fun `different delegates do not have the same contents`() {
        val callback = callback(
            RecordingDelegate<TextItem>(matcher = { item -> item is TextItem }),
            RecordingDelegate<ImageItem>(matcher = { item -> item is ImageItem }),
        )

        val result = callback.areContentsTheSame(
            TextItem(id = 1L, text = "Same"),
            ImageItem(id = 1L, url = "Same"),
        )

        assertFalse(result)
    }

    @Test
    fun `delegate resolution errors are not suppressed by callback`() {
        val delegate = RecordingDelegate<TextItem>(
            matcher = { item -> item is TextItem },
        )
        val callback = callback(delegate)

        val error = assertThrows(IllegalStateException::class.java) {
            callback.areItemsTheSame(
                TextItem(id = 1L, text = "Text"),
                ImageItem(id = 1L, url = "image.png"),
            )
        }

        assertTrue(error.message.orEmpty().contains(ImageItem::class.java.name))
    }

    private fun callback(
        vararg delegates: AdapterDelegate<
            DiffItem,
            out DiffItem,
            out RecyclerView.ViewHolder,
        >,
    ): DelegateItemCallback<DiffItem> = DelegateItemCallback(
        DelegateRegistry(delegates.toList()),
    )
}

private sealed interface DiffItem {
    val id: Long
}

private data class TextItem(
    override val id: Long,
    val text: String,
) : DiffItem

private data class ImageItem(
    override val id: Long,
    val url: String,
) : DiffItem

private class RecordingDelegate<Item : DiffItem>(
    private val matcher: (DiffItem) -> Boolean,
    private val itemComparator: (Item, Item) -> Boolean = { oldItem, newItem ->
        oldItem.id == newItem.id
    },
    private val contentComparator: (Item, Item) -> Boolean = { oldItem, newItem ->
        oldItem == newItem
    },
    private val payloadProvider: (Item, Item) -> Any? = { _, _ -> null },
) : AdapterDelegate<DiffItem, Item, RecyclerView.ViewHolder>() {

    var itemsComparisonCount: Int = 0
        private set

    var contentsComparisonCount: Int = 0
        private set

    var payloadRequestCount: Int = 0
        private set

    override fun isForItem(item: DiffItem): Boolean = matcher(item)

    override fun createViewHolder(parent: ViewGroup): RecyclerView.ViewHolder =
        throw NotImplementedError("ViewHolder creation is not used by local unit tests.")

    override fun bindViewHolder(
        holder: RecyclerView.ViewHolder,
        item: Item,
        payloads: List<Any>,
    ): Unit = Unit

    override fun areItemsTheSame(oldItem: Item, newItem: Item): Boolean {
        itemsComparisonCount += 1
        return itemComparator(oldItem, newItem)
    }

    override fun areContentsTheSame(oldItem: Item, newItem: Item): Boolean {
        contentsComparisonCount += 1
        return contentComparator(oldItem, newItem)
    }

    override fun getChangePayload(oldItem: Item, newItem: Item): Any? {
        payloadRequestCount += 1
        return payloadProvider(oldItem, newItem)
    }
}
