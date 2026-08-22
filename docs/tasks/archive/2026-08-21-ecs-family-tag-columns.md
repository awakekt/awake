# 2026-08-21: ECS payload-free family tag columns

Status: implemented and decision-benchmarked on 2026-08-21.

## Outcome

`Family1` and `Family2` no longer repeat the same `EcsTag` singleton in a payload array for
every matching entity. A family column selects one of two representations on its first value:

| Representation | Stored state | Hot iteration |
|---|---|---|
| Ordinary component | Dense typed payload array | Indexed array load |
| `EcsTag` | One canonical singleton reference | Singleton captured once before the loop |

The public `components()`, `componentsA()`, and `componentsB()` array APIs remain source
compatible. Requesting one for a tag lazily materializes the repeated compatibility array and
keeps it synchronized until the normal dense-array capacity growth replaces the backing array.
Callers that use `forEach`, `forEachComponent`, or `forEachComponents` never pay that cost.

The former 424-line `Families.kt` was split into focused files in its package-correct source
directory:

```text
ecs/
├── Family.kt
├── FamilyCache.kt
├── Family1Cache.kt
├── Family2Cache.kt
└── FamilyValueColumn.kt
```

The public `Family1`/`Family2` package remains unchanged. A nested filesystem folder with the
same package was rejected by Detekt's package-layout rule; changing to an `.ecs.family` package
would break public imports and split the sealed cache hierarchy across packages.

`FamilyValueColumn` owns value representation only. `Family1Cache` and `Family2Cache` retain
dense membership and swap removal. `FamilyRegistry` remains the structural-change coordinator.
No public storage selector, archetype table, gameplay component, or authored game code was added.

## Kotlin 2.4 research and applied choices

The project now uses Kotlin **2.4.10**, the latest stable bug-fix release as of this task. Kotlin
2.4.20 was still an upcoming release, so an RC/EAP was not used for production. The authoritative
source is JetBrains' [Kotlin release history](https://kotlinlang.org/docs/releases.html).

The implementation follows current official Kotlin performance guidance:

