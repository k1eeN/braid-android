@file:JvmName("BraidAdapters")

package io.github.k1een.braid

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding

/**
 * Creates an immutable [DelegateRegistry] from existing [delegates].
 *
 * Delegate order is preserved in a defensive snapshot. An empty argument list
 * is rejected by the standard [DelegateRegistry] fail-fast validation.
 */
public fun <BaseItem : Any> braidDelegateRegistry(
    vararg delegates: AdapterDelegate<
        BaseItem,
        out BaseItem,
        out RecyclerView.ViewHolder,
    >,
): DelegateRegistry<BaseItem> = DelegateRegistry(delegates.toList())

/**
 * Creates an immutable [DelegateRegistry] from delegates declared in [block].
 *
 * The scope exists only for construction. Delegate order is preserved in a
 * defensive snapshot, and an empty block is rejected by [DelegateRegistry].
 */
public fun <BaseItem : Any> braidDelegateRegistry(
    block: BraidAdapterScope<BaseItem>.() -> Unit,
): DelegateRegistry<BaseItem> {
    val scope = BraidAdapterScope<BaseItem>()
    scope.block()

    return DelegateRegistry(scope.delegateSnapshot())
}

/**
 * Creates a [BraidListAdapter] from existing [delegates].
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
): BraidListAdapter<BaseItem> = BraidListAdapter(
    registry = braidDelegateRegistry(*delegates),
)

/**
 * Creates a [BraidListAdapter] from delegates declared in [block].
 *
 * Use this overload for heterogeneous lists or when combining stateless,
 * stateful, and reusable delegates. The scope exists only for construction;
 * delegate order is preserved in an immutable registry snapshot.
 */
public fun <BaseItem : Any> braidListAdapter(
    block: BraidAdapterScope<BaseItem>.() -> Unit,
): BraidListAdapter<BaseItem> = BraidListAdapter(
    registry = braidDelegateRegistry(block),
)

/**
 * Creates a [BraidListAdapter] for a single ViewBinding-backed item type.
 *
 * [keySelector] defines item identity, while content comparison uses structural
 * equality by default. [keySelector], [areContentsTheSame], and
 * [getChangePayload] must be fast, deterministic, and thread-safe because
 * diffing may run off the main thread. Use the block-based [braidListAdapter]
 * overload for lists with multiple item types. The adapter is created
 * immediately; this factory does not use lazy initialization.
 */
public inline fun <
    reified Item : Any,
    VB : ViewBinding,
    Key,
> braidListAdapter(
    noinline inflate: (LayoutInflater, ViewGroup, Boolean) -> VB,
    noinline keySelector: (Item) -> Key,
    noinline areContentsTheSame: (Item, Item) -> Boolean = { old, new ->
        old == new
    },
    noinline getChangePayload: (Item, Item) -> Any? = { _, _ -> null },
    noinline bindPayloads: (VB.(Item, List<Any>) -> Unit)? = null,
    noinline bind: VB.(Item) -> Unit,
): BraidListAdapter<Item> = braidListAdapter {
    viewBinding(
        inflate = inflate,
        keySelector = keySelector,
        areContentsTheSame = areContentsTheSame,
        getChangePayload = getChangePayload,
        bindPayloads = bindPayloads,
        bind = bind,
    )
}
