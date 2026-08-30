# Navigation Plan — a Heightmap-Derived NavGrid, in commonMain

Date: 2026-08-30
Status: complete, plus the first deferred item. Phases 1-4 landed in `:awake:scene:navigation` and
`:awake:scene:scene-core`; HPA*'s trigger fired once cells streamed for real, and a coarse
cell-region graph landed with it. Theta*, RVO and voxel navmesh keep their stated triggers.

Replaces the navigation capability that left the tree with `samples:scene3d-playground`. Pairs with
[the behavior tree & state machine plan](2026-08-30-behavior-tree-state-machine-plan.md), which owns control flow and
records the seam between the two; this plan owns geometry and search.

## Why now, and why not Recast

The gate is met: [terrain clipmap draw](2026-08-29-terrain-clipmap-draw-plan.md) is complete, so a
heightfield renders. "Walk on it" is the next honest ask, and there is currently no path source on
any target — `NavMesh` has one implementor in the whole tree and it is a test fake.

**Recast voxelizes because arbitrary triangle soup hides the walkable surface.** Given a mesh of a
building interior with stairs, overhangs and railings, nothing in the geometry says which triangles
a human-sized agent can stand on, so Recast rasterizes the world into voxel spans, finds the spans
with headroom, partitions them into regions, traces contours, and builds polygons. Every stage of
that pipeline exists to recover a fact that a heightmap states directly.

**A heightmap is already the walkable surface.** It is a 2.5D function: one height per `(x, z)`.
Walkability is a slope test between adjacent samples — arithmetic, not a pipeline. Recast's cost
buys nothing here.

The second reason is the one that actually bit us: `recast4j` is JVM-only, so it never covered iOS
or wasmJs, and no pre-built C wrapper exists for Recast the way `JoltC` exists for Jolt. A navgrid
in `commonMain` is pure Kotlin arithmetic over `FloatArray` and covers all four targets with no
`expect`/`actual` at all. That is not a consolation prize — it is a better outcome than the library
we are replacing.

Voxel navmesh generation earns itself back only when real multi-level geometry exists: building
interiors with floors, bridges with space underneath, caves. Until then it is a pipeline with
nothing to recover.

## What already exists

- `Heightmap` (`:awake:asset:terrain`) — immutable, row-major `z * width + x`, `heightAt(x, z)`,
  corner origin, per-axis `scale: Vec3f`. Everything the bake needs, already neutral data.
- `HeightFieldShape` + `PhysicsWorld.raycast` (`:awake:physics:api`) — a second possible source of
  "what is the ground here". **Not the one to use for baking**: a raycast per grid sample is orders
  of magnitude more expensive than reading the array the shape was built from, and it drags a
  physics backend into a bake that needs none. Raycast stays for runtime spot queries.
- `WorldCellCoord` / `WorldPartitionConfig` (`cellSize` 512 m, `loadingRadius` 1024 m) — the tiling
  scheme. Navigation reuses it rather than inventing a second one.
- `AsyncWorldCellStreamListener` — the async template: suspend to produce off-thread, apply on the
  frame thread, cancel when the cell leaves the radius. A path request has the same shape.
- `NavMesh.findPath(start, end): List<Vec3f>` — the surviving interface. Synchronous and
  allocating, which this plan expected to change and in the end did not: see Phase 3.

Two things that do **not** exist and have to be written, small but worth naming so they are not
discovered mid-implementation:

- **No priority queue in `commonMain`.** `java.util.PriorityQueue` is JVM-only. A* needs a
  hand-rolled binary heap over `IntArray`, which is also what keeps the search allocation-free.
- **No terrain surface query.** Nothing answers "what is the ground height at this world position".
  The bake needs bilinear sampling between heightmap samples; it belongs next to `Heightmap`.

## Design

### Bake: slope threshold, per tile

Per world cell, a `NavGridTile`: a walkability bitset at navigation resolution, plus the cell's
origin and sample spacing. A sample is walkable when the height delta to each neighbour, over the
horizontal spacing, is below `maxSlope`, and when it is not covered by a static obstacle rasterized
in at bake time.

Resolution is a real budget decision, so here are the numbers rather than a shrug: at a 512 m cell
and 1 m navigation samples, a tile is 512 × 512 bits = **32 KB**. At 0.5 m it is 128 KB. With the
default 1024 m loading radius, roughly 9–25 tiles are resident. 1 m is the starting point; 0.5 m is
affordable if agents need to squeeze through gaps a metre wide.

