package io.github.k1een.braid

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding

@PublishedApi
internal fun <
    BaseItem : Any,
    Item : BaseItem,
    VB : ViewBinding,
    State : Any,
    Key,
> createStatefulViewBindingDelegate(
    inflate: (LayoutInflater, ViewGroup, Boolean) -> VB,
    matcher: (BaseItem) -> Boolean,
    keySelector: (Item) -> Key,
    contentComparator: (Item, Item) -> Boolean,
    payloadProvider: (Item, Item) -> Any?,
    stateFactory: VB.() -> State,
    fullBinder: VB.(Item, State) -> Unit,
    payloadBinder: (VB.(Item, State, List<Any>) -> Unit)?,
    recycleCallback: VB.(State) -> Unit,
    attachedCallback: VB.(State) -> Unit,
    detachedCallback: VB.(State) -> Unit,
    failedToRecycleCallback: VB.(State) -> Boolean,
): AdapterDelegate<
    BaseItem,
    Item,
    StatefulViewBindingViewHolder<VB, State>,
> = LambdaStatefulViewBindingDelegate(
    inflate = inflate,
    matcher = matcher,
    keySelector = keySelector,
    contentComparator = contentComparator,
    payloadProvider = payloadProvider,
    stateFactory = stateFactory,
    fullBinder = fullBinder,
    payloadBinder = payloadBinder,
    recycleCallback = recycleCallback,
    attachedCallback = attachedCallback,
    detachedCallback = detachedCallback,
    failedToRecycleCallback = failedToRecycleCallback,
)

internal class LambdaStatefulViewBindingDelegate<
    BaseItem : Any,
    Item : BaseItem,
    VB : ViewBinding,
    State : Any,
    Key,
>(
    inflate: (LayoutInflater, ViewGroup, Boolean) -> VB,
    private val matcher: (BaseItem) -> Boolean,
    private val keySelector: (Item) -> Key,
    private val contentComparator: (Item, Item) -> Boolean,
    private val payloadProvider: (Item, Item) -> Any?,
    private val stateFactory: VB.() -> State,
    private val fullBinder: VB.(Item, State) -> Unit,
    private val payloadBinder: (VB.(Item, State, List<Any>) -> Unit)?,
    private val recycleCallback: VB.(State) -> Unit,
    private val attachedCallback: VB.(State) -> Unit,
    private val detachedCallback: VB.(State) -> Unit,
    private val failedToRecycleCallback: VB.(State) -> Boolean,
) : StatefulViewBindingDelegate<BaseItem, Item, VB, State>(inflate) {

    override fun isForItem(item: BaseItem): Boolean = matcher(item)

    override fun createState(binding: VB): State = stateFactory.invoke(binding)

    override fun areItemsTheSame(
        oldItem: Item,
        newItem: Item,
    ): Boolean = keySelector(oldItem) == keySelector(newItem)

    override fun areContentsTheSame(
        oldItem: Item,
        newItem: Item,
    ): Boolean = contentComparator(oldItem, newItem)

    override fun getChangePayload(
        oldItem: Item,
        newItem: Item,
    ): Any? = payloadProvider(oldItem, newItem)

    override fun bind(
        binding: VB,
        state: State,
        item: Item,
        payloads: List<Any>,
    ) {
        val partialBinder = payloadBinder
        if (payloads.isNotEmpty() && partialBinder != null) {
            partialBinder.invoke(binding, item, state, payloads)
        } else {
            fullBinder.invoke(binding, item, state)
        }
    }

    override fun recycle(binding: VB, state: State) {
        recycleCallback.invoke(binding, state)
    }

    override fun attachedToWindow(binding: VB, state: State) {
        attachedCallback.invoke(binding, state)
    }

    override fun detachedFromWindow(binding: VB, state: State) {
        detachedCallback.invoke(binding, state)
    }

    override fun failedToRecycle(binding: VB, state: State): Boolean =
        failedToRecycleCallback.invoke(binding, state)
}
