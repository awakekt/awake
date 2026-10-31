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
| `launchWebGpuGame(game, plan, canvasId = "awake-canvas", cursor)` | The usual case: finds the `<canvas>` by id and builds a `WebGpuEngine` from your plan. |
| `launchWebGpuGame(game, plan, canvas: HTMLCanvasElement, cursor)` | Uses a canvas you already have and builds a `WebGpuEngine` from your plan. |
| `launchWebGpuGame(canvasId = "awake-canvas", cursor, applicationFactory)` | Finds the `<canvas>` by id when you construct a custom `WebGpuEngine` subclass yourself. |
| `launchWebGpuGame(canvas: HTMLCanvasElement, cursor, applicationFactory)` | Uses a canvas you already have with your own `WebGpuEngine` factory. |

`cursor` is optional. Pass `cursor = { runtime.cursor }`, the app runtime's request, and the canvas shows the
pointer shape the UI asks for, as a text beam over a text field or a diagonal arrow over a corner handle.
Without it the canvas keeps the page's cursor, as the desktop window does without its `cursor`.

`applicationFactory` returns a `WebGpuEngine(appLifecycle, requestedPlan)`. Pass the same
`RenderPlan` you give the Vulkan backend.

## Resolution and render scale

The canvas buffer is the canvas's box on the page times `devicePixelRatio`, so a high-DPI screen
shades 2.25 (ratio 1.5) to 9 (ratio 3) times the pixels of a 1x screen. Two providers trade those
pixels for frame rate. Both are lambdas, read before every frame, so a game can put them behind a
graphics setting and change them while it runs:

| Provider | What it scales | Use it for |
| --- | --- | --- |
| `renderScale: () -> Float` | Only the 3D scene, and the passes sized from it (depth, post-processing). A bilinear upscale copies the scene to the canvas, then the UI draws at the full ratio. | Quality settings. Text and HUD edges stay as sharp as the screen can show. |
| `maxPixelRatio: () -> Float?` | The whole canvas buffer, UI included. The UI's density follows it, so layout keeps its size, but text is drawn at the capped ratio. `null` is no cap. | A hard ceiling on pixels, whatever the scene costs. |

```kotlin
launchWebGpuGame(
    game,
    plan,
    maxPixelRatio = { 2f },
    renderScale = { settings.renderScale },
)
```

`renderScale` is clamped to `(0, 1]` (values at or below 0 render at `RenderScale.MIN`, 0.1). The
two compose: the scene renders at `renderScale` of the capped buffer. At 1, the default, the frame
is the one drawn without a provider: no extra target and no extra pass.

Below 1, `Renderer.surfaceWidth`/`surfaceHeight` and `RenderScale.sceneExtent` report the scene
target's size, so a settings screen can show what a scale costs. `Renderer.outputWidth`/
`outputHeight` stay the canvas buffer's size; scene viewports and pointer picking are measured
there and map onto the scene at any scale. A game without the launcher's provider sets
`renderer.renderScale` itself.

The render scale is implemented on WebGPU only. The Vulkan backend reads 1 and ignores a new value
for now.

## What happens at launch

1. If the browser has no `navigator.gpu`, it stops with "WebGPU is unavailable in this browser or
   device…" before the engine starts.
2. If no log sink is installed, it installs one that prints warnings and errors.
3. It listens for pointer, keyboard and text input on the window.
4. It sizes the canvas to its box on the page times `devicePixelRatio`, capped at `maxPixelRatio`
   when you pass one, and keeps it in step on resize.
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
