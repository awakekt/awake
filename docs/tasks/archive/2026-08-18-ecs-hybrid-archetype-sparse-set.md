# 2026-08-18: ECS hybrid archetype + sparse-set

Status: closed. Archetype migration was rejected after matched stable-row, structural-migration, and
256-signature query controls failed the production gate; payload-free tag specialization
implemented as the evidence-backed follow-up. `awake:ecs`
remains sparse-set based (see
`skills/awake-ecs-authoring/SKILL.md`). The proposed hybrid remains benchmark-only evidence,
not planned production architecture.

The completed maintainability follow-up is
[`2026-08-21-ecs-storage-decoupling.md`](2026-08-21-ecs-storage-decoupling.md).
The completed family-column follow-up and broader ECS comparison are in
[`2026-08-21-ecs-family-tag-columns.md`](2026-08-21-ecs-family-tag-columns.md).

## Why archetype storage was investigated

Sparse-set gives O(1) add/remove with no migration cost, which is why it was chosen for
Phase 3's modest scope. Archetype storage was proposed to improve contiguous iteration for
large uniform `Transform` + `MeshRenderer` workloads. Inspection and corrected measurement
showed that Awake's maintained `Family2` cache already provides dense, parallel component
arrays for this hot path, so the expected iteration gap does not currently exist.

## Why pure archetype isn't the answer either

**Archetype explosion.** A character with 10 optional status effects (`IsBurning`,
`IsStunned`, `PoisonDamage`, `SpeedBuff`, `Invulnerable`, ...) can produce up to 2^10
(1,024) distinct archetype tables under pure archetype storage — one per unique
component combination. Instead of few large contiguous tables, data fragments into
hundreds of near-empty ones, destroying the cache-locality benefit archetypes exist for.
Toggling one status effect on 100 entities mid-fight forces ripping all 100 out of their
current table, allocating/copying into a new one — including every unrelated field
(inventory, stats, model refs) — for a single tag flip. That's the "archetype nightmare"
case: gameplay-visible stutter from a routine combat action.

## Reference: Flecs

