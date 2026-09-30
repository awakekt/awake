# Your first window

<p class="awake-lede">Describe an app with the <code>app { }</code> DSL, give it a render plan, and open it in a desktop window with the Vulkan backend.</p>

<div class="awake-badges" markdown>
<span class="awake-badge awake-badge--ok">Vulkan</span>
<span class="awake-badge">Desktop</span>
</div>

This page uses `awake-engine-bootstrap` and `awake-asset-shader-pack` in `commonMain`, and `awake-backend-vulkan` in
`desktopMain`. See [Installation](installation.md).

## Describe the app

In `commonMain`, the `app { }` DSL describes the window and the hooks the engine calls. `render`
runs once per frame.

```kotlin title="commonMain/kotlin/Game.kt"
--8<-- "samples/engine-showcase/src/commonTest/kotlin/com/awakekt/awake/showcase/docs/FirstWindowDocsSampleTest.kt:imports"

--8<-- "samples/engine-showcase/src/commonTest/kotlin/com/awakekt/awake/showcase/docs/FirstWindowDocsSampleTest.kt:app"
```

The app does not name a backend, so the same app runs on Vulkan here and on WebGPU in
[Run in the browser](run-in-the-browser.md).

## Choose what it draws with

A `RenderPlan` lists the shaders the app renders with. Every app needs a `primary` pipeline: meshes
draw through it unless their vertex format asks for another. This plan uses the lit shader from the
shader pack, and turns on the depth pre-pass it samples for shadows. Add it to the same file:

```kotlin title="commonMain/kotlin/Game.kt"
--8<-- "samples/engine-showcase/src/commonTest/kotlin/com/awakekt/awake/showcase/docs/FirstWindowDocsSampleTest.kt:plan"
```

The plan lives in `commonMain` so that every backend reads the same one. A backend that cannot run
part of a plan leaves that part out and reports it, rather than failing.

## Open the window

In `desktopMain`, hand the app and the plan to `runVulkanDesktopGame`:

```kotlin title="desktopMain/kotlin/Main.kt"
--8<-- "samples/engine-showcase/src/desktopTest/kotlin/com/awakekt/awake/showcase/docs/firstwindow/FirstWindowMainDocsSampleTest.kt:main"
```

Run `main`. A 1280 × 720 window titled "Hello AwakeKt" opens. Nothing has been added to
draw yet, so it shows only the clear colour. Closing the window ends the loop and disposes the app.

!!! warning "macOS needs `-XstartOnFirstThread`"
    GLFW only opens windows from the process's first thread on macOS. Add `-XstartOnFirstThread` to
    the JVM options of whatever runs `main`, such as your IDE run configuration or Gradle `JavaExec`
    task.

## Window settings

Everything inside `window { }`:

| Property | Type | Default | What it does |
| --- | --- | --- | --- |
| `title` | `String` | `"Awake"` | The window title. |
| `size(width, height)` | function | 800 × 600 | The window size, in pixels. |
| `backend` | `vulkan()` · `webGpu()` · `default()` | `default()` | Which backend the app asks for. `runVulkanDesktopGame` accepts `vulkan()` or `default()`. |
| `frameRateMode` | `FrameRateMode` | `FrameRateMode.Auto` | `Auto` follows the display, `Capped(fps)` limits it, `Unlimited` never waits. |
| `presentMode` | `PresentMode` | `PresentMode.Auto` | How finished frames reach the display. A request: the surface may pick another. |
| `throttleCpuWhenNotForeground` | `Boolean` | `true` | Drops to `backgroundFrameRate` while the window is not focused. |
| `backgroundFrameRate` | `Int` | `15` | The frame rate used while throttled. |

Besides `render`, the app DSL has `ready`, `resize`, `pause`, `resume`, and `dispose` hooks.

## Next

[Your first scene](first-scene.md) puts a camera, a light, and a spinning cube in this window.
