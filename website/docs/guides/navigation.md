# Navigation

<p class="awake-lede">Pathfinding over terrain and around buildings: bake where an agent can stand, then ask for routes around walls, up gentle slopes and over kerbs, the same on the web, the JVM and Native.</p>

<div class="awake-badges" markdown>
<span class="awake-badge">module: <code>awake:navigation</code></span>
<span class="awake-badge">Desktop · Android · iOS · Web</span>
</div>

`com.awakekt.awake:navigation` bakes where an agent can stand from [terrain](terrain.md) heights and
static colliders, and searches it with A*. It is common Kotlin with no platform code, so a browser
client and an authoritative JVM server bake the same bytes and find the same route. The [AI](ai.md)
behaviours use it to walk.

There are two ways in. A **walkable surface** reads terrain, obstacles, water and no-walk areas, and
answers for any agent radius, with the nearest reachable point when a target cannot be reached. A
**walkability grid** reads terrain slope alone and backs the streamed and hierarchical grids below.
Start with the surface.

## Bake a walkable surface

Describe the agent, list what is in the way, and bake a region:

```kotlin title="Kotlin"
--8<-- "awake/navigation/src/desktopTest/kotlin/com/awakekt/awake/navigation/WalkableNavigationDocsSampleTest.kt:bake-surface"
```

A sample is blocked when the ground has no height there, when the terrain to any neighbour is
steeper than `maxSlopeDegrees`, when the water is deeper than `maxDepth`, when a no-walk outline
touches it, or when an obstacle touches it and is too high to step over (`stepHeight`) and too low
to walk under (`height`). Obstacles are tested against the ground under each sample, so a crate on a
hill is as tall as the crate. Cells are tested rather than centres, so a wall thinner than a sample
still blocks.

The bake stores each sample's distance to the nearest blocked one, not a bit. That is what lets the
radius wait for the query: one surface serves a narrow and a wide agent of the same height class.

## Find a path

```kotlin title="Kotlin"
--8<-- "awake/navigation/src/desktopTest/kotlin/com/awakekt/awake/navigation/WalkableNavigationDocsSampleTest.kt:query"
```

| Status | What the path holds |
| --- | --- |
| `Complete` | From the start to exactly the target, through the corners that matter. |
| `Partial` | To the reachable point nearest the target: the target is walled in, across water, inside a building, or past the node budget. |
| `NoPath` | Nothing: the start has nowhere to stand within `snapSamples`. |

An endpoint inside an obstacle first moves to the nearest standable sample, so an agent pushed into a
wall still finds its way out, and a target inside a building is reached at its wall.

!!! tip "Reuse the query and the path"
    `NavPathQuery` keeps its search state between queries and `NavPath` keeps its waypoints in one
    float array, so a warm query allocates nothing. Keep one query per thread; a server answering many
    agents keeps one per worker.

A search expands at most `maxExpandedNodes` samples. The budget counts nodes, not milliseconds: a
time budget would make a route depend on the machine, and a server and its clients must agree. A
route across a kilometre of town at 1 m expands about 120,000 samples in about 50 ms on a desktop JVM.

## Large regions and caching

Bake a large region as a lattice of tiles, and cache each tile's bytes:

```kotlin title="Kotlin"
--8<-- "awake/navigation/src/desktopTest/kotlin/com/awakekt/awake/navigation/WalkableNavigationDocsSampleTest.kt:tiles"
```

Each tile is baked looking `maxAgentRadius` past its border, so tiles baked separately, or on
different machines, join without a seam. A tile not yet put reads as blocked, and a route stops at
the edge of what is loaded. `NavSurfaceCodec` writes a versioned little-endian layout and rejects
bytes it does not recognise rather than decoding them into a different surface.

## Walkable surface reference

| Call or property | Type | Default | What it does |
| --- | --- | --- | --- |
| `NavAgent(radius, height, stepHeight, maxSlopeDegrees)` | | `0.4`, `1.8`, `0.3`, `45` | The body routed. Height, step and slope are baked; the radius is read by the query. |
| `NavObstacle(footprint, minY, maxY)` | | | A static collider: a `NavFootprint.Rect` or `Circle` spanning `minY..maxY`. |
| `NavAreas(obstacles, noWalk, water)` | | none | Everything besides terrain that blocks. |
| `NavWater(surfaceY, maxDepth)` | | depth `0` | Water deeper than `maxDepth` is blocked. |
| `bakeNavSurface(region, ground, agent, areas, maxAgentRadius)` | `NavSurfaceTile` | `agent.radius` | Bakes one region; `ground` returns `NaN` where there is none. |
| `NavGridTile.toNavSurface()` | `NavSurfaceTile` | | A slope-baked grid tile as a surface, without re-baking. |
| `NavTiledSurface(tilesX, tilesZ, tileSamples, sampleSize, originX, originZ)` | `NavSurface` | origin `0` | A lattice of tiles searched as one surface. |
| `NavPathQuery(surface, ground, maxExpandedNodes, snapSamples)` | | `FLAT`, `200000`, `8` | The reusable query; `ground` gives waypoint heights. |
| `NavPathQuery.findPath(from, to, agent, out)` | `NavPathStatus` | | Writes the route into `out`. |
| `NavSurfaceMesh(surface, agent)` | `NavMesh` | | The surface for `PathRequestSystem` and the AI behaviours. |

