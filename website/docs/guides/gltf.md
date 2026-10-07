# glTF models

<p class="awake-lede">Load <code>.gltf</code> and <code>.glb</code> models and draw them by path: the geometry, its textures, and for skinned models, the skeleton and clips.</p>

<div class="awake-badges" markdown>
<span class="awake-badge">component: <code>mesh_renderer</code></span>
<span class="awake-badge"><code>.gltf</code> · <code>.glb</code></span>
<span class="awake-badge awake-badge--ok">Vulkan</span>
<span class="awake-badge awake-badge--ok">WebGPU</span>
<span class="awake-badge">Desktop · Android · iOS · Web</span>
</div>

There is no separate model component. A `mesh_renderer` whose `mesh` is a path ending in `.gltf` or
`.glb` draws that model, once the app's glTF resolver has loaded it. Two modules do the work:
`awake:asset:gltf` parses the file (`GltfParser`), and `awake:scene:gltf` turns it into meshes and
materials for a scene (`GltfAssetResolver`).

## Place a model

=== "Scene document"

    ```json title="model.scene.json"
    --8<-- "website/docs/snippets/rendering/model.scene.json"
    ```

=== "Scene DSL"

    ```kotlin title="Kotlin"
    --8<-- "awake/project/runtime/src/desktopTest/kotlin/com/awakekt/awake/project/runtime/GltfDocsSampleTest.kt:dsl"
    ```

=== "Studio"

    1. Choose **File** > **Import Asset...** and pick a `.glb` or `.gltf`. Studio copies it into the
       project's `assets/models/` folder.
    2. Open the model from the **Files** tab.
    3. Click **Place in Scene**. Studio adds an entity with a `mesh_renderer` naming the model's path.

    Import a `.glb` to bring one self-contained file. A `.gltf` that refers to separate `.bin` or
    image files needs those copied too.

Both forms build the same `MeshRenderer`. An untextured model draws with `lit-shadow`; a model with a
base colour texture gets a material named `gltf-material:<path>`. `materialName(path)` returns the
right one. A textured model draws through the render plan's
`PipelineKey.Format(VertexFormat.PositionNormalColorUv)` pipeline (`PackShaderSets.Textured`, with
`GroupBindings.StandardMaterial`), so the plan needs one.

## Load models in your app

The resolver must load a model before a scene names it. Register it next to the other assets:

```kotlin title="Kotlin"
--8<-- "awake/project/runtime/src/desktopTest/kotlin/com/awakekt/awake/project/runtime/GltfDocsSampleTest.kt:resolver"
```

`setAssetSource` tells the resolver where to read files, including a `.gltf`'s separate buffers and
images. `builtInSceneAssets()` registers `lit-shadow`, which the resolver does not provide.

To play a whole AwakeKt Studio project, `loadProject` reads the project, loads every model its
entry scene names, and `runProject` wires the resolver and the systems for you:

```kotlin title="Kotlin"
--8<-- "awake/project/runtime/src/desktopTest/kotlin/com/awakekt/awake/project/runtime/GltfDocsSampleTest.kt:project"
```

`runProject` also starts every skinned model's first clip on a loop; see [Animation](animation.md).

## What is supported

| Feature | Support |
| --- | --- |
| Buffers | Embedded `data:` URIs, the GLB binary chunk, and separate `.bin` files read through the asset source. |
| Geometry | Positions, normals, `COLOR_0` (VEC3), `TEXCOORD_0`, 8-, 16- and 32-bit indices. Normalised integer attributes, as `KHR_mesh_quantization` writes them. |
| Scenes | Every mesh and primitive in the default scene, with node matrices or TRS baked in. |
| Materials | Base colour, metallic-roughness, normal, occlusion and emissive textures; `OPAQUE` and `MASK` alpha. `baseColorFactor` is multiplied into the vertex colour. |
| Skins | `JOINTS_0` (8- or 16-bit) and `WEIGHTS_0`, inverse bind matrices, up to 64 joints. |
| Animation | Translation, rotation and scale channels, sampled linearly. |

Not supported: Draco and meshopt compression, sparse accessors, morph targets, other texture
coordinate sets, texture samplers, and `BLEND` alpha (drawn as opaque).

## How it works

`preload(path)` parses the file once and keeps the result. For a `.glb`, the resolver keeps static
geometry. For a `.gltf`, it also keeps the skeleton, skins and clips, and `getLoadedScene(path)`
returns them. When a scene asks for the mesh, a skinned `.gltf` becomes a `PositionNormalColorSkin`
mesh (use the material `skinned-material`); anything else becomes static geometry.

Static geometry is rebuilt, not copied. Node transforms are baked into the vertices, normals are
recomputed from the triangles, and `baseColorFactor` is multiplied into the vertex colour. A model
with several primitives and materials is split into one mesh per primitive, named
`gltf-primitive:<path>#<index>`; `materialSlots(path)` lists each primitive's mesh and material.

!!! warning "Preload before the scene loads"
    A scene that names a model the resolver has not loaded fails with "glTF asset '…' has not been
    preloaded".

!!! warning "A `.glb` does not animate"
    The resolver reads skins and clips only from `.gltf` files. A skinned `.glb` draws as static
    geometry.

!!! warning "Material factors are not applied for you"
    Apart from `baseColorFactor`, a model's factors (`metallicFactor`, `roughnessFactor`,
    `emissiveFactor`) are read, and `materialParameters(path)` returns them, but nothing puts them on
    the entity. Add a `pbr_material` with the values you want.

!!! tip "Missing textures do not stop the model"
    If a model's textures fail to load, it draws untextured with `lit-shadow`, and the `scene.gltf`
    logger warns "Drawing '…' untextured".

## See also

- [Meshes and materials](meshes-and-materials.md) for `mesh_renderer` and `pbr_material`.
- [Animation](animation.md) for playing a skinned model's clips.
