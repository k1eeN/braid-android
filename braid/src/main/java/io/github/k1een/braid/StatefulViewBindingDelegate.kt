package io.github.k1een.braid

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding

/**
 * A ViewBinding-backed [AdapterDelegate] with state owned by each ViewHolder.
 *
 * [State] is created once for every holder and reused when that holder is bound
 * or receives RecyclerView lifecycle callbacks. The consumer owns the contents
 * of [State] and is responsible for releasing listeners or resources from
 * [recycle] when necessary. The state is not an adapter item or screen-state
 * model and Braid never stores the currently bound item in the holder.
 *
 * Stateless items should use [ViewBindingDelegate] or
 * [BraidListAdapterScope.viewBinding].
 *
 * @param BaseItem common item type used by the adapter.
 * @param Item item subtype handled by this delegate.
 * @param VB ViewBinding type used to render [Item].
 * @param State holder-local state type used by this delegate.
 * @param inflate function that creates [VB] for a RecyclerView parent.
 */
public abstract class StatefulViewBindingDelegate<
    BaseItem : Any,
    Item : BaseItem,
    VB : ViewBinding,
    State : Any,
>(
    private val inflate: (LayoutInflater, ViewGroup, Boolean) -> VB,
) : AdapterDelegate<
    BaseItem,
    Item,
    StatefulViewBindingViewHolder<VB, State>,
>() {

    final override fun createViewHolder(
        parent: ViewGroup,
    ): StatefulViewBindingViewHolder<VB, State> {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = inflate(layoutInflater, parent, false)
        val state = createState(binding)
        return StatefulViewBindingViewHolder(binding, state)
    }

    final override fun bindViewHolder(
        holder: StatefulViewBindingViewHolder<VB, State>,
        item: Item,
        payloads: List<Any>,
    ) {
        bind(
            binding = holder.binding,
            state = holder.state,
            item = item,
            payloads = payloads,
        )
    }

    final override fun onViewRecycled(
        holder: StatefulViewBindingViewHolder<VB, State>,
    ) {
        recycle(holder.binding, holder.state)
    }

    final override fun onViewAttachedToWindow(
        holder: StatefulViewBindingViewHolder<VB, State>,
    ) {
        attachedToWindow(holder.binding, holder.state)
    }

    final override fun onViewDetachedFromWindow(
        holder: StatefulViewBindingViewHolder<VB, State>,
    ) {
        detachedFromWindow(holder.binding, holder.state)
    }

    final override fun onFailedToRecycleView(
        holder: StatefulViewBindingViewHolder<VB, State>,
    ): Boolean = failedToRecycle(holder.binding, holder.state)

    /** Creates the holder-local [State] for [binding]. */
    protected abstract fun createState(binding: VB): State

    /**
     * Binds [item] using the holder's [binding] and [state].
     *
     * [payloads] are forwarded unchanged from RecyclerView. An empty list
     * represents a full bind.
     */
    protected abstract fun bind(
        binding: VB,
        state: State,
        item: Item,
        payloads: List<Any>,
    )

    /** Releases listeners or resources associated with [binding] and [state]. */
    protected open fun recycle(
        binding: VB,
        state: State,
    ): Unit = Unit

    /** Handles attachment of the holder that owns [binding] and [state]. */
    protected open fun attachedToWindow(
        binding: VB,
        state: State,
    ): Unit = Unit

    /** Handles detachment of the holder that owns [binding] and [state]. */
    protected open fun detachedFromWindow(
        binding: VB,
        state: State,
    ): Unit = Unit

    /**
     * Handles a failed recycling attempt for the holder that owns [binding]
     * and [state]. Return `true` when the holder may still be recycled.
     */
    protected open fun failedToRecycle(
        binding: VB,
        state: State,
    ): Boolean = false
}
