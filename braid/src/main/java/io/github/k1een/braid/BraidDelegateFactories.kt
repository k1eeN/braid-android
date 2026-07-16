@file:JvmName("BraidDelegateFactories")

package io.github.k1een.braid

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding

// This binary bridge forwards every public stateless DSL option without changing semantics.
@Suppress("LongParameterList")
@PublishedApi
internal fun <
    BaseItem : Any,
    Item : BaseItem,
    VB : ViewBinding,
    Key
    > createViewBindingDelegate(
    inflate: (LayoutInflater, ViewGroup, Boolean) -> VB,
    matcher: (BaseItem) -> Boolean,
    keySelector: (Item) -> Key,
    contentComparator: (Item, Item) -> Boolean,
    payloadProvider: (Item, Item) -> Any?,
    payloadBinder: (VB.(Item, List<Any>) -> Unit)?,
    fullBinder: VB.(Item) -> Unit
): AdapterDelegate<BaseItem, Item, ViewBindingViewHolder<VB>> = LambdaViewBindingDelegate(
    inflate = inflate,
    matcher = matcher,
    keySelector = keySelector,
    contentComparator = contentComparator,
    payloadProvider = payloadProvider,
    payloadBinder = payloadBinder,
    fullBinder = fullBinder
)

// This binary bridge forwards every stateful lifecycle option without introducing a config object.
@Suppress("LongParameterList")
@PublishedApi
internal fun <
    BaseItem : Any,
    Item : BaseItem,
    VB : ViewBinding,
    State : Any,
    Key
    > createStatefulViewBindingDelegate(
    inflate: (LayoutInflater, ViewGroup, Boolean) -> VB,
    matcher: (BaseItem) -> Boolean,
    keySelector: (Item) -> Key,
    contentComparator: (Item, Item) -> Boolean,
    payloadProvider: (Item, Item) -> Any?,
    stateFactory: VB.() -> State,
    fullBinder: VB.(Item, State) -> Unit,
    payloadBinder: (VB.(Item, State, List<Any>) -> Unit)?,
    recycleCallback: VB.(State) -> Unit,
    attachedCallback: VB.(State) -> Unit,
    detachedCallback: VB.(State) -> Unit,
    failedToRecycleCallback: VB.(State) -> Boolean
): AdapterDelegate<
    BaseItem,
    Item,
    StatefulViewBindingViewHolder<VB, State>
    > =
    LambdaStatefulViewBindingDelegate(
        inflate = inflate,
        matcher = matcher,
        keySelector = keySelector,
        contentComparator = contentComparator,
        payloadProvider = payloadProvider,
        stateFactory = stateFactory,
        fullBinder = fullBinder,
        payloadBinder = payloadBinder,
        recycleCallback = recycleCallback,
        attachedCallback = attachedCallback,
        detachedCallback = detachedCallback,
        failedToRecycleCallback = failedToRecycleCallback
    )
