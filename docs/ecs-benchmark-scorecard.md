# ECS Benchmark Scorecard

> **Naming note:** `SparseIndex` and `GeneralFamilyCache`/`GeneralFamily.kt` mentioned in the
> dated sections below were later renamed to `EntityIndexMap` and `FamilySpecCache`/
> `FamilySpec.kt` for clarity. Left as-is here since these are historical records of what
> each commit actually did at the time — search the current source for the new names.

> Current matrix below: run 2026-08-21 on `Rons-MacBook-Pro-2.local` with Kotlin 2.4.10
> and Temurin JDK 17.0.19, 1 fork, 5 warmup iterations, 5 measurement iterations, 1 second
> per iteration. Historical profiling notes below are preserved for context.
>
> Awake benchmark shape: pure `awake-ecs` runtime plus `awake-scene` components/systems.

## Current matrix

Latest rerun: `2026-08-21`, from the working tree based on commit `851663afb`, using the exact
four-library operation filter recorded in the storage decision report. The `Awake` column is
bolded so it stands out quickly in plain markdown. Values are score ± 99.9% JMH error.

| Benchmark | Size | **Awake** | Fleks 2.14 | Artemis-odb 2.3.0 | Ashley 1.7.3 | Fastest |
|---|---:|---:|---:|---:|---:|---|
| Entity create/destroy | 10k | **8,634.117 ± 141.032** | 7,755.325 ± 535.862 | 2,566.932 ± 3,219.979 | 155.432 ± 8.738 | Awake |
| Entity create/destroy | 100k | **908.979 ± 9.607** | 801.458 ± 27.819 | 481.423 ± 137.157 | 2.188 ± 0.064 | Awake |
| Component add/remove | 10k | **2,633.746 ± 25.505** | 3,026.158 ± 33.212 | 2,527.302 ± 1,061.283 | 392.222 ± 8.708 | Fleks |
| Component add/remove | 100k | **188.597 ± 4.029** | 196.045 ± 1.698 | 121.580 ± 136.596 | 30.677 ± 2.173 | Fleks |
| Family churn | 10k | **2,649.934 ± 54.666** | 2,854.911 ± 81.468 | 821.615 ± 29.621 | 167.416 ± 10.256 | Fleks |
| Family churn | 100k | **110.505 ± 39.259** | 161.719 ± 29.785 | 241.011 ± 4.819 | 2.989 ± 0.379 | Artemis-odb |
| Transform hierarchy | depth 10 | **3,122,865.827 ± 32,474.962** | 747,124.930 ± 5,461.747 | 885,073.547 ± 30,867.393 | 791,041.812 ± 11,443.323 | Awake |
| Transform hierarchy | depth 50 | **734,005.393 ± 47,941.002** | 146,549.743 ± 5,569.909 | 179,985.282 ± 3,417.655 | 151,345.590 ± 17,488.427 | Awake |
| Transform+MeshRenderer query | 10k | **425,929.064 ± 9,886.741** | 79,379.361 ± 45,331.098 | 58,891.964 ± 5,390.279 | 29,397.653 ± 2,422.559 | Awake |
| Transform+MeshRenderer query | 100k | **44,982.856 ± 3,192.015** | 6,890.007 ± 14,194.920 | 2,719.384 ± 462.034 | 298.193 ± 62.198 | Awake |

Ops/sec, all rows. Awake is bold so the eye lands on it first.

## Cached type-ID structural churn (2026-08-21)

JFR profiling found that the supposedly cached `ComponentTypeId` path still evaluated
`T::class` on every add/remove. The retained change resolves a store before entering the shared
mutation routine, recycles replaced values by type ID, and removes the unused class argument from
removal. Public APIs and sparse/family storage shapes are unchanged.

Matched baseline commit `dd1276aee` and candidate runs used Temurin JDK 17.0.19, Kotlin 2.4.10,
3 forks, 5 one-second warmups, and 5 one-second measurements:

| Awake workload | Size | Before | After | Change |
|---|---:|---:|---:|---:|
| Component add/remove | 10k | 2,351.023 ± 325.476 | **2,747.332 ± 75.253** | +16.9% |
| Component add/remove | 100k | 180.436 ± 13.271 | **220.363 ± 5.803** | +22.1% |
| Maintained-family churn | 10k | 2,358.154 ± 194.753 | **2,475.613 ± 93.179** | +5.0% |
| Maintained-family churn | 100k | 105.174 ± 15.682 | **111.574 ± 13.803** | +6.1% |

