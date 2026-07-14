package io.github.k1een.braid.sample.list

import io.github.k1een.braid.ViewBindingDelegate
import io.github.k1een.braid.sample.databinding.ItemSampleBinding

class SampleItemDelegate(
    private val onClick: (SampleItem) -> Unit,
) : ViewBindingDelegate<
    SampleItem,
    SampleItem,
    ItemSampleBinding,
>(
    inflate = ItemSampleBinding::inflate,
) {

    override fun isForItem(item: SampleItem): Boolean = true

    override fun areItemsTheSame(
        oldItem: SampleItem,
        newItem: SampleItem,
    ): Boolean = oldItem.id == newItem.id

    override fun bind(
        binding: ItemSampleBinding,
        item: SampleItem,
        payloads: List<Any>,
    ) {
        binding.tvTitle.text = item.title
        binding.tvDescription.text = item.description
        binding.root.setOnClickListener {
            onClick(item)
        }
    }
}
