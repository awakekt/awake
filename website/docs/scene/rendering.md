# Scene rendering

`scene3d` turns visible scene components into backend-neutral render work. It does not contain
Vulkan or WebGPU code; the selected backend consumes the render contract.

## Components

- `MeshRenderer` draws one mesh/material pair at an entity transform.
- Instanced mesh components draw many copies efficiently.
- `Camera` selects the view used for the scene.
- `Light` supplies scene lighting data.
- `LodGroup`, `MeshBounds`, and `Occluder` support visibility and level-of-detail decisions.
- `ParticleEmitter` and `SkinnedPose` support common animated content.

`RenderSystem3D` assembles draw work, performs the scene-side visibility decisions, and submits it
to the selected `Renderer`.