Ops/sec, all rows. A matched 100k `-prof gc` run reduced normalized family-churn allocation
from 3,698,373.520 B/op to 368,606.457 B/op (about 90%) and observed 17 versus 3 collections.
JFR allocation samples no longer showed `ReflectionFactory.getOrCreateKotlinClass` on the hot
path. The untouched 100k create/destroy control was flat within error: 853.802 ± 58.738 before
and 864.026 ± 57.685 after.

A follow-up that trusted the world signature to skip `ComponentStore`'s membership lookup was
slower and noisier at both scales, so it was fully reverted. The broader single-fork library
comparison also had wide Fleks/Artemis intervals; it is retained as raw evidence, not used to
rewrite the full-matrix rankings above. Reproduction details and the closed follow-up are in the
[cached type-ID churn report](tasks/archive/2026-08-21-ecs-cached-type-id-churn.md).

### Final family-index experiment

An isolated no-family/`Family1`/`Family2` profiling benchmark confirmed that the remaining
two-component-family gap is CPU/index maintenance rather than allocation pressure. A final
candidate fused `Family2` sparse lookup/removal and bypassed growth checks only for the
already-indexed swap-moved entity. The matched 10k run measured 2,996.705 ± 160.040 ops/s for the
baseline and 2,939.131 ± 160.710 ops/s for the candidate (-1.9%). The candidate was fully reverted.
The profiling benchmark remains as a reproducible control. This micro-optimization lane is closed;
the separately gated next feature is
[adaptive bulk structural mutation](tasks/2026-08-21-ecs-adaptive-bulk-mutation-plan.md), which
targets repeated work across large batches without changing storage shape.

## Family tag-column specialization (2026-08-21)

This focused decision run uses Kotlin 2.4.10 and Temurin JDK 17.0.19. Both baseline commit
`5358d92fa` and the final implementation used 3 forks, 5 one-second warmups, and 5 one-second
measurements. The change retains one `EcsTag` singleton in a maintained family instead of one
reference per entity; public array access remains available through lazy materialization.

| Benchmark | Size | Before | After | Change |
|---|---:|---:|---:|---:|
| Tag-family churn | 10k | 3,158.275 ± 160.152 | **3,209.308 ± 74.373** | +1.6% |
| Tag-family churn | 100k | 284.483 ± 10.845 | **304.939 ± 22.840** | +7.2% |
| Tag-family iteration | 10k | 208,232.122 ± 2,132.950 | **492,879.156 ± 7,556.426** | +136.7% |
| Tag-family iteration | 100k | 20,206.434 ± 777.717 | **48,879.048 ± 1,040.246** | +141.9% |
| Ordinary family iteration | 10k | **513,426.572 ± 4,785.585** | 508,014.300 ± 14,267.735 | -1.1% |
| Ordinary family iteration | 100k | 51,197.918 ± 434.602 | **51,496.869 ± 825.746** | +0.6% |

The unchanged ordinary path remains inside the 5% regression gate. A 100k GC-profiler check
observed no collection during tag-family iteration. Full methodology, correctness coverage,
Kotlin guidance, and the broader design matrix are in
[`docs/tasks/archive/2026-08-21-ecs-family-tag-columns.md`](tasks/archive/2026-08-21-ecs-family-tag-columns.md).

## Bulk-mutation batch size and density profile (2026-08-21)

Phase 0 evidence freeze for the
[adaptive bulk structural mutation plan](tasks/2026-08-21-ecs-adaptive-bulk-mutation-plan.md).
This is a deliberately reduced first pass: batch sizes 1, 100, 10k, and 100k, and family density
1% and 100%.

Every row is the `immediate` control — one `World.add`/`World.remove` call per mutation. There is
no forced-incremental, forced-rebuild, or adaptive control here because none of that code exists;
those controls ship with the code they measure in plan phases 1, 3, and 4.

Working tree based on commit `5868aa8c7` on `Rons-MacBook-Pro-2.local` (Apple M3 Pro, 11 cores,
18 GiB, AC power), Kotlin 2.4.10, Temurin JDK 17.0.19, 1 fork, 5 one-second warmups, and
15 one-second measurements. Fifteen measurement iterations rather than the usual five: at five, the
invocation-restored add/remove halves reported up to ±90% error and were unusable. The host was on
AC power but not otherwise quiesced, and this is a single fork — treat the numbers as a profile
shape, not as a decision-grade baseline. Phase 4's 3-fork idle-host run is the baseline. Frozen JSON is
in [`benchmarks/2026-08-21-ecs-bulk-mutation-phase0.json`](benchmarks/2026-08-21-ecs-bulk-mutation-phase0.json)
and [`benchmarks/2026-08-21-ecs-bulk-mutation-phase0-sequential-anchor.json`](benchmarks/2026-08-21-ecs-bulk-mutation-phase0-sequential-anchor.json).

