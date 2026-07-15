package io.github.k1een.braid

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewbinding.ViewBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatefulViewBindingDelegateInstrumentedTest {

    @Test
    fun stateFactoryIsCalledOncePerCreatedHolder() = onMainThread {
        var stateFactoryCalls = 0
        val delegate = instrumentedDelegate(
            stateFactory = {
                stateFactoryCalls += 1
                InstrumentedHolderState()
            },
        )
        val holder = delegate.createViewHolder(parent())

        delegate.bindViewHolder(holder, InstrumentedItem(id = 1L), emptyList())
        delegate.bindViewHolder(holder, InstrumentedItem(id = 1L), emptyList())
        delegate.onViewRecycled(holder)

        assertEquals(1, stateFactoryCalls)
    }

    @Test
    fun sameStateIsReusedForRepeatedBindsOfOneHolder() = onMainThread {
        val boundStates = mutableListOf<InstrumentedHolderState>()
        val delegate = instrumentedDelegate(
            fullBinder = { _, state -> boundStates += state },
        )
        val holder = delegate.createViewHolder(parent())

        delegate.bindViewHolder(holder, InstrumentedItem(id = 1L), emptyList())
        delegate.bindViewHolder(holder, InstrumentedItem(id = 1L), emptyList())

        assertEquals(2, boundStates.size)
        assertSame(boundStates[0], boundStates[1])
        assertSame(holder.state, boundStates[0])
    }

    @Test
    fun differentHoldersReceiveDifferentStates() = onMainThread {
        val delegate = instrumentedDelegate()

        val firstHolder = delegate.createViewHolder(parent())
        val secondHolder = delegate.createViewHolder(parent())

        assertNotSame(firstHolder.state, secondHolder.state)
    }

    @Test
    fun recycleReceivesTheHolderBindingAndState() = onMainThread {
        var recycledBinding: InstrumentedBinding? = null
        var recycledState: InstrumentedHolderState? = null
        val delegate = instrumentedDelegate(
            recycleCallback = { state ->
                recycledBinding = this
                recycledState = state
            },
        )
        val holder = delegate.createViewHolder(parent())

        delegate.onViewRecycled(holder)

        assertSame(holder.binding, recycledBinding)
        assertSame(holder.state, recycledState)
    }

    @Test
    fun attachedToWindowReceivesTheHolderState() = onMainThread {
        var attachedState: InstrumentedHolderState? = null
        val delegate = instrumentedDelegate(
            attachedCallback = { state -> attachedState = state },
        )
        val holder = delegate.createViewHolder(parent())

        delegate.onViewAttachedToWindow(holder)

        assertSame(holder.state, attachedState)
    }

    @Test
    fun detachedFromWindowReceivesTheHolderState() = onMainThread {
        var detachedState: InstrumentedHolderState? = null
        val delegate = instrumentedDelegate(
            detachedCallback = { state -> detachedState = state },
        )
        val holder = delegate.createViewHolder(parent())

        delegate.onViewDetachedFromWindow(holder)

        assertSame(holder.state, detachedState)
    }

    @Test
    fun failedToRecycleReturnsTheCallbackResult() = onMainThread {
        var failedState: InstrumentedHolderState? = null
        val delegate = instrumentedDelegate(
            failedToRecycleCallback = { state ->
                failedState = state
                true
            },
        )
        val holder = delegate.createViewHolder(parent())

        val result = delegate.onFailedToRecycleView(holder)

        assertTrue(result)
        assertSame(holder.state, failedState)
    }

    @Test
    fun emptyPayloadsInvokeTheFullBinder() = onMainThread {
        var fullBindCalls = 0
        var payloadBindCalls = 0
        val delegate = instrumentedDelegate(
            fullBinder = { _, _ -> fullBindCalls += 1 },
            payloadBinder = { _, _, _ -> payloadBindCalls += 1 },
        )
        val holder = delegate.createViewHolder(parent())

        delegate.bindViewHolder(holder, InstrumentedItem(id = 1L), emptyList())

        assertEquals(1, fullBindCalls)
        assertEquals(0, payloadBindCalls)
    }

    @Test
    fun nonEmptyPayloadsInvokeThePayloadBinderWithoutCopyingPayloads() = onMainThread {
        var fullBindCalled = false
        var receivedPayloads: List<Any>? = null
        val delegate = instrumentedDelegate(
            fullBinder = { _, _ -> fullBindCalled = true },
            payloadBinder = { _, _, payloads -> receivedPayloads = payloads },
        )
        val holder = delegate.createViewHolder(parent())
        val payloads: List<Any> = listOf(Any())

        delegate.bindViewHolder(holder, InstrumentedItem(id = 1L), payloads)

        assertFalse(fullBindCalled)
        assertSame(payloads, receivedPayloads)
    }

    @Test
    fun nonEmptyPayloadsFallBackToFullBinderWhenPayloadBinderIsMissing() =
        onMainThread {
            var fullBindCalls = 0
            val delegate = instrumentedDelegate(
                fullBinder = { _, _ -> fullBindCalls += 1 },
                payloadBinder = null,
            )
            val holder = delegate.createViewHolder(parent())

            delegate.bindViewHolder(
                holder,
                InstrumentedItem(id = 1L),
                listOf(Any()),
            )

            assertEquals(1, fullBindCalls)
        }
}

