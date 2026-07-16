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

class BraidListAdapterApiTest {

    @Test
    fun `custom scope extensions preserve delegate registration order`() {
        val scope = ergonomicScope()
        scope.registerTextDelegate()
        scope.registerImageDelegate()

        val registry = scope.registry()

        assertEquals(0, registry.viewTypeFor(ErgonomicTextItem(id = 1L, text = "Text")))
        assertEquals(1, registry.viewTypeFor(ErgonomicImageItem(id = 2L, url = "image.png")))
    }

    @Test
    fun `scope snapshot is unaffected by later registrations`() {
        val scope = ergonomicScope()
        scope.registerTextDelegate()
        val snapshot = scope.delegateSnapshot()

        scope.registerImageDelegate()
        val registry = DelegateRegistry(snapshot)

        assertEquals(0, registry.viewTypeFor(ErgonomicTextItem(id = 1L, text = "Text")))
        assertThrows(IllegalStateException::class.java) {
            registry.viewTypeFor(ErgonomicImageItem(id = 2L, url = "image.png"))
        }
    }

    @Test
    fun `reified view binding delegate accepts its item subtype`() {
        val scope = ergonomicScope()
        scope.registerTextDelegate()

        val resolved = scope.registry().resolve(ErgonomicTextItem(id = 1L, text = "Text"))

        assertEquals(0, resolved.viewType)
    }

    @Test
    fun `reified view binding delegate rejects another item subtype`() {
        val scope = ergonomicScope()
        scope.registerTextDelegate()

        assertThrows(IllegalStateException::class.java) {
            scope.registry().resolve(ErgonomicImageItem(id = 1L, url = "image.png"))
        }
    }

    @Test
    fun `matches additionally filters items of the same subtype`() {
        val scope = ergonomicScope()
        scope.viewBinding(
            inflate = testInflater,
            keySelector = ErgonomicTextItem::id,
            matches = { item -> !item.isExpanded }
        ) { }
        scope.viewBinding(
            inflate = testInflater,
            keySelector = ErgonomicTextItem::id,
            matches = { item -> item.isExpanded }
        ) { }
        val registry = scope.registry()

        assertEquals(
            0,
            registry.viewTypeFor(
                ErgonomicTextItem(id = 1L, text = "Text", isExpanded = false)
            )
        )
        assertEquals(
            1,
            registry.viewTypeFor(
                ErgonomicTextItem(id = 1L, text = "Text", isExpanded = true)
            )
        )
    }

    @Test
    fun `key selector controls item identity`() {
        val callback = textCallback()

        assertTrue(
            callback.areItemsTheSame(
                ErgonomicTextItem(id = 1L, text = "Old"),
                ErgonomicTextItem(id = 1L, text = "New")
            )
        )
        assertFalse(
            callback.areItemsTheSame(
                ErgonomicTextItem(id = 1L, text = "Text"),
                ErgonomicTextItem(id = 2L, text = "Text")
            )
        )
    }

    @Test
    fun `default contents comparison uses structural equality`() {
        val callback = textCallback()

        assertTrue(
            callback.areContentsTheSame(
                ErgonomicTextItem(id = 1L, text = "Same"),
                ErgonomicTextItem(id = 1L, text = "Same")
            )
        )
        assertFalse(
            callback.areContentsTheSame(
                ErgonomicTextItem(id = 1L, text = "Old"),
                ErgonomicTextItem(id = 1L, text = "New")
            )
        )
    }

    @Test
    fun `custom contents comparison is invoked`() {
        var comparisonCount = 0
        val scope = ergonomicScope()
        scope.viewBinding(
            inflate = testInflater,
            keySelector = ErgonomicTextItem::id,
            areContentsTheSame = { oldItem, newItem ->
                comparisonCount += 1
                oldItem.id == newItem.id
            }
        ) { }

        val result = scope.callback().areContentsTheSame(
            ErgonomicTextItem(id = 1L, text = "Old"),
            ErgonomicTextItem(id = 1L, text = "New")
        )

        assertTrue(result)
        assertEquals(1, comparisonCount)
    }