```bash
./gradlew :awake:ecs:benchmark:mainBenchmarkJar --no-daemon
java -jar awake/ecs/benchmark/build/benchmarks/main/jars/benchmark-main-jmh-*-JMH.jar \
  '.*BulkMutationProfilingBenchmarks.*' -f 1 -wi 5 -i 15 -w 1s -r 1s
```

The world is a fixed 100,000 entities in every row; batch size is how many of them one invocation
mutates, so even a batch of 1 walks a 100,000-wide sparse index. Density is the share of entities
carrying `MeshRenderer`. It is a live axis only at arity 2, where it sets how many `Transform`
mutations also move an entity in or out of `Family2`; at arity 0 and 1 the two density rows repeat
the same work on differently sized heaps and act as a consistency check.

### Interleaved add/remove (mixed)

Each invocation performs `2 x batch` mutations as per-entity remove-then-add pairs, which restores
the world without any invocation fixture. The parenthetical is nanoseconds per individual
structural mutation — throughput alone is not comparable across five orders of magnitude of batch.

| Batch | Density | No family | `Family1<Transform>` | `Family2<Transform, MeshRenderer>` |
|---:|---:|---:|---:|---:|
| 1 | 1% | 36,505,659.206 ± 1,027,521.723 (13.7) | 28,586,260.643 ± 511,491.363 (17.5) | 35,956,916.053 ± 550,979.476 (13.9) |
| 1 | 100% | 33,224,170.499 ± 2,200,834.171 (15.0) | 26,553,993.196 ± 716,312.452 (18.8) | 24,664,404.085 ± 591,022.438 (20.3) |
| 100 | 1% | 408,159.361 ± 5,947.375 (12.3) | 277,905.641 ± 4,138.687 (18.0) | 340,355.459 ± 5,295.558 (14.7) |
| 100 | 100% | 450,759.953 ± 5,498.578 (11.1) | 291,373.877 ± 8,836.419 (17.2) | 225,459.663 ± 346.719 (22.2) |
| 10,000 | 1% | 4,279.213 ± 30.811 (11.7) | 2,723.714 ± 56.621 (18.4) | 3,371.811 ± 36.333 (14.8) |
| 10,000 | 100% | 3,382.807 ± 53.446 (14.8) | 2,480.083 ± 215.399 (20.2) | 2,179.961 ± 12.460 (22.9) |
| 100,000 | 1% | 335.705 ± 10.649 (14.9) | 252.744 ± 12.224 (19.8) | 320.292 ± 27.270 (15.6) |
| 100,000 | 100% | 319.384 ± 23.717 (15.7) | 220.447 ± 26.490 (22.7) | 180.854 ± 5.715 (27.6) |

Three results decide how phase 4 has to be designed:

1. **Batch size is not a crossover variable on its own.** Per-mutation cost is 11-28 ns from a
   batch of 1 to a batch of 100,000. The immediate path carries no per-batch fixed cost for a
   command buffer to amortize; the only growth is a mild cache effect (about +20% from 10k to 100k).
2. **The entire addressable cost is family maintenance.** At 100k and 100% density the arity ladder
   is 15.7 → 22.7 → 27.6 ns per mutation, so a `Family1` update costs about 7 ns and a `Family2`
   update about 12 ns. Drop density to 1% and arity 2 falls back to 15.6 ns, statistically
   indistinguishable from the no-family control.
3. **Therefore the adaptive threshold must key on affected-family membership changes, not batch
   size.** At 1% density a batch of 100,000 produces roughly 1,000 family updates; rebuilding a
   family from its store would do strictly more work than the incremental path it replaces.

The no-family control is also the hard ceiling for any family-maintenance work. At 100k/100% that
ceiling is 319.384 ops/s, which clears the plan's 241.011 ops/s Artemis target with about 32%
headroom — so the gate is reachable, but only by removing nearly all per-mutation family
maintenance, not by shaving it.

### Isolated add and remove halves

Both restore their precondition in a `Level.Invocation` fixture, so these values are comparable
within this table only, never against the self-restoring table above. Batch 1 and 100 are absent on
purpose: there the untimed restore is the same order as the timed work.

