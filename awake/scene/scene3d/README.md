# Awake Scene Rendering

Status: **stable**.

`awake:scene:scene3d` bridges `awake:scene:scene-core` (ECS `Transform`, entities) and
`awake:engine:render:contract` (backend-neutral `Renderer`/`DrawCall`/`Mesh`/`Material`) --
it owns the components a scene author attaches to make an entity visible, and the systems
that turn those components into a frame's `DrawCall` list every frame.

## Installation

```kotlin
implementation(project(":awake:scene:scene3d"))
```

`api`-depends on both `awake:scene:scene-core` and `awake:engine:render:contract`, so a
consumer gets `Transform`/`Mesh`/`Material`/`DrawCall` visible transitively.

## Components (`components/`)

- `MeshRenderer` -- one mesh/material pair drawn at an entity's `Transform`. The common case.
- `InstancedMeshRenderer` / `InstancedSkinnedMeshRenderer` -- many copies of one mesh/material
  drawn in a single GPU-instanced call; carries its own list of transforms (and joint palettes
  for the skinned variant) instead of relying on per-entity `Transform`s.
- Particles are not defined here. `awake:particles` owns `ParticleEmitter` and `ParticleSystem`,
  and `awake:scene:particles` owns the `particle_emitter` scene component. This module only draws
  them: `SceneParticleCompiler` hands the scene's `Camera` and each entity's `Transform` to that
  library's `ParticleDrawBuilder`.
- `Camera` -- wraps `awake:core`'s `CoreCamera` math; `isPrimary` marks the one `RenderSystem3D`
  actually renders through.
- `Light` -- a single scene-wide directional light (`RenderSystem3D.sceneLight` falls back to
  `DEFAULT_SCENE_LIGHT` when no `Light` entity exists).
- `LodGroup` -- picks one mesh/material level by distance to the camera each frame.
- `MeshBounds` -- opt-in local AABB; entities without it are never frustum/occlusion-culled.
- `Occluder` -- opt-in occluder box; see `awake:core`'s `Occlusion.kt` for the containment test.
- `PbrMaterial` / `SkinnedPose` -- per-entity material factors / joint palette, read by
  `RenderSystem3D` into a `DrawCall`'s `extraUniformFloats`.
- `WorldDebugSettings` -- singleton toggles (`showFrustum`/`showBounds`/`showOcclusion`) read
  by `DebugVisualizationSystem`.

## Systems (`systems/`)

- `RenderSystem3D` -- the one system that assembles a frame's `DrawCall` list: frustum/occlusion
  culls `MeshRenderer`/`LodGroup` entities, builds instanced draw calls for
  `InstancedMeshRenderer`/`InstancedSkinnedMeshRenderer` and, through `ParticleDrawBuilder`,
  `ParticleEmitter`, resolves the scene
  light, and calls `Renderer.draw`.
- `DebugVisualizationSystem` -- turns `WorldDebugSettings`'s toggles into `Renderer`
  debug-line draws (frustum/bounds/occlusion wireframes).
- `RenderSystemSupport.kt` -- shared helpers (`primaryCamera`, `CONSERVATIVE_ASPECT`) used by
  both systems above.

## Scope

This module has no rendering-backend code of its own -- it only builds `DrawCall`s and hands
them to whatever `Renderer` the app is running (`awake:backend:vulkan`/`awake:backend:webgpu`).
It also has no scene-authoring/DSL surface -- that's `awake:scene:authoring`; this module is
consumed by it, not the other way around.
