package io.github.k1een.braid

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DelegateRegistryIntegrationInstrumentedTest {

    @Test
    fun publicIntegrationApiRoutesCreationBindingAndLifecycle() = onMainThread {
        val delegate = RegistryIntegrationDelegate()
        val registry = braidDelegateRegistry<RegistryIntegrationItem>(delegate)
        val item = RegistryIntegrationItem(id = 1L)
        val viewType = registry.viewTypeFor(item)
        val holder = registry.createViewHolder(parent(), viewType)
        val payloads = listOf<Any>(Any())

        registry.bindViewHolder(holder, item, payloads)
        registry.onViewAttachedToWindow(holder)
        registry.onViewDetachedFromWindow(holder)
        val failedToRecycle = registry.onFailedToRecycleView(holder)
        registry.onViewRecycled(holder)

        assertEquals(0, viewType)
        assertEquals(1, delegate.createCalls)
        assertSame(item, delegate.boundItem)
        assertSame(payloads, delegate.boundPayloads)
        assertEquals(1, delegate.attachedCalls)
        assertEquals(1, delegate.detachedCalls)
        assertEquals(1, delegate.failedToRecycleCalls)
        assertEquals(1, delegate.recycledCalls)
        assertTrue(failedToRecycle)
    }

    @Test
    fun holderFromAnotherRegistryIsRejectedEvenWithSameLocalViewType() = onMainThread {
        val registryA = braidDelegateRegistry<RegistryIntegrationItem>(
            RegistryIntegrationDelegate(),
        )
        val registryB = braidDelegateRegistry<RegistryIntegrationItem>(
            RegistryIntegrationDelegate(),
        )
        val holder = registryA.createViewHolder(
            parent = parent(),
            viewType = 0,
        )
        val item = RegistryIntegrationItem(id = 1L)

        val bindError = assertThrows(IllegalStateException::class.java) {
            registryB.bindViewHolder(holder, item, emptyList())
        }
        val recycleError = assertThrows(IllegalStateException::class.java) {
            registryB.onViewRecycled(holder)
        }

        assertTrue(bindError.message.orEmpty().contains(FOREIGN_HOLDER_MESSAGE))
        assertTrue(recycleError.message.orEmpty().contains(FOREIGN_HOLDER_MESSAGE))
    }

    @Test
    fun holderCreatedOutsideRegistryIsRejectedWithIntegrationGuidance() = onMainThread {
        val registry = braidDelegateRegistry<RegistryIntegrationItem>(
            RegistryIntegrationDelegate(),
        )
        val holder = RegistryIntegrationHolder(View(parent().context))

        val error = assertThrows(IllegalStateException::class.java) {
            registry.bindViewHolder(
                holder = holder,
                item = RegistryIntegrationItem(id = 1L),
                payloads = emptyList(),
            )
        }

        assertTrue(error.message.orEmpty().contains(FOREIGN_HOLDER_MESSAGE))
    }

    @Test
    fun holderAndItemFromDifferentDelegatesReportLocalRoute() = onMainThread {
        val firstDelegate = FirstRegistryMismatchDelegate()
        val secondDelegate = SecondRegistryMismatchDelegate()
        val registry = braidDelegateRegistry<RegistryMismatchItem>(
            firstDelegate,
            secondDelegate,
        )
        val holder = registry.createViewHolder(
            parent = parent(),
            viewType = registry.viewTypeFor(FirstRegistryMismatchItem),
        )

        val error = assertThrows(IllegalStateException::class.java) {
            registry.bindViewHolder(
                holder = holder,
                item = SecondRegistryMismatchItem,
                payloads = emptyList(),
            )
        }
        val message = error.message.orEmpty()

        assertTrue(message.contains("local viewType=0"))
        assertTrue(message.contains(firstDelegate.javaClass.name))
        assertTrue(message.contains(secondDelegate.javaClass.name))
        assertTrue(message.contains(SecondRegistryMismatchItem.javaClass.name))
    }
}

private const val FOREIGN_HOLDER_MESSAGE =
    "ViewHolder was not created by this DelegateRegistry"

private data class RegistryIntegrationItem(val id: Long)

private sealed interface RegistryMismatchItem

private object FirstRegistryMismatchItem : RegistryMismatchItem

private object SecondRegistryMismatchItem : RegistryMismatchItem

private class RegistryIntegrationHolder(
    itemView: View,
) : RecyclerView.ViewHolder(itemView)

private class RegistryIntegrationDelegate :
    AdapterDelegate<
        RegistryIntegrationItem,
        RegistryIntegrationItem,
        RegistryIntegrationHolder,
    >() {

    var createCalls: Int = 0
    var boundItem: RegistryIntegrationItem? = null
    var boundPayloads: List<Any>? = null
    var recycledCalls: Int = 0
    var attachedCalls: Int = 0
    var detachedCalls: Int = 0
    var failedToRecycleCalls: Int = 0

    override fun isForItem(item: RegistryIntegrationItem): Boolean = true

    override fun createViewHolder(parent: ViewGroup): RegistryIntegrationHolder {
        createCalls += 1
        return RegistryIntegrationHolder(View(parent.context))
    }

    override fun bindViewHolder(
        holder: RegistryIntegrationHolder,
        item: RegistryIntegrationItem,
        payloads: List<Any>,
    ) {
        boundItem = item
        boundPayloads = payloads
    }

    override fun onViewRecycled(holder: RegistryIntegrationHolder) {
        recycledCalls += 1
    }

    override fun onViewAttachedToWindow(holder: RegistryIntegrationHolder) {
        attachedCalls += 1
    }

    override fun onViewDetachedFromWindow(holder: RegistryIntegrationHolder) {
        detachedCalls += 1
    }

    override fun onFailedToRecycleView(
        holder: RegistryIntegrationHolder,
    ): Boolean {
        failedToRecycleCalls += 1
        return true
    }

    override fun areItemsTheSame(
        oldItem: RegistryIntegrationItem,
        newItem: RegistryIntegrationItem,
    ): Boolean = oldItem.id == newItem.id
}

private abstract class RegistryMismatchDelegate<Item : RegistryMismatchItem> :
    AdapterDelegate<
        RegistryMismatchItem,
        Item,
        RegistryIntegrationHolder,
    >() {

    override fun createViewHolder(parent: ViewGroup): RegistryIntegrationHolder =
        RegistryIntegrationHolder(View(parent.context))

    override fun bindViewHolder(
        holder: RegistryIntegrationHolder,
        item: Item,
        payloads: List<Any>,
    ): Unit = Unit
}

private class FirstRegistryMismatchDelegate :
    RegistryMismatchDelegate<FirstRegistryMismatchItem>() {

    override fun isForItem(item: RegistryMismatchItem): Boolean =
        item is FirstRegistryMismatchItem

    override fun areItemsTheSame(
        oldItem: FirstRegistryMismatchItem,
        newItem: FirstRegistryMismatchItem,
    ): Boolean = true
}

private class SecondRegistryMismatchDelegate :
    RegistryMismatchDelegate<SecondRegistryMismatchItem>() {

    override fun isForItem(item: RegistryMismatchItem): Boolean =
        item is SecondRegistryMismatchItem

    override fun areItemsTheSame(
        oldItem: SecondRegistryMismatchItem,
        newItem: SecondRegistryMismatchItem,
    ): Boolean = true
}

private fun parent(): ViewGroup = FrameLayout(
    InstrumentationRegistry.getInstrumentation().targetContext,
)

private fun onMainThread(block: () -> Unit) {
    InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
}
