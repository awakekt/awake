# Meshes and materials

<p class="awake-lede">Draw geometry: one mesh per entity with a surface material, many copies in one draw call, detail levels that swap with distance, and the culling that skips what the camera cannot see.</p>

<div class="awake-badges" markdown>
<span class="awake-badge">component: <code>mesh_renderer</code></span>
<span class="awake-badge">component: <code>pbr_material</code></span>
<span class="awake-badge awake-badge--ok">Vulkan</span>
<span class="awake-badge awake-badge--ok">WebGPU</span>
<span class="awake-badge">Desktop · Android · iOS · Web</span>
</div>

A scene document and the scene DSL are two ways to write the same ECS components. The draw
components live in `awake:scene:scene3d`; `RenderSystem3D` turns them into draws for whichever
backend runs the app.

## Draw a mesh

A `mesh_renderer` pairs a mesh with a material. A `pbr_material` next to it sets how metallic and
rough the surface looks.

=== "Scene document"

    ```json title="crate.scene.json"
    --8<-- "website/docs/snippets/rendering/crate.scene.json"
    ```

=== "Scene DSL"

    ```kotlin title="Kotlin"
    --8<-- "awake/project/runtime/src/desktopTest/kotlin/com/awakekt/awake/project/runtime/MeshesDocsSampleTest.kt:crate-dsl"
    ```

=== "Studio"

    1. In the **Hierarchy**, click **Add entity** and choose **3D Primitives: Cube** (or **Sphere**,
       **Plane**). Studio saves it as a `mesh_renderer` with mesh `cube` and material `lit-shadow`.
    2. In the **Mesh Renderer** section of the **Inspector**, set **Visible**, **Transparent** and
       **Cull**.

    Studio's **Add component** menu does not offer **PBR Material**. When an entity already has one,
    its Inspector section edits **Metallic** and **Roughness**.

Both forms build equal components.

## Name the meshes and materials

In a scene document, `mesh` and `material` are names. The app's `assets { }` block says what each
name builds, and a name nobody registered fails when the scene loads.

```kotlin title="Kotlin"
--8<-- "awake/project/runtime/src/desktopTest/kotlin/com/awakekt/awake/project/runtime/MeshesDocsSampleTest.kt:assets"
```

`builtInSceneAssets()` from `awake:project:runtime` registers the names AwakeKt Studio uses: the
meshes `cube`, `sphere`, `ground` and `plane`, and the material `lit-shadow`. A mesh name ending in
`.gltf` or `.glb` resolves through the glTF resolver; see [glTF models](gltf.md).

In the scene DSL you can also build meshes and materials yourself and pass them to `meshRenderer`,
as long as the render plan has a pipeline for the mesh's vertex format.

## Draw many copies

`InstancedMeshRenderer` draws one mesh many times in a single draw call. Each matrix is a full
world transform; the entity's own transform is not applied.

```kotlin title="Kotlin"
--8<-- "awake/project/runtime/src/desktopTest/kotlin/com/awakekt/awake/project/runtime/MeshesDocsSampleTest.kt:instancing"
```

The render plan needs a `PipelineKey.Instanced` pipeline for this; see
[Render plans and shaders](shaders.md).

## Swap detail with distance

An `LodGroup` holds one `LodLevel` per detail level. Each frame the renderer picks a level by the
entity's distance from the camera. An entity carries an `LodGroup` instead of a `MeshRenderer`.

```kotlin title="Kotlin"
--8<-- "awake/project/runtime/src/desktopTest/kotlin/com/awakekt/awake/project/runtime/MeshesDocsSampleTest.kt:lod"
```

## Cull what the camera cannot see

- **Frustum culling.** An entity with `MeshBounds` is skipped when its bounds are outside the camera's
  view. An entity without it is always drawn.
- **Occlusion culling.** An entity with an `Occluder` hides any entity with `MeshBounds` that lies
  entirely inside its screen rectangle and farther from the camera. One occluder must cover the whole
  entity; several partly covering it do not count. It runs only when at least one entity has an
  `Occluder`.

```kotlin title="Kotlin"
--8<-- "awake/project/runtime/src/desktopTest/kotlin/com/awakekt/awake/project/runtime/MeshesDocsSampleTest.kt:occluder"
```

Both boxes are in the entity's local space, before its transform.

## Properties

`mesh_renderer`:

| Property | Type | Default | What it does |
| --- | --- | --- | --- |
| `mesh` | string | required | The mesh name, resolved by the app's `assets { }`. |
| `material` | string | required | The material name, resolved the same way. |
| `cullMode` | `None` · `Back` · `Front` | `None` | Which triangle faces to skip. `Back` suits a solid, correctly wound mesh. |
| `transparent` | boolean | `false` | Draws in the transparent pass: blended by the material's alpha, sorted back to front, no depth write. |

The ECS `MeshRenderer` also has `visible` (default `true`; `false` skips the draw) and
`vertexAnimation`, a shader-defined vertex effect that is off at zero. Neither is saved in a scene
document.

`pbr_material`:

| Property | Type | Default | What it does |
| --- | --- | --- | --- |
| `metallic` | number, 0 to 1 | `0` | 0 is a dielectric, 1 is metal. |
| `roughness` | number, 0 to 1 | `0.5` | 0 is smooth, 1 is rough. |
| `baseColorFactor` | color | white | Multiplies the base colour texture. Textured pipeline only. |
| `emissiveFactor` | color | transparent | Multiplies the emissive texture. Textured pipeline only. |
| `alphaMode` | `Opaque` · `Masked` | `Opaque` | `Masked` discards pixels whose alpha is below `alphaCutoff`. |
| `alphaCutoff` | number, 0 to 1 | `0.5` | The `Masked` threshold. |

