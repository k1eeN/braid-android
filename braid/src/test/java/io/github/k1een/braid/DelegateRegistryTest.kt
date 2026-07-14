package io.github.k1een.braid

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DelegateRegistryTest {

    @Test
    fun `empty delegate list is rejected`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DelegateRegistry<RegistryItem>(emptyList())
        }

        assertTrue(error.message.orEmpty().contains("at least one delegate"))
    }

    @Test
    fun `single matching delegate is resolved`() {
        val firstDelegate = FirstDelegate()
        val registry = DelegateRegistry<RegistryItem>(listOf(firstDelegate))

        val resolved = registry.resolve(FirstItem(id = 1L))

        assertSame(firstDelegate, resolved.delegate)
        assertEquals(0, resolved.viewType)
    }

    @Test
    fun `missing delegate reports item type and registered delegates`() {
        val firstDelegate = FirstDelegate()
        val registry = DelegateRegistry<RegistryItem>(listOf(firstDelegate))

        val error = assertThrows(IllegalStateException::class.java) {
            registry.viewTypeFor(UnknownItem)
        }

        val message = error.message.orEmpty()
        assertTrue(message.contains(UnknownItem.javaClass.name))
        assertTrue(message.contains(firstDelegate.javaClass.name))
    }

    @Test
    fun `multiple delegates report item type and every matching delegate`() {
        val firstDelegate = FirstDelegate()
        val catchAllDelegate = CatchAllDelegate()
        val registry = DelegateRegistry<RegistryItem>(
            listOf(firstDelegate, catchAllDelegate),
        )

        val item = FirstItem(id = 1L)
        val error = assertThrows(IllegalStateException::class.java) {
            registry.viewTypeFor(item)
        }

        val message = error.message.orEmpty()
        assertTrue(message.contains(item.javaClass.name))
        assertTrue(message.contains(firstDelegate.javaClass.name))
        assertTrue(message.contains(catchAllDelegate.javaClass.name))
    }

    @Test
    fun `view types follow registration order and remain stable`() {
        val firstDelegate = FirstDelegate()
        val secondDelegate = SecondDelegate()
        val forwardRegistry = DelegateRegistry<RegistryItem>(
            listOf(firstDelegate, secondDelegate),
        )
        val reverseRegistry = DelegateRegistry<RegistryItem>(
            listOf(secondDelegate, firstDelegate),
        )
        val firstItem = FirstItem(id = 1L)
        val secondItem = SecondItem(id = 2L)

        assertEquals(0, forwardRegistry.viewTypeFor(firstItem))
        assertEquals(1, forwardRegistry.viewTypeFor(secondItem))
        assertEquals(0, forwardRegistry.viewTypeFor(firstItem))
        assertEquals(0, reverseRegistry.viewTypeFor(secondItem))
        assertEquals(1, reverseRegistry.viewTypeFor(firstItem))
    }

    @Test
    fun `registry keeps a defensive copy of delegates`() {
        val firstDelegate = FirstDelegate()
        val source = mutableListOf<
            AdapterDelegate<RegistryItem, out RegistryItem, out RecyclerView.ViewHolder>,
        >(firstDelegate)
        val registry = DelegateRegistry(source)

        source.clear()
        source += CatchAllDelegate()

        assertSame(firstDelegate, registry.resolve(FirstItem(id = 1L)).delegate)
        assertThrows(IllegalStateException::class.java) {
            registry.resolve(UnknownItem)
        }
    }

    @Test
    fun `unknown view type reports registered view types`() {
        val registry = DelegateRegistry<RegistryItem>(listOf(FirstDelegate()))

        val error = assertThrows(IllegalStateException::class.java) {
            registry.registeredDelegateFor(viewType = -1)
        }

        val message = error.message.orEmpty()
        assertTrue(message.contains("viewType=-1"))
        assertTrue(message.contains("Registered viewTypes"))
    }
}

private sealed interface RegistryItem

private data class FirstItem(val id: Long) : RegistryItem

private data class SecondItem(val id: Long) : RegistryItem

private object UnknownItem : RegistryItem

private abstract class RegistryTestDelegate<Item : RegistryItem> :
    AdapterDelegate<RegistryItem, Item, RecyclerView.ViewHolder>() {

    override fun createViewHolder(parent: ViewGroup): RecyclerView.ViewHolder =
        throw NotImplementedError("ViewHolder creation is not used by local unit tests.")

    override fun bindViewHolder(
        holder: RecyclerView.ViewHolder,
        item: Item,
        payloads: List<Any>,
    ): Unit = Unit
}

private class FirstDelegate : RegistryTestDelegate<FirstItem>() {
    override fun isForItem(item: RegistryItem): Boolean = item is FirstItem

    override fun areItemsTheSame(oldItem: FirstItem, newItem: FirstItem): Boolean =
        oldItem.id == newItem.id
}

private class SecondDelegate : RegistryTestDelegate<SecondItem>() {
    override fun isForItem(item: RegistryItem): Boolean = item is SecondItem

    override fun areItemsTheSame(oldItem: SecondItem, newItem: SecondItem): Boolean =
        oldItem.id == newItem.id
}

private class CatchAllDelegate : RegistryTestDelegate<RegistryItem>() {
    override fun isForItem(item: RegistryItem): Boolean = true

    override fun areItemsTheSame(oldItem: RegistryItem, newItem: RegistryItem): Boolean =
        oldItem == newItem
}
