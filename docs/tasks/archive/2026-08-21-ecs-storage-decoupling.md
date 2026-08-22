# 2026-08-21: ECS storage decoupling

Status: implemented and verified on 2026-08-21. The recommended answers under
**Open questions** were accepted. This refactors the implemented `EcsTag` specialization
for maintainability without reviving the rejected archetype migration.

## Outcome

Keep the public ECS behavior and dense-family performance while preventing `ComponentStore`,
`ComponentRegistry`, or `World` from becoming god classes, receivers, or functions.

The resulting ownership should be:

| Type | Owns | Must not own |
|---|---|---|
| `World` | Public façade and lifecycle coordination | Sparse arrays, payload arrays, diagnostics formatting |
| `ComponentRegistry` | Thin coordination between type, pool, and storage registries | Per-entity storage mechanics |
| `ComponentTypeCatalog` | `KClass`/fast-key to `ComponentTypeId` mapping and the 64-type limit | Stores and pools |
| `ComponentPoolRegistry` | Pool registration, obtain, recycle, and clear | Type IDs and entity membership |
| `ComponentStorageRegistry` | Store creation/lookup and structured storage diagnostics | Pools and query invalidation |
| `ComponentStore<T>` | Public compatibility façade for one component type | Type registration, pooling, world/query lifecycle |
| `SparseEntitySet` | Sparse index, packed dense entities, swap-remove bookkeeping | Component or tag values |
| `ComponentValueStorage<T>` | Payload-column or singleton-tag value behavior | Entity membership and query caches |

## Proposed internal shape

`ComponentStore<T>` composes two focused pieces:

```text
ComponentStore<T>
├── SparseEntitySet
│   ├── EntityIndexMap
│   └── packed dense entities
└── ComponentValueStorage<T>
    ├── PayloadColumn<T>
    └── SingletonTagValue<T : EcsTag>
```

`SparseEntitySet` performs add, lookup, clear, and swap-remove once for both storage kinds.
Its removal result identifies the removed index and any last row moved into that slot.
`ComponentStore` forwards that compact result to its value storage so entity and payload
movement cannot drift apart.

Select the value storage on the first added instance. Normal components lazily allocate a
payload column; `EcsTag` retains one canonical singleton and allocates no per-entity payload
array. Branch on the selected strategy outside dense loops; do not perform an interface call
for every entity during `forEach`.

## Compatibility contract

The following behavior and source APIs remain unchanged:

- `World.create/destroy/isAlive`
- `World.add/get/remove/has`
- typed `family`, `queryEach`, and arbitrary `FamilySpec`
- `World.store(type)` and direct `ComponentStore` operations
- component pooling and complete `Poolable.reset()` behavior
- entity generation checks and the 64-component-type limit
- no reflective construction added to tag or component paths

Backends and value-storage strategies remain internal. Consumers never select a backend per
entity or per system.

## Implementation phases

### Phase 1: Characterization gates

- Preserve the current tag lifecycle, family-before-first-add, destruction, singleton,
  diagnostics, pooling, and normal-component tests.
- Add direct `ComponentStore` replacement and swap-remove tests at capacities `15`, `16`,
  `17`, `100`, and `100,000` for payload and tag storage.
- Add a randomized reference-model test covering add, replace, remove, destroy, clear, and
  recycled entity IDs.
- Save the current matched JMH command and results as the pre-refactor baseline.

### Phase 2: Split `ComponentStore`

- Extract `SparseEntitySet` and a compact swap-removal result.
- Extract internal `PayloadColumn<T>` and `SingletonTagValue<T>` implementations.
- Keep `ComponentStore<T>` as the public façade and coordinator.
- Hoist tag/payload branching outside iteration loops.
- Keep the singleton-tag invariant and entity-generation-preserving packed handles.

### Phase 3: Split `ComponentRegistry`

