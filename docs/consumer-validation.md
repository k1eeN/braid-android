# External consumer validation

## Scope

Braid `0.1.0-alpha01` was evaluated in a separate Android consumer project by
migrating three existing RecyclerView adapters to the published Maven Central
artifact, `io.github.k1een:braid`.

This was external consumer validation, not a built-in Braid sample or a
synthetic benchmark. The pass focused on consumer integration, API ergonomics,
item identity, ViewBinding delegates, strict routing, RecyclerView recycling,
and holder-owned state.

The evaluation was not a performance benchmark, did not use live production
telemetry, and does not assert that the alpha API is stable.

## Method

The adapters were migrated one at a time while preserving the existing models
and UI behavior. Braid was consumed from Maven Central through the published
artifact. The evaluation did not use a project dependency on Braid sources, a
local AAR, `mavenLocal()`, JitPack, an included build, or a snapshot version.

AndroidX remained responsible for `ListAdapter`, `DiffUtil`, and `submitList`.
Each migration was checked with a build and consumer-side runtime verification.
Temporary test fixtures and the instrumentation harness were not included in
the final commits.

Line counts were calculated as follows:

- `Before` is the number of physical lines in the removed custom adapter.
- `After` is the number of lines in the replacement Braid declaration.
- Holder state is included in the stateful scenario.
- Imports and unrelated Fragment code are excluded.
- Line reduction is a secondary maintainability metric.
- Correctness and behavior preservation take priority over minimizing line
  count.

## Migration summary

| Scenario | Before | After | Reduction | Braid API |
| --- | ---: | ---: | ---: | --- |
| Homogeneous ViewBinding list | 66 | 10 | 56 / 84.8% | Homogeneous `braidListAdapter` |
| Heterogeneous three-layout list | 168 | 61 | 107 / 63.7% | Three ViewBinding delegates |
| Stateful form list | 189 | 116 | 73 / 38.6% | One stateful and one regular delegate |
| **Total** | **423** | **187** | **236 / 55.8%** | **Six delegates** |

Across the three migrations:

- 3 custom adapter classes were removed.
- 6 custom ViewHolder classes were removed.
- 3 global `DiffUtil.ItemCallback` implementations were removed.
- Manual view-type routing was removed.
- 6 Braid delegates were used.
- 1 stateful delegate was used.
- No custom `AdapterDelegate` was required.
- No Braid API changes were required.

No extension functions or workaround abstraction layers were required.
The migrated lists continued to use the standard `submitList` API.

## Homogeneous ViewBinding list

This scenario used one item subtype and one ViewBinding layout. A model property
provided the stable key, structural equality provided content comparison, and
the click listener was assigned inside `bind`. Updates continued to use the
standard `submitList` API, and RecyclerView cleanup remained tied to destruction
of the owning View.

The homogeneous factory covered the scenario without a separate adapter class,
custom delegate, extension function, or workaround.

## Heterogeneous list

This scenario used a sealed model hierarchy with three subtypes and three
different ViewBinding layouts. Each subtype had a separate delegate with its
own key selector. Binding covered enabled and disabled states, cleared the
listener for a disabled item, and selected text from either the model value or
a resource. Strict routing ensured that every subtype matched exactly one
delegate.

The previous adapter used:

```text
model key only
```

Braid used:

```text
delegate registration + model key
```

If an item keeps its key but changes subtype and layout, Braid treats it as a
different item. This is safer because different subtypes use different
ViewHolder and layout contracts.

## Stateful holder resources

The stateful scenario used a heterogeneous list containing an editable field
and a selection field. The editable holder owned a `TextWatcher`, stored in
private holder state:

```kotlin
private class EditTextHolderState {
    var textWatcher: TextWatcher? = null
}
```

The watcher was removed before a programmatic text update and installed again
only after the update. Recycling removed the watcher and cleared click
listeners. `detachedFromWindow` was intentionally not used as the primary
watcher cleanup point.

The following transitions and lifecycle cases were verified:

- text to and from numeric input;
- text to and from a picker-style field;
- enabled to and from disabled;
- required to and from optional;
- editable subtype to and from selection subtype with the same key;
- repeated bind;
- programmatic value update;
- recycling;
- Fragment View recreation.

One user input produced one callback. A programmatic update produced no
callback, and each callback referred to the current item key. No stale watcher
or stale click listener was retained.

The smaller line reduction reflects that most of this scenario was real UI
behavior rather than RecyclerView boilerplate. Braid removed the
adapter/ViewHolder/routing infrastructure without hiding the complex bind code.

## Behavior verified

- Maven Central dependency resolution;
- standard `ListAdapter.submitList` behavior;
- homogeneous ViewBinding adapter;
- heterogeneous delegate routing;
- strict zero/multiple-match diagnostics;
- per-delegate identity;
- structural content comparison;
- cross-type transitions;
- enabled/disabled recycling;
- holder-local state;
- listener cleanup on rebind and recycle;
- programmatic updates without duplicate callbacks;
- Fragment View recreation;
- debug build and APK installation;
- local unit tests;
- consumer-side device or instrumentation verification.

## Findings

1. The standard Braid API covered all three scenarios.
2. No custom `AdapterDelegate` was required.
3. No public API change was required.
4. The homogeneous DSL was compact.
5. The heterogeneous DSL keeps layout, identity, and binding together.
6. Delegate-aware identity safely handles layout changes.
7. `statefulViewBinding` is suitable for a holder-owned `TextWatcher`.
8. AndroidX remains responsible for the list, diff, and adapter lifecycle.
9. Complex binding logic remains explicit.

Consumer-facing delegate testing utilities are not yet available. As a result,
the following checks required a temporary consumer-side test harness:

- `stateFactory` reuse;
- recycle callbacks;
- strict routing;
- zero and multiple matches;
- callback count after repeated recycling.

This is an opportunity to improve testing ergonomics, not an integration
blocker.

## Limitations

- One separate Android consumer project was evaluated.
- No performance benchmark was conducted.
- Live production telemetry was not analyzed.
- Nested RecyclerView integration has not yet been validated.
- Paging 3 has not yet been validated in a separate consumer project.
- Process-death restoration was not validated.
- The API remains alpha.
- The compatibility matrix remains limited.
- Line reduction alone does not prove correctness.

## Next validation targets

1. Nested RecyclerView integration.
2. Paging 3 integration in a separate consumer project.
3. State restoration across configuration and process recreation.
4. Consumer-facing delegate testing utilities.
5. Payload validation in a realistic consumer screen.
6. Broader Gradle, AGP and Kotlin compatibility testing.
