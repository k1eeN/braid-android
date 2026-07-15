package io.github.k1een.braid

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding

@PublishedApi
internal fun <
    BaseItem : Any,
    Item : BaseItem,
    VB : ViewBinding,
    Key,
> createViewBindingDelegate(
    inflate: (LayoutInflater, ViewGroup, Boolean) -> VB,
    matcher: (BaseItem) -> Boolean,
    keySelector: (Item) -> Key,
    contentComparator: (Item, Item) -> Boolean,
    payloadProvider: (Item, Item) -> Any?,
    payloadBinder: (VB.(Item, List<Any>) -> Unit)?,
    fullBinder: VB.(Item) -> Unit,
): AdapterDelegate<BaseItem, Item, ViewBindingViewHolder<VB>> =
    LambdaViewBindingDelegate(
        inflate = inflate,
        matcher = matcher,
        keySelector = keySelector,
        contentComparator = contentComparator,
        payloadProvider = payloadProvider,
        payloadBinder = payloadBinder,
        fullBinder = fullBinder,
    )

internal class LambdaViewBindingDelegate<
    BaseItem : Any,
    Item : BaseItem,
    VB : ViewBinding,
    Key,
>(
    inflate: (LayoutInflater, ViewGroup, Boolean) -> VB,
    private val matcher: (BaseItem) -> Boolean,
    private val keySelector: (Item) -> Key,
    private val contentComparator: (Item, Item) -> Boolean,
    private val payloadProvider: (Item, Item) -> Any?,
    private val payloadBinder: (VB.(Item, List<Any>) -> Unit)?,
    private val fullBinder: VB.(Item) -> Unit,
) : ViewBindingDelegate<BaseItem, Item, VB>(inflate) {

    override fun isForItem(item: BaseItem): Boolean = matcher(item)

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
        item: Item,
        payloads: List<Any>,
    ) {
        val partialBinder = payloadBinder
        if (payloads.isNotEmpty() && partialBinder != null) {
            partialBinder.invoke(binding, item, payloads)
        } else {
            fullBinder.invoke(binding, item)
        }
    }
}
