package io.github.k1een.braid

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DelegateRegistryIntegrationInstrumentedTest {

    @Test
    fun publicIntegrationApiRoutesCreationBindingAndLifecycle() = onMainThread {
        val delegate = RegistryIntegrationDelegate()
        val registry = braidDelegateRegistry<RegistryIntegrationItem>(delegate)
        val adapter = DelegateListAdapter(registry)
        val item = RegistryIntegrationItem(id = 1L)
        val viewType = registry.viewTypeFor(item)
        val holder = adapter.createViewHolder(parent(), viewType)
        val payloads = listOf<Any>(Any())

        registry.bindViewHolder(holder, item, payloads)
        registry.onViewAttachedToWindow(holder, viewType)
        registry.onViewDetachedFromWindow(holder, viewType)
        val failedToRecycle = registry.onFailedToRecycleView(holder, viewType)
        registry.onViewRecycled(holder, viewType)

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
}

private data class RegistryIntegrationItem(val id: Long)

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

private fun parent(): ViewGroup = FrameLayout(
    InstrumentationRegistry.getInstrumentation().targetContext,
)

private fun onMainThread(block: () -> Unit) {
    InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
}
