# Navigation

<p class="awake-lede">Pathfinding over terrain: bake which ground an agent can stand on, then ask for routes around walls and up gentle slopes.</p>

<div class="awake-badges" markdown>
<span class="awake-badge">module: <code>awake:navigation</code></span>
<span class="awake-badge">Desktop · Android · iOS · Web</span>
</div>

`com.awakekt.awake:navigation` bakes a walkability grid from a [terrain](terrain.md) heightmap and
searches it with A*. Navigation is code-only: there is no scene document component for it. The
[AI](ai.md) behaviours use it to walk.

## Find a path

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

## How it works

A `NavGridTile` stores one bit per sample: walkable or not. It records only terrain shape, so waypoints
carry X and Z and leave Y at zero; read the height from `Heightmap.heightAtWorld` if you need it.
Tiles are immutable once baked, which is what lets a search read one off the frame thread.

!!! warning "Only slope blocks a path"
    The bake reads terrain height and nothing else. Buildings, props and other agents are not in
    the grid.

!!! tip "Keep searches off the frame"
    A* over a large grid costs milliseconds. Pass a `CoroutineScope` to `PathRequestSystem` so a
    search does not stall the frame, and repath on an interval rather than every frame.

## Debugging

`navGridDebugLines` returns world-space lines: a cross on every blocked sample, and each agent's
route if you pass `routes`.

```kotlin title="Kotlin"
--8<-- "awake/navigation/src/desktopTest/kotlin/com/awakekt/awake/navigation/NavigationDocsSampleTest.kt:debug-lines"
```

## See also

- [AI](ai.md) for patrol, chase and flee behaviours that walk these paths.
- [Terrain](terrain.md) for building the heightmap.
- [Large worlds](large-worlds.md) for streaming navigation with the world.
