package io.github.k1een.braid.paging

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.github.k1een.braid.AdapterDelegate
import io.github.k1een.braid.braidDelegateRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BraidPagingDataAdapterTest {

    @Test
    fun emptyVarargFactoryUsesRegistryFailFastValidation() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            braidPagingDataAdapter<PagingUnitItem>()
        }

        assertTrue(error.message.orEmpty().contains("at least one delegate"))
    }

    @Test
    fun emptyDslFactoryUsesRegistryFailFastValidation() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            braidPagingDataAdapter<PagingUnitItem> { }
        }

        assertTrue(error.message.orEmpty().contains("at least one delegate"))
    }

    @Test
    fun registryItemCallbackKeepsPerDelegateDiffRules() {
        val registry = braidDelegateRegistry<PagingUnitItem>(
            PagingUnitDelegate(),
        )

        assertTrue(
            registry.itemCallback.areItemsTheSame(
                PagingUnitItem(id = 1L, value = "Old"),
                PagingUnitItem(id = 1L, value = "New"),
            ),
        )
        assertFalse(
            registry.itemCallback.areContentsTheSame(
                PagingUnitItem(id = 1L, value = "Old"),
                PagingUnitItem(id = 1L, value = "New"),
            ),
        )
    }

    @Test
    fun presentedItemHelperReturnsOriginalItem() {
        val item = PagingUnitItem(id = 1L, value = "Value")

        assertSame(item, requirePresentedItem(item, position = 4))
    }

    @Test
    fun presentedItemHelperRejectsNullPlaceholderWithActionableMessage() {
        val error = assertThrows(IllegalStateException::class.java) {
            requirePresentedItem<PagingUnitItem>(
                item = null,
                position = 7,
            )
        }

        assertEquals(
            "BraidPagingDataAdapter does not support Paging placeholders. " +
                "Configure PagingConfig with enablePlaceholders = false. " +
                "Null item at position=7.",
            error.message,
        )
    }
}

private data class PagingUnitItem(
    val id: Long,
    val value: String,
)

private class PagingUnitDelegate :
    AdapterDelegate<
        PagingUnitItem,
        PagingUnitItem,
        RecyclerView.ViewHolder,
    >() {

    override fun isForItem(item: PagingUnitItem): Boolean = true

    override fun createViewHolder(parent: ViewGroup): RecyclerView.ViewHolder =
        throw NotImplementedError(
            "Android views are not used by local unit tests.",
        )

    override fun bindViewHolder(
        holder: RecyclerView.ViewHolder,
        item: PagingUnitItem,
        payloads: List<Any>,
    ): Unit = Unit

    override fun areItemsTheSame(
        oldItem: PagingUnitItem,
        newItem: PagingUnitItem,
    ): Boolean = oldItem.id == newItem.id
}
