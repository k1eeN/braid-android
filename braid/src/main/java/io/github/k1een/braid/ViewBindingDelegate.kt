package io.github.k1een.braid

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding

/**
 * Convenience [AdapterDelegate] for ordinary ViewBinding-backed view holders.
 *
 * Concrete delegates still define their own item identity and content diffing.
 * ViewBinding delegates that require holder-local state should use
 * [StatefulViewBindingDelegate]. Delegates requiring a custom holder contract
 * may inherit directly from [AdapterDelegate].
 *
 * @param BaseItem common item type used by the adapter.
 * @param Item item subtype handled by this delegate.
 * @param VB ViewBinding type used to render [Item].
 * @param inflate function that creates [VB] for a RecyclerView parent.
 */
public abstract class ViewBindingDelegate<
    BaseItem : Any,
    Item : BaseItem,
    VB : ViewBinding,
>(
    private val inflate: (LayoutInflater, ViewGroup, Boolean) -> VB,
) : AdapterDelegate<
    BaseItem,
    Item,
    ViewBindingViewHolder<VB>,
>() {

    final override fun createViewHolder(
        parent: ViewGroup,
    ): ViewBindingViewHolder<VB> {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = inflate(layoutInflater, parent, false)
        return ViewBindingViewHolder(binding)
    }

    final override fun bindViewHolder(
        holder: ViewBindingViewHolder<VB>,
        item: Item,
        payloads: List<Any>,
    ) {
        bind(
            binding = holder.binding,
            item = item,
            payloads = payloads,
        )
    }

    /**
     * Binds [item] to [binding].
     *
     * An empty [payloads] list represents a full bind. Non-empty payloads are
     * forwarded unchanged from RecyclerView.
     */
    protected abstract fun bind(
        binding: VB,
        item: Item,
        payloads: List<Any>,
    )
}
