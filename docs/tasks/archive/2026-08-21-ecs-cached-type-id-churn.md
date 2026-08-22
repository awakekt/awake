# 2026-08-21: ECS cached type-ID structural churn

Status: accepted. Public ECS shape and sparse/family storage remain unchanged.

## Problem

`awakeFamilyChurn` already cached `Transform`'s `ComponentTypeId`, but the reified type-ID
overloads still evaluated `T::class` for every entity. A JFR allocation profile showed
`ReflectionFactory.getOrCreateKotlinClass` dominating allocation samples. At 100k, the matched
GC-profiler baseline allocated 3,698,373.520 B/op for one remove-all/add-all invocation.

## Change

- Resolve the component store before entering `World.addInternal`.
- Recycle replacements through the already-bound `ComponentTypeId` pool slot.
- Remove the unused `KClass` parameter from `World.removeInternal`.
- Keep lazy store creation for callers that register a type ID before creating its store.

The change adds no storage strategy, manager, public receiver, or authored game abstraction.
`World` remains the facade and `ComponentRegistry` remains the store/pool owner.

## Decision-grade results

Baseline: commit `dd1276aee`. Candidate: working tree based on that commit. Both used Kotlin
2.4.10, Temurin JDK 17.0.19, 3 forks, 5 one-second warmups, and 5 one-second measurements.

| Workload | Size | Before | After | Change |
|---|---:|---:|---:|---:|
| Component add/remove | 10k | 2,351.023 ± 325.476 | **2,747.332 ± 75.253** | +16.9% |
| Component add/remove | 100k | 180.436 ± 13.271 | **220.363 ± 5.803** | +22.1% |
| Maintained-family churn | 10k | 2,358.154 ± 194.753 | **2,475.613 ± 93.179** | +5.0% |
| Maintained-family churn | 100k | 105.174 ± 15.682 | **111.574 ± 13.803** | +6.1% |

Ops/sec, all rows. The unchanged 100k create/destroy negative control measured
853.802 ± 58.738 before and 864.026 ± 57.685 after.

## Allocation evidence

Matched 100k `-prof gc` runs used one fork and the same 5x5 timing:

| Metric | Before | After | Change |
|---|---:|---:|---:|
| Normalized allocation | 3,698,373.520 B/op | 368,606.457 B/op | -90.0% |
| Observed GC count | 17 | 3 | -82.4% |

The counts are totals for each five-iteration profiler run, not a per-operation guarantee. A
candidate JFR follow-up no longer contained `ReflectionFactory.getOrCreateKotlinClass` among the
hot-path allocation samples.

## Rejected follow-up

A second experiment used `EntityArena`'s signature as proof that a component was absent and
skipped `ComponentStore`'s membership lookup on insertion. It made both scales slower and more
variable in the matched candidate run, so all code from that experiment was reverted. No
complexity suppression was added to `ComponentStore`.

## Reproduction

Build the benchmark JAR:

```bash
./gradlew :awake:ecs:benchmark:mainBenchmarkJar --no-daemon
```

Run the two focused workloads with `-f 3 -wi 5 -i 5 -w 1s -r 1s` and filters
`.*\.awakeComponentAddRemove$` and `.*\.awakeFamilyChurn$`. Add `-p entityCount=100000 -f 1
-prof gc` for the allocation pass.

## Next target

Profile the remaining maintained-family membership work independently. Do not combine it with
archetype routing or trust signatures to bypass sparse-set invariants without a new benchmark
control and correctness proof.

## Follow-up: family-maintenance decomposition

`FamilyMaintenanceProfilingBenchmarks` now runs the same pooled `Transform` remove-all/add-all
cycle with no maintained family, a `Family1<Transform>`, or a
`Family2<Transform, MeshRenderer>`. Trial setup is outside measurement, entity handles are kept in
a `LongArray`, and every variant performs exactly `2 * entityCount` structural mutations. This
isolates store and family-index maintenance from fixture construction and boxed iteration.

The decision-grade 100k run used Temurin JDK 17.0.19, 3 forks, 5 one-second warmups, and 5
one-second measurements:

| Maintained family | Throughput | Approx. ns/mutation |
|---|---:|---:|
| None | **205.309 ± 10.146 ops/s** | **24.35** |
| `Family1<Transform>` | 161.837 ± 46.882 ops/s | 30.90 |
| `Family2<Transform, MeshRenderer>` | 152.124 ± 13.780 ops/s | 32.87 |

The two-component family is the reliable signal: it costs about 8.5 ns more per mutation than the
no-family control in this workload (roughly 35% more time per mutation). The one-component result
has overlapping confidence intervals and substantial fork variance, so it is directional rather
than decision-grade on its own.

A separate 100k GC-profiler pass reported 27-44 B/op for an entire 200k-mutation invocation and no
collections for all three variants. The remaining gap is CPU/data-structure work, not allocation
pressure. A focused `Family2` JFR recording placed `EntityIndexMap.set`,
`SparseEntitySet.addNew`, `FamilyRegistry.removeComponent`, `FamilyValueColumn.insert`, and
`Family2Cache.removeAt` among the leading execution samples. Capacity-check methods also appear in
samples even though the steady-state structures are already allocated.

### Profiling conclusion

Do not add another broad cache or change the public ECS shape from this evidence. The final bounded
experiment targeted redundant family-index probing/capacity checks in `Family2` removal while
retaining the no-family and `Family1` controls.

The candidate fused sparse lookup/removal and used a no-growth update for the already-indexed
swap-moved entity. Its consecutive swap-removal/re-add correctness test passed, but its matched
10k decision run did not:

| Variant | Throughput | Change |
|---|---:|---:|
| Baseline | **2,996.705 ± 160.040 ops/s** | — |
| Fused `Family2` sparse update | 2,939.131 ± 160.710 ops/s | -1.9% |

Both runs used separate frozen JARs, Temurin JDK 17.0.19, 3 forks, 5 one-second warmups, and 5
one-second measurements. The intervals overlap and the candidate mean is lower, so the complete
production experiment and its candidate-only test were reverted.

This closes the single-operation micro-optimization lane. The retained result is the profiling
benchmark and evidence, not an unproven hot-path change. The broader, separately gated plan for
avoiding repeated maintenance across large batches is
[`2026-08-21-ecs-adaptive-bulk-mutation-plan.md`](../2026-08-21-ecs-adaptive-bulk-mutation-plan.md).

Reproduce the decomposition with filter
`.*FamilyMaintenanceProfilingBenchmarks\\.pooledTransformChurn$`; select a scale or family with
`-p entityCount=100000` and `-p familyArity=2`. Add `-prof gc` or `-prof jfr:dir=<directory>` for
the profiler passes.
