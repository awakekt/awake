# Shaders and pipelines

The shader module defines backend-neutral shader sets, uniform layouts, descriptor bindings, and
pipeline declarations. It keeps shader data descriptions separate from Vulkan and WebGPU resource
upload code.

## Typical flow

1. Define the shader stages and their resource layout.
2. Validate or compile the source for the target backend.
3. Add the resulting pipeline to the application’s `RenderPlan`.
4. Submit scene or UI work through the render contract.

The [RHI guide](rhi.md) describes the boundary between these declarations and backend execution.
