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

## Render bundles

The scene pass's opaque draws and each shadow cascade's casters are kept as render bundles and
replayed with one `executeBundles` a pass; a frame records again only the cells whose draws changed,
and a change of instance counts alone rewrites indirect arguments (#1555).

**If a frame draws differently on some browser or GPU, turn this off first:**

```kotlin
(renderer as com.awakekt.awake.webgpu.renderer.Renderer).keepsRenderBundles = false
```

Off records every draw directly each frame, as before, and draws the same pixels: a desktop test
and a browser test (`RenderBundleBrowserTest`, at one and four samples) compare both settings frame
by frame. Report the difference with the browser and GPU, so it can be fixed with bundles on.

## Device loss

When the browser reports the `GPUDevice` lost (a GPU process crash, a driver reset, memory
pressure), the canvas host stops drawing and reports it once: to the app's `AppErrorHandler` as
`AppErrorSource.GraphicsDevice`, whose cause is a `WebGpuDeviceLostException`, and to
`WebGpuEngine.onDeviceLost` with the reason and the browser's message. Set the callback in the
factory passed to `launchWebGpuGame`:

```kotlin
launchWebGpuGame(canvas) {
    WebGpuEngine(game, plan).apply { onDeviceLost = { loss -> crashReports.record(loss) } }
}
```

The engine does not recreate the device: every pipeline, buffer and texture it built belonged to
the lost one. Reload the page, or dispose the engine and launch a new one. The loss that the
engine's own teardown causes is not reported.

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