Tiles are **immutable once baked**. That is what makes an off-thread search safe against a
concurrent unload: a search holds references to the tiles it entered, and an unload drops the
partition's reference without invalidating the search's.

### Search: A*, and specifically not JPS

8-connected A* over the resident tiles treated as one virtual grid, with octile distance as the
heuristic.

**Do not reach for Jump Point Search.** JPS is the standard "obvious" speedup and it is wrong here:
its pruning rules are only valid on a *uniform-cost* grid, and the moment terrain cost varies with
slope — which is the point of deriving the grid from a heightmap — the jump-point identity breaks
and JPS returns non-optimal paths while looking like it works. If the grid ever does become
uniform-cost, JPS is available then. Recording this because it is a mistake that produces plausible
output rather than a crash.

No hierarchy in v1. With a 1024 m loading radius and 512 m cells, any path within the resident set
crosses at most a few tiles, and a flat search over them is fine. HPA* is for paths that exceed the
loaded set — the roadmap already parks it at P1/MVP4, and it should stay there until a request
demonstrably needs it.

### Smoothing: line-of-sight, not funnel

A raw grid path is a staircase. The funnel/string-pulling algorithm everyone reaches for operates
on a corridor of convex polygons, which a grid does not have. The grid analogue is line-of-sight
simplification: walk the waypoint list, and drop any waypoint whose neighbours have an unobstructed
Bresenham line between them. Cheap, and enough.

Theta* (any-angle A* with line-of-sight parent updates) is the better answer if post-smoothing
proves insufficient, and it is a change to the search rather than an addition to it. Not v1.

### Dynamic obstacles: local avoidance, not re-baking

Static obstacles rasterize into the tile at bake time. Moving ones do **not** trigger a re-bake —
that is the trap the old roadmap row named as unfinished, and re-baking a 32 KB tile because a crate
slid two metres is the wrong shape of fix. Moving obstacles belong to local steering on top of the
path. RVO/ORCA is the eventual answer for crowds; a repulsion term is the version worth writing
first, and neither is in this plan.

### The contract: a request, not a call

`findPath(start, end): List<Vec3f>` cannot stay. It is synchronous, so it stalls the frame; it
allocates, so it violates the per-frame allocation rule; and it cannot be cancelled.

The replacement is ECS-native rather than a suspending function, because `System.update` is not a
suspending context: a `PathRequest` component holding start, goal and a status, and a system that
issues searches off-thread and writes results back on the frame thread. Same split as cell
streaming — the off-thread half never touches `World`.

**This is also where the two plans join.** A `MoveTo` leaf task in the decision runtime writes a
`PathRequest`, stays in a running state while the status is pending, and cancels the request when a
transition fires. That is exactly the long-running-task requirement recorded in the BT/FSM plan, and it
is satisfied by one component rather than by a mechanism invented for it.

### Determinism

The MMO direction is an authoritative server, so identical inputs must produce an identical path.
The concrete trap: A* ties are common on a uniform grid, and resolving them by iterating a `HashSet`
or `HashMap` open set makes the result depend on hash order. Tie-break on an explicit, stable key
(node index), and keep the heap comparison total. Assert it in a test rather than hoping.

## Boundary

By the [framework boundary](../reference/framework-game-boundary.md) rule, this fails the limitation
test the same way the decision runtime does: a consumer can compute a grid from the public
`Heightmap`, implement the public `NavMesh` seam, and register a system, all through existing APIs.
Nothing is missing. So: **build consumer-side, promote when a second consumer needs it.**

One inconsistency this exposes and does not resolve: `NavMesh` is an engine interface in
`:awake:scene:scene-core` with no engine implementation, and `ChaseAiSystem` sits beside it in the
same state. Either the engine implements them or the seam belongs outside. Recommendation is to keep
the interface where it is — a seam with no implementor is a defensible thing for a framework to
own — and to decide the rest when the promotion actually comes up, not in advance.

## Phases

**Phase 1 — bake. Done.** Bilinear surface sampling next to `Heightmap`, then `NavGridTile` and the
slope threshold. Testable with no search at all: a known heightmap produces a known walkable bitset,
and a cliff is rejected.

