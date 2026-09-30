# WebGPU backend

<p class="awake-lede">The browser renderer: the same app and render plan as Vulkan, drawn with the browser's WebGPU from a Kotlin/Wasm build.</p>

<div class="awake-badges" markdown>
<span class="awake-badge">module: <code>awake:backend:webgpu</code></span>
<span class="awake-badge awake-badge--ok">WebGPU</span>
<span class="awake-badge">Web (wasmJs)</span>
</div>

The WebGPU backend is published only as a snapshot, because the WebGPU bindings it builds on have no
stable release yet. [Run in the browser](../get-started/run-in-the-browser.md) walks through the
snapshot repository, the `wasmJsMain` entry point, the page and the loading screen. This page covers
how the backend behaves once it runs.

## Launch

In `wasmJsMain`, `launchWebGpuGame` (package `com.awakekt.awake.webgpu.application`) starts the app on
a canvas:

| Overload | What it does |
| --- | --- |
| `launchWebGpuGame(canvasId = "awake-canvas", applicationFactory)` | Finds the `<canvas>` by id. Fails with "No canvas found with id '…'" when there is none. |
| `launchWebGpuGame(canvas: HTMLCanvasElement, applicationFactory)` | Uses a canvas you already have. |

`applicationFactory` returns a `WebGpuEngine(appLifecycle, requestedPlan)`. Pass the same
`RenderPlan` you give the Vulkan backend.

## What happens at launch

1. If the browser has no `navigator.gpu`, it stops with "WebGPU is unavailable in this browser or
   device…" before the engine starts.
2. If no log sink is installed, it installs one that prints warnings and errors.
3. It listens for pointer, keyboard and text input on the window.
4. It sizes the canvas to the window times `devicePixelRatio`, and keeps it in step on resize.
5. It draws one frame per `requestAnimationFrame`.

## The plan on WebGPU

`WebGpuEngine` narrows the plan it is given to what the backend can run, and reports each part it
drops. Today the backend declares a depth pre-pass and every pipeline supported, so nothing is
dropped. See [Render plans and shaders](shaders.md#how-it-works) for how narrowing works.

Shaders go to the browser as WGSL, exactly as the shader sets carry them. There is no compile step.

## Desktop WebGPU

WebGPU also builds for the desktop JVM, over wgpu-native, but only to render headlessly in tests:
`webGpuHeadlessScene()` and `webGpuHeadlessUi()` return a `HeadlessRenderSession`. There is no
desktop window host; use the [Vulkan backend](vulkan.md) for desktop apps. The desktop build needs
JDK 25.

## Debugging

The backend has no validation switch. Errors go to the browser console:

| Message | Meaning |
| --- | --- |
| `WebGPU uncaptured error: …` | The GPU device reported an error. The frame loop stops. |
| `WebGPU frame failed: …` | A frame threw an exception. |
| `WebGPU startup failed before the first frame: …` | The engine could not start. |

The `awake-loader.js` loading screen shows these messages on the page instead of leaving the canvas
black.

## See also

- [Run in the browser](../get-started/run-in-the-browser.md) to set up a WebGPU build.
- [Render plans and shaders](shaders.md) for the plan both backends share.
- [Vulkan backend](vulkan.md) for desktop, Android and iOS.