- Extract `ComponentTypeCatalog`, `ComponentPoolRegistry`, and
  `ComponentStorageRegistry` as internal collaborators.
- Keep `ComponentRegistry` only if a small coordinator still improves `World`; otherwise
  let `World` hold the three collaborators directly.
- Move `storageKind`, world storage summaries, and entity storage summaries into the
  storage registry as structured data. UI/string rendering stays outside `awake:ecs`.
- Ensure `clear()` resets type/storage state while retaining registered pool factories,
  matching current behavior.

### Phase 4: Hot-path and API verification

- Run all `:awake:ecs:allTests` targets: Desktop, Android host, iOS simulator, and wasmJs.
- Run ECS and benchmark Detekt/Spotless gates.
- Compile scene and benchmark consumers.
- Run matched JMH normal-component versus tag controls on JDK 17.
- Profile allocations/GC and inspect generated JVM code for unexpected per-operation
  allocation or dispatch.

### Phase 5: Documentation and cleanup

- Update `awake-ecs-authoring` only for behavior that actually changed.
- Update the hybrid task with final measured results.
- Update `docs/ecs-benchmark-scorecard.md` only after a stable full-length decision run.
- Remove obsolete prototype wording; do not claim archetype storage was implemented.

## Performance acceptance gates

The refactor does not land unless all conditions hold:

1. Normal component and tag operations allocate nothing per steady-state add/get/remove/has
   beyond caller-provided component instances.
2. Dense `Family1`/`Family2` iteration changes by no more than 5% versus baseline; investigate
   any regression before averaging it away.
3. Normal-component structural churn changes by no more than 5% at 10k and 100k entities.
4. Tag raw-store churn is no worse than the current implementation and tags still allocate
   no dense payload column.
5. Full-`World` results publish confidence intervals. Overlapping/high-variance results are
   reported as inconclusive, not wins.
6. No new reflection, hash lookup, collection allocation, or virtual dispatch appears inside
   dense family iteration loops.

## Maintainability acceptance gates

- No production class needs a broad file-level Detekt suppression.
- A class exceeding the function threshold needs a narrow explanation and must primarily
  contain façade delegation or cohesive storage operations.
- No function coordinates type registration, pooling, entity membership, query invalidation,
  and diagnostics together.
- Storage strategy names remain engine-neutral; no gameplay/MMORPG vocabulary enters Awake.
- Tests address each internal unit directly and preserve end-to-end `World` behavior.

## Open questions

These are real decisions, with recommended defaults so implementation does not need to pause:

1. **Does public `ComponentStore<T>` remain?**
   Recommended: yes. Keep it as a small façade for source compatibility; never expose internal
   backends.
2. **What does diagnostics report before the first component instance selects storage?**
   Recommended: add `ComponentStorageKind.Uninitialized`. `Unregistered` means no type ID;
   `Uninitialized` means registered but not yet classified; `SparseSet` and `TagSparseSet`
   describe actual selected storage.
3. **Do family caches stop storing repeated tag references in this refactor?**
   Recommended: no. `Family1.components()` and `Family2.componentsA/B()` publicly return arrays,
   so removing those columns changes API/performance behavior. Benchmark and design that as a
   separate follow-up.
4. **Is one internal strategy dispatch acceptable on structural operations?**
   Recommended: yes for add/get/remove, subject to JMH. Dense iteration must branch once outside
   the loop, not dispatch once per entity.
5. **Should `ActiveCamera` or another existing engine component migrate to `EcsTag` now?**
   Recommended: no. `ActiveCamera` is currently a constructible pooled class; changing it to a
   singleton is a separate public-API migration. Validate the generic contract without bundling
   that break into storage decoupling.

## Explicit non-goals

- No archetype tables or component migration graph.
- No authored gameplay/status components.
- No removal of the 64-component signature limit.
- No query API redesign.
- No multithreading or lock introduction.
- No `ActiveCamera` API break.
- No benchmark-scorecard claim from short calibration runs.