**Phase 2 — search. Done.** Binary heap, 8-connected A*, octile heuristic, line-of-sight smoothing.
Tests: a known grid yields a known path; an obstacle is routed around rather than through;
unreachable returns empty rather than hanging; the same query twice returns an identical path.

**Phase 3 — tiling and the request contract. Done.** `StreamedNavGrid` holds a tile per
`WorldCellCoord` and searches across the resident set; `Heightmap.bakeNavGridCell` bakes the
exactly-one-cell tile that tiling needs, where `bakeNavGrid` samples both edges and would place
every boundary sample in two tiles. `PathRequestSystem` takes an optional search scope and runs
off the frame thread when given one, applying answers on the next update.

Cancellation landed differently than this plan assumed, and the difference is worth recording.
Cell-unload cancellation turned out to be unnecessary rather than hard: tiles are immutable and
the resident map is replaced rather than mutated, so a search holds a consistent snapshot and
unloading underneath it is safe. The cancellation that *is* needed is per-requester — a search
whose question was withdrawn — which the streaming tests were no model for, because a cell cannot
change its mind. `PathRequest.queryGeneration` is the answer: a status check alone cannot tell a
withdrawn question from the one that replaced it, since both read `Pending`.

`NavMesh.findPath` stayed synchronous and allocating, against this plan's expectation. Once the
asynchrony lives in `PathRequestSystem`, moving it into the interface as well would make every
implementation carry a suspending signature to solve a problem one system already solves.

**Phase 4 — reconnect the consumer. Done.** `ChaseAiSystem` requests paths through `PathRequest`
instead of taking a `NavMesh` in its constructor; `samples:engine-showcase`'s `NavChaseExampleDriver`
is the first thing that visibly walks.

Deferred with stated triggers: Theta* when line-of-sight smoothing visibly fails; RVO when agents
visibly collide; voxel navmesh when multi-level geometry exists.

**HPA*'s trigger fired, and what landed is smaller than HPA*.** Once cells streamed, every path to
somewhere outside the resident set returned empty — an agent asked to walk anywhere over the
horizon simply stood still. The answer is `NavCellSummary` (a cell's walkable regions and which
border samples each one touches, a few hundred bytes kept after the cell unloads), `CoarseNavGraph`
(A* over `(cell, region)` nodes, returning a corridor of cells), and `HierarchicalNavGrid`, which
walks that corridor as far as the world is loaded and returns a fine path to where it would leave.

Two decisions worth recording. Nodes are cell *regions*, not cells: a cell split by a ridge
connects on both sides, so a cell-level graph would route through the wall and every local path
along that corridor would fail — the failure this whole layer exists to avoid. And the result is a
leg rather than a full route: the part beyond the resident set is never expressed as waypoints,
because waypoints through unverified terrain are exactly the confident-path-through-a-wall problem.
An agent walks the leg, cells stream in, and `PathRequest`'s existing repath interval asks again.

Real HPA* also precomputes intra-cluster edge costs and refines with cached paths. Neither is here:
the corridor costs one cell per step and refinement is the ordinary fine search. Add them when a
profile says the coarse search is what costs, which at a dozen resident cells it is not.

## Non-goals

- No Recast, no voxelization, no library. The trigger for revisiting is stated above.
- No JPS. See the search section — this one is a correctness issue, not a preference.
- No crowd simulation, no off-mesh links (jumps, ladders, doors), no dynamic re-baking.
- No navigation for arbitrary meshes. Heightmap terrain only, which is what exists.

## Risks

Navigation resolution and `WorldPartitionConfig.cellSize` are coupled, and the coupling is easy to
get wrong quietly: a tile that is not an integer number of navigation samples leaves a seam where a
path can fail to cross a cell boundary for reasons no test is looking at. Make it a construction
requirement, not a convention.

The bake cost lands inside cell streaming, which is already the thing being kept off the frame
thread. A 512 × 512 slope pass is not free, and it should be measured inside the existing async load
rather than added as a separate scheduling mechanism.

Line-of-sight smoothing can cut a corner through a cell that was walkable at bake time but is
occupied at runtime. That is inherent to smoothing against static data and is the local-avoidance
layer's problem, not a bug in the smoother — recorded so it is not later diagnosed as one.
