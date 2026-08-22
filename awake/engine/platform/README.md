# Awake Engine Platform

The backend-neutral application contract for [Awake](../../../README.md): what a frame is, what an
app's own behavior looks like, and the bootstrap that drives one. Holds no Vulkan or WebGPU
dependency — each backend supplies a `GraphicsEngine` subclass instead (see
[`:awake:engine:app`](../app/README.md)).

## Installation

```kotlin
implementation(project(":awake:engine:platform"))
```

## Key Primitives

- `AppLifecycle` — the app's own behavior: `ready(renderer)`, `update(frame)`, `resize`, `pause`,
  `resume`, `dispose`. Injected into the engine rather than inherited from it.
- `AppFrame` — everything one frame carries: `delta`, `viewportWidth`/`viewportHeight`, and this
  frame's `InputSnapshot`. Pushed to `update`, so nothing reaches into ambient state for the
  frame it is already being called about.
- `AwakeAppLifecycle` — one running session: an `AppLifecycle` plus its `WindowConfig` and
  service registry. Owns the session's `Input` accumulator.
- `GraphicsEngine` — the template-method bootstrap. Owns the frame loop, the renderer handle and
  teardown ordering; each backend implements `createBackendResources`/`destroyBackend`.
- `AppSpec` / `AppSpecBuilder` — the immutable spec a session is built from, and its builder.
  `AppInstaller`/`AppModule` let a feature register itself into one.
- `FrameStats` — fps and frame-time tracking, including p50/p95/p99.

## Usage Example

```kotlin
val lifecycle = AppSpecBuilder().apply {
    window { title = "My App"; size(1280, 720) }
    ready { renderer -> /* load meshes, materials */ }
    render { frame -> /* frame.delta, frame.input, ... */ }
}.build().createLifecycle()
```

Most code should not build a spec by hand — [`:awake:engine:bootstrap`](../bootstrap/README.md)'s
`app { }` DSL wraps this.

## Related Modules

- [`:awake:engine:app`](../app/README.md) — `AwakeApplication`, the backend-bound host
  (Vulkan/WebGPU) built on `GraphicsEngine`.
- [`:awake:engine:bootstrap`](../bootstrap/README.md) — the `app { }` / `module { }` authoring
  DSLs over `AppSpecBuilder`.
- [`:awake:scene`](../../scene/README.md) — `SceneAppLifecycleRuntime` (in `:awake:scene:runtime`)
  is the ECS scene implementation of `AppLifecycle`.
- [`:awake:engine:render:contract`](../render/contract/README.md) — `Renderer` and the render
  vocabulary types this module exposes.