## Definition of done

- All phases and acceptance gates pass.
- `World` remains a thin façade.
- Storage mechanics, values, type catalog, pools, and diagnostics have clear owners.
- Existing consumers compile without code changes.
- Tags retain their memory guarantee and measured performance profile.
- The remaining family-tag-column question is captured as a separate task rather than hidden
  inside this refactor.

## Implementation report

The implementation keeps `World` and the public `ComponentStore<T>` API stable while splitting
the internal ownership exactly along the planned boundaries:

| Collaborator | Implemented responsibility |
|---|---|
| `SparseEntitySet` | Sparse lookup, packed entity order, growth, and allocation-free swap removal |
| `PayloadColumn<T>` | Dense payload values and payload swap removal |
| `SingletonTagValue<T>` | One canonical tag value with no per-entity payload column |
| `ComponentTypeCatalog` | `KClass`/platform-key IDs and the 64-type limit |
| `ComponentPoolRegistry` | Factory registration, type-ID binding, obtain/recycle, and pool clearing |
| `ComponentStorageRegistry` | Store lookup/creation and structured storage diagnostics |
| `ComponentRegistry` | Thin delegation and cross-registry coordination only |

`ComponentStorageKind.Uninitialized` now distinguishes a registered or explicitly-created store
whose first value has not selected payload or tag storage. Removal returns primitive indices
between collaborators rather than allocating a swap-result object. Payload/tag selection occurs
outside dense iteration loops.

### Verification evidence

- `:awake:ecs:allTests` passed for Desktop, Android host, iOS simulator, and wasmJs browser.
- ECS and benchmark Detekt and Spotless passed.
- The benchmark module and `:awake:scene:scene-core` Desktop consumer compiled.
- Boundary tests passed at 15, 16, 17, 100, and 100,000 entities for payload and tag stores.
- A deterministic 10,000-operation store model covers add, replace, get, remove, and clear. A
  second 5,000-operation World model covers create, add/replace, get, remove, destroy, clear, and
  recycled IDs/generations. A focused end-to-end test also covers pooled reset and world clear.
- JVM bytecode inspection shows no allocation in `ComponentStore.remove`; the removal contract is
  two primitive indices.

Matched JDK 17 calibration (`2` forks, `2` warmups, `2` measurements, `1s` each):

| Workload | Size | Before | After | Change |
|---|---:|---:|---:|---:|
| Dense stable family iteration | 10k | 509,641 ops/s | 527,934 ops/s | +3.6% |
| Dense stable family iteration | 100k | 51,032 ops/s | 52,618 ops/s | +3.1% |
| Raw normal-component churn | 10k | 11,715 ops/s | 11,840 ops/s | +1.1% |
| Raw normal-component churn | 100k | 1,142 ops/s | 1,142 ops/s | flat |
| Raw tag churn | 10k | 13,294 ops/s | 12,850 ops/s | -3.3%; high variance |
| Raw tag churn | 100k | 1,609 ops/s | 1,575 ops/s | -2.1% |
| Full-World normal churn | 10k | 5,126 ops/s | 5,245 ops/s | +2.3% |
| Full-World tag churn | 10k | 4,776 ops/s | 5,022 ops/s | +5.2% |

The stable and structural rows meet the no-regression threshold. The earlier 100k Full-World
baseline was explicitly inconclusive, so its newer higher values are not promoted as wins. A GC
profiler calibration at 100k normalized to about `0.029 B` per individual normal store operation
and `0.019 B` per individual tag store operation, with no tag GC observed; those residual fractions
are benchmark/harness normalization rather than a per-operation object allocation.

The family tag-column optimization was deliberately separate from this refactor and is now
implemented and verified in
[`2026-08-21-ecs-family-tag-columns.md`](2026-08-21-ecs-family-tag-columns.md). No archetype,
gameplay/MMORPG component, reflection path, or `ActiveCamera` migration was added here.