| Operation | Batch | Density | No family | `Family1<Transform>` | `Family2<Transform, MeshRenderer>` |
|---|---:|---:|---:|---:|---:|
| Add-only | 10,000 | 1% | 11,240.871 ± 1,562.048 (8.9) | 7,802.623 ± 610.287 (12.8) | 9,587.874 ± 524.892 (10.4) |
| Add-only | 10,000 | 100% | 11,622.050 ± 301.734 (8.6) | 8,291.359 ± 269.629 (12.1) | 6,051.236 ± 236.898 (16.5) |
| Add-only | 100,000 | 1% | 501.857 ± 38.636 (19.9) | 421.918 ± 12.396 (23.7) | 472.064 ± 20.059 (21.2) |
| Add-only | 100,000 | 100% | 459.847 ± 110.534 (21.7) | 384.466 ± 49.616 (26.0) | 324.623 ± 15.270 (30.8) |
| Remove-only | 10,000 | 1% | 5,136.042 ± 712.660 (19.5) | 4,633.462 ± 304.309 (21.6) | 7,029.177 ± 337.030 (14.2) |
| Remove-only | 10,000 | 100% | 5,600.364 ± 96.908 (17.9) | 4,340.749 ± 240.051 (23.0) | 4,344.749 ± 52.648 (23.0) |
| Remove-only | 100,000 | 1% | 468.690 ± 31.732 (21.3) | 438.120 ± 17.091 (22.8) | 409.208 ± 8.116 (24.4) |
| Remove-only | 100,000 | 100% | 376.148 ± 12.337 (26.6) | 318.464 ± 9.495 (31.4) | 289.614 ± 13.108 (34.5) |

The halves confirm the mixed table's shape rather than adding a new one: density separates arity 2
from the controls (16.5 vs 10.4 ns for add at 10k; 30.8 vs 21.2 ns at 100k), and removal is the
more expensive half at every 100% density point. Nothing here suggests add and remove need separate
batching policies.

### Tag-family churn

`Family1` over a singleton `EcsTag`, same interleaved shape. Neither arity nor density applies: a
tag family is one-arity and every tagged entity is a member.

| Batch | Throughput | ns per mutation |
|---:|---:|---:|
| 1 | 37,928,990.104 ± 4,728,790.850 | 13.2 |
| 100 | 531,022.282 ± 45,745.108 | 9.4 |
| 10,000 | 5,910.724 ± 366.680 | 8.5 |
| 100,000 | 588.985 ± 20.777 | 8.5 |

Tag churn is the cheapest structural mutation Awake has, at 8.5 ns and flat across batch size —
about 3.3x cheaper than `Family2` churn at 100% density and cheaper even than the no-family
control, because a singleton tag column stores no payload. Batching has the least to win here, and
the tag path must not regress to buy a win elsewhere.

### Mutation ordering within a batch

`FamilyMaintenanceProfilingBenchmarks.pooledTransformChurn` was re-run in the same session and
settings as the anchor for the sequential shape (remove every entity, then add every entity back).
Its 100k fixture matches the density-100% fixture above exactly; its 10k fixture does not, because
it builds a 10,000-entity world rather than a 10,000-entity batch inside a 100,000-entity world.
Only the 100k rows are compared.

| Family | Sequential remove-all/add-all | Interleaved per-entity pairs | Change |
|---|---:|---:|---:|
| None | 244.565 ± 88.864 | 319.384 ± 23.717 | +30.6% |
| `Family1<Transform>` | 176.731 ± 31.251 | 220.447 ± 26.490 | +24.7% |
| `Family2<Transform, MeshRenderer>` | 167.677 ± 10.519 | 180.854 ± 5.715 | +7.9% |

Interleaving is faster in all three, but every interval overlaps at one fork, so this is
directional only. It is recorded because phase 2 has to choose an apply order, and the cheap
hypothesis — keep an entity's commands adjacent so its sparse slots stay in cache — currently has
evidence pointing at it. Confirm with 3 forks before designing around it.

### Cross-library semantics

The four-library rows in the current matrix were re-read rather than re-run; this pass adds Awake
profiling depth, not new Fleks/Artemis/Ashley sizes. Awake, Fleks, and Ashley all maintain family
membership immediately inside each add/remove. Artemis-odb does not: `ComponentMapper.remove`/
`create` mark the entity, and `EntitySubscription` membership is reconciled once inside
`world.process()`, which `artemisFamilyChurn` calls after its loops. Artemis's 241.011 ops/s at
100k family churn is therefore already a batched family update, and should be labelled as an
unmatched bulk API rather than as a like-for-like structural-churn score. That is a point in the
plan's favour: the capability it proposes is one Artemis has and the other three do not.

### Deferred, not dropped

| Item | Phase it belongs to |
|---|---|
| Forced-incremental and forced-rebuild controls | Landed early — see the forced-rebuild section below |
| Adaptive control | Phase 4 |
| Batch sizes 10 and 1k | Phase 4, when the crossover is being located |
| Family density 25% | Phase 4, same reason |
| Replacement (add-then-replace) coverage | Phase 1, with the ordering/conflict model tests |
| `FamilySpec` coverage | Phase 3, with excluded/one-of spec rebuild proofs |
| 3 forks and GC/JFR evidence | Phase 4, the decision-grade run |

