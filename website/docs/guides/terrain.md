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

`TerrainComponent` also has `splatMap` and `clipmapConfig`, which only Kotlin can set.

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
| `mutableCopy()` | An editable `MutableHeightmap`. |
| `RawHeightmapCodec.decode(...)` / `decodeFromBitmap(...)` | Reads raw 8- or 16-bit heights, or a greyscale image. |

`Heightmap` is immutable. To sculpt, edit a copy and take a snapshot:

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
unchanged. `awake:kit:terrain-layers` ships one provider, `awake.terrain.layers`, which blends
textured layers from a palette and a control map. Each control texel keeps its four strongest
layers, or eight where more than four meet; an eight-layer map costs about twice as much per pixel,
so only terrains that need it pay for it.

For large terrains, `TerrainClipmapSystem` keeps concentric rings of geometry centred on the primary
camera, so memory stays constant however far the camera travels. `TerrainClipmapConfig` sets how many
rings there are and how fine they start.

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
