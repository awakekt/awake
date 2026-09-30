# Vulkan backend

<p class="awake-lede">The native renderer: a Vulkan device, swapchain and command recording behind the same render plan WebGPU uses, on desktop, Android and iOS.</p>

<div class="awake-badges" markdown>
<span class="awake-badge">module: <code>awake:backend:vulkan</code></span>
<span class="awake-badge awake-badge--ok">Vulkan</span>
<span class="awake-badge">Desktop · Android · iOS</span>
</div>

The Vulkan backend is released on its own, as version `{{ awake_vulkan_version }}`, and each release
depends on one exact AwakeKt Engine release. See
[Core and Vulkan versions](../get-started/installation.md#core-and-vulkan-versions) for the pairing
and [Your first window](../get-started/first-window.md) for a first `main()`.

## Open a window

On desktop, `runVulkanDesktopGame` (package `com.awakekt.awake.vulkan.application`) opens a GLFW
window, runs the frame loop until it closes, then disposes the app. It has three overloads:

| First parameters | Use it when |
| --- | --- |
| `game, plan: RenderPlan` | The usual case: the backend builds a `VulkanEngine` from your plan. |
| `game, applicationFactory: (AwakeAppLifecycle) -> VulkanEngine` | You construct the engine yourself. |
| `game, application: VulkanEngine` | You already have the engine. |

All three also take:

| Parameter | Default | What it does |
| --- | --- | --- |
| `pollInput` | reads GLFW keyboard, mouse and scroll | Fills `game.input` each frame. |
| `beforeFrame` | nothing | Runs before each frame. |
| `afterLoop` | nothing | Runs once when the window closes, before the engine is disposed. |
| `cursor` | `null` | Returns the pointer shape to show each frame. `null` leaves the cursor alone. |

The app's window must ask for the `vulkan()` or `default()` backend; any other choice fails before the
window opens.

## Show the UI's pointer

An app with UI passes its runtime's cursor, so the pointer turns into a text beam over a text field or
a hand over a link:

```kotlin title="Kotlin"
--8<-- "samples/engine-showcase/src/desktopTest/kotlin/com/awakekt/awake/showcase/docs/vulkan/VulkanDocsSampleTest.kt:cursor"
```

## Turn on validation

The Khronos validation layer checks every Vulkan call and reports misuse. It is off unless you ask for
it, so a shipped app never changes behaviour because a player has the Vulkan SDK installed. Ask with
either:

- the environment variable `AWAKE_VULKAN_VALIDATION=1`, or
- the JVM system property `-Dawake.vulkan.validation=true` on the app's JVM.

`1`, `true`, `yes` and `on` turn it on, in any case. On desktop the engine also reads the setting from
the first of `.env`, `studio.properties` or `awake.properties` in the working directory, or
`~/.awake/studio.env` or `~/.awake/awake.env`. On Android and iOS it reads only the process
environment.

```bash
AWAKE_VULKAN_VALIDATION=1 java -jar your-game.jar
```

The layer must be installed where the Vulkan loader finds it: the Vulkan SDK, or on macOS the
Homebrew validation layers. Only `VK_LAYER_KHRONOS_validation` is enabled; other installed layers
are never added. In this repository, the Gradle `run` and desktop test tasks turn validation on.

Each message prints as a line starting `AWAKE_VERIFY_VALIDATION [<severity>]`, followed by the
message ID and text.

!!! warning "Asked for, but not installed"
    When the layer cannot load, the engine prints
    `Awake/Vulkan: validation was requested but [VK_LAYER_KHRONOS_validation] failed to load; continuing without it`
    and starts without validation.

## Present modes

`presentMode` in the app's `window { }` block is a request; the surface may not offer every mode.

| `PresentMode` | Vulkan mode | Behaviour |
| --- | --- | --- |
| `Auto` (default) | FIFO | Waits for the display. No tearing. |
| `Vsync` | FIFO | The same as `Auto`. |
| `LowLatency` | MAILBOX | Replaces the queued frame instead of waiting. Not always offered by MoltenVK. |
| `NoVsync` | IMMEDIATE, else MAILBOX | Never waits; may tear. |

A mode the surface does not offer falls back to FIFO. At startup the engine prints
`Awake/Vulkan: present mode requested=… selected=…`.

## Platforms

| Platform | Host |
| --- | --- |
| Desktop (JVM) | `runVulkanDesktopGame`, a GLFW window. The Vulkan calls go through AwakeKt's own JNI library, `awake-vulkan`, loaded from `java.library.path` or extracted from the jar into `~/.awake/natives/`. |
| macOS | The desktop host on MoltenVK. Run the JVM with `-XstartOnFirstThread`. |
| Android | `VulkanView`, a `SurfaceView` in `awake:engine:platform`. |
| iOS | `makeVulkanGameViewController(application: VulkanEngine)`, a `UIViewController` on MoltenVK. |

The backend does not run in a browser; use the [WebGPU backend](webgpu.md) there.

## Render without a window

On desktop, `vulkanHeadlessScene(width, height)` and `vulkanHeadlessUi(width, height)` return a
`HeadlessRenderSession` that renders offscreen. The engine's own pixel tests use them, and so can
yours.

## How it works

`VulkanEngine(appLifecycle, plan)` owns the GPU side of an app: it creates the device, swapchain and
the plan's pipelines, and tears them down. The app's own behaviour comes from its lifecycle. Shaders
arrive as WGSL and are compiled to SPIR-V at load with the built-in naga compiler; see
[Shader compilation](shader-compilation.md).

The engine picks a discrete GPU first, then integrated, virtual, and finally a CPU implementation. It
fails with "Cannot find suitable gpu!" when there is none.

## Debugging

- Turn on validation, as above, and read the `AWAKE_VERIFY_VALIDATION` lines.
- Check the `present mode requested=… selected=…` line when frame times look capped at the refresh
  rate.

## See also

- [Render plans and shaders](shaders.md) for the plan the engine builds.
- [WebGPU backend](webgpu.md) for the browser.
