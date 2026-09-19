# Backend tutorials and samples

Backend work follows the [install and first application guide](../getting-started.md). Keep scene
vocabulary in the scene layer and choose a backend only at the application boundary.

## Recommended path

1. Read the [render hardware interface overview](rhi.md) to understand the boundary.
2. Choose [Vulkan](vulkan.md) for native desktop or [WebGPU](webgpu.md) for WasmJs.
3. Define [shaders and pipelines](shaders.md), using the [shader DSL](../asset/shader-dsl.md) and
   [Naga compiler](shader-compiler.md) where appropriate.

An end-to-end backend tutorial is intentionally marked as future work. It needs a real headless
render capture for the selected backend, not a hand-written pseudo-renderer. Until that capture is
available, the pages above document the stable integration boundaries.