[github.com/SanderMertens/flecs](https://github.com/SanderMertens/flecs) (C/C++/Rust) is
primarily archetype-based. Its zero-sized tags avoid payload columns, but ordinary tags and
`Sparse` components still participate in archetype changes. Flecs 4.1's more precise analogue
for dynamic data is the [`DontFragment`
trait](https://www.flecs.dev/flecs/md_docs_2ComponentTraits.html): it stores the component
outside tables and excludes it from table types, while documenting limitations for monitors and
some query operators. Awake follows that non-fragmenting intent with a smaller KMP-specific
contract, not Flecs's full archetype/query model.

## Historical proposed split

| Storage | Owns | Example | Why |
|---|---|---|---|
| Archetype tables | Stable, dense core components every relevant entity has | `Transform`, `MeshRenderer` | Contiguous memory; physics/render loops stream sequentially at max cache speed, zero migration once an entity's archetype is set |
| Sparse sets | Dynamic, transient, frequently-toggled components | `IsBurning`, `IsStunned`, gameplay tags/buffs | O(1) add/remove; freezing 100 entities pushes 100 IDs into one sparse array, no table rebuild |

An entity's core data lives once, contiguously, in its archetype table. Dynamic tags
reference that same entity ID through independent sparse lookup maps — adding/removing a
tag never touches the archetype table at all.

## Historical diagnostics requirement

The rejected design would not have made authors guess where a component lived. Storage selection
was required to be
centralized per *component type*, never selected per entity or system:

- The engine registry declares core dense components such as `Transform`,
  `MeshRenderer`, `Camera`, and `Light` as archetype-backed.
- New components default to sparse-set storage. Promote one to archetype-backed only after
  profiling shows it is a stable, dense, hot-path component.
- Calling `world.add(entity, Component())` remains the same regardless of storage backing.

Any production implementation would have required diagnostics equivalent to:

```kotlin
world.storageKind<Transform>() // Archetype
world.storageKind<IsStunned>() // SparseSet
world.describeStorage()        // component counts and owning table/set
```

An entity inspector/debug dump must also report its core archetype table and dynamic sparse
components. This makes routing testable and debuggable instead of hidden engine behavior.

## Evaluated dynamic-component patterns

1. **Tag system (sparse arrays for flags)** — marker/status components with no heavy
   payload (`IsDead`, `IsSelected`, `IsBurning`) live in global sparse sets entirely
   outside the archetype tables. Adding a tag is a pure sparse-set insert, zero
   archetype-table involvement.
2. **Component holder pattern** — a single `StatusEffects` component lives *inside* the
   archetype table (so the entity's archetype membership never changes when effects
   change) and internally holds a small dynamic array of active buffs. The archetype
   stays perfectly stable; gameplay logic stays fully dynamic inside one field.

Tag system is simpler and matches Flecs's own approach most directly. Component holder
keeps everything for one entity in one place at the cost of an extra indirection inside
the component. Pick per-component-shape, not a single blanket rule -- a boolean-shaped
tag (`IsStunned`) fits tag-system; a value-bearing, frequently-queried-by-magnitude effect
(`PoisonDamage: Float`) may fit component-holder better.

## Constraints used for evaluation

- `Entity`'s generation-counter value class, the 64-component-type hard limit, the
  reified-generics hot-path rule -- all still apply regardless of storage backing.
- `awake:ecs:benchmark`'s Fleks comparison remained the validation mechanism, with hybrid,
  pure-sparse-set, and pure-archetype controls alongside the ECS-vs-Fleks comparison.

## Completed prerequisites

1. Stable and dynamic component shapes were represented in benchmark-only controls.
2. Current sparse, hybrid, and pure-archetype strategies were measured before any production move.
3. The failed gate was recorded in ECS authoring guidance; no production hybrid was documented.

## Prototype status (2026-08-20)

A JVM-only `HybridArchetypeBenchmarks` prototype now exists in `awake:ecs:benchmark`.
It keeps `Transform` + `MeshRenderer` in a dense stable-row table and manages a synthetic
dynamic tag through an independent sparse set, with a matching current-`World` sparse-set
tag-churn benchmark. It is intentionally not wired into the published ECS API: first collect
and review its numbers, then decide whether a production design earns its migration cost.

### Corrected calibration result (migration gate failed)

The first calibration compared the prototype's raw tag set against the full `World` API.
That was not an equivalent workload: `World` also validates entity generations, updates
signatures, query versions, family membership, and pooling. Its apparent advantage is not
valid archetype evidence.

The corrected JDK 17 run added equivalent raw-store tag churn and current dense-family
iteration baselines (`2` forks, `2` warmups, `2` measurements, `1s` each):

| Workload | Size | Current Awake | Prototype | Result |
|---|---:|---:|---:|---|
| Stable `Transform` + `MeshRenderer` iteration | 10k | 509,641 ops/s | 284,691 ops/s | Current family cache faster |
| Stable `Transform` + `MeshRenderer` iteration | 100k | 51,032 ops/s | 29,523 ops/s | Current family cache faster |
| Raw dynamic tag churn | 10k | 11,792 ops/s | 40,345 ops/s | Specialized tag set faster |
| Raw dynamic tag churn | 100k | 1,165 ops/s | 4,149 ops/s | Specialized tag set faster |

The stable-row result blocks an archetype migration. The tag result supported investigating
a payload-free specialized tag store independently; it did not support moving stable components
into archetype tables. Do not implement production archetype routing unless a future equivalent
benchmark demonstrates a workload the existing dense family caches cannot serve.

### Implemented follow-up: payload-free tag storage (2026-08-21)

`EcsTag` is now the engine-generic contract for payload-free markers. A singleton tag uses the
same `World.add/get/remove/has`, family, query, destruction, and signature APIs as any component,
but its `ComponentStore` keeps entity IDs plus one canonical singleton reference and never
allocates a dense payload array. Game-specific tags remain authored in the consumer repository.

The implementation also exposes `storageKind<T>()`, `describeStorage()`, and
`inspectStorage(entity)`. Storage classification becomes visible as `TagSparseSet` after the
first tag instance is added; no per-entity or per-system storage decision is required.

A matched JDK 17 control compared the same singleton marker and identical loops as a normal
component versus an `EcsTag` (`2` forks, `2` warmups, `2` measurements, `1s` each):

| Workload | Size | Normal component | `EcsTag` | Interpretation |
|---|---:|---:|---:|---|
| Raw `ComponentStore` churn | 10k | 11,715 ops/s | 13,294 ops/s | High tag-run variance; no acceptance claim |
| Raw `ComponentStore` churn | 100k | 1,142 ops/s | 1,609 ops/s | Tag path ~41% higher throughput |
| Full `World` churn | 10k | 5,126 ops/s | 4,776 ops/s | Similar; lifecycle bookkeeping dominates |
| Full `World` churn | 100k | 336 ops/s | 230 ops/s | Confidence intervals overlap; inconclusive |

The guaranteed win is storage shape: tags have no per-entity payload array. Raw churn improves
at 100k, while full-world churn is not claimed as faster. These calibration rows stay in this
task note rather than the official scorecard until a normal full-length benchmark run produces
stable confidence intervals.

The storage implementation was subsequently decoupled behind the same public API. Entity
membership now lives in `SparseEntitySet`; payload and singleton-tag values live in separate
strategies; and type IDs, pools, and stores have focused registries. Matched post-refactor
calibration stayed inside the 5% regression gate: stable family iteration was about 3% higher,
normal raw churn was +1%/flat, and tag raw churn was -3.3% at 10k (high variance) and -2.1% at
100k. The full implementation and verification report is in
[`2026-08-21-ecs-storage-decoupling.md`](2026-08-21-ecs-storage-decoupling.md).

## Pure-archetype migration and fragmentation control (2026-08-21)

The original stable-row prototype did not implement a complete archetype transition: it owned
one table and never copied an entity when a tag changed. The follow-up benchmark adds a
benchmark-only `PureArchetypeStorage` with:

- one dense table per dynamic-tag signature;
- entity-to-table and entity-to-row location maps;
- physical `Transform` and `MeshRenderer` row migration for tag changes;
- swap removal with displaced-row location repair;
- eight optional tags producing all 256 signatures; and
- required-tag plus excluded-tag table matching.

Focused unit tests cover migration, displaced-row repair, idempotent tag operations, removal,
and all 256 required/excluded combinations. This remains a control, not a second production ECS.

### Decision-grade throughput

Temurin JDK 17.0.19, Kotlin 2.4.10, 3 forks, 5 one-second warmups, and 5 one-second
measurements. Ops/sec:

| Workload | Size | Current Awake | Hybrid control | Pure archetype |
|---|---:|---:|---:|---:|
| Stable render-row iteration | 10k | **505,417.822 ± 13,033.122** | 313,175.408 ± 8,150.537 | 300,994.319 ± 12,865.085 |
| Stable render-row iteration | 100k | **50,421.543 ± 1,979.629** | 26,936.261 ± 3,256.172 | 30,989.607 ± 1,406.472 |
| Dynamic-tag churn | 10k | 16,663.846 ± 1,875.181 | **40,375.076 ± 1,735.303** | 5,218.882 ± 501.510 |
| Dynamic-tag churn | 100k | 1,695.029 ± 48.884 | **4,006.350 ± 166.442** | 530.804 ± 13.058 |
| 256-signature query | 10k | **6,222,312.750 ± 391,448.281** | 308,398.733 ± 11,326.403 | 1,428,708.525 ± 336,424.760 |
| 256-signature query | 100k | **440,046.129 ± 25,399.027** | 27,879.333 ± 3,375.323 | 205,277.897 ± 33,777.757 |

The query requires tag zero, excludes tag one, and returns 25% of entities. Current Awake uses
its production incrementally maintained `Family`; pure archetype scans only matching tables;
hybrid intersects independent sparse tag sets. The churn comparison preserves the same stable
rows in both synthetic controls, making the cost of copying them into another table explicit.

Pure archetype does win over the unmaintained hybrid intersection query, but not over Awake's
actual maintained-family query. It also loses stable iteration and is about 7.6x slower than the
hybrid sparse-tag control at 100k churn. That combination fails the migration gate.

### Allocation evidence

The first GC pass rebuilt fixtures at `Setup(Level.Iteration)`, which polluted allocation
telemetry even though setup was outside timed throughput. Immutable/idempotent fixtures were
corrected to `Setup(Level.Trial)` and the 100k pass was rerun with `-prof gc` (1 fork, 5 warmups,
5 measurements). No control collected. Normalized allocation per complete benchmark invocation:

| Workload | Current Awake | Hybrid control | Pure archetype |
|---|---:|---:|---:|
| Tag remove-all/add-all | 3.381 B/op | 1.405 B/op | 10.979 B/op |
| Required/excluded query | 0.013 B/op | 0.184 B/op | 0.024 B/op |

These values are close to the profiler/harness floor and are not multiplied into a per-entity
claim. They establish that steady-state differences are table movement and lookup work, not a
hidden allocation storm.

### Final decision

Keep production Awake on per-type sparse sets, singleton `EcsTag` storage, and maintained dense
families. A future archetype proposal must identify a new real workload the maintained family
cannot serve, provide an exact sparse/hybrid/pure control, and clear the existing throughput and
complexity gate before adding storage routing to the public runtime.

### Reproduction commands

The benchmark JAR was built with:

```bash
./gradlew :awake:ecs:benchmark:mainBenchmarkJar --no-daemon
```

The storage decision used the JAR's JMH CLI on Temurin JDK 17.0.19 with `-f 3 -wi 5 -i 5
-w 1s -r 1s`, filtering the stable-iteration, dynamic-tag-churn, and fragmentation-query
methods in `HybridArchetypeBenchmarks` and `StorageFragmentationBenchmarks`. The corrected GC
pass added `-p entityCount=100000 -f 1 -prof gc` after moving idempotent fixtures to
`Setup(Level.Trial)`.

The refreshed four-library scorecard used this exact filter with one fork (matching the
scorecard's historical method):

```text
.*\.(awake|fleks|artemis|ashley)(CreateDestroy|ComponentAddRemove|FamilyChurn|TransformMeshQuery|TransformHierarchyPropagation)$
```

## Closed follow-up

The decision-grade tables above and the canonical
[`docs/ecs-benchmark-scorecard.md`](../../ecs-benchmark-scorecard.md) close every former pending
storage-shape control. Future structural-churn work is the non-archetype adaptive batching plan in
[`2026-08-21-ecs-adaptive-bulk-mutation-plan.md`](../2026-08-21-ecs-adaptive-bulk-mutation-plan.md).
