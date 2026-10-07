# Awake WebGPU Backend

Status: **stable**.

WebGPU implementation of [`awake:engine:render:contract`](../../engine/render/contract/README.md),
targeting `wasmJs`. `awake-webgpu`'s `expect class Renderer` implements the contract
interface for the browser target.

## Installation

```kotlin
implementation(project(":awake:backend:webgpu"))
```

## Extensibility

Same convention as [`awake:backend:vulkan`](../vulkan/README.md): `Renderer`'s
constructor takes nullable injected pipelines for optional content
(`skyboxRenderPipeline`, `wireframeRenderPipeline`) and non-null capabilities for
always-available draw primitives. Full convention:
[docs/reference/render-extensibility.md](../../../docs/reference/render-extensibility.md).

## Production-plan verification

`WebGpuProductionPlanTest` compiles the engine showcase's existing render-plan declaration as
a test fixture and boots it through `WebGpuEngine`. It renders lit geometry offscreen, reads
the pixels, and compares the frame with the same plan's content features disabled. This checks
production pipeline construction, scene-depth wiring, and depth-fog feature recording together.
The engine's normal lifecycle owns initialization and teardown.

Run `./gradlew :awake:backend:webgpu:desktopTest --tests '*WebGpuProductionPlanTest*'`.
CI runs it with the backend pixel suite under Xvfb and Mesa's software Vulkan adapter.
Desktop tests use wgpu-native; browser canvas and JavaScript behavior are covered by the
separate Wasm browser suite.
