package io.github.k1een.braid

import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

/**
 * Immutable registry that assigns view types and routes items to delegates.
 *
 * Every item must match exactly one delegate. View types are zero-based and
 * follow the order of [delegates]. The input list is copied during construction.
 *
 * @param BaseItem common item type used by the adapter.
 * @param delegates delegates available to the adapter.
 * @throws IllegalArgumentException when [delegates] is empty.
 */
public class DelegateRegistry<BaseItem : Any>(
    delegates: List<AdapterDelegate<BaseItem, out BaseItem, out RecyclerView.ViewHolder>>,
) {

    init {
        require(delegates.isNotEmpty()) {
            "DelegateRegistry requires at least one delegate."
        }
    }

    private val registeredDelegates: List<RegisteredDelegate<BaseItem>> =
        delegates.mapIndexed { viewType, delegate ->
            RegisteredDelegate(
                viewType = viewType,
                delegate = delegate,
            )
        }

    private val ownerToken: Any = Any()

    /**
     * Diff callback backed by this registry's per-delegate identity, content,
     * and payload rules.
     *
     * This property is intended for advanced adapter integrations. Reusing it
     * keeps diffing and view routing on the same immutable registry snapshot.
     */
    public val itemCallback: DiffUtil.ItemCallback<BaseItem> =
        DelegateItemCallback(this)

    /**
     * Returns the view type assigned to the single delegate matching [item].
     *
     * The returned value must be passed back to this registry when creating a
     * holder. Missing and overlapping delegates preserve strict diagnostics.
     */
    public fun viewTypeFor(item: BaseItem): Int = resolve(item).viewType

    internal fun resolve(item: BaseItem): RegisteredDelegate<BaseItem> {
        var firstMatch: RegisteredDelegate<BaseItem>? = null
        var multipleMatches: MutableList<RegisteredDelegate<BaseItem>>? = null

        for (registeredDelegate in registeredDelegates) {
            if (!registeredDelegate.delegate.isForItem(item)) {
                continue
            }

            val existingMatch = firstMatch
            if (existingMatch == null) {
                firstMatch = registeredDelegate
            } else {
                val matches = multipleMatches
                    ?: mutableListOf(existingMatch).also { multipleMatches = it }
                matches += registeredDelegate
            }
        }

        multipleMatches?.let { matches ->
            throw IllegalStateException(multipleDelegatesMessage(item, matches))
        }

        return firstMatch
            ?: throw IllegalStateException(noMatchingDelegateMessage(item))
    }

    internal fun registeredDelegateFor(viewType: Int): RegisteredDelegate<BaseItem> =
        registeredDelegates.getOrNull(viewType)
            ?: throw IllegalStateException(
                "No delegate is registered for viewType=$viewType. " +
                    "Registered viewTypes: ${registeredDelegates.map { it.viewType }}.",
            )

    /**
     * Creates a holder for a [viewType] previously returned by [viewTypeFor].
     *
     * This method is intended for advanced adapter integrations.
     */
    public fun createViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): RecyclerView.ViewHolder {
        val holder = registeredDelegateFor(viewType)
            .delegate
            .createViewHolderErased(parent)

        holder.itemView.setTag(
            R.id.braid_internal_delegate_route,
            HolderRoute(
                ownerToken = ownerToken,
                localViewType = viewType,
            ),
        )

        return holder
    }

    /**
     * Binds [item] to [holder] using the matching delegate.
     *
     * The holder must originate from [createViewHolder]. An empty [payloads]
     * list represents a full bind. Routing is independent of the holder's
     * externally visible view type, which may be isolated by a parent adapter.
     */
    public fun bindViewHolder(
        holder: RecyclerView.ViewHolder,
        item: BaseItem,
        payloads: List<Any>,
    ) {
        val route = holderRoute(holder)
        val holderDelegate = registeredDelegateFor(route.localViewType)
        val itemDelegate = resolve(item)

        check(holderDelegate === itemDelegate) {
            "ViewHolder local viewType=${route.localViewType} belongs to " +
                "${holderDelegate.delegate.javaClass.name}, but item type " +
                "${item.javaClass.name} resolves to ${itemDelegate.delegate.javaClass.name}."
        }

        holderDelegate.delegate.bindViewHolderErased(holder, item, payloads)
    }

    /**
     * Routes recycling to the delegate that created [holder].
     *
     * This method is intended for advanced adapter integrations. The holder
     * must originate from [createViewHolder].
     */
    public fun onViewRecycled(
        holder: RecyclerView.ViewHolder,
    ): Unit = registeredDelegateFor(holderRoute(holder).localViewType)
        .delegate
        .onViewRecycledErased(holder)

    /**
     * Routes attachment to the delegate that created [holder].
     *
     * This method is intended for advanced adapter integrations. The holder
     * must originate from [createViewHolder].
     */
    public fun onViewAttachedToWindow(
        holder: RecyclerView.ViewHolder,
    ): Unit = registeredDelegateFor(holderRoute(holder).localViewType)
        .delegate
        .onViewAttachedToWindowErased(holder)

    /**
     * Routes detachment to the delegate that created [holder].
     *
     * This method is intended for advanced adapter integrations. The holder
     * must originate from [createViewHolder].
     */
    public fun onViewDetachedFromWindow(
        holder: RecyclerView.ViewHolder,
    ): Unit = registeredDelegateFor(holderRoute(holder).localViewType)
        .delegate
        .onViewDetachedFromWindowErased(holder)

    /**
     * Returns the recycling decision from the delegate that created [holder].
     *
     * This method is intended for advanced adapter integrations. The holder
     * must originate from [createViewHolder]. Adapter integrations should
     * combine this decision with their superclass result.
     */
    public fun onFailedToRecycleView(
        holder: RecyclerView.ViewHolder,
    ): Boolean = registeredDelegateFor(holderRoute(holder).localViewType)
        .delegate
        .onFailedToRecycleViewErased(holder)

    internal fun areItemsTheSame(
        registeredDelegate: RegisteredDelegate<BaseItem>,
        oldItem: BaseItem,
        newItem: BaseItem,
    ): Boolean = registeredDelegate.delegate.areItemsTheSameErased(oldItem, newItem)

    internal fun areContentsTheSame(
        registeredDelegate: RegisteredDelegate<BaseItem>,
        oldItem: BaseItem,
        newItem: BaseItem,
    ): Boolean = registeredDelegate.delegate.areContentsTheSameErased(oldItem, newItem)

    internal fun getChangePayload(
        registeredDelegate: RegisteredDelegate<BaseItem>,
        oldItem: BaseItem,
        newItem: BaseItem,
    ): Any? = registeredDelegate.delegate.getChangePayloadErased(oldItem, newItem)

    private fun noMatchingDelegateMessage(item: BaseItem): String =
        "No delegate matches item type ${item.javaClass.name}. " +
            "Registered delegates: ${registeredDelegateNames(registeredDelegates)}."

    private fun multipleDelegatesMessage(
        item: BaseItem,
        matches: List<RegisteredDelegate<BaseItem>>,
    ): String =
        "Multiple delegates match item type ${item.javaClass.name}. " +
            "Matching delegates: ${registeredDelegateNames(matches)}. " +
            "Registered delegates: ${registeredDelegateNames(registeredDelegates)}."

    private fun registeredDelegateNames(
        delegates: List<RegisteredDelegate<BaseItem>>,
    ): List<String> = delegates.map { registeredDelegate ->
        "viewType=${registeredDelegate.viewType}:${registeredDelegate.delegate.javaClass.name}"
    }

    private fun holderRoute(holder: RecyclerView.ViewHolder): HolderRoute {
        val route = holder.itemView.getTag(
            R.id.braid_internal_delegate_route,
        ) as? HolderRoute

        if (route == null || route.ownerToken !== ownerToken) {
            throw IllegalStateException(
                "ViewHolder was not created by this DelegateRegistry. " +
                    "Braid adapters must create holders through " +
                    "DelegateRegistry.createViewHolder().",
            )
        }

        return route
    }
}

private class HolderRoute(
    val ownerToken: Any,
    val localViewType: Int,
)

internal class RegisteredDelegate<BaseItem : Any>(
    val viewType: Int,
    val delegate: AdapterDelegate<BaseItem, out BaseItem, out RecyclerView.ViewHolder>,
)