`texture_animation`, a component of its own beside the material, plays the texture as a frame sheet
and scrolls it. `columns` and `rows` (default 1) describe the sheet, read left to right, then top to
bottom. `frameCount` (0 means every cell from `firstFrame` on) and `framesPerSecond` (0 holds the
first frame of the run) play it. `scrollU` and `scrollV` scroll the UVs, in UV units per second.

`firstFrame` is the cell a run starts at, counted from 0 in that reading order. One sheet can hold
several animations, an idle and a walk, and each entity plays its own:

```json
{ "component": "texture_animation", "columns": 8, "rows": 4, "firstFrame": 8, "frameCount": 8, "framesPerSecond": 12 }
```

That plays the second row of an eight-column sheet, cells 8 to 15, twelve frames a second. With
`framesPerSecond` 0 it shows `firstFrame` and nothing else, which is how an entity shows one chosen cell.

`texture_clips` cuts a sheet into named clips and plays one of them, for a sprite that changes what it
is doing. A clip is a run of cells (`firstFrame`, `frameCount`), a rate (`framesPerSecond`) and
whether it repeats (`loop`):

```json
{
  "component": "texture_clips", "columns": 8, "rows": 4, "clip": "idle",
  "clips": {
    "idle":   { "firstFrame": 0,  "frameCount": 4, "framesPerSecond": 6 },
    "walk":   { "firstFrame": 8,  "frameCount": 8, "framesPerSecond": 12 },
    "attack": { "firstFrame": 16, "frameCount": 6, "framesPerSecond": 16, "loop": false }
  }
}
```

`texture_animation` is played by the GPU on the renderer's clock. These clips are stepped on the CPU by
the scene's own clock, so a game can choose among them and a paused game holds still. From a system,
`world.get<TextureClips>(entity)?.play("walk")` switches clip. Asking for the clip that is already
playing does nothing, so the line can run every frame. A clip with `loop` false holds its last cell
and `TextureClips.isFinished` is true from then. `clip` is the one that plays when the scene loads;
left out, the first listed plays. Give an entity `texture_clips` or `texture_animation`, not both.

Kotlin-only components:

| Component | Fields | What it does |
| --- | --- | --- |
| `InstancedMeshRenderer` | `mesh`, `material`, `transforms: List<Mat4>` | Draws `mesh` once per matrix, in one draw call. |
| `LodGroup` | `levels: List<LodLevel>`, `hysteresis = 0.1` | Picks one level per frame by camera distance. |
| `LodLevel` | `mesh`, `material`, `maxDistance` | Draws while the entity is at most `maxDistance` away. |
| `MeshBounds` | `localBounds: Aabb` | Lets the entity be frustum-culled. |
| `Occluder` | `localBounds: Aabb` | Hides `MeshBounds` entities it fully covers on screen. |

## How it works

`RenderSystem3D` is one of the three systems every scene gets by default. Each frame it finds the
primary camera, collects `MeshRenderer`, `InstancedMeshRenderer`, `LodGroup`, particle and skinned
components, culls them, and hands the draws to the renderer. A mesh draws through the render plan's
pipeline for its vertex format: the primary pipeline for the primary's own format, the plan's
`scenePipelines` for the others. A format with no pipeline is not drawn.

`metallic` and `roughness` mean two things. The primary lit pipeline uses them as the surface's only
values. The textured pipeline multiplies them into its metallic-roughness texture, as glTF's
`metallicFactor` and `roughnessFactor` do.

`LodGroup` levels must be sorted finest first. The first level whose `maxDistance` covers the
distance draws; past the last threshold, the coarsest level still draws, so LOD never hides an
entity. `hysteresis` is a band, as a fraction of each threshold, in which the level already showing
keeps showing, so a camera near a threshold does not flicker between levels.

!!! warning "A scene DSL mesh is never frustum-culled on its own"
    When a scene document's `mesh_renderer` loads, the entity also gets `MeshBounds` from the
    mesh's bounds, if the backend reports them. `meshRenderer()` in the scene DSL does not add
    `MeshBounds`, so add it yourself to let the entity be culled.

!!! warning "One `LodGroup` per entity"
    An `LodGroup` remembers which level it last drew. Entities that share one instance share that
    memory. Give each entity its own.

!!! warning "Instancing is for static meshes"
    `InstancedMeshRenderer` has no per-instance joint palette, so a skinned mesh cannot be instanced
    with it. Use `InstancedSkinnedMeshRenderer`; see [Animation](animation.md).

!!! tip "`PbrMaterial` and skinning"
    `PbrMaterial` and `SkinnedPose` fill the same per-draw uniform slot, for different vertex formats.
    An entity carries one or the other, not both.

## Debugging

`RenderDiagnostics` holds the last frame's counters: `frustumCulled`, `occluded`,
`submittedDrawCalls`, `submittedInstances`, and `unresolvedDrawCalls` (draws the backend could not
match to a pipeline or resource). `RenderSystem3D` also exposes `lastFrustumCulledCount` and
`lastOccludedCount`.

## See also

- [Lights and shadows](lights-and-shadows.md) for lighting what you draw.
- [glTF models](gltf.md) for meshes and materials from model files.
- [Render plans and shaders](shaders.md) for the pipelines each vertex format needs.
