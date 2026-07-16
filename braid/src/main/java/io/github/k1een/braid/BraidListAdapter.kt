package io.github.k1een.braid

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding

/**
 * Transient scope used to compose delegates for a [DelegateListAdapter].
 *
 * The scope is mutable only while the adapter is being built. Braid takes a
 * defensive snapshot before creating the immutable [DelegateRegistry].
 *
 * @param BaseItem common item type stored by the adapter.
 */
public class BraidListAdapterScope<BaseItem : Any> internal constructor() {

    private val delegates = mutableListOf<
        AdapterDelegate<BaseItem, out BaseItem, out RecyclerView.ViewHolder>,
    >()

    /**
     * Registers an existing [delegate] in declaration order.
     *
     * Use this method for reusable delegates and delegates with stateful view
     * holders or specialized lifecycle handling.
     */
    public fun delegate(
        delegate: AdapterDelegate<
            BaseItem,
            out BaseItem,
            out RecyclerView.ViewHolder,
        >,
    ): Unit {
        delegates += delegate
    }

    /**
     * Registers a type-safe ViewBinding delegate for [Item].
     *
     * [keySelector] defines item identity. Content comparison uses structural
     * equality by default. [matches] can distinguish multiple representations
     * of the same [Item] type. When RecyclerView supplies payloads,
     * [bindPayloads] handles them when provided; otherwise [bind] performs a
     * full bind.
     *
     * [matches], [keySelector], [areContentsTheSame], and [getChangePayload]
     * must be fast, deterministic, and thread-safe because delegate resolution
     * and diffing may run off the main thread. Stateful items should use
     * [statefulViewBinding] or a reusable [StatefulViewBindingDelegate] through
     * [delegate].
     */
    public inline fun <
        reified Item : BaseItem,
        VB : ViewBinding,
        Key,
    > viewBinding(
        noinline inflate: (LayoutInflater, ViewGroup, Boolean) -> VB,
        noinline keySelector: (Item) -> Key,
        noinline matches: (Item) -> Boolean = { true },
        noinline areContentsTheSame: (Item, Item) -> Boolean = { old, new ->
            old == new
        },
        noinline getChangePayload: (Item, Item) -> Any? = { _, _ -> null },
        noinline bindPayloads: (VB.(Item, List<Any>) -> Unit)? = null,
        noinline bind: VB.(Item) -> Unit,
    ): Unit {
        delegate(
            createViewBindingDelegate(
                inflate = inflate,
                matcher = { baseItem: BaseItem ->
                    baseItem is Item && matches(baseItem)
                },
                keySelector = keySelector,
                contentComparator = areContentsTheSame,
                payloadProvider = getChangePayload,
                payloadBinder = bindPayloads,
                fullBinder = bind,
            ),
        )
    }

    /**
     * Registers a type-safe ViewBinding delegate with holder-local [State].
     *
     * [stateFactory] creates state once for each ViewHolder. The same state is
     * reused by [bind] and every lifecycle callback for that holder; it is not
     * part of the adapter item or screen-state model. [keySelector] defines item
     * identity. [matches], [keySelector], [areContentsTheSame], and
     * [getChangePayload] must be fast, deterministic, and thread-safe because
     * delegate resolution and diffing may run off the main thread.
     *
     * [bind], [bindPayloads], and lifecycle callbacks operate on Android views
     * when RecyclerView invokes the corresponding adapter callbacks. Consumers
     * must release listeners or resources in [recycle] when necessary. This API
     * does not synchronize editable views with a ViewModel or define an input
     * conflict policy.
     */
    public inline fun <
        reified Item : BaseItem,
        VB : ViewBinding,
        State : Any,
        Key,
    > statefulViewBinding(
        noinline inflate: (LayoutInflater, ViewGroup, Boolean) -> VB,
        noinline keySelector: (Item) -> Key,
        noinline stateFactory: VB.() -> State,
        noinline matches: (Item) -> Boolean = { true },
        noinline areContentsTheSame: (Item, Item) -> Boolean = { old, new ->
            old == new
        },
        noinline getChangePayload: (Item, Item) -> Any? = { _, _ -> null },
        noinline bindPayloads: (VB.(Item, State, List<Any>) -> Unit)? = null,
        noinline recycle: VB.(State) -> Unit = {},
        noinline attachedToWindow: VB.(State) -> Unit = {},
        noinline detachedFromWindow: VB.(State) -> Unit = {},
        noinline failedToRecycle: VB.(State) -> Boolean = { false },
        noinline bind: VB.(Item, State) -> Unit,
    ): Unit {
        delegate(
            createStatefulViewBindingDelegate(
                inflate = inflate,
                matcher = { baseItem: BaseItem ->
                    baseItem is Item && matches(baseItem)
                },
                keySelector = keySelector,
                contentComparator = areContentsTheSame,
                payloadProvider = getChangePayload,
                stateFactory = stateFactory,
                fullBinder = bind,
                payloadBinder = bindPayloads,
                recycleCallback = recycle,
                attachedCallback = attachedToWindow,
                detachedCallback = detachedFromWindow,
                failedToRecycleCallback = failedToRecycle,
            ),
        )
    }

    internal fun delegateSnapshot(): List<
        AdapterDelegate<BaseItem, out BaseItem, out RecyclerView.ViewHolder>,
    > = delegates.toList()
}

/**
 * Creates a [DelegateListAdapter] from existing [delegates].
 *
 * Delegate order is preserved. An empty argument list is rejected by the
 * standard [DelegateRegistry] fail-fast validation.
 */
public fun <BaseItem : Any> braidListAdapter(
    vararg delegates: AdapterDelegate<
        BaseItem,
        out BaseItem,
        out RecyclerView.ViewHolder,
    >,
): DelegateListAdapter<BaseItem> = DelegateListAdapter(
    registry = DelegateRegistry(delegates.toList()),
)

/**
 * Creates a [DelegateListAdapter] from delegates declared in [block].
 *
 * The scope exists only for construction. Delegate order is preserved in a
 * defensive snapshot, and an empty block is rejected by [DelegateRegistry].
 */
public fun <BaseItem : Any> braidListAdapter(
    block: BraidListAdapterScope<BaseItem>.() -> Unit,
): DelegateListAdapter<BaseItem> {
    val scope = BraidListAdapterScope<BaseItem>()
    scope.block()

    return DelegateListAdapter(
        registry = DelegateRegistry(scope.delegateSnapshot()),
    )
}
