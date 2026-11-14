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

| | Vulkan | WebGPU |
|---|---|---|
| Plain instanced pipelines | Formats the app's shader plan requests (`PipelineKey.Instanced`, `InstancedFormat`) | Same |
| Back-culled instances | Where the plan built a back-culled instanced twin | Same |
| Copies per draw | 4,096 (larger sets split) | 4,096 |
| Skinned copies per draw | 256, authored only | 256, authored only |
| Still frame handed back unplanned | No (frames in flight, #1555) | Yes (`GpuDrawPreparer.redrawsPreparedFrames`) |

Nothing is merged into one mesh; instancing is the only batching.

## What keeps draws apart

`SceneDrawStats.misses` names the reason for every scene-pass draw that did not fold:

- **`DistinctUniforms`**: copies that differ in tint (`PbrMaterial.baseColorFactor` per entity),
  `DitherFade` opacity, or any other factor. Every distinct value is its own draw in the scene
  pass. Plain instanced pipelines carry no per-instance colour or fade yet, so seven tints over 190
  meshes draw 1,330 scene draws; the shadow passes still draw 190.
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
