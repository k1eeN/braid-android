package io.github.k1een.braid

import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding

/**
 * Opaque [RecyclerView.ViewHolder] implementation used by [ViewBindingDelegate].
 *
 * The typed binding is passed to consumers through [ViewBindingDelegate.bind];
 * consumers should not read it directly from this holder.
 *
 * @param VB concrete ViewBinding type owned by this holder.
 */
public class ViewBindingViewHolder<VB : ViewBinding> internal constructor(internal val binding: VB) :
    RecyclerView.ViewHolder(binding.root)
