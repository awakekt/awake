# Terrain

<p class="awake-lede">Ground built from a heightmap: a grid of height samples that becomes a lit mesh, a collider and a navigation grid, all from the same data.</p>

<div class="awake-badges" markdown>
<span class="awake-badge">component: <code>terrain</code></span>
<span class="awake-badge awake-badge--ok">Vulkan</span>
<span class="awake-badge awake-badge--ok">WebGPU</span>
<span class="awake-badge">Desktop · Android · iOS · Web</span>
</div>

`awake:asset:terrain` holds the backend-free data: `Heightmap`, mesh building, sculpting, clipmap
rings and splat weights. The `terrain` scene component, `TerrainComponent`, lives in
`awake:scene:scene3d` and is one of the default scene components.

## Add a terrain

A 4 × 4 sample hill, 2 m between samples. Both forms below build the same component.

=== "Scene document"

    ```json title="hill.scene.json"
    --8<-- "website/docs/snippets/world/hill.scene.json"
    ```

=== "Scene DSL"

    ```kotlin title="Kotlin"
    --8<-- "awake/scene/authoring/src/desktopTest/kotlin/com/awakekt/awake/scene/authoring/TerrainDocsSampleTest.kt:terrain-dsl"
    ```

    There is no dedicated DSL function for terrain yet, so attach a `TerrainComponent` with
    `with(...)`.

=== "Studio"

    1. In the **Hierarchy**, click **Add entity** and choose
       **Environment & World: Terrain Heightmap**. Studio adds a 32 × 32 heightmap.
    2. Select it to edit the terrain in the **Inspector**.

    Studio also saves a `terrain_asset` component beside `terrain`, recording which asset files the
    terrain was authored from.

## Properties

