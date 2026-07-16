# Braid

[![CI](https://github.com/k1eeN/braid-android/actions/workflows/ci.yml/badge.svg)](https://github.com/k1eeN/braid-android/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
![API](https://img.shields.io/badge/API-pre--release-orange)

Braid is a type-safe delegate layer for AndroidX `ListAdapter` and `PagingDataAdapter`. It keeps AndroidX responsible for list storage, diff execution, and paging while standardizing item rendering, per-type diff rules, payloads, ViewBinding, holder-local state, and strict delegate routing.

## Status

Braid is in active pre-release development. Its public API may still change before the first published alpha, and semantic compatibility is not guaranteed yet.

Integration behavior is being validated against adapters migrated from a real Android codebase. The artifacts have not been published to Maven Central, and there are no public Maven coordinates yet.

## Why Braid

A heterogeneous RecyclerView often repeats the same infrastructure: view-type constants, routing `when` expressions, holder casts, one global `DiffUtil.ItemCallback`, and lifecycle forwarding. Moving that code into delegates is useful only if the result preserves the behavior and extension points of the AndroidX adapters beneath it.

Braid does not replace `RecyclerView`, `ListAdapter`, `DiffUtil`, or Paging. It composes delegates around those APIs so that each rendered item type owns its holder, identity, content comparison, payloads, and lifecycle behavior.

## Features

- AndroidX `ListAdapter` foundation with no separate item storage.
- Type-safe delegates for homogeneous and heterogeneous lists.
- Concise homogeneous ViewBinding factory and a heterogeneous delegate DSL.
- Per-delegate item identity, content comparison, and change payloads.
- Holder-local state with explicit recycle, attach, detach, and failed-recycle callbacks.
- Strict diagnostics when an item matches zero or multiple delegates.
- Correct holder routing with isolated `ConcatAdapter` view types.
- Optional Paging 3 adapter built on `PagingDataAdapter`.
- Standard Paging load-state, retry, refresh, snapshot, and header/footer APIs.
- Binary API validation and an executable sample application.

Nested-list helpers and consumer-facing delegate testing utilities are not implemented yet.

## Modules

| Module | Purpose |
| --- | --- |
| [`braid`](braid) | Core `ListAdapter`, delegate, registry, ViewBinding, payload, and holder-state APIs. |
| [`braid-paging`](braid-paging) | Optional Paging 3 integration. Depends on `braid`. |
| [`sample`](sample) | Executable app demonstrating homogeneous lists, heterogeneous Paging, holder state, load states, retry, and refresh. |

## Requirements

- Android `minSdk` 23.
- JVM target 11.
- AndroidX RecyclerView.
- ViewBinding enabled in modules that use the ViewBinding DSL.
- Paging 3 only when using `braid-paging`.

The repository is currently tested with this build baseline:

| Tool | Tested version |
| --- | --- |
| Kotlin | 2.0.21 |
| Android Gradle Plugin | 8.5.2 |
| Gradle | 8.7 |
| `compileSdk` | 34 |

These are the versions used to build and test Braid itself, not a claim that every consumer must use the exact same versions.

## Installation

Braid has not been published to Maven Central yet. For local evaluation, clone this repository and either use the included `sample` application, include the library modules as Gradle project dependencies, or build local AARs.

```kotlin
dependencies {
    implementation(project(":braid"))

    // Add only when Paging integration is needed.
    implementation(project(":braid-paging"))
}
```

Build release AARs with:

```shell
./gradlew :braid:assembleRelease :braid-paging:assembleRelease
```

The outputs are written under each module's `build/outputs/aar/` directory. Maven coordinates will be documented after the first release is published; Braid does not use JitPack as an interim distribution channel.

## Homogeneous lists

For one item type and one layout, use the short ViewBinding factory:

```kotlin
data class UserItem(
    val id: Long,
    val name: String,
    val description: String,
)

private val adapter = braidListAdapter(
    inflate = ItemUserBinding::inflate,
    keySelector = UserItem::id,
) { item ->
    tvName.text = item.name
    tvDescription.text = item.description
}

recyclerView.adapter = adapter
adapter.submitList(items)
```

`keySelector` defines item identity. Structural equality is the default content comparison. The returned adapter is a real `ListAdapter`: it owns the current immutable list snapshot, accepts updates through `submitList`, and delegates asynchronous diff execution to AndroidX.

Submit a new list snapshot when data changes. Mutating an item or a list already submitted to `ListAdapter` breaks the assumptions made by `DiffUtil`.

## Heterogeneous lists

The block DSL composes multiple item types without a shared base model beyond the type chosen by the application:

```kotlin
sealed interface FeedItem {
    data class User(
        val id: Long,
        val name: String,
    ) : FeedItem

    data class Banner(
        val id: Long,
        val title: String,
    ) : FeedItem
}

private val adapter = braidListAdapter<FeedItem> {
    viewBinding(
        inflate = ItemUserBinding::inflate,
        keySelector = FeedItem.User::id,
    ) { item ->
        tvName.text = item.name
    }

    viewBinding(
        inflate = ItemBannerBinding::inflate,
        keySelector = FeedItem.Banner::id,
    ) { item ->
        tvTitle.text = item.title
    }
}
```

Declaration order assigns local view types. It is not first-match routing: every submitted item must match exactly one delegate. A missing match or overlapping match fails with a diagnostic that lists the relevant delegates.

Identity also includes the delegate registration. Two values routed to different delegates are never the same list item, even when their model keys are equal.

## Payloads

Each delegate can define its own content comparison and deterministic change payload:

```kotlin
enum class UserPayload {
    Name,
}

private val adapter = braidListAdapter<FeedItem> {
    viewBinding(
        inflate = ItemUserBinding::inflate,
        keySelector = FeedItem.User::id,
        areContentsTheSame = { old, new ->
            old.name == new.name
        },
        getChangePayload = { old, new ->
            UserPayload.Name.takeIf { old.name != new.name }
        },
        bindPayloads = { item, payloads ->
            if (UserPayload.Name in payloads) {
                tvName.text = item.name
            }
        },
    ) { item ->
        tvName.text = item.name
    }
}
```

An empty payload list performs the full bind. If `bindPayloads` is omitted, Braid also falls back to the full bind for non-empty payloads. Matcher, key, content, and payload callbacks can run during background diffing, so they must be fast, deterministic, and thread-safe.

## Stateful ViewBinding

Use `statefulViewBinding` for a resource that belongs to one ViewHolder instance, such as an animator, listener, job, or `TextWatcher` reference:

```kotlin
private class BannerState(
    var animator: ObjectAnimator? = null,
)

private val bannerAdapter = braidListAdapter<FeedItem.Banner> {
    statefulViewBinding(
        inflate = ItemBannerBinding::inflate,
        keySelector = FeedItem.Banner::id,
        stateFactory = {
            BannerState()
        },
        recycle = { state ->
            state.animator?.cancel()
            state.animator = null
            root.setOnClickListener(null)
        },
        detachedFromWindow = { state ->
            state.animator?.cancel()
            state.animator = null
        },
    ) { item, state ->
        tvTitle.text = item.title
        root.setOnClickListener {
            state.animator?.cancel()
            state.animator = ObjectAnimator.ofFloat(
                root,
                View.ALPHA,
                0.5f,
                1f,
            ).apply { start() }
        }
    }
}
```

`stateFactory` runs once per holder. The same state reaches every bind and lifecycle callback for that holder. It is not screen UI state and is not a replacement for a `ViewModel`; do not store an `Activity`, `Fragment`, `LifecycleOwner`, current adapter item, or adapter position in it. Release listeners and holder-owned resources explicitly when recycling or detaching requires it.

## Paging 3

The optional Paging module uses the same delegate DSL:

```kotlin
private val adapter = braidPagingDataAdapter<FeedItem> {
    viewBinding(
        inflate = ItemUserBinding::inflate,
        keySelector = FeedItem.User::id,
    ) { item ->
        tvName.text = item.name
    }

    statefulViewBinding(
        inflate = ItemBannerBinding::inflate,
        keySelector = FeedItem.Banner::id,
        stateFactory = {
            ObjectAnimator.ofFloat(root, View.ALPHA, 0.5f, 1f)
        },
        recycle = { animator ->
            animator.cancel()
            root.setOnClickListener(null)
        },
        detachedFromWindow = { animator ->
            animator.cancel()
        },
    ) { item, animator ->
        tvTitle.text = item.title
        root.setOnClickListener {
            animator.start()
        }
    }
}

val pager = Pager(
    config = PagingConfig(
        pageSize = 20,
        enablePlaceholders = false,
    ),
    pagingSourceFactory = ::FeedPagingSource,
)

lifecycleScope.launch {
    pager.flow.collectLatest { pagingData ->
        adapter.submitData(pagingData)
    }
}
```

Null Paging placeholders are intentionally unsupported. Always set `enablePlaceholders = false`; encountering a null presented item fails fast with an actionable error.

`BraidPagingDataAdapter` directly inherits `PagingDataAdapter`. Braid does not create a custom Paging differ or wrap standard load states:

```kotlin
adapter.retry()
adapter.refresh()

val loadStates = adapter.loadStateFlow
val presentedItems = adapter.snapshot()

recyclerView.adapter = adapter.withLoadStateHeaderAndFooter(
    header = FeedLoadStateAdapter(adapter::retry),
    footer = FeedLoadStateAdapter(adapter::retry),
)
```

## Custom delegates

Use `AdapterDelegate` when a reusable item needs a custom ViewHolder contract:

```kotlin
private class UserViewHolder(
    val binding: ItemUserBinding,
) : RecyclerView.ViewHolder(binding.root)

private class UserDelegate : AdapterDelegate<FeedItem, FeedItem.User, UserViewHolder>() {

    override fun isForItem(item: FeedItem): Boolean = item is FeedItem.User

    override fun createViewHolder(parent: ViewGroup): UserViewHolder {
        val binding = ItemUserBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return UserViewHolder(binding)
    }

    override fun bindViewHolder(
        holder: UserViewHolder,
        item: FeedItem.User,
        payloads: List<Any>,
    ) {
        holder.binding.tvName.text = item.name
    }

    override fun areItemsTheSame(
        oldItem: FeedItem.User,
        newItem: FeedItem.User,
    ): Boolean = oldItem.id == newItem.id
}
```

`isForItem` is the type-safety contract: return `true` only for items the delegate can handle. Override content, payload, and lifecycle callbacks when needed. `ViewBindingDelegate` and `StatefulViewBindingDelegate` are narrower base classes for reusable ViewBinding-backed delegates.

## Strict routing and diagnostics

Braid snapshots delegate registration when an adapter or registry is created. For every item it requires exactly one matching delegate:

- no match is an error rather than a silently missing row;
- multiple matches are an error rather than an implicit first match;
- a holder must be routed back through the registry that created it;
- a holder and bound item must resolve to the same delegate;
- registration-local routing remains valid when `ConcatAdapter` isolates public view types.

Matcher and diff callbacks should be pure. Do not use mutable UI state, adapter positions, or main-thread-only objects to decide routing or identity.

### Advanced registry integration

`DelegateRegistry` is an advanced API used by `braid-paging` and available to custom adapter implementations that need the same routing, diff, and lifecycle semantics:

```kotlin
val registry = braidDelegateRegistry<FeedItem> {
    delegate(UserDelegate())

    viewBinding(
        inflate = ItemBannerBinding::inflate,
        keySelector = FeedItem.Banner::id,
    ) { item ->
        tvTitle.text = item.title
    }
}
```

Create it through `braidDelegateRegistry`; its constructor is internal. Advanced integrations can reuse `itemCallback`, `viewTypeFor`, holder creation and binding, and all four lifecycle routing methods. Quick-start consumers should use `braidListAdapter` or `braidPagingDataAdapter` instead.

## Design principles

- Build on AndroidX primitives instead of replacing them.
- Treat submitted lists as immutable snapshots.
- Fail on ambiguous routing instead of applying a silent fallback.
- Keep holder-local cleanup explicit.
- Keep optional capabilities, such as Paging, in optional modules.
- Do not maintain separate list storage or a custom Paging differ.
- Do not use reflection-based item routing, code generation, or mandatory base UI models.
- Prefer a Kotlin-first API while keeping the low-level RecyclerView contract visible.

## Sample application

The sample contains two end-to-end screens:

- [`MainActivity`](sample/src/main/java/io/github/k1een/braid/sample/MainActivity.kt) demonstrates the homogeneous `ListAdapter` factory.
- [`PagingSampleActivity`](sample/src/main/java/io/github/k1een/braid/sample/paging/PagingSampleActivity.kt) demonstrates heterogeneous Paging, stateful holders, lifecycle-aware collection, retry, refresh, and load-state header/footer composition.

The Paging example uses a deterministic in-memory [`PagingSampleSource`](sample/src/main/java/io/github/k1een/braid/sample/paging/PagingSampleSource.kt) and a standard [`LoadStateAdapter`](sample/src/main/java/io/github/k1een/braid/sample/paging/PagingLoadStateAdapter.kt).

## API reference

Generate the aggregated HTML reference for `braid` and `braid-paging` locally:

```shell
./gradlew :dokkaGenerate
```

Open `build/dokka/html/index.html`. Documentation generation is part of `qualityCheck`; generated files remain under `build/` and are not committed or deployed by this repository.

## Development

Run the complete non-device quality gate:

```shell
./gradlew qualityCheck
```

Validate the Maven publications locally without credentials or a remote upload:

```shell
./gradlew publishingCheck
```

The task publishes only to `build/test-maven-repository`, validates the AAR,
sources, Dokka javadoc, POM, and Gradle Module Metadata for both library
modules, and never writes to `~/.m2`. Maintainers should follow the
[release runbook](docs/releasing.md) before enabling Central publishing or
signing.

Run unit tests and build the modules:

```shell
./gradlew \
    :braid:testDebugUnitTest \
    :braid:assembleDebug \
    :braid-paging:testDebugUnitTest \
    :braid-paging:assembleDebug \
    :sample:testDebugUnitTest \
    :sample:assembleDebug
```

On Windows, use `gradlew.bat` instead of `./gradlew`. CI also assembles both library Android-test APKs, but connected device tests require an available emulator or device.

## Roadmap

- Publish the artifacts to Maven Central.
- Cut the first alpha release.
- Validate nested-list behavior and state restoration in production scenarios.
- Expand documentation and focused examples.
- Add broader consumer compatibility testing.

No release dates are promised while the API remains pre-release.

## License

Braid is licensed under the [Apache License 2.0](LICENSE).
