@file:JvmName("BraidPagingAdapters")

package io.github.k1een.braid.paging

import androidx.paging.PagingConfig
import androidx.recyclerview.widget.RecyclerView
import io.github.k1een.braid.AdapterDelegate
import io.github.k1een.braid.BraidAdapterScope
import io.github.k1een.braid.braidDelegateRegistry

/**
 * Creates a [BraidPagingDataAdapter] from existing [delegates].
 *
 * Configure the associated Pager with [PagingConfig] and
 * `enablePlaceholders = false`.
 */
public fun <Item : Any> braidPagingDataAdapter(
    vararg delegates: AdapterDelegate<
        Item,
        out Item,
        out RecyclerView.ViewHolder
        >
): BraidPagingDataAdapter<Item> = BraidPagingDataAdapter(
    registry = braidDelegateRegistry(*delegates)
)

/**
 * Creates a [BraidPagingDataAdapter] from delegates declared in [block].
 *
 * The [BraidAdapterScope] is used only during construction, and delegate order
 * is preserved in the immutable registry snapshot shared with the adapter.
 *
 * Configure the associated Pager with [PagingConfig] and
 * `enablePlaceholders = false`.
 */
public fun <Item : Any> braidPagingDataAdapter(
    block: BraidAdapterScope<Item>.() -> Unit
): BraidPagingDataAdapter<Item> = BraidPagingDataAdapter(
    registry = braidDelegateRegistry(block)
)
