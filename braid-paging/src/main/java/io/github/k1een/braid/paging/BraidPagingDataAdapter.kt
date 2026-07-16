package io.github.k1een.braid.paging

import android.view.ViewGroup
import androidx.paging.PagingConfig
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.k1een.braid.DelegateRegistry

/**
 * AndroidX [PagingDataAdapter] that delegates rendering and diffing through a
 * Braid [DelegateRegistry].
 *
 * This adapter intentionally does not support null Paging placeholders. Its
 * [PagingConfig] must use enablePlaceholders = false. Paging submission,
 * refresh, retry, snapshots, load states, and load-state adapter composition
 * remain the standard inherited Paging APIs. Create instances with
 * [braidPagingDataAdapter].
 *
 * @param Item non-null item type presented by Paging.
 * @param registry immutable registry used for diffing and view routing.
 */
public class BraidPagingDataAdapter<Item : Any> internal constructor(private val registry: DelegateRegistry<Item>) :
    PagingDataAdapter<Item, RecyclerView.ViewHolder>(registry.itemCallback) {

    /** Resolves a presented item without triggering Paging prefetch. */
    override fun getItemViewType(position: Int): Int = registry.viewTypeFor(
        requirePresentedItem(
            item = peek(position),
            position = position
        )
    )

    /** Creates a holder through the delegate registered for [viewType]. */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        registry.createViewHolder(parent, viewType)

    /** Fully binds the accessed item and preserves Paging prefetch signaling. */
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int): Unit = registry.bindViewHolder(
        holder = holder,
        item = requirePresentedItem(
            item = getItem(position),
            position = position
        ),
        payloads = emptyList()
    )

    /**
     * Partially binds with [payloads], or performs a full bind when they are
     * empty.
     */
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
        } else {
            registry.bindViewHolder(
                holder = holder,
                item = requirePresentedItem(
                    item = getItem(position),
                    position = position
                ),
                payloads = payloads
            )
        }
    }

    /** Routes recycling before invoking the standard adapter callback. */
    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        registry.onViewRecycled(holder)
        super.onViewRecycled(holder)
    }

    /** Routes attachment after invoking the standard adapter callback. */
    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        super.onViewAttachedToWindow(holder)
        registry.onViewAttachedToWindow(holder)
    }

    /** Routes detachment before invoking the standard adapter callback. */
    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        registry.onViewDetachedFromWindow(holder)
        super.onViewDetachedFromWindow(holder)
    }

    /** Combines delegate and standard adapter recycling decisions. */
    override fun onFailedToRecycleView(holder: RecyclerView.ViewHolder): Boolean {
        val delegateResult = registry.onFailedToRecycleView(holder)
        val adapterResult = super.onFailedToRecycleView(holder)
        return delegateResult || adapterResult
    }
}

internal fun <Item : Any> requirePresentedItem(item: Item?, position: Int): Item = checkNotNull(item) {
    "BraidPagingDataAdapter does not support Paging placeholders. " +
        "Configure PagingConfig with enablePlaceholders = false. " +
        "Null item at position=$position."
}
