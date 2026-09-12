# D32: scene and render domain modules

Status: accepted

The scene and render layers use domain-specific modules where the boundary is real:

```text
awake:scene:scene-core       ECS transforms and shared scene state
awake:scene:scene3d          3D components and RenderSystem3D
awake:engine:render:contract generic hardware handles and pass packets
awake:engine:render:passes   shared scene/pass compilation and generic 3D execution packets
awake:engine:render:passes2d 2D staging, batching, and 2D pass packets
```

A future `awake:scene:scene2d` is safe when the engine owns reusable 2D ECS components such as
sprites or tilemaps. Until then, the showcase may keep a small `RenderSystem2D` coordinator local
to the sample and lower directly into `render:passes2d`. That demonstrates the dependency boundary
without inventing a second ECS renderer.

We do not create `awake:render:passes:2d` and `awake:render:passes:3d` aliases. The repository's
render modules already live under `awake:engine:render`, and moving or aliasing them would duplicate
publication coordinates and invite cycles between the hardware contract and scene layers. The
existing `passes2d` module is the stable 2D boundary; the shared `passes` module remains the
backend-neutral compiler for generic scene packets. If 3D-only code later becomes large enough to
warrant a module, it can be extracted as `passes3d` behind the same contract without changing the
RHI or backend APIs.

Dependency rules:

- `scene3d` and a future `scene2d` may depend on `scene-core` and their matching render-pass module.
- `render:contract` never depends on either scene module.
- Vulkan/WebGPU depend on render contracts and pass interfaces, never on scene authoring packages.
- The application/bootstrap layer composes 2D, 3D, and UI features into one frame.
