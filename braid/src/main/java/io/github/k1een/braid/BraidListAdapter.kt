package io.github.k1een.braid

import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

/**
 * AndroidX [ListAdapter] that delegates item rendering and diffing to a
 * [DelegateRegistry].
 *
 * The adapter relies on the standard `ListAdapter` list storage, `submitList`,
 * and asynchronous diff calculation. It does not keep a separate item list.
 *
 * @param Item item type stored by this adapter.
 * @param registry immutable registry used internally for adapter routing.
 */
public class BraidListAdapter<Item : Any>(
    private val registry: DelegateRegistry<Item>,
) : ListAdapter<Item, RecyclerView.ViewHolder>(registry.itemCallback) {

    /** Returns the registry view type for the item at [position]. */
    override fun getItemViewType(position: Int): Int =
        registry.viewTypeFor(getItem(position))

    /** Creates a view holder through the delegate registered for [viewType]. */
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): RecyclerView.ViewHolder = registry.createViewHolder(parent, viewType)

    /** Fully binds the item at [position] with an empty payload list. */
    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
    ): Unit = registry.bindViewHolder(
        holder = holder,
        item = getItem(position),
        payloads = emptyList(),
    )

    /**
     * Partially binds the item at [position] with [payloads], or performs a full
     * bind when [payloads] is empty.
     */
    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
        } else {
            registry.bindViewHolder(
                holder = holder,
                item = getItem(position),
                payloads = payloads,
            )
        }
    }

    /** Routes the recycled lifecycle callback to the holder's delegate. */
    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        registry.onViewRecycled(holder)
        super.onViewRecycled(holder)
    }

    /** Routes the attached lifecycle callback to the holder's delegate. */
    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        super.onViewAttachedToWindow(holder)
        registry.onViewAttachedToWindow(holder)
    }

    /** Routes the detached lifecycle callback to the holder's delegate. */
    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        registry.onViewDetachedFromWindow(holder)
        super.onViewDetachedFromWindow(holder)
    }

    /** Combines the delegate result with the standard adapter result. */
    override fun onFailedToRecycleView(holder: RecyclerView.ViewHolder): Boolean {
        val delegateResult = registry.onFailedToRecycleView(holder)
        val adapterResult = super.onFailedToRecycleView(holder)
        return delegateResult || adapterResult
    }
}