Ops/sec, all rows.

## Forced-rebuild vs incremental family maintenance (2026-08-21)

A de-risking spike run out of order, ahead of phases 1 and 2 of the
[adaptive bulk structural mutation plan](tasks/2026-08-21-ecs-adaptive-bulk-mutation-plan.md).
That plan's stop condition — revert if forced rebuild never beats incremental maintenance at any
realistic batch/density point — is testable as a benchmark-only control without building the
command buffer first, so it was tested first.

`BulkRebuildStrategyBenchmarks` and `RebuildFamilyControl` are benchmark-only controls. No
production batch API exists and none was added; `:awake:ecs` is unchanged. `ControlFamily1`/
`ControlFamily2`/`ControlFamilyRegistry` replicate `Family1Cache`/`Family2Cache`/`FamilyRegistry`
because those are `internal`, and both arms drive the same replica so the measured difference is the
maintenance strategy alone. `RebuildFamilyControlTest` proves the rebuilt family's membership,
per-entity value identity, and sparse repair match both the incremental control and the real
`Family1`/`Family2`, across swap-remove churn, destroy with recycled generations, and a tag column.

Same host and toolchain as the phase-0 profile: `Rons-MacBook-Pro-2.local` (Apple M3 Pro, 11 cores,
18 GiB, AC power), Kotlin 2.4.10, Temurin JDK 17.0.19, 1 fork, 5 one-second warmups, 15 one-second
measurements. `:awake:ecs` is unchanged since `1b2b5765e`; the working tree was at `6e8f8002f`,
whose extra commits touch `awake/core`, `awake/engine`, and `awake/platform` only.

Read these as a directional profile, not a baseline. Beyond the single fork, an unrelated build was
running in the same tree throughout the measurement, so the host carried background compilation
load. The ordering below survives that comfortably — the losses are 5x to 125x and the wins are
24-36% — but treat every individual percentage as soft until the 3-fork idle-host run. Frozen JSON
is in [`benchmarks/2026-08-21-ecs-bulk-rebuild-spike.json`](benchmarks/2026-08-21-ecs-bulk-rebuild-spike.json).

```bash
./gradlew :awake:ecs:benchmark:mainBenchmarkJar --no-daemon
java -jar awake/ecs/benchmark/build/benchmarks/main/jars/benchmark-main-jmh-*-JMH.jar \
  '.*(BulkMutationProfilingBenchmarks\.immediateMixedInterleaved|BulkRebuildStrategyBenchmarks\..*)' \
  -p batchSize=100,10000,100000 -p densityPercent=1,100 -p familyArity=0,1,2 \
  -f 1 -wi 5 -i 15 -w 1s -r 1s
```

The workload is phase 0's self-restoring interleaved shape in a fixed 100,000-entity world:
`2 x batch` mutations as per-entity remove-then-add pairs. The parenthetical is nanoseconds per
individual structural mutation. Arity 0 registers no family, so its rows measure store work plus
each strategy's own bookkeeping and act as the ceiling and the harness-overhead check.