    @Test
    fun `change payload is returned without transformation`() {
        val expectedPayload = Any()
        var payloadRequestCount = 0
        val scope = ergonomicScope()
        scope.viewBinding(
            inflate = testInflater,
            keySelector = ErgonomicTextItem::id,
            getChangePayload = { _, _ ->
                payloadRequestCount += 1
                expectedPayload
            }
        ) { }

        val actualPayload = scope.callback().getChangePayload(
            ErgonomicTextItem(id = 1L, text = "Old"),
            ErgonomicTextItem(id = 1L, text = "New")
        )

        assertSame(expectedPayload, actualPayload)
        assertEquals(1, payloadRequestCount)
    }

    @Test
    fun `overlapping view binding delegates keep registry conflict diagnostics`() {
        val scope = ergonomicScope()
        scope.registerTextDelegate()
        scope.registerTextDelegate()

        val error = assertThrows(IllegalStateException::class.java) {
            scope.registry().viewTypeFor(ErgonomicTextItem(id = 1L, text = "Text"))
        }

        assertTrue(error.message.orEmpty().contains("Multiple delegates match"))
    }

    @Test
    fun `view binding and custom delegates preserve mixed registration order`() {
        val scope = ergonomicScope()
        val imageDelegate = ImageDelegate()
        scope.delegate(imageDelegate)
        scope.registerTextDelegate()

        val registry = scope.registry()

        assertEquals(
            0,
            registry.viewTypeFor(ErgonomicImageItem(id = 1L, url = "image.png"))
        )
        assertEquals(1, registry.viewTypeFor(ErgonomicTextItem(id = 2L, text = "Text")))
        assertSame(
            imageDelegate,
            registry.resolve(ErgonomicImageItem(id = 1L, url = "image.png")).delegate
        )
    }

    @Test
    fun `empty builder uses registry fail fast validation`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            braidListAdapter<ErgonomicItem> { }
        }

        assertTrue(error.message.orEmpty().contains("at least one delegate"))
    }

    @Test
    fun `empty vararg uses registry fail fast validation`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            braidListAdapter<ErgonomicItem>()
        }

        assertTrue(error.message.orEmpty().contains("at least one delegate"))
    }

    @Test
    fun registryVarargFactoryPreservesDelegateOrder() {
        val registry = braidDelegateRegistry<ErgonomicItem>(
            ImageDelegate(),
            textDelegate()
        )

        assertEquals(
            0,
            registry.viewTypeFor(ErgonomicImageItem(id = 1L, url = "image.png"))
        )
        assertEquals(
            1,
            registry.viewTypeFor(ErgonomicTextItem(id = 2L, text = "Text"))
        )
    }

    @Test
    fun registryBlockFactoryPreservesDelegateOrder() {
        val registry = braidDelegateRegistry<ErgonomicItem> {
            registerTextDelegate()
            registerImageDelegate()
        }

        assertEquals(
            0,
            registry.viewTypeFor(ErgonomicTextItem(id = 1L, text = "Text"))
        )
        assertEquals(
            1,
            registry.viewTypeFor(ErgonomicImageItem(id = 2L, url = "image.png"))
        )
    }

    @Test
    fun registryVarargFactoryKeepsDefensiveSnapshot() {
        val original = ImageDelegate()
        val replacement = ImageDelegate()
        val delegates = arrayOf<
            AdapterDelegate<
                ErgonomicItem,
                out ErgonomicItem,
                out RecyclerView.ViewHolder
                >
            >(original)
        val registry = braidDelegateRegistry(*delegates)

        delegates[0] = replacement

        assertSame(
            original,
            registry.resolve(
                ErgonomicImageItem(id = 1L, url = "image.png")
            ).delegate
        )
    }

    @Test
    fun emptyRegistryBlockUsesRegistryFailFastValidation() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            braidDelegateRegistry<ErgonomicItem> { }
        }

        assertTrue(error.message.orEmpty().contains("at least one delegate"))
    }

    @Test
    fun emptyRegistryVarargUsesRegistryFailFastValidation() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            braidDelegateRegistry<ErgonomicItem>()
        }

        assertTrue(error.message.orEmpty().contains("at least one delegate"))
    }

    @Test
    fun publicRegistryItemCallbackUsesDelegateDiffRules() {
        val registry = braidDelegateRegistry<ErgonomicItem> {
            registerTextDelegate()
        }

        assertTrue(
            registry.itemCallback.areItemsTheSame(
                ErgonomicTextItem(id = 1L, text = "Old"),
                ErgonomicTextItem(id = 1L, text = "New")
            )
        )
        assertFalse(
            registry.itemCallback.areContentsTheSame(
                ErgonomicTextItem(id = 1L, text = "Old"),
                ErgonomicTextItem(id = 1L, text = "New")
            )
        )
    }

    private fun textCallback(): DelegateItemCallback<ErgonomicItem> {
        val scope = ergonomicScope()
        scope.registerTextDelegate()
        return scope.callback()
    }
}

