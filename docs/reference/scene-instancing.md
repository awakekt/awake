# Scene instancing

How the scene compiler turns many renderers that share a mesh into few draws, what keeps them
apart, and how to see it. Code: `ScenePassCompiler`, `InstanceBatcher`, `ShadowCasterFolding`
(`:awake:engine:render:passes`).

## What folds

Every frame, the scene pass folds the draws that **differ only in placement** into one instanced
draw. Those draws share a mesh, a GPU material, extras (material factors, tint), vertex
animation, alpha mode and cutoff, fade, mask layer, covering exemption, cull mode and whether they
cast. Nothing has to be marked static: a still town of 3,000 renderers on 190 meshes draws 190
instanced draws in its scene pass.

The **shadow passes fold again**, by what a depth pass reads alone: mesh, GPU material, cull mode
and vertex animation. Copies the scene keeps apart for their tint or fade cast as one draw, so each
cascade costs about one draw per mesh.

On WebGPU those casters also **share one depth group**. An opaque instanced caster's depth shader
reads only its vertex animation from its group (its matrices come from the instance buffer), so
casters of different meshes that animate alike bind the first one's group, and a depth pass binds
about one group rather than one per mesh. A masked caster keeps its own, for its texture; an
ordinary caster keeps its own, for its model matrix. Vulkan binds a group per draw as before.

| | Vulkan | WebGPU |
|---|---|---|
| Plain instanced pipelines | Formats the app's shader plan requests (`PipelineKey.Instanced`, `InstancedFormat`) | Same |
| Back-culled instances | Where the plan built a back-culled instanced twin | Same |
| Copies per draw | 4,096 (larger sets split) | 4,096 |
| Skinned copies per draw | 256, authored only | 256, authored only |
| Still frame handed back unplanned | No (frames in flight, #1555) | Yes (`GpuDrawPreparer.redrawsPreparedFrames`) |

Nothing is merged into one mesh; instancing is the only batching.

## Tinted and fading copies

A textured mesh's copies that differ only in their tint (`PbrMaterial.baseColorFactor` rgb) or
their `DitherFade` opacity fold too, on a pipeline built with `PipelineVariant.InstancedTinted`, as
a played project's render plan builds the textured instanced pipeline. Each copy carries one
`vec4` after its model matrix, on the instance-rate binding `PipelineVariant.instanceAlpha` adds:
rgb multiplies its colour and a is its fade. The shared block's tint is white and its fade 1, so
each copy draws exactly as it does alone; the parity tests hold both backends to that. Copies that
agree on tint and fade fold as before and carry nothing extra; an untinted copy on a tinting
pipeline reads (1, 1, 1, 1). The tint's alpha stays in the shared block, so copies must agree on it.

Blended draws never fold, tinted or not: they are sorted by depth and keep their own tint. The
untextured instanced pipeline reads no base colour factor, alone or folded, so it is not a tinting
pipeline and keeps copies with different fades apart. The 4,096-copy cap is unchanged; a tinting
draw writes 16 more bytes per copy.

A custom instanced pipeline built with `PipelineVariant.Instanced` is unchanged: it gets no tint
binding and is never sent copies that differ in tint. To fold tinted copies, build it with
`PipelineVariant.InstancedTinted` and declare a `vec4` input at the location after the matrix
(after the vertex format's attributes and the matrix's four columns), multiplying it into the
colour and the dither fade as `instancedTexturedTintedShader` does. A backend reports which
pipelines tint through `GpuDrawPreparer.canTintInstances`.

## Materials are shared by name

Folding needs the copies to share one GPU material. A scene's asset library builds each material
once per name (`SceneAssetLibrary.requireMaterial`), so every `mesh_renderer` that names the same
`material` draws with the same one. An importer that writes one material per mesh, as a glTF import
names them, folds; one that writes a material name per renderer, even with equal settings, never
folds.

To check a scene, turn on `RenderDiagnostics.drawStatsEnabled` and read `RenderDiagnostics.drawStats`:
`misses(InstanceFoldMiss.DistinctMaterial)` counts the scene-pass draws kept apart only by their
material, and `opaqueDraws` near the renderer count rather than the mesh count says the same.
`Renderer.framePassStats.sceneOpaque` shows it from the backend's side: draws close to its instances
mean little folded.

## What keeps draws apart

`SceneDrawStats.misses` names the reason for every scene-pass draw that did not fold:

- **`DistinctUniforms`**: copies that differ in a factor their pipeline cannot carry per instance.
  On a tinting pipeline (below) that is anything but the tint's rgb and the `DitherFade` opacity; on
  a plain instanced pipeline it is any factor at all, so seven tints over 190 meshes draw 1,330
  scene draws there. The shadow passes draw 190 either way.
- **`DistinctMaterial`**: copies sharing a mesh, each with its own GPU material instance.
- **`VertexFormat`** / **`CullMode`**: the backend has no instanced pipeline for the format, or for
  back culling of it.
- **`Masked`**, **`Blended`**: alpha-masked without an instanced pipeline, or transparent.
- **`SkinnedNotInstanced`**: posed characters, a draw each unless authored as skinned instances.

A frame's total also counts every shadow cascade, point-shadow face, the camera's depth and mask
passes, and the UI. 700 live renderers can come to 1,500 draws that way.

## Seeing it

- `Renderer.framePassStats`: draws, instances and triangles per pass (scene opaque, transparent,
  each shadow pass, UI, other), as the backend recorded them.
- `RenderDiagnostics.drawStatsEnabled = true`, then `RenderDiagnostics.drawStats`: per-pass draws
  after folding, folded copies, and the miss reasons above.
