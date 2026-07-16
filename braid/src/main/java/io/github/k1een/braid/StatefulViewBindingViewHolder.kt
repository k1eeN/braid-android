package io.github.k1een.braid

import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding

/**
 * A ViewBinding-backed [RecyclerView.ViewHolder] with holder-local [State].
 *
 * Braid creates the state once together with this holder and reuses it for every
 * bind and lifecycle callback of the same holder. The holder does not retain an
 * adapter item or own any application lifecycle.
 *
 * @param VB concrete ViewBinding type owned by this holder.
 * @param State holder-local state type owned by this holder.
 */
public class StatefulViewBindingViewHolder<
    VB : ViewBinding,
    State : Any
    > internal constructor(
    internal val binding: VB,
    internal val state: State
) : RecyclerView.ViewHolder(binding.root)
