# Awake Core Spatial

Uniform 2D-column spatial index for 3D worlds in [Awake](../../../README.md) — depends only on
`awake:core:math`, with no ECS, scene, or renderer dependency. Compiles for every target the
engine ships (desktop, Android, iOS, and wasmJs).

## Installation

```kotlin
implementation(project(":awake:core:spatial"))
```

Published Maven coordinate: `com.awakekt.awake.core:spatial`.

## `SpatialGrid`

`SpatialGrid` partitions world space into vertical `(x, z)` columns of `cellSize` units
(default `DEFAULT_SPATIAL_CELL_SIZE = 64f`) and indexes integer ids by their world-space `Aabb`.
Insert and move share one `insert(id, bounds)` operation; staying inside the same column span and
height band costs no list churn or column walk.

```kotlin
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.spatial.SpatialGrid

val grid = SpatialGrid(cellSize = 64f)
grid.insert(entityId = 1, bounds = Aabb(Vec3f(-1f, 0f, -1f), Vec3f(1f, 2f, 1f)))

val hits = ArrayList<Int>()
grid.queryRange(center = Vec3f.ZERO, radius = 32f, out = hits)
```

Queries append exact matches into a caller-supplied `MutableCollection<Int>` so per-frame callers
allocate nothing:

- `queryAabb(box, out)` — ids whose bounds overlap `box`.
- `queryRange(center, radius, out)` — ids whose bounds lie within `radius` of `center`.
- `queryFrustum(lens, aspect, out)` — ids whose bounds intersect `lens`'s view frustum, tested
  column-first and then per entry against one shared plane set.
- `queryVolume(volume, out)` — ids admitted by any `BoundedVolume` (for instance,
  `ShadowCasterVolume` in `awake:engine:render:passes`), clipping an unbounded volume against the
  extent of indexed content before walking columns.

For ECS scene integration, see `SpatialIndex` and `SpatialIndexSystem` in
[`awake:scene:scene3d`](../../scene/scene3d/README.md).

## Moved from `awake:scene:scene3d`

Until 0.27 this grid was `com.awakekt.awake.scene.rendering.spatial.SpatialGrid`. That class is
still there, deprecated, with every 0.27 member forwarding to the core grid it wraps (`core`), so
code compiled against 0.27 keeps linking; it and the scene-side `DEFAULT_SPATIAL_CELL_SIZE` are
removed at 1.0. `SpatialIndex.grid` keeps the scene-side type until then: read the core grid as
`index.grid.core`, and build an index over your own core grid with `SpatialIndex(coreGrid)`.
`queryShadowCasters(volume, out)` is `queryVolume(volume, out)` here.
