package io.github.k1een.braid

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StatefulViewBindingApiTest {

    @Test
    fun `stateful view binding delegate accepts its item subtype`() {
        val scope = statefulApiScope()
        scope.registerStatefulTextDelegate()

        val resolved = scope.registry().resolve(
            StatefulApiTextItem(id = 1L, text = "Text")
        )

        assertEquals(0, resolved.viewType)
    }

    @Test
    fun `stateful view binding delegate rejects another item subtype`() {
        val scope = statefulApiScope()
        scope.registerStatefulTextDelegate()

        assertThrows(IllegalStateException::class.java) {
            scope.registry().resolve(
                StatefulApiChoiceItem(id = 1L, title = "Choice")
            )
        }
    }

    @Test
    fun `matches additionally filters stateful items of the same subtype`() {
        val scope = statefulApiScope()
        scope.registerStatefulTextDelegate { item -> !item.isExpanded }
        scope.registerStatefulTextDelegate { item -> item.isExpanded }
        val registry = scope.registry()

        assertEquals(
            0,
            registry.viewTypeFor(
                StatefulApiTextItem(id = 1L, text = "Text", isExpanded = false)
            )
        )
        assertEquals(
            1,
            registry.viewTypeFor(
                StatefulApiTextItem(id = 1L, text = "Text", isExpanded = true)
            )
        )
    }

    @Test
    fun `stateful key selector controls item identity`() {
        val callback = statefulTextCallback()

        assertTrue(
            callback.areItemsTheSame(
                StatefulApiTextItem(id = 1L, text = "Old"),
                StatefulApiTextItem(id = 1L, text = "New")
            )
        )
        assertFalse(
            callback.areItemsTheSame(
                StatefulApiTextItem(id = 1L, text = "Text"),
                StatefulApiTextItem(id = 2L, text = "Text")
            )
        )
    }

    @Test
    fun `stateful default contents comparison uses structural equality`() {
        val callback = statefulTextCallback()

        assertTrue(
            callback.areContentsTheSame(
                StatefulApiTextItem(id = 1L, text = "Same"),
                StatefulApiTextItem(id = 1L, text = "Same")
            )
        )
        assertFalse(
            callback.areContentsTheSame(
                StatefulApiTextItem(id = 1L, text = "Old"),
                StatefulApiTextItem(id = 1L, text = "New")
            )
        )
    }

    @Test
    fun `stateful custom contents comparison is invoked`() {
        var comparisonCount = 0
        val scope = statefulApiScope()
        scope.statefulViewBinding(
            inflate = statefulApiInflater,
            keySelector = StatefulApiTextItem::id,
            stateFactory = { StatefulApiHolderState() },
            areContentsTheSame = { oldItem, newItem ->
                comparisonCount += 1
                oldItem.id == newItem.id
            }
        ) { _, _ -> }

        val result = scope.callback().areContentsTheSame(
            StatefulApiTextItem(id = 1L, text = "Old"),
            StatefulApiTextItem(id = 1L, text = "New")
        )

        assertTrue(result)
        assertEquals(1, comparisonCount)
    }

    @Test
    fun `stateful change payload is returned without transformation`() {
        val expectedPayload = Any()
        var payloadRequestCount = 0
        val scope = statefulApiScope()
        scope.statefulViewBinding(
            inflate = statefulApiInflater,
            keySelector = StatefulApiTextItem::id,
            stateFactory = { StatefulApiHolderState() },
            getChangePayload = { _, _ ->
                payloadRequestCount += 1
                expectedPayload
            }
        ) { _, _ -> }

        val actualPayload = scope.callback().getChangePayload(
            StatefulApiTextItem(id = 1L, text = "Old"),
            StatefulApiTextItem(id = 1L, text = "New")
        )

        assertSame(expectedPayload, actualPayload)
        assertEquals(1, payloadRequestCount)
    }

    @Test
    fun `overlapping stateful delegates keep registry conflict diagnostics`() {
        val scope = statefulApiScope()
        scope.registerStatefulTextDelegate()
        scope.registerStatefulTextDelegate()

        val error = assertThrows(IllegalStateException::class.java) {
            scope.registry().viewTypeFor(
                StatefulApiTextItem(id = 1L, text = "Text")
            )
        }

        assertTrue(error.message.orEmpty().contains("Multiple delegates match"))
    }

    @Test
    fun `stateful and stateless delegates preserve registration order`() {
        val scope = statefulApiScope()
        scope.registerStatefulTextDelegate()
        scope.viewBinding(
            inflate = statefulApiInflater,
            keySelector = StatefulApiChoiceItem::id
        ) { }
        val registry = scope.registry()

        assertEquals(
            0,
            registry.viewTypeFor(StatefulApiTextItem(id = 1L, text = "Text"))
        )
        assertEquals(
            1,
            registry.viewTypeFor(StatefulApiChoiceItem(id = 2L, title = "Choice"))
        )
    }

    @Test
    fun `custom stateful view binding delegate can be registered through delegate`() {
        val customDelegate = ReusableStatefulTextDelegate()
        val scope = statefulApiScope()
        scope.delegate(customDelegate)

        val resolved = scope.registry().resolve(
            StatefulApiTextItem(id = 1L, text = "Text")
        )

        assertSame(customDelegate, resolved.delegate)
    }

    @Test
    fun `stateful factory preserves its typed holder contract without caller casts`() {
        val delegate: AdapterDelegate<
            StatefulApiItem,
            StatefulApiTextItem,
            StatefulViewBindingViewHolder<StatefulApiBinding, StatefulApiHolderState>
            > = createStatefulViewBindingDelegate(
            inflate = statefulApiInflater,
            matcher = { item -> item is StatefulApiTextItem },
            keySelector = StatefulApiTextItem::id,
            contentComparator = { oldItem, newItem -> oldItem == newItem },
            payloadProvider = { _, _ -> null },
            stateFactory = { StatefulApiHolderState() },
            fullBinder = { _, _ -> },
            payloadBinder = null,
            recycleCallback = {},
            attachedCallback = {},
            detachedCallback = {},
            failedToRecycleCallback = { false }
        )

        assertTrue(
            delegate.isForItem(StatefulApiTextItem(id = 1L, text = "Text"))
        )
    }

    private fun statefulTextCallback(): DelegateItemCallback<StatefulApiItem> {
        val scope = statefulApiScope()
        scope.registerStatefulTextDelegate()
        return scope.callback()
    }
}

