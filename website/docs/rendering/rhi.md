# Render hardware interface

Awake’s render hardware interface is the backend-neutral boundary between engine code and graphics
drivers. It carries GPU resources, pipeline descriptions, render targets, and resolved pass input;
it does not describe game-specific scene features.

## Responsibilities

- Engine and scene code prepare render work once.
- `Renderer` and related contract types describe hardware-facing operations.
- Vulkan and WebGPU translate that contract into native command recording.
- Optional content features can be absent on a backend without changing the shared scene model.

This separation keeps backend code focused on devices, buffers, textures, samplers, pipelines, and
command execution. Scene concepts such as cameras, lights, and materials are resolved before they
reach the hardware boundary.

See [Vulkan](vulkan.md), [WebGPU](webgpu.md), and [Shaders and pipelines](shaders.md) for the
public integration points. The [backend tutorials and samples](tutorial.md) put those pages in
the recommended order.
