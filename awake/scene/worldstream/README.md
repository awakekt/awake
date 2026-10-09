# `awake:scene:worldstream`

Cell streamers: what fills a cell as `awake:scene:world` streams it in, and empties it on the way
out. Each is an `AsyncWorldCellStreamListener`; compose them with `CompositeCellStreamListener`.

| Streamer | Loads per cell |
|---|---|
| `MeshCellStreamer` | Mesh entities with their renderers |
| `PhysicsCellStreamer` | Static physics bodies |
| `heightFieldCellStreamer` | Heightfield collider tiles, and `tilesTouchedByEdit` for rebuilding after a terrain edit |
| `VirtualTerrainCellStreamListener` | Terrain texture tiles from a `VirtualTerrainTileProvider`, cached |

`WorldstreamTerrainDriver` wires them as the scene runtime's `TerrainDriver`.

`PagedTerrainSystem` runs Core's `TerrainPageStreamer` over the scene observer and maintains nearby
heightfield entities from the same pages. Install it before the scene's `PhysicsSystem`, pass that
same system for collision ownership, and call `close(world)` on teardown. `collisionReady` reports
whether a real body exists; visual fallback does not imply collision readiness.

Register `PagedTerrainBinding` to serialize `ScenePagedTerrain` (`paged_terrain`). It references the
page index and preserves capacity, radius, read/upload budgets and collision options. Scope, reader,
observer, render host and physics system are code-only dependencies, which `awake:project:runtime`'s
streamed-terrain capability supplies for a project scene played through `loadProject`. See the
[integration example](../../../docs/plans/streamed-terrain.md).

`scene:world` stays content-agnostic: it streams coordinates, and this module is one answer to what
a coordinate holds. Nav tiles have their own streamer in `awake:navigation`.
