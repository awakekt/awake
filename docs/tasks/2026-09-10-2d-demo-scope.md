# 2D demo scope and dependency boundary

Status: active architecture decision

The existing ECS system is named `RenderSystem3D` because it owns camera, light, mesh, particle,
shadow, and visibility extraction. A `RenderSystem2D` for the showcase is intentionally a small
demo coordinator around `render:passes2d`; it is not a second general-purpose ECS renderer yet.

The engine showcase will use 2D as a small proof of dependency separation. It will demonstrate that a 2D scene can compile and render alongside a 3D scene and UI without making the RHI or the 3D scene renderer depend on 2D authoring types.

## Boundary

- `awake:engine:render:contract` remains hardware-only. It owns generic buffers, textures, pipeline handles, pass inputs, and resolved draw packets.
- `awake:engine:render:passes2d` owns 2D staging, tessellation, batching, and generic 2D pass output.
- Scene or demo code owns sprites, tilemaps, and authored 2D components. It lowers them into `passes2d` primitives.
- The backend only records the generic 2D runs after the 3D passes and before the final UI overlay. It does not import sprite, tilemap, or game component classes.
- 2D depth/shadow casting is out of the first demo scope. `DepthCasterKind.Sprite` and `Tilemap` remain reserved keys until a real depth requirement exists.

## Showcase acceptance slice

1. A colored quad and a textured sprite are emitted through `passes2d`.
2. A 3D mesh is emitted through the shared scene pipeline in the same frame.
3. Compose/UI is recorded as the overlay and preserves the scene color target.
4. The demo can run headless with a packet-count assertion even when no GPU adapter is available.
5. WebGPU and Vulkan consume the same 2D packet shape; no backend receives authored 2D types.

This keeps the 2D demo useful for architectural verification while leaving advanced tilemap culling, sprite atlases, 2D lighting, and 2D shadows as separate features.
