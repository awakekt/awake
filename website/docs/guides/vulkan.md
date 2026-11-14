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

The `plan` and `application` overloads also take `windowOptions`, a `DesktopWindowOptions` from
`awake:engine:window`, for how the window opens.

The app's window must ask for the `vulkan()` or `default()` backend; any other choice fails before the
window opens.

## Follow the display scale

On desktop the UI density, framebuffer pixels per dp, follows the OS display scale of the monitor the
window is on, as a browser's `devicePixelRatio` does on the web, so the two builds lay the UI out alike.

| Display | Density |
| --- | --- |
| Windows at 100%, 125%, 150% | 1, 1.25, 1.5 |
| macOS, a Retina panel | 2 |
| Linux X11, `Xft.dpi: 120` or `144` | 1.25 or 1.5 |
| Linux Wayland | the framebuffer's scale |

The density changes while the app runs when the window moves to a monitor with another scale, or when
the user changes the scale (Windows' `WM_DPICHANGED`); the next frame lays out at the new density. Text
stays sharp at a fractional density: its em rounds to whole physical pixels and each line snaps to the
pixel grid, so 12 sp at 125% is 15 pixels drawn 1:1. The window keeps the size `WindowConfig` asks for in
pixels; only the UI inside it scales.

To draw the UI at native pixels instead, one framebuffer pixel per dp on every display:

```kotlin
runVulkanDesktopGame(
    game,
    plan,
    windowOptions = desktopWindowOptions { displayScale = DesktopDisplayScale.Unscaled },
)
```

A capture run (`-Dawake.capture`) has no window and keeps density 1.

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
| Android | `AwakeSurfaceView`, a `SurfaceView` in `awake:engine:window`. |
| iOS | `makeVulkanGameViewController(application: VulkanEngine)`, a `UIViewController` on MoltenVK. |

The backend does not run in a browser; use the [WebGPU backend](webgpu.md) there.

## Render without a window

On desktop, `vulkanHeadlessScene(width, height)` and `vulkanHeadlessUi(width, height)` return a
`HeadlessRenderSession` that renders offscreen. The engine's own pixel tests use them, and so can
yours.

## Check a build in CI

A CI job can run your game as it ships, such as its obfuscated release, to check that it starts and
draws. The game needs no code for it: `runVulkanDesktopGame` reads two system properties.

| Property | What it does |
| --- | --- |
| `-Dawake.frames=120` | Plays 120 frames in the window, then returns. |
| `-Dawake.capture=frame.png` | Plays with no window and writes the last frame as a PNG. It plays `awake.frames` frames, or 60 if that isn't set. It needs a Vulkan driver but no display. |

Either run fails if the engine never starts, with the reason, so a missing driver or native library
can't pass as a short run. In code, `VulkanEngine.playHeadless(width, height, frames)` plays the
same way and returns the last frame's pixels, and `snapshotPng(file, width, height)` also writes them
as a PNG, for a CI screenshot or a picture in the docs. A project plays as it ships when the app
composes it as usual:

```kotlin title="Kotlin"
val game = app { scene("shot") { runProject(loadProject(files)) } }
VulkanEngine(game, ProjectRenderPlan).snapshotPng(File("build/shots/entry.png"), 1280, 720, frames = 60)
```

With no GPU, Mesa's lavapipe is enough. `awake render <scene>` (`awake:project:cli`)
renders one scene from the command line the same way.

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