private sealed interface ErgonomicItem

private data class ErgonomicTextItem(val id: Long, val text: String, val isExpanded: Boolean = true) : ErgonomicItem

private data class ErgonomicImageItem(val id: Long, val url: String) : ErgonomicItem

private class TestBinding : ViewBinding {
    override fun getRoot(): View = throw NotImplementedError("Android views are not used by local unit tests.")
}

private val testInflater: (LayoutInflater, ViewGroup, Boolean) -> TestBinding =
    { _, _, _ ->
        throw NotImplementedError("ViewBinding inflation is not used by local unit tests.")
    }

private fun ergonomicScope(): BraidAdapterScope<ErgonomicItem> = BraidAdapterScope()

private fun BraidAdapterScope<ErgonomicItem>.registerTextDelegate() {
    viewBinding(
        inflate = testInflater,
        keySelector = ErgonomicTextItem::id
    ) { }
}

private fun BraidAdapterScope<ErgonomicItem>.registerImageDelegate() {
    viewBinding(
        inflate = testInflater,
        keySelector = ErgonomicImageItem::id
    ) { }
}

private fun textDelegate(): AdapterDelegate<
    ErgonomicItem,
    out ErgonomicItem,
    out RecyclerView.ViewHolder
    > {
    val scope = ergonomicScope()
    scope.registerTextDelegate()
    return scope.delegateSnapshot().single()
}

private fun BraidAdapterScope<ErgonomicItem>.registry(): DelegateRegistry<ErgonomicItem> =
    DelegateRegistry(delegateSnapshot())

private fun BraidAdapterScope<ErgonomicItem>.callback(): DelegateItemCallback<ErgonomicItem> =
    DelegateItemCallback(registry())

@Suppress("unused")
private fun inferredSingleItemFactoryUsage(): BraidListAdapter<ErgonomicTextItem> = braidListAdapter(
    inflate = testInflater,
    keySelector = ErgonomicTextItem::id
) { }

@Suppress("unused")
private fun explicitSingleItemFactoryUsage(): BraidListAdapter<ErgonomicTextItem> =
    braidListAdapter<ErgonomicTextItem, TestBinding, Long>(
        inflate = testInflater,
        keySelector = ErgonomicTextItem::id
    ) { }

@Suppress("unused")
private fun configuredSingleItemFactoryUsage(): BraidListAdapter<ErgonomicTextItem> = braidListAdapter(
    inflate = testInflater,
    keySelector = ErgonomicTextItem::id,
    areContentsTheSame = { oldItem, newItem ->
        oldItem.text == newItem.text
    },
    getChangePayload = { _, _ -> Any() },
    bindPayloads = { _, _ -> }
) { }

private class ImageDelegate : AdapterDelegate<ErgonomicItem, ErgonomicImageItem, RecyclerView.ViewHolder>() {

    override fun isForItem(item: ErgonomicItem): Boolean = item is ErgonomicImageItem

    override fun createViewHolder(parent: ViewGroup): RecyclerView.ViewHolder =
        throw NotImplementedError("ViewHolder creation is not used by local unit tests.")

    override fun bindViewHolder(holder: RecyclerView.ViewHolder, item: ErgonomicImageItem, payloads: List<Any>): Unit =
        Unit

    override fun areItemsTheSame(oldItem: ErgonomicImageItem, newItem: ErgonomicImageItem): Boolean =
        oldItem.id == newItem.id
}