| Batch | Density | Arity | immediate (production) | control incremental | control forced rebuild | Rebuild vs production |
|---:|---:|---:|---:|---:|---:|---:|
| 100 | 1% | 0 | 438,449.200 ± 23,521.739 (11.4) | 375,162.990 ± 13,822.685 (13.3) | 334,654.192 ± 9,252.794 (14.9) | -23.7% |
| 100 | 1% | 1 | 270,676.730 ± 6,083.197 (18.5) | 229,148.266 ± 10,369.051 (21.8) | 3,271.542 ± 89.708 (1528.3) | -98.8% |
| 100 | 1% | 2 | 358,921.725 ± 3,569.847 (13.9) | 294,715.005 ± 15,536.913 (17.0) | 60,756.477 ± 1,202.740 (82.3) | -83.1% |
| 100 | 100% | 0 | 415,159.772 ± 17,272.015 (12.0) | 343,383.676 ± 46,732.396 (14.6) | 392,995.510 ± 17,082.657 (12.7) | -5.3% |
| 100 | 100% | 1 | 245,548.415 ± 35,976.890 (20.4) | 239,600.320 ± 15,794.084 (20.9) | 3,200.654 ± 88.095 (1562.2) | -98.7% |
| 100 | 100% | 2 | 227,921.907 ± 4,032.545 (21.9) | 217,780.291 ± 15,091.047 (23.0) | 1,686.587 ± 77.926 (2964.6) | -99.3% |
| 10,000 | 1% | 0 | 3,193.384 ± 132.470 (15.7) | 2,648.332 ± 397.705 (18.9) | 3,153.911 ± 266.202 (15.9) | -1.2% |
| 10,000 | 1% | 1 | 2,701.601 ± 129.617 (18.5) | 1,912.905 ± 482.504 (26.1) | 1,500.764 ± 84.586 (33.3) | -44.4% |
| 10,000 | 1% | 2 | 2,914.220 ± 94.953 (17.2) | 2,813.625 ± 169.227 (17.8) | 3,205.403 ± 330.766 (15.6) | +10.0% |
| 10,000 | 100% | 0 | 3,333.946 ± 64.285 (15.0) | 2,495.844 ± 646.628 (20.0) | 3,022.929 ± 172.024 (16.5) | -9.3% |
| 10,000 | 100% | 1 | 2,570.144 ± 159.297 (19.5) | 2,188.184 ± 303.003 (22.8) | 1,576.146 ± 223.889 (31.7) | -38.7% |
| 10,000 | 100% | 2 | 1,978.573 ± 163.065 (25.3) | 2,072.820 ± 125.654 (24.1) | 1,257.186 ± 39.243 (39.8) | -36.5% |
| 100,000 | 1% | 0 | 333.949 ± 19.310 (15.0) | 321.163 ± 34.576 (15.6) | 352.471 ± 3.895 (14.2) | +5.5% |
| 100,000 | 1% | 1 | 266.508 ± 5.993 (18.8) | 222.463 ± 11.697 (22.5) | 276.181 ± 8.404 (18.1) | +3.6% |
| 100,000 | 1% | 2 | 291.747 ± 11.666 (17.1) | 246.795 ± 13.739 (20.3) | 336.495 ± 15.834 (14.9) | +15.3% |
| 100,000 | 100% | 0 | 321.239 ± 11.477 (15.6) | 319.801 ± 9.582 (15.6) | 334.773 ± 7.545 (14.9) | +4.2% |
| 100,000 | 100% | 1 | 240.696 ± 12.319 (20.8) | 205.817 ± 38.920 (24.3) | 271.727 ± 13.151 (18.4) | +12.9% |
| 100,000 | 100% | 2 | 188.611 ± 6.110 (26.5) | 190.126 ± 5.308 (26.3) | 249.666 ± 7.802 (20.0) | +32.4% |

### The head-to-head, with intervals applied

Rebuild against the matched control incremental, keeping only the rows whose confidence intervals
do not overlap. Everything else in the matrix is noise at one fork.

| Batch | Density | Arity | Rebuild vs control incremental | Rebuild vs production |
|---:|---:|---:|---:|---:|
| 100 | 1% | 1 | -98.6% | -98.8% |
| 100 | 1% | 2 | -79.4% | -83.1% |
| 100 | 100% | 1 | -98.7% | -98.7% |
| 100 | 100% | 2 | -99.2% | -99.3% |
| 10,000 | 100% | 1 | -28.0% | -38.7% |
| 10,000 | 100% | 2 | -39.3% | -36.5% |
| 100,000 | 1% | 1 | **+24.1%** | +3.6% (overlapping) |
| 100,000 | 1% | 2 | **+36.3%** | **+15.3%** |
| 100,000 | 100% | 1 | **+32.0%** | **+12.9%** |
| 100,000 | 100% | 2 | **+31.3%** | **+32.4%** |

Four findings:

1. **Forced rebuild does win, but only where the batch is the whole world.** At batch 100,000 in a
   100,000-entity world it beats matched incremental maintenance by 24-36% and production immediate
   mutation by 13-32%, with non-overlapping intervals in three of four cells. The plan's literal
   stop condition — never beats incremental at any point — is therefore not met.
2. **Everywhere else it loses, and at small batch it loses catastrophically.** At batch 100 rebuild
   is 5x to 125x slower, because it rebuilds all 100,000 members to service 200 mutations. At batch
   10,000 (a tenth of the world) it is still 28-39% slower. The 10,000/1%/arity-2 cell that looks
   like a win has overlapping intervals and is not one.
3. **The crossover is the mutations-per-member ratio, not the batch size.** Rebuild costs one append
   per surviving family member plus a full sparse-map clear; incremental costs one update per
   mutation touching the family. The win appears at 200,000 mutations against a 100,000-member
   family (ratio 2) and is gone at 20,000 against the same family (ratio 0.2). The crossover sits
   somewhere in that decade and this matrix does not locate it.
4. **Deferring maintenance is free; the win is entirely in the rebuild.** The arity-0 rows compare
   `markDirty` bookkeeping against no-op incremental dispatch, and every one of them overlaps or
   is within a few percent. Recording which families a batch dirtied costs nothing measurable.

