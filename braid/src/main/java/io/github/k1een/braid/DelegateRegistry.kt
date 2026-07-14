package io.github.k1een.braid

import android.view.ViewGroup
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

    internal fun viewTypeFor(item: BaseItem): Int = resolve(item).viewType

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

    internal fun createViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): RecyclerView.ViewHolder =
        registeredDelegateFor(viewType).delegate.createViewHolderErased(parent)

    internal fun bindViewHolder(
        holder: RecyclerView.ViewHolder,
        item: BaseItem,
        payloads: List<Any>,
    ) {
        val holderDelegate = registeredDelegateFor(holder.itemViewType)
        val itemDelegate = resolve(item)

        check(holderDelegate === itemDelegate) {
            "ViewHolder viewType=${holder.itemViewType} belongs to " +
                "${holderDelegate.delegate.javaClass.name}, but item type " +
                "${item.javaClass.name} resolves to ${itemDelegate.delegate.javaClass.name}."
        }

        holderDelegate.delegate.bindViewHolderErased(holder, item, payloads)
    }

    internal fun onViewRecycled(
        holder: RecyclerView.ViewHolder,
        viewType: Int,
    ): Unit = registeredDelegateFor(viewType).delegate.onViewRecycledErased(holder)

    internal fun onViewAttachedToWindow(
        holder: RecyclerView.ViewHolder,
        viewType: Int,
    ): Unit = registeredDelegateFor(viewType).delegate.onViewAttachedToWindowErased(holder)

    internal fun onViewDetachedFromWindow(
        holder: RecyclerView.ViewHolder,
        viewType: Int,
    ): Unit = registeredDelegateFor(viewType).delegate.onViewDetachedFromWindowErased(holder)

    internal fun onFailedToRecycleView(
        holder: RecyclerView.ViewHolder,
        viewType: Int,
    ): Boolean = registeredDelegateFor(viewType).delegate.onFailedToRecycleViewErased(holder)

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
}

internal class RegisteredDelegate<BaseItem : Any>(
    val viewType: Int,
    val delegate: AdapterDelegate<BaseItem, out BaseItem, out RecyclerView.ViewHolder>,
)