## Find a path on a slope grid

Bake a grid from a `Heightmap`, search it, then smooth the result:

```kotlin title="Kotlin"
--8<-- "awake/navigation/src/desktopTest/kotlin/com/awakekt/awake/navigation/NavigationDocsSampleTest.kt:bake"
```

A sample is walkable when the ground rises no more than `maxSlopeDegrees` to each of its four
neighbours, `cellSize` metres away. `findPath` returns an empty list when no route exists, including
when either end is off the grid.

## Ask from an entity

Inside the ECS, an entity asks through a `PathRequest` component and `PathRequestSystem` answers it:

```kotlin title="Kotlin"
--8<-- "awake/navigation/src/desktopTest/kotlin/com/awakekt/awake/navigation/NavigationDocsSampleTest.kt:path-request"
```

`PathRequest.status` moves `Idle` → `Pending` → `Ready` or `Unreachable`. `cancel()` returns it to
`Idle` and drops any answer. The system answers only pending requests, so a route stays put until its
owner asks again.

## Properties

| Call or property | Type | Default | What it does |
| --- | --- | --- | --- |
| `bakeNavGrid(cellSize, maxSlopeDegrees)` | on `Heightmap` | `1`, `45` | Bakes a whole heightmap into a `NavGridTile`. |
| `bakeNavGridCell(samples, sampleSize, maxSlopeDegrees)` | on `Heightmap` | `45` | Bakes exactly `samples × samples`, for one streamed cell. |
| `NavGridTile.findPath(start, goal)` | `List<Vec3f>` | | 8-connected A*. Waypoints carry X and Z; Y is `0`. |
| `NavGridTile.smoothPath(path)` | `List<Vec3f>` | | Removes waypoints the neighbours can see past. |
| `NavGrid(tile)` | `NavMesh` | | A single tile as the `NavMesh` that `PathRequestSystem` searches. |
| `PathRequestSystem(navMesh, searchScope)` | `System` | no scope | Answers requests; with a `CoroutineScope`, searches run off the frame thread and land on the next update. |

## Large and streamed worlds

| Type | What it is |
| --- | --- |
| `StreamedNavGrid(samplesPerCell, sampleSize)` | A `NavMesh` over one tile per loaded world cell. Routes stop at the edge of what is loaded. |
| `NavGridCellStreamer(grid, config, heightmapAt, …)` | An `AsyncWorldCellStreamListener` that bakes each cell's tile off the frame thread as it streams in. |
| `HierarchicalNavGrid(grid, coarse)` | Plans across unloaded cells with a `CoarseNavGraph` and returns the next loaded leg. |

Wire `NavGridCellStreamer` into a `WorldPartitionSystem`; see [Large worlds](large-worlds.md).

## How the grid works

A `NavGridTile` stores one bit per sample: walkable or not. It records only terrain shape, so waypoints
carry X and Z and leave Y at zero; read the height from `Heightmap.heightAtWorld` if you need it.
Tiles are immutable once baked, which is what lets a search read one off the frame thread.

!!! warning "Only slope blocks a grid path"
    `bakeNavGrid` reads terrain height and nothing else. For buildings, props, water and agent
    width, bake a walkable surface instead. Moving agents are neither's job: that is local avoidance.

!!! tip "Keep searches off the frame"
    A* over a large grid costs milliseconds. Pass a `CoroutineScope` to `PathRequestSystem` so a
    search does not stall the frame, and repath on an interval rather than every frame.

## Debugging

`navGridDebugLines` returns world-space lines: a cross on every blocked sample, and each agent's
route if you pass `routes`. `NavDebugSource` draws the same lines through the debug registry, turned
on with `--debug=nav`, and counts walkable and blocked samples on the debug panel; see
[Debug tools](debug-tools.md#the-navigation-source).

```kotlin title="Kotlin"
--8<-- "awake/navigation/src/desktopTest/kotlin/com/awakekt/awake/navigation/NavigationDocsSampleTest.kt:debug-lines"
```

## See also

- [AI](ai.md) for patrol, chase and flee behaviours that walk these paths.
- [Terrain](terrain.md) for building the heightmap.
- [Large worlds](large-worlds.md) for streaming navigation with the world.
