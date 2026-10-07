# Large worlds

<p class="awake-lede">Worlds bigger than memory and bigger than a float: stream content in cells around the player, and move the origin so positions stay precise kilometres out.</p>

<div class="awake-badges" markdown>
<span class="awake-badge">module: <code>awake:scene:world</code></span>
<span class="awake-badge">module: <code>awake:world</code></span>
<span class="awake-badge">module: <code>awake:scene:worldstream</code></span>
<span class="awake-badge">Desktop · Android · iOS · Web</span>
</div>

`awake:scene:world` streams cell coordinates and shifts the origin; it does not know what a cell
contains. The cell types it streams, `WorldCellCoord`, `WorldPartitionConfig`,
`AsyncWorldCellStreamListener` and `CellContent`, are in `awake:world`, which has no scene
dependency, so a listener can be written without the scene layer; `awake:scene:world` brings it in.
`awake:scene:worldstream` supplies ready-made cell contents: meshes, colliders and terrain textures.
All three are code-only; there is no scene document component for streaming.

## Stream cells around the player

The world is cut into square cells. `WorldPartitionSystem` follows the entity tagged
`StreamObserver`: cells whose centre comes within `loadingRadius` load, and cells beyond
`unloadRadius` unload. A listener decides what a cell holds:

```kotlin title="Kotlin"
--8<-- "awake/scene/world/src/desktopTest/kotlin/com/awakekt/awake/scene/world/LargeWorldDocsSampleTest.kt:listener"
```

Give the system the listener and a scope to load in, and tag the player:

```kotlin title="Kotlin"
--8<-- "awake/scene/world/src/desktopTest/kotlin/com/awakekt/awake/scene/world/LargeWorldDocsSampleTest.kt:partition"
```

For cells built from memory, implement `WorldCellStreamListener` instead: its `onCellLoad` runs on
the frame thread and needs no scope. To fill a cell from several sources, combine listeners with
`CompositeCellStreamListener`.

## Properties

| Property | Type | Default | What it does |
| --- | --- | --- | --- |
| `cellSize` | number | `512` | Width and depth of one cell, in metres. |
| `loadingRadius` | number | `1024` | Cells within this distance of the observer load. At least `cellSize`. |
| `unloadRadius` | number | `1536` | Cells beyond this distance unload. More than `loadingRadius`. |

These are `WorldPartitionConfig` fields. The gap between the two radii stops a cell from loading and
unloading every frame while the player stands on its edge.

## Keep positions precise

A `Float` holds about seven significant digits, so 10 km from the origin positions snap to the
nearest millimetre and the view jitters. `FloatingOriginSystem` moves the world back towards the
origin whenever the observer gets too far out:

```kotlin title="Kotlin"
--8<-- "awake/scene/world/src/desktopTest/kotlin/com/awakekt/awake/scene/world/LargeWorldDocsSampleTest.kt:floating-origin"
```

| Property | Type | Default | What it does |
| --- | --- | --- | --- |
| `threshold` | number | `2048` | Distance from the origin, on any axis, that triggers a shift. |
| `quantum` | number | `1024` | Size of each shift step. Shifts are whole steps, so they are exact. |

`WorldOrigin` records the total shift; `toAbsolute` and `toLocal` convert between the two frames.
`WorldPartitionSystem` works in absolute coordinates, so a shift never changes which cells are
loaded.

!!! warning "Tell physics about the shift"
    The origin shift moves root `Transform`s only. `PhysicsSystem` writes the simulation's pose
    back every step, so without `origin.addListener(PhysicsOriginShiftListener(physicsWorld))`
    every body snaps back to where it was. Navigation grids and other caches of world positions
    need their own `OriginShiftListener`.

## Ready-made cell contents

`awake:scene:worldstream` has listeners for common cell contents:

| Type | Loads per cell |
| --- | --- |
| `MeshCellStreamer` | Mesh entities and their renderers. |
| `PhysicsCellStreamer` | Static physics bodies. |
| `heightFieldCellStreamer(...)` | Heightfield collider tiles; `tilesTouchedByEdit` lists the tiles to rebuild after a terrain edit. |
| `VirtualTerrainCellStreamListener` | Terrain texture tiles from a `VirtualTerrainTileProvider`, kept in a `VirtualTerrainTileCache`. |
| `WorldstreamTerrainDriver` | Wires the streamers as the scene runtime's `TerrainDriver`. |

`awake:navigation` adds `NavGridCellStreamer`, which bakes a navigation tile per cell. See
[Navigation](navigation.md#large-and-streamed-worlds).

## How it works

Each update, `WorldPartitionSystem` first applies cells that finished loading, then compares the
observer's position with the active cells. A new cell starts `loadCell` in the load scope, which runs
off the frame thread and returns a `CellContent`. The next update applies that content on the frame
thread, and only if the cell is still active. A cell that leaves the radius has its load cancelled
and `onCellUnload` called.

!!! warning "Only the first observer counts"
    Streaming follows the first entity with both `StreamObserver` and a `Transform`. An observer
    without a `Transform` is ignored.

!!! tip "Follow the player, not the camera"
    Tag the player entity. A camera that orbits or cuts away would drag cells in and out behind it.

## See also

- [Terrain](terrain.md) for the heightmaps streamed cells are built from.
- [Physics](physics.md) for colliders and `shiftOrigin`.
- [Navigation](navigation.md) for streamed navigation grids.
