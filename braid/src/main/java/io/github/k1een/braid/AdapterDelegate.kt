package io.github.k1een.braid

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

/**
 * Owns creation, binding, diffing, and lifecycle handling for one [Item] subtype.
 *
 * Implementations of [isForItem] must return `true` only for values that can be
 * safely handled as [Item]. Braid uses this contract to keep type erasure inside
 * the library while exposing typed items and view holders to consumers.
 *
 * Typed lifecycle APIs and their erased routing bridges intentionally remain
 * together as one low-level contract.
 *
 * @param BaseItem common item type used by the adapter.
 * @param Item item subtype handled by this delegate.
 * @param VH view holder type created and handled by this delegate.
 */
@Suppress("TooManyFunctions")
public abstract class AdapterDelegate<
    BaseItem : Any,
    Item : BaseItem,
    VH : RecyclerView.ViewHolder
    > {

    /**
     * Returns whether this delegate handles [item].
     *
     * A `true` result guarantees that [item] can be treated as [Item]. This
     * method must be fast, deterministic, and safe to call from a background
     * thread because list diffing may resolve delegates off the main thread.
     */
    public abstract fun isForItem(item: BaseItem): Boolean

    /** Creates this delegate's view holder for [parent]. */
    public abstract fun createViewHolder(parent: ViewGroup): VH

    /**
     * Binds [item] to [holder].
     *
     * An empty [payloads] list represents a full bind. A non-empty list is
     * passed through unchanged from RecyclerView for a partial bind.
     */
    public abstract fun bindViewHolder(holder: VH, item: Item, payloads: List<Any>)

    /**
     * Returns whether [oldItem] and [newItem] represent the same list item.
     *
     * This method must be fast, deterministic, and thread-safe.
     */
    public abstract fun areItemsTheSame(oldItem: Item, newItem: Item): Boolean

    /**
     * Returns whether the rendered contents of [oldItem] and [newItem] are equal.
     *
     * The default implementation uses structural equality. Overrides must be
     * fast, deterministic, and thread-safe.
     */
    public open fun areContentsTheSame(oldItem: Item, newItem: Item): Boolean = oldItem == newItem

    /**
     * Returns a payload describing the visual change from [oldItem] to [newItem].
     *
     * The default implementation requests a full bind. Overrides must be fast,
     * deterministic, and thread-safe.
     */
    public open fun getChangePayload(oldItem: Item, newItem: Item): Any? = null

    /** Called when [holder] is recycled. */
    public open fun onViewRecycled(holder: VH): Unit = Unit

    /** Called when [holder] is attached to a window. */
    public open fun onViewAttachedToWindow(holder: VH): Unit = Unit

    /** Called when [holder] is detached from a window. */
    public open fun onViewDetachedFromWindow(holder: VH): Unit = Unit

    /**
     * Called when RecyclerView could not recycle [holder].
     *
     * Return `true` when the holder may still be recycled.
     */
    public open fun onFailedToRecycleView(holder: VH): Boolean = false

    internal fun createViewHolderErased(parent: ViewGroup): RecyclerView.ViewHolder = createViewHolder(parent)

    internal fun bindViewHolderErased(holder: RecyclerView.ViewHolder, item: BaseItem, payloads: List<Any>): Unit =
        bindViewHolder(holder.asTypedHolder(), item.asTypedItem(), payloads)

    internal fun areItemsTheSameErased(oldItem: BaseItem, newItem: BaseItem): Boolean =
        areItemsTheSame(oldItem.asTypedItem(), newItem.asTypedItem())

    internal fun areContentsTheSameErased(oldItem: BaseItem, newItem: BaseItem): Boolean =
        areContentsTheSame(oldItem.asTypedItem(), newItem.asTypedItem())

    internal fun getChangePayloadErased(oldItem: BaseItem, newItem: BaseItem): Any? =
        getChangePayload(oldItem.asTypedItem(), newItem.asTypedItem())

    internal fun onViewRecycledErased(holder: RecyclerView.ViewHolder): Unit = onViewRecycled(holder.asTypedHolder())

    internal fun onViewAttachedToWindowErased(holder: RecyclerView.ViewHolder): Unit =
        onViewAttachedToWindow(holder.asTypedHolder())

    internal fun onViewDetachedFromWindowErased(holder: RecyclerView.ViewHolder): Unit =
        onViewDetachedFromWindow(holder.asTypedHolder())

    internal fun onFailedToRecycleViewErased(holder: RecyclerView.ViewHolder): Boolean =
        onFailedToRecycleView(holder.asTypedHolder())

    // The registry resolves an item through isForItem before invoking typed APIs.
    @Suppress("UNCHECKED_CAST")
    private fun BaseItem.asTypedItem(): Item = this as Item

    // RecyclerView routes a holder back to the delegate that created its viewType.
    @Suppress("UNCHECKED_CAST")
    private fun RecyclerView.ViewHolder.asTypedHolder(): VH = this as VH
}
