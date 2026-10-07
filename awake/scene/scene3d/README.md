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
  them: `ParticleDrawAdapter` hands the scene's `Camera` and each entity's `Transform` to that
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

## Orthographic virtual viewports

An orthographic camera can author a fixed virtual rectangle in world units:

```json
{
  "component": "camera",
  "projection": "orthographic",
  "viewport": { "width": 16, "height": 9, "scaling": "fit" }
}
```

| Scaling | Behavior |
| --- | --- |
| `fit` | Shows the whole virtual rectangle between centered bars. |
| `extend` | Shows at least the virtual rectangle, extending the visible world along the longer axis. |
| `fill` | Fills the target with uniform scale, cropping the virtual rectangle. |
| `stretch` | Shows the whole virtual rectangle with independent X/Y scaling. |
| `screen` | One world unit per physical framebuffer pixel; virtual dimensions are ignored. |

The optional `viewport` replaces `orthoHalfHeight` for the rendered view. Omitting it keeps the
existing fixed vertical extent and live aspect behavior. Width and height must be positive and
finite; viewport policies require orthographic projection. Resizing never changes authored camera
values or the exported scene. Fit bars retain the target clear color (black by default); UI still
uses the whole target.

`RenderSystem3D` resolves inside `viewportProvider` panel bounds, or the renderer's physical
`surfaceWidth`/`surfaceHeight`. Custom renderers must expose these dimensions when using virtual
viewports without panel bounds. An undrawable target invalidates picking and skips the scene frame.
Captures with policies use `planCapture(world, camera, width, height)`, with the offscreen target's
pixel dimensions, rather than the aspect-only overload.

Picking uses `system.cameraViewport.rayThroughSurface(x, y, renderer.clipSpace)` after the frame:
coordinates are physical framebuffer pixels with top-left origin, including panel offsets. It
returns null over bars or outside the view. `projectToSurface` uses the same resolved projection.
A tool or another host can keep its own `CameraViewport` and call `update(camera, width, height,
x, y)` before rendering or picking. Each view owns its reusable result and scratch lens.

The resize math lives in `core:math` (`VirtualViewport`, `ViewportScaling`, `ViewportLayout`),
with no scene dependency. `CameraViewport` only binds its result to a camera and GPU rectangle.

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
