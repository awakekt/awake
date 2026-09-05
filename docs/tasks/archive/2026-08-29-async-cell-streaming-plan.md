# Async Cell Streaming Plan

Date: 2026-08-29
Status: complete

Closes the half of [D28](../decisions/D28-open-world-framework-boundary.md)'s streaming row that
the observer-driven pass left open: `WorldCellStreamListener.onCellLoad` is still called inline on
the frame thread, so a consumer doing real IO there stalls the frame.

## The scoping correction this investigation forced

D28 pairs "async cell streaming" with `AssetManager`, and I repeated that pairing when deferring
both — "there is nothing to schedule until something owns loaded resources." **That is wrong, and
worth correcting before it shapes the work.**

They solve different problems:

- **Async streaming is scheduling.** The consumer decides what a cell contains; the engine decides
  when its load runs, on which thread, and what happens when the cell leaves the radius mid-flight.
- **An asset manager is shared-resource lifetime.** It only earns its keep when two cells reference
  the same asset and unloading one must not free what the other still draws.

Nothing shares assets across cells today, because nothing loads cells at all. Building refcounted
handles now would be building for a consumer that does not exist — the thing D28's own
two-consumer rule exists to prevent.

**Recommendation: async streaming now, `AssetManager` when two cells demonstrably share an asset.**

## What already exists

- `WorldPartitionSystem` computes the active cell set from a `StreamObserver` entity, with a
  hysteresis band, and calls the listener synchronously.
- `SceneAssetLibrary` in `:awake:scene:runtime` is already a memoising, name-keyed cache of
  `Mesh`/`Material` built from registered factories. It is the proto-`AssetManager`, and the thing
  to grow later rather than build beside. It has no refcounting, no eviction, and no async.
- `:awake:scene:scene-core` has **no** coroutines dependency; `:awake:core:host` does.

## Design

### Loading is suspend, applying is not

`onCellLoad` becomes suspending and runs off the frame thread. Its result does not touch the ECS:
a load produces content, and *applying* that content to the `World` happens on the frame thread,
where every other system mutates it. Anything else makes cell loading the one place in the engine
that races the ECS.

So the listener splits in two:

- `suspend fun loadCell(coord): T` — off-thread, no `World` parameter, which makes the rule
  structural rather than documented.
- `fun applyCell(world, coord, loaded: T)` — frame thread, drained from a completion queue.

`onCellUnload` stays synchronous and frame-thread: it removes entities, which is ECS mutation.

### Cancellation is the hard part, not the dispatch

A cell can leave the unload radius while its load is still running. Three things have to hold:

1. the in-flight job is cancelled;
2. its result, if it lands anyway, is discarded rather than applied to a cell no longer active;
3. re-entering the radius before the old job finishes does not start a second one, nor apply the
   first twice.

A per-coord job map, cancelled on unload and consulted before starting, covers all three. This is
the part to test, because every failure mode here is a leak or a ghost entity rather than a crash.

### Who owns the scope

`WorldPartitionSystem` takes a `CoroutineScope`. Not one it creates: a system that owns a scope
owns a lifetime, and the runtime already has one to hand. A consumer driving the system manually
in a test passes `TestScope`, which is what makes the cancellation cases assertable without a
frame loop.

`:awake:scene:scene-core` gains a `kotlinx-coroutines-core` dependency. That is the real cost of
this plan and worth stating: the module is currently dependency-light by design.

## Outcome

`AsyncWorldCellStreamListener` splits loading into a suspending `loadCell(coord): CellContent` and
a frame-thread `CellContent.applyTo(world)`. The suspending half is handed no `World`, so keeping
ECS mutation off the load thread is structural rather than documented. `onCellUnload` stays
synchronous on both listeners, since removing entities is ECS mutation either way. The existing
`WorldCellStreamListener` keeps its inline path and no current caller changed.

`AsyncCellStreamingTest` covers all three cancellation cases plus the two that would make the
feature vacuous — that content actually reaches the `World`, and that the synchronous path still
runs inline.

**Phase 3 turned out to be unnecessary.** The plan had `SceneAppLifecycleRuntime` calling a
separate drain step; the system already has a frame-thread entry point in `update`, so it drains
its own completion queue there, before recomputing the active set. That keeps content that
finished last frame in the world before anything decides what to stream next, and needs no runtime
change at all.

One thing the tests taught rather than confirmed: on a `TestScope`, `launch` only schedules — the
body does not run until the scheduler does. Every assertion about a load having *started* needs a
`runCurrent()` first, and a test that leaves gated jobs alive fails `runTest` with
`UncompletedCoroutinesError` rather than passing.

## Follow-up landed: refcounting (2026-08-30)

The deferral above tested the wrong thing. It asked whether two cells *share* an asset -- the
argument for refcounting -- and missed that streaming had introduced a **leak**: cells load and
unload continuously, `SceneAssetLibrary` freed nothing until session teardown, and an unloading
cell had no way to release the meshes its entities referenced. D28's streaming row lists "cell
entity lifecycle" in scope, so this belonged in this plan rather than after it.

`SceneAssetLibrary` grew the refcount rather than gaining a second cache beside it, as the risk
section below already asked for. `requireMesh`/`requireMaterial` acquire, `releaseMesh`/
`releaseMaterial` let go, and the asset dies with its last holder. A caller that never releases
behaves exactly as before, so no existing caller changed.

## Follow-up landed: retained-mesh budget (2026-08-30)

Budget and eviction turned out to need a semantic change, not just a limit. **Refcounting
destroys at zero holders, so there is nothing unreferenced for an eviction policy to act on** --
and a budget can never evict a live asset, because something is drawing it.

So `retainedMeshBudgetBytes` makes a released mesh *kept* rather than destroyed, and eviction
trims that retained set least-recently-released first. Re-acquiring revives it, which is what a
player crossing a cell boundary back and forth wants. A budget of zero -- the default -- retains
nothing and behaves exactly as destroy-on-last-release did, so nothing existing changed.

`Mesh` gained `sizeBytes`, since a budget cannot bound what it cannot measure. Both backends
already computed those buffer sizes to allocate them, so this reports a number each had rather
than teaching either something new.

Materials are refcounted but not budgeted: a material is a small uniform buffer plus textures the
`Renderer` owns, so counting one would miss most of its cost or double-count a shared texture.
Meshes are where a streamed world's memory goes.

## Non-goals

- No VRAM budget, no eviction. Refcounting landed; see above.
- No HLOD, no cell-content format, no prefetch along a movement vector. All are consumer policy or
  later work.
- No thread-safety in `World`. The design keeps ECS mutation on the frame thread precisely so this
  stays out of scope.

## Risks

The completion queue is unbounded. A consumer whose loads finish faster than the frame drains them
grows it without limit; a cap with a stated drop or stall policy may be needed once anything real
loads, and guessing the policy now would be guessing.

`SceneAssetLibrary` and any future `AssetManager` will overlap. Whoever builds the latter should
grow the former rather than add a second cache — recorded here because the overlap is not obvious
from either type's name.