- [Arrays](https://kotlinlang.org/docs/arrays.html) are explicitly appropriate for custom
  performance-sensitive data structures. Family membership therefore stays in `LongArray`, and
  ordinary values stay in typed arrays rather than boxed collections.
- [Ranges and arrays compile to index-based loops](https://kotlinlang.org/docs/control-flow.html),
  so dense loops use index iteration and perform no iterator allocation.
- [Inline functions](https://kotlinlang.org/docs/inline-functions.html) remove higher-order
  function objects and virtual calls at hot call sites. Public family callbacks and internal
  pair-column iteration remain inline.
- Representation branching occurs once before each dense loop. There is no strategy-interface
  dispatch, `KClass` lookup, reflection, collection construction, or tag-array read per entity.
- `ComponentStore` remains the single owner of component-kind and tag-singleton validation.
  Family caches trust the already-validated structural event instead of duplicating checks on
  every add or replacement.

These are applied design choices, not a claim that a newer compiler automatically makes an
algorithm faster. The pre/post JMH runs below use the same Kotlin 2.4.10 toolchain.

## Flecs and broader ECS comparison

This is an architectural comparison, not a cross-language speed ranking. Only Awake, Fleks,
Artemis-odb, and Ashley have equivalent same-JVM rows in the project benchmark harness. C/C++,
Rust, Burst, and JVM results are not numerically interchangeable.

| ECS | Storage model | Iteration performance shape | Structural-change shape | Tag/transient strategy | Clean-code / simplicity tradeoff | Awake relevance |
|---|---|---|---|---|---|---|
| **Awake (current)** | Sparse set per component plus incrementally maintained dense typed families | Direct parallel arrays; tag singleton captured once | O(1) sparse membership and family swap removal | `EcsTag` stores IDs plus one singleton; no family payload unless array compatibility is requested | Small dependency-free KMP core, focused collaborators, public API unchanged; deliberate 64-type and single-thread limits | Selected: measured for Awake's actual workloads |
| **Fleks 2.14** | KMP component stores plus reactive family membership | Family iteration followed by component access | Families receive entity add/update/remove notifications | Component model; no Awake-style singleton tag contract in the compared API | Mature KMP systems/DSL/hooks feature set, but a larger framework surface than Awake needs | Direct same-JVM competitor in the scorecard |
| **Flecs 4.1** | Archetype tables and cached queries, with `Sparse` and `DontFragment` traits | Very strong table streaming and cached table matching | Default add/remove changes archetype; `Sparse` moves payload out of tables but still changes archetype | `DontFragment` uses sparse storage outside table types and avoids fragmentation, with documented query/monitor limitations | Extremely capable relationships, observers, reflection, pipelines, and tooling; materially more runtime/API complexity | Primary reference for explicit storage tradeoffs, not a drop-in KMP dependency |
| **EnTT** | Paged sparse sets with tightly packed component pools; views/groups combine pools | Fast packed-pool iteration; multi-type views may probe other pools | Sparse-set add/remove is a core strength; policies can preserve pointers | Empty/tag types and customizable component traits | Header-only and highly customizable, but template-heavy C++ policy surface | Validates Awake's sparse-set foundation |
| **Bevy 0.19** | Per-component choice: table by default or sparse set | Official docs define table as cache-friendly and sparse set as slower to iterate | Official docs define sparse set as faster to add/remove | Component-level `StorageType` makes the tradeoff explicit | Excellent explicit model, inside a much larger Rust scheduler/engine ecosystem | Closest conceptual hybrid reference |
| **Unity Entities 1.0** | Archetypes stored in 16 KiB chunks with one packed array per component | Chunk streaming, cached archetype queries, Burst/jobs | Adding/removing a component moves the entity to another archetype and is documented as expensive | Zero-sized markers still participate in archetype membership | Industrial data-oriented toolchain, but high conceptual/tooling weight and C#/Burst coupling | Useful upper-bound reference for full archetype complexity |

Primary sources:

- [Flecs queries and caching](https://www.flecs.dev/flecs/md_docs_2Queries.html)
- [Flecs `Sparse` and `DontFragment` traits](https://www.flecs.dev/flecs/md_docs_2ComponentTraits.html)
- [Fleks family contract](https://javadoc.io/static/io.github.quillraven.fleks/Fleks-jvm/2.8/-fleks/com.github.quillraven.fleks/-family/index.html)
- [EnTT ECS storage design](https://github.com/skypjack/entt/wiki/Entity-Component-System)
- [Bevy `StorageType`](https://docs.rs/bevy/latest/bevy/ecs/component/enum.StorageType.html)
- [Unity archetype concepts](https://docs.unity.cn/Packages/com.unity.entities%401.0/manual/concepts-archetypes.html)

### Decision

Keep Awake's sparse-set stores and maintained dense families. Do not introduce production
archetype migration: the existing stable-family path remains faster than the benchmark-only
archetype prototype, while `EcsTag` plus payload-free family columns solves the demonstrated
dynamic-marker memory and iteration problem with far less machinery. Revisit archetypes only
if a future equivalent workload defeats the current family cache under a decision-grade run.

## Decision benchmark

Both sides used commit-compatible Kotlin 2.4.10 benchmark sources, Temurin JDK 17.0.19, one
thread, three independent forks, five one-second warmups, and five one-second measurements.
The baseline is commit `5358d92fa`; higher throughput is better.

| Workload | Size | Before (ops/s) | After (ops/s) | Change |
|---|---:|---:|---:|---:|
| Tag-family structural churn | 10k | 3,158.275 ± 160.152 | 3,209.308 ± 74.373 | +1.6% |
| Tag-family structural churn | 100k | 284.483 ± 10.845 | 304.939 ± 22.840 | +7.2% |
| Tag-family iteration | 10k | 208,232.122 ± 2,132.950 | 492,879.156 ± 7,556.426 | **+136.7%** |
| Tag-family iteration | 100k | 20,206.434 ± 777.717 | 48,879.048 ± 1,040.246 | **+141.9%** |
| Ordinary two-component family iteration | 10k | 513,426.572 ± 4,785.585 | 508,014.300 ± 14,267.735 | -1.1%; inside 5% gate |
| Ordinary two-component family iteration | 100k | 51,197.918 ± 434.602 | 51,496.869 ± 825.746 | +0.6% |

The first implementation rerun showed noisy churn regression. Profiling the changed path found
that family columns were redundantly repeating `ComponentStore` validation and capacity checks.
Removing that duplicate coordination produced the final table above. The iteration result was
also repeated after that change and remained approximately 2.4× the baseline.

A one-fork GC-profiler check at 100k reported no GC during tag-family iteration. Its roughly
509 B per complete 100k-entity pass includes JMH/state effects and normalizes to about
0.005 B per visited entity; the deterministic internal test is the authoritative proof that no
repeated tag array exists. Full-world churn reported about 3.27 MB per complete remove-all/add-all
operation (roughly 16.4 B per individual structural call), which includes existing `World`, query,
signature, and benchmark bookkeeping and is not claimed as zero-allocation.

## Verification evidence

- Family correctness covers empty iteration, family-before-first-tag, both mixed column orders,
  two-tag families, replacement, swap removal, compatibility arrays before and after selection,
  capacity replacement, and 100,000 tag entities without family payload materialization.
- The public family APIs and ordinary dense arrays remain compatible.
- All ECS targets passed (Desktop, Android host, iOS simulator, wasmJs), along with ECS/benchmark
  Spotless and Detekt plus benchmark and scene-core consumer compilation.
- KDoc documents the public family contract, compatibility-array behavior, representation owner,
  structural-mutation restriction, and internal iteration invariants.
- Dokka generated the new `Family1`/`Family2` pages and resolved their `EcsTag` links. The module
  publication task still exits non-zero because warnings are treated as errors for 72 pre-existing
  undocumented declarations elsewhere in the ECS API; none of the new family declarations appears
  in that warning list.