### What this says about the plan's shape

The control that won is not the design the plan proposes. It records no commands, buffers no
values, and applies every store mutation immediately — it defers only family maintenance. Phase 0
already showed per-mutation cost is flat from batch 1 to batch 100,000, so a command buffer
amortizes no fixed cost; this spike now shows the entire measured win comes from phase 3's rebuild.
Phases 1 and 2 would add per-command recording and replay that this control never pays, so they can
only subtract from the numbers above. The +3.6% at 100,000/1%/arity 1 already sits inside the plan's
own "regress no more than 5%" band.

Two caveats a production version would inherit. A rebuild reorders the entire family, where
incremental maintenance only swap-moves the touched entries, so anything caching a dense index or
relying on frame-to-frame iteration locality sees a full reshuffle per batch. And the family caches
are keyed on `entity.id` alone: the rebuild carries packed handles correctly, but a stale-generation
handle still resolves through the sparse index exactly as it does in production, which `World`
guards with `isAlive` rather than the family.

### Not measured here

| Item | Why |
|---|---|
| Batch sizes 1k and 30k | Where the crossover actually sits; only worth locating if the lane proceeds |
| `FamilySpec`, excluded/one-of rebuild | The control covers `Family1`/`Family2` only |
| Tag-family rebuild throughput | Correctness is covered; throughput is not, and tag churn is already the cheapest path Awake has |
| GC evidence and 3 forks | Decision-grade run, not a spike |
| Command-buffer recording cost | The code does not exist; the numbers above are its upper bound |

Ops/sec, all rows.

## Sparse vs hybrid vs pure archetype decision (2026-08-21)

This decision run closes the earlier prototype's main evidence gap: the pure-archetype control
owns a table per tag signature and physically migrates each entity's `Transform` and
`MeshRenderer` row on tag changes, including source-table swap-location repair. The fragmented
query uses eight optional tags (256 observed signatures), requires tag zero, excludes tag one,
and returns exactly 25% of entities.

Temurin JDK 17.0.19, Kotlin 2.4.10, 3 forks, 5 one-second warmups, and 5 one-second
measurements were used throughout. These are benchmark-only controls; only the `Current Awake`
column is production code.

| Workload | Size | Current Awake sparse/family | Hybrid stable table + sparse tag | Pure archetype | Winner |
|---|---:|---:|---:|---:|---|
| Stable `Transform` + `MeshRenderer` iteration | 10k | **505,417.822 ± 13,033.122** | 313,175.408 ± 8,150.537 | 300,994.319 ± 12,865.085 | Awake |
| Stable `Transform` + `MeshRenderer` iteration | 100k | **50,421.543 ± 1,979.629** | 26,936.261 ± 3,256.172 | 30,989.607 ± 1,406.472 | Awake |
| Dynamic-tag structural churn | 10k | 16,663.846 ± 1,875.181 | **40,375.076 ± 1,735.303** | 5,218.882 ± 501.510 | Hybrid raw control |
| Dynamic-tag structural churn | 100k | 1,695.029 ± 48.884 | **4,006.350 ± 166.442** | 530.804 ± 13.058 | Hybrid raw control |
| 256-signature required/excluded query | 10k | **6,222,312.750 ± 391,448.281** | 308,398.733 ± 11,326.403 | 1,428,708.525 ± 336,424.760 | Awake |
| 256-signature required/excluded query | 100k | **440,046.129 ± 25,399.027** | 27,879.333 ± 3,375.323 | 205,277.897 ± 33,777.757 | Awake |

Ops/sec, all rows. The structural-churn row deliberately labels the minimal hybrid control as
such: Awake's raw `ComponentStore` retains production validation and singleton-tag semantics,
so the synthetic hybrid number is useful for the storage-shape ceiling, not as a claim that a
second public ECS already exists. Pure archetype vs hybrid is directly matched: both retain the
same stable rows, but only pure archetype copies those rows when a tag changes. Hybrid was about
7.6x faster at 100k churn. Awake's maintained family was about 2.1x faster than archetype table
matching at the 100k fragmented query and about 1.6x faster at stable iteration.

A corrected 100k `-prof gc` follow-up moved immutable fixtures to `Setup(Level.Trial)` so setup
allocation was not attributed to operations. All six controls recorded no collection. Normalized
allocation was close to the profiler floor: current/hybrid/pure query paths reported
0.013/0.184/0.024 B/op, while raw sparse/hybrid/pure churn reported 3.381/1.405/10.979 B/op for
an entire remove-all/add-all invocation.

