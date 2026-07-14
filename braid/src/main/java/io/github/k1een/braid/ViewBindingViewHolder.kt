package io.github.k1een.braid

import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding

/**
 * A [RecyclerView.ViewHolder] that exposes the [binding] created for its item view.
 *
 * @param VB concrete ViewBinding type owned by this holder.
 * @property binding binding whose root view is managed by RecyclerView.
 */
public class ViewBindingViewHolder<VB : ViewBinding> internal constructor(
    public val binding: VB,
) : RecyclerView.ViewHolder(binding.root)
