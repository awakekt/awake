# 2026-08-21: ECS adaptive bulk structural mutation plan

Status: **narrowed**. No production implementation exists and none is authorized by this document
as written. A benchmark-only forced-rebuild spike (2026-08-21, below) tested the stop condition
ahead of phases 1 and 2 and changed what the lane should build. Read
[Spike: forced rebuild vs incremental](#spike-forced-rebuild-vs-incremental-2026-08-21) before
starting any phase.

Phase 0 has had a reduced first pass
(2026-08-21): batch sizes 1/100/10k/100k at 1% and 100% density, add-only, remove-only, mixed, and
tag churn, across family arity 0/1/2, immediate control only. Results are in the
[bulk-mutation batch size and density profile](../ecs-benchmark-scorecard.md#bulk-mutation-batch-size-and-density-profile-2026-08-21).

The remaining Phase 0 items split two ways. The forced-incremental and forced-rebuild controls
turned out not to need phases 1-3 at all — the 2026-08-21 spike built them as benchmark-only
replicas and got the answer without a line of production code. Replacement and `FamilySpec`
coverage still wait on the ordering-model and rebuild work. The 25% density point is held for
Phase 4. The Fleks and Artemis gate numbers were not re-measured: the acceptance gates ask for a
*fresh same-semantics* comparison, which is the Phase 4 decision run, so re-running them now would
freeze a result that has to be thrown away.

What the first pass changed about the plan: per-mutation cost is flat from batch 1 to batch 100k,
so an allocation-free command buffer buys nothing by itself. The whole addressable cost is family
maintenance (about 7 ns per `Family1` update and 12 ns per `Family2` update), and at 1% density
arity 2 is already indistinguishable from the no-family control. Phase 4's policy must therefore
key on affected-family membership changes, not batch size. The no-family control at 100k/100%
(319.384 ops/s) is the hard ceiling for this lane and clears the 241.011 ops/s Artemis target by
about 32%, so the gate is reachable only if batching removes nearly all per-mutation family
maintenance.

## Objective

Improve large component/family mutation batches without weakening Awake's fast maintained-family
iteration or changing the default immediate `World.add`/`remove` behavior.

This is the next feature lane after the cached type-ID win and rejected `Family2` micro-
optimization. It does not reopen archetype routing.

## Evidence and target

The current four-library JVM matrix still leaves structural churn behind its leaders:

| Workload | Awake | Current leader | Minimum mean target |
|---|---:|---:|---:|
| Family churn, 10k | 2,649.934 ops/s | Fleks, 2,854.911 | >2,854.911 ops/s |
| Family churn, 100k | 110.505 ops/s | Artemis, 241.011 | >241.011 ops/s |

These are planning thresholds, not acceptance baselines. Phase 0 must freeze fresh same-machine,
same-JDK, same-commit results with errors before implementation. A win requires non-overlapping
confidence intervals or a repeated result strong enough to resolve overlap.

Profiling established that steady-state family churn allocates only tens of bytes per complete
200k-mutation invocation. The remaining opportunity is avoiding repeated maintenance work, not
removing another allocation or bounds check.

## Proposed behavior

Keep immediate mutation unchanged. Add an opt-in batch path whose restricted receiver accepts
structural commands and flushes before returning. Queries and maintained-family iteration are not
available from that receiver, so no caller can observe partially applied family state.

Commands preserve submission order. For the same entity and component type:

| Sequence | Required result |
|---|---|
| Add, then replace | One final component; normal replacement/recycling semantics |
| Add, then remove | No component; temporary pooled value recycled exactly once |
| Remove, then add | Final component present; removed pooled value reset exactly once |
| Destroy mixed with mutation | Deterministic rejection or documented terminal-destroy rule |

The destroy rule must be selected and tested in Phase 1 before lifecycle commands join the batch.
Component add/remove can ship independently.

## Ownership

| Collaborator | Responsibility | Must not own |
|---|---|---|
| `World` | Thin immediate/batch entrypoints and commit coordination | Command arrays or rebuild policy |
| `StructuralMutationBuffer` | Reusable primitive command/type/entity/value columns | Stores, families, queries |
| `StructuralMutationPlanner` | Affected type/family counts and incremental-vs-rebuild decision | Component payloads |
| `ComponentRegistry` | Apply validated store/pool changes | Family maintenance policy |
| `FamilyRegistry` | Incremental updates or one rebuild per affected family | Command capture or public DSL |
| `QueryCache` | One invalidation at successful commit | Mutation ordering |

No god receiver or manager is introduced. Each collaborator owns one axis, and `World` remains the
public facade.

## Adaptive strategy

- Small batches use the current incremental family updates.
- Large batches apply component-store changes, mark affected families dirty, then rebuild each
  affected family once from its smallest required store.
- Phase 0 benchmarks derive the crossover from batch size, family size, density, and arity. Do not
  hardcode a threshold from one 100k benchmark. The reduced first pass already rules batch size out
  as a standalone variable: per-mutation cost is flat from 1 to 100k. Density and arity are what
  move it.
- Reusable primitive arrays grow geometrically and remain allocated. Steady-state recording and
  commit must not allocate per command.
- A failed command validates before commit or aborts without exposing partially synchronized
  stores/families. Full rollback is out of scope unless validation cannot guarantee this.

## Spike: forced rebuild vs incremental (2026-08-21)

Run out of order, before phases 1 and 2, because the stop condition below — "revert if forced
rebuild never beats incremental maintenance at any realistic batch/density point" — is testable as
a benchmark-only control without building a command buffer first. If rebuild had lost everywhere,
four phases would have died here. It did not lose everywhere, but it wins in one narrow corner and
that changes the shape of what should be built.

Code is `awake/ecs/benchmark/.../RebuildFamilyControl.kt` and `BulkRebuildStrategyBenchmarks.kt`,
proved correct by `RebuildFamilyControlTest.kt`. These are benchmark-only controls in the same
sense as `PureArchetypeStorage`; `:awake:ecs` was not modified. Numbers and methodology are in the
[forced-rebuild vs incremental section](../ecs-benchmark-scorecard.md#forced-rebuild-vs-incremental-family-maintenance-2026-08-21).

Result, at one fork on a host running a concurrent build — directional, not decision-grade:

| Batch (world = 100k) | Rebuild vs matched incremental |
|---|---|
| 100 | 5x to 125x slower |
| 10,000 | 28-39% slower |
| 100,000 | 24-36% faster, intervals separated |

Rebuild wins only where the batch mutates roughly the whole family — 200,000 mutations against a
100,000-member family. The crossover is the mutations-per-member ratio, not the batch size, and it
sits somewhere between 0.2 and 2.0, unlocated. Deferring maintenance is itself free: the arity-0
rows show `markDirty` bookkeeping costs nothing measurable.

### Recommendation: narrow, do not build phases 1 and 2 as specified

1. **Drop the command buffer and the restricted mutation receiver.** Phase 0 showed per-mutation
   cost is flat from batch 1 to 100,000, so a buffer amortizes no fixed cost, and this spike shows
   the whole win comes from phase 3's rebuild. The winning control records no commands and applies
   every store mutation immediately. Phases 1 and 2 would add recording and replay work the control
   never pays, so the +13% to +32% over production immediate mutation is an upper bound they would
   spend against. The +3.6% cell already sits inside this plan's own 5% regression band.
2. **If the lane proceeds, build only deferred family maintenance.** A scope in which store
   mutations stay immediate, affected families are marked dirty, and each dirty family rebuilds
   once on exit. That is what the control is, it needs no ordering or conflict model, and it keeps
   `StructuralMutationBuffer`/`StructuralMutationPlanner` unbuilt.
3. **Gate it on a real workload first.** The only measured win is at batch = world size, and
   `awake-ecs-authoring` already tells callers not to churn components per frame. Before writing
   production code, name an Awake workload that mutates a whole family in one batch and is not
   itself a benchmark. If none exists, invoke the stop condition and keep the controls.
4. **Do not adopt the plan's adaptive policy as written.** It keys on batch size. The decision
   variable is affected mutations divided by family size, and locating that crossover needs batch
   sizes between 10,000 and 100,000, which this matrix skipped.

Costs a production version would inherit, both recorded during the spike: a rebuild reorders the
entire family where incremental maintenance only swap-moves touched entries, and the family caches
key on `entity.id` alone, so a stale-generation handle still resolves through the sparse index and
`World.isAlive` remains the only guard.

Whatever happens to the production candidate, the controls, their tests, and this record stay.

## Delivery phases

### Phase 0 — Freeze evidence and contract

Reduced first pass landed 2026-08-21 in `BulkMutationProfilingBenchmarks`. Unchecked items below
are unchecked because the code they measure does not exist yet, not because they were skipped.

- [x] Add immediate, forced-incremental, forced-rebuild, and adaptive benchmark controls.
      *Immediate, forced-incremental, and forced-rebuild all landed: the last two arrived early via
      the 2026-08-21 spike, as benchmark-only replicas rather than as phase 1/3 production code. The
      adaptive control is not built and should not be until recommendation 3 above is answered.*
- [ ] Cover batch sizes 1, 10, 100, 1k, 10k, and 100k; family density 1%, 25%, and 100%.
      *Done for 1/100/10k/100k at 1% and 100%. Batch 10 and 1k and density 25% are held for
      Phase 4, where the crossover is located; per-mutation cost is flat across the sizes measured,
      so there is nothing for the intermediate points to resolve yet.*
- [ ] Record add-only, remove-only, mixed, replacement, tag, `Family1`, `Family2`, and `FamilySpec`.
      *Done for add-only, remove-only, mixed, tag, `Family1`, and `Family2`. Replacement lands with
      the Phase 1 ordering/conflict model; `FamilySpec` lands with the Phase 3 rebuild proofs.*
- [x] Confirm equivalent semantics for every Fleks/Artemis/Ashley comparison; label unmatched
      bulk APIs separately rather than claiming direct parity.
      *Source audit of the four family-churn and four add/remove benchmarks. Awake, Fleks, and
      Ashley maintain family membership immediately per mutation; Artemis-odb reconciles
      `EntitySubscription` membership once inside `world.process()`, so its 241.011 ops/s at 100k
      is a batched family update and is now labelled as an unmatched bulk API in the scorecard.*
- [x] Freeze JSON results, JDK/Kotlin versions, commit, power state, and benchmark commands.
      *`docs/benchmarks/2026-08-21-ecs-bulk-mutation-phase0.json` plus the sequential anchor;
      commit `5868aa8c7`, Kotlin 2.4.10, Temurin JDK 17.0.19, AC power, command in the scorecard.*

### Phase 1 — Allocation-free command buffer

**Cancelled by the 2026-08-21 spike.** Kept for the record, not for execution. Per-mutation cost is
flat across five orders of magnitude of batch, so there is no fixed cost to amortize, and the
control that won records no commands at all. Do not revive without new evidence that command
recording pays for itself.

- [ ] ~~Implement focused command storage and a restricted mutation receiver.~~
- [ ] ~~Preserve cached `ComponentTypeId`, pooling, generation, and immediate API behavior.~~
- [ ] ~~Add model-based ordering/conflict tests and failures for stale/dead entities.~~
- [ ] ~~Keep family updates incremental; measure command-buffer overhead before adding rebuild logic.~~

### Phase 2 — Batched coordination

**Reduced to one item by the 2026-08-21 spike.** With mutations staying immediate there is no
ordering model to enforce and no partially-applied state to hide, so only the query invalidation
remains.

- [ ] ~~Apply store mutations in order while collecting affected family/type IDs.~~
- [ ] Invalidate typed queries once at the end of a deferred-maintenance scope.
- [ ] ~~Add deterministic failure tests proving stores, signatures, pools, queries, and families agree.~~

### Phase 3 — Dirty-family rebuild

The only phase the spike supports, and only if recommendation 3 above is satisfied first.

- [x] Add explicit forced-incremental and forced-rebuild internal strategies.
      *Landed as benchmark-only controls in `RebuildFamilyControl.kt`, not as production code.*
- [ ] Rebuild each affected family once from its smallest required component store.
- [ ] Prove dense entity/value alignment, swap-remove repair, tag columns, excluded/one-of specs,
      destruction, recycled generations, clear, and capacity growth.
      *`RebuildFamilyControlTest` covers swap-remove repair, tag columns, destruction, and recycled
      generations against the control. `FamilySpec`, clear, and capacity growth remain.*
- [ ] Decide what a full-family reorder per batch breaks; incremental maintenance only swap-moves
      touched entries.

### Phase 4 — Adaptive policy and decision

- [ ] Derive and encode a portable crossover policy from the full matrix, keyed on affected
      mutations divided by family size. The spike bounds it between 0.2 and 2.0; locating it needs
      batch sizes between 10,000 and 100,000, not the 10/1k points this plan originally reserved.
- [ ] Run 3 forks, 5 one-second warmups, and 5 one-second measurements on an idle host.
- [ ] Capture GC/JFR evidence and repeat any surprising result from frozen JARs.
- [ ] Keep, narrow, or revert each phase independently; publish retained results in
      `docs/reference/performance-matrix.md` and `docs/ecs-benchmark-scorecard.md`.

## Acceptance gates

- Batched 10k family churn exceeds the fresh same-semantics Fleks result.
- Batched 100k family churn exceeds the fresh same-semantics Artemis result.
- Immediate APIs, stable family iteration, queries, and entity lifecycle regress no more than 5%.
- After warm-up/capacity growth, the buffer adds no per-command allocation and does not increase GC.
- Desktop, Android host, iOS simulator, and wasmJs ECS tests pass; Detekt, Spotless, benchmark tests,
  benchmark JAR, and scene-core consumer compilation pass.
- Public API is added only after the internal prototype clears correctness and performance gates.

## Stop conditions

Stop and revert the production candidate if forced rebuild never beats incremental maintenance at
any realistic batch/density point, if batching cannot preserve pooling/order semantics without a
complex rollback system, or if the stable-path regression exceeds 5%. Keep benchmark-only controls
and the decision record even when production code is reverted.

The 2026-08-21 spike did not trigger the first condition: rebuild beats incremental by 24-36% at
batch = world size, with separated intervals. It did narrow what "realistic" has to mean. One
condition is now added: stop if no Awake workload outside a benchmark mutates a whole family in a
single batch, since that is the only region where rebuild wins.

## Out of scope

Archetype storage, automatic per-component storage routing, parallel mutation, scheduler changes,
network replication, persistence, and MMORPG-specific commands remain separate work.