private data class InstrumentedItem(val id: Long)

private class InstrumentedHolderState

private class InstrumentedBinding private constructor(
    private val rootView: View,
) : ViewBinding {

    override fun getRoot(): View = rootView

    companion object {
        fun inflate(
            layoutInflater: LayoutInflater,
            parent: ViewGroup,
            attachToParent: Boolean,
        ): InstrumentedBinding {
            val root = View(layoutInflater.context)
            if (attachToParent) {
                parent.addView(root)
            }
            return InstrumentedBinding(root)
        }
    }
}

private typealias InstrumentedDelegate = AdapterDelegate<
    Any,
    InstrumentedItem,
    StatefulViewBindingViewHolder<InstrumentedBinding, InstrumentedHolderState>,
>

private fun instrumentedDelegate(
    stateFactory: InstrumentedBinding.() -> InstrumentedHolderState = {
        InstrumentedHolderState()
    },
    fullBinder: InstrumentedBinding.(
        InstrumentedItem,
        InstrumentedHolderState,
    ) -> Unit = { _, _ -> },
    payloadBinder: (InstrumentedBinding.(
        InstrumentedItem,
        InstrumentedHolderState,
        List<Any>,
    ) -> Unit)? = null,
    recycleCallback: InstrumentedBinding.(InstrumentedHolderState) -> Unit = {},
    attachedCallback: InstrumentedBinding.(InstrumentedHolderState) -> Unit = {},
    detachedCallback: InstrumentedBinding.(InstrumentedHolderState) -> Unit = {},
    failedToRecycleCallback:
        InstrumentedBinding.(InstrumentedHolderState) -> Boolean = { false },
): InstrumentedDelegate = createStatefulViewBindingDelegate(
    inflate = InstrumentedBinding::inflate,
    matcher = { item -> item is InstrumentedItem },
    keySelector = InstrumentedItem::id,
    contentComparator = { oldItem, newItem -> oldItem == newItem },
    payloadProvider = { _, _ -> null },
    stateFactory = stateFactory,
    fullBinder = fullBinder,
    payloadBinder = payloadBinder,
    recycleCallback = recycleCallback,
    attachedCallback = attachedCallback,
    detachedCallback = detachedCallback,
    failedToRecycleCallback = failedToRecycleCallback,
)

private fun parent(): ViewGroup = FrameLayout(
    InstrumentationRegistry.getInstrumentation().targetContext,
)

private fun onMainThread(block: () -> Unit) {
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
        block()
    }
}