| Property | Type | Default | What it does |
| --- | --- | --- | --- |
| `width` | integer | required | Samples along X, at least 2. |
| `depth` | integer | required | Samples along Z, at least 2. |
| `samples` | list of numbers | required | `width * depth` heights, row by row: index `z * width + x`. |
| `scaleX` | number | `1` | Metres between samples on X. |
| `scaleY` | number | `1` | Multiplier applied to every height. |
| `scaleZ` | number | `1` | Metres between samples on Z. |
| `tilingScale` | number | `16` | How often surface textures repeat across the terrain. |
| `isVisible` | boolean | `true` | Whether the terrain is drawn. |
| `surface` | object or none | none | Names a surface provider and its data; see [How it works](#how-it-works). |
| `clipmapRings` | integer | `6` | Levels of the geometry clipmap, the core and its rings, 2 to 8. Each ring is twice as coarse as the one inside. See [Clipmap detail and cost](#clipmap-detail-and-cost). |
| `clipmapRingResolution` | integer | `64` | Grid cells along one side of each level, an even count from 16 to 256. |
| `clipmapBaseSpacing` | number | `2` | Metres between the finest level's vertices. Above 0. |

A terrain that leaves the clipmap fields out draws exactly as it did before they existed. `TerrainComponent` also has `splatMap`, which only Kotlin can set.

## Work with a heightmap

A `Heightmap` is centred on its entity: it spans `-halfExtentX..halfExtentX` and the same on Z.

```kotlin title="Kotlin"
--8<-- "awake/scene/authoring/src/desktopTest/kotlin/com/awakekt/awake/scene/authoring/TerrainDocsSampleTest.kt:heightmap"
```

| Call | What it does |
| --- | --- |
| `heightAt(x, z)` | The raw sample at a grid position. |
| `heightAtWorld(worldX, worldZ)` | Interpolated world height, with `scale.y` applied. `NaN` off the map. |
| `normalAt(x, z)` | The surface normal at a sample. |
| `toPositionNormalColorMesh(color)` / `toPositionNormalColorMesh { x, z, height -> color }` | A lit mesh, uniformly coloured or coloured per sample. |
| `mutableCopy()` | An editable `MutableHeightmap` with the same `origin`. |
| `RawHeightmapCodec.decode(...)` / `decodeFromBitmap(...)` | Reads raw 8- or 16-bit heights, or a greyscale image. |

`Heightmap` is immutable. To sculpt, edit a copy and take a snapshot. Both keep the map's `origin`,
so a corner-anchored map stays where it was:

```kotlin title="Kotlin"
--8<-- "awake/scene/authoring/src/desktopTest/kotlin/com/awakekt/awake/scene/authoring/TerrainDocsSampleTest.kt:sculpt"
```

## Collide and navigate

The same heights feed the other systems:

- **Physics:** build a `HeightFieldShape` from them for a static collider. See
  [Physics](physics.md#shapes). A heightfield is square; a rectangular map needs tiling with
  `heightFieldTile(...)`.
- **Navigation:** `heightmap.bakeNavGrid()` marks the walkable ground. See
  [Navigation](navigation.md).

## How it works

`TerrainContentSystem` draws every `TerrainComponent` with the built-in terrain shaders. A `surface`
names a `TerrainSurfaceProvider` that supplies different shaders and textures; if that provider is
not installed, the terrain draws with the built-in shaders and the scene keeps the `surface` data
unchanged. A project played with `runProject` gets it with nothing to wire: on a renderer, its
terrains draw, and the layered-terrain kit's provider is installed. A host that runs the scene's
systems itself passes more providers under `CoreSceneContent.TerrainSurfaces`. `TerrainSurfaceProvider` is in `awake:terrain`, which has no scene dependency, so a
provider is written against that module alone. `awake:kit:terrain-layers` ships one provider,
`awake.terrain.layers`, which blends textured layers from a palette and a control map. Each control
texel keeps its four strongest layers, or eight where more than four meet; an eight-layer map costs
about twice as much per pixel, so only terrains that need it pay for it.

For large terrains, `TerrainClipmapSystem` keeps concentric rings of geometry centred on the primary
camera, so memory stays constant however far the camera travels. `TerrainClipmapConfig` sets how many
rings there are and how fine they start; a scene sets it with the `clipmap*` fields above.

### Clipmap detail and cost

Level 0 is a square of `clipmapRingResolution` cells, `clipmapBaseSpacing` metres apart, around the
camera. Each ring around it has the same number of cells at twice the spacing, so it reaches twice as
far. The whole clipmap is one draw, whatever the setting. Each level adds `(clipmapRingResolution + 1)²`
vertices: 4,225 at 64 cells, 16,641 at 128 and 66,049 at 256.

| Rings | Cells a side | Base spacing | Vertices | Triangles | Reach from the camera | Vertex spacing 1 km out |
| --- | --- | --- | ---: | ---: | ---: | ---: |
| 6 (default) | 64 (default) | 2 m (default) | 25,350 | 41,312 | 2,048 m | 32 m |
| 8 | 64 | 2 m | 33,800 | 54,560 | 8,192 m | 32 m |
| 6 | 128 | 2 m | 99,846 | 160,608 | 4,096 m | 16 m |
| 8 | 128 | 2 m | 133,128 | 211,744 | 16,384 m | 16 m |
| 6 | 256 | 2 m | 396,294 | 632,672 | 8,192 m | 8 m |
| 8 | 256 | 2 m | 528,392 | 833,312 | 32,768 m | 8 m |

More rings reach farther without changing the detail at any distance already covered. A finer grid
halves the spacing at every distance and roughly quadruples the vertex count. Past the outermost ring
the terrain is not drawn. To keep the mesh as fine as the source data at a distance, take the source's
sample spacing, double it once per ring that reaches that distance, and choose a grid that keeps up.
The vertex shader samples the heightmap once per vertex, so the vertex count is the cost to watch on a
phone.

!!! warning "One terrain per shader set"
    A content host accepts one terrain per shader set. A second terrain with the same shaders is
    logged and not drawn.

!!! tip "Collider and mesh must agree on the origin"
    Heightmaps and heightfields are centred by default. Content authored with the corner at the
    origin passes `GridOrigin.Corner` to both, or the collider sits half a map away from the mesh.

## See also

- [Physics](physics.md) for heightfield colliders.
- [Navigation](navigation.md) for baking walkable ground.
- [Large worlds](large-worlds.md) for streaming terrain in cells.
- [Lights and shadows](lights-and-shadows.md) for lighting it.
