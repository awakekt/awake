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

`scene:world` stays content-agnostic: it streams coordinates, and this module is one answer to what
a coordinate holds. Nav tiles have their own streamer in `awake:navigation`.