Decision: retain sparse component stores, singleton `EcsTag` storage, and maintained dense
families. Do not add production archetype routing unless a new real workload beats these controls
by the documented migration gate. Full implementation and methodology are in the
[hybrid storage decision note](tasks/archive/2026-08-18-ecs-hybrid-archetype-sparse-set.md).

## Takeaway

- Awake leads create/destroy at both scales and retains 6 of 10 category wins.
- Transform hierarchy remains Awake's clearest lead: about 3.5x over the next score at depth 10 and 4.1x at depth 50.
- Awake query iteration remains first at both scales. Fleks's single-fork query errors are broad, so use the recorded scores as the current matrix rather than a precise ratio claim.
- The "Decoupled World" architecture has successfully isolated concerns into `EntityArena`, `ComponentRegistry`, and `QueryCache` without losing the performance profile from previous rounds.
- Cached type-ID mutation removed the first structural-change allocation hotspot. The subsequent
  family-index fusion did not improve the matched benchmark and was reverted; no further
  single-operation micro-optimization is currently justified by the profile. Adaptive batching is
  tracked separately and must earn its production API through matched gates.
- The phase-0 bulk profile shows per-mutation cost is flat from batch 1 to batch 100k, so batching
  can only win by removing family maintenance, and only where a batch actually changes family
  membership. Density, not batch size, is the crossover variable.
- The forced-rebuild spike narrows that further: rebuilding a dirty family once beats incremental
  maintenance only when the batch mutates roughly the whole family, and loses by up to 125x below
  that. Deferring maintenance is free; the command buffer the plan proposed is not worth building.

## Architecture reference (not benchmarked here — different language/runtime)

Do not compare raw throughput across JVM, C/C++, Rust, and Burst runtimes. These rows identify
design tradeoffs; only the Awake/Fleks/Artemis/Ashley tables above are same-runtime measurements.

| ECS | Core storage | Iteration bias | Structural-change bias | Complexity / portability |
|---|---|---|---|---|
| Awake | Per-type sparse sets plus maintained dense typed families; singleton tag columns | Dense arrays; tags branch once outside the loop | O(1) swap removal; no archetype migration | Small dependency-free KMP API; 64 types, single-threaded by design |
| Flecs 4.1 | Archetype tables, cached queries, optional `Sparse`/`DontFragment` traits | Table streaming and cached table matches | Default add/remove changes archetype; `DontFragment` avoids table fragmentation | Feature-rich C/C++ runtime with relationships, observers, reflection, pipelines |
| EnTT | Paged sparse sets and packed pools | Packed-pool views/groups | Sparse-set optimized; configurable deletion/pointer policies | Header-only, highly customizable C++ templates |
| Bevy 0.19 | Per-component table or sparse-set choice | Table is documented as cache-friendly | Sparse set is documented as faster for add/remove | Explicit hybrid policy inside a full Rust engine/scheduler |
| Unity Entities 1.0 | Archetype-specific 16 KiB chunks | Packed chunk arrays, cached queries, Burst/jobs | Component add/remove moves entities between archetypes | Industrial C#/Burst toolchain with substantially greater machinery |

Primary references: [Flecs queries](https://www.flecs.dev/flecs/md_docs_2Queries.html),
[Flecs component traits](https://www.flecs.dev/flecs/md_docs_2ComponentTraits.html),
[EnTT storage](https://github.com/skypjack/entt/wiki/Entity-Component-System),
[Bevy storage types](https://docs.rs/bevy/latest/bevy/ecs/component/enum.StorageType.html), and
[Unity archetypes](https://docs.unity.cn/Packages/com.unity.entities%401.0/manual/concepts-archetypes.html).

## Decoupled World & Clean Architecture (2026-07-09)

1. **Decoupled World Architecture**: Split `World` into `EntityArena`, `ComponentRegistry`, and `QueryCache` internal managers. `World` is now a thin facade coordinating these specialized units.
2. **Tiered Query Invalidation**: Implemented `emptyQueryVersion` and `typedQueryVersion` in `QueryCache`. `create()` only invalidates empty queries (all-alive-entities), while component changes invalidate typed queries. This preserves the lifecycle boost while ensuring correctness.
3. **UI Scroll Fix**: Resolved a critical UI bug where `beginFrame` clobbered global input state during measurement passes. The fix gates the `pointerOverScrollable` reset on non-measuring contexts.
4. **Verified KMP Hygiene**: All optimizations and the new decoupled architecture are verified across Desktop JVM and tested for multiplatform readiness.
5. **Maintained Performance**: The 2026-08-21 JMH refresh confirms Awake leads 6 of 10 major benchmark categories; component and family churn remain the explicit gaps.
