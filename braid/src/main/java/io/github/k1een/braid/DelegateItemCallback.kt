package io.github.k1een.braid

import androidx.recyclerview.widget.DiffUtil

internal class DelegateItemCallback<BaseItem : Any>(
    private val registry: DelegateRegistry<BaseItem>,
) : DiffUtil.ItemCallback<BaseItem>() {

    override fun areItemsTheSame(
        oldItem: BaseItem,
        newItem: BaseItem,
    ): Boolean {
        val oldDelegate = registry.resolve(oldItem)
        val newDelegate = registry.resolve(newItem)

        return isSameRegistration(oldDelegate, newDelegate) &&
            registry.areItemsTheSame(oldDelegate, oldItem, newItem)
    }

    override fun areContentsTheSame(
        oldItem: BaseItem,
        newItem: BaseItem,
    ): Boolean {
        val oldDelegate = registry.resolve(oldItem)
        val newDelegate = registry.resolve(newItem)

        return isSameRegistration(oldDelegate, newDelegate) &&
            registry.areContentsTheSame(oldDelegate, oldItem, newItem)
    }

    override fun getChangePayload(
        oldItem: BaseItem,
        newItem: BaseItem,
    ): Any? {
        val oldDelegate = registry.resolve(oldItem)
        val newDelegate = registry.resolve(newItem)

        return if (isSameRegistration(oldDelegate, newDelegate)) {
            registry.getChangePayload(oldDelegate, oldItem, newItem)
        } else {
            null
        }
    }

    private fun isSameRegistration(
        first: RegisteredDelegate<BaseItem>,
        second: RegisteredDelegate<BaseItem>,
    ): Boolean = first.viewType == second.viewType
}