private sealed interface StatefulApiItem

private data class StatefulApiTextItem(val id: Long, val text: String, val isExpanded: Boolean = false) :
    StatefulApiItem

private data class StatefulApiChoiceItem(val id: Long, val title: String) : StatefulApiItem

private class StatefulApiHolderState

private class StatefulApiBinding : ViewBinding {
    override fun getRoot(): View = throw NotImplementedError("Android views are not used by local unit tests.")
}

private val statefulApiInflater:
    (LayoutInflater, ViewGroup, Boolean) -> StatefulApiBinding = { _, _, _ ->
        throw NotImplementedError(
            "ViewBinding inflation is not used by local unit tests."
        )
    }

private fun statefulApiScope(): BraidAdapterScope<StatefulApiItem> = BraidAdapterScope()

private fun BraidAdapterScope<StatefulApiItem>.registerStatefulTextDelegate(
    matches: (StatefulApiTextItem) -> Boolean = { true }
) {
    statefulViewBinding(
        inflate = statefulApiInflater,
        keySelector = StatefulApiTextItem::id,
        stateFactory = { StatefulApiHolderState() },
        matches = matches
    ) { _, _ -> }
}

private fun BraidAdapterScope<StatefulApiItem>.registry(): DelegateRegistry<StatefulApiItem> =
    DelegateRegistry(delegateSnapshot())

private fun BraidAdapterScope<StatefulApiItem>.callback(): DelegateItemCallback<StatefulApiItem> =
    DelegateItemCallback(registry())

private class ReusableStatefulTextDelegate :
    StatefulViewBindingDelegate<
        StatefulApiItem,
        StatefulApiTextItem,
        StatefulApiBinding,
        StatefulApiHolderState
        >(
        inflate = statefulApiInflater
    ) {

    override fun isForItem(item: StatefulApiItem): Boolean = item is StatefulApiTextItem

    override fun createState(binding: StatefulApiBinding): StatefulApiHolderState = StatefulApiHolderState()

    override fun areItemsTheSame(oldItem: StatefulApiTextItem, newItem: StatefulApiTextItem): Boolean =
        oldItem.id == newItem.id

    override fun bind(
        binding: StatefulApiBinding,
        state: StatefulApiHolderState,
        item: StatefulApiTextItem,
        payloads: List<Any>
    ): Unit = Unit
}
