# Awake App Platform

`awake:engine:platform` is the UI-free, backend-neutral application contract. It turns a window
host's frame callbacks into a portable application lifecycle; Vulkan and WebGPU supply concrete
`GraphicsEngine` subclasses.

## Owns

- `AppFrame`, `AppLifecycle`, and `AwakeAppLifecycle`
- `GraphicsEngine` and its backend-resource lifecycle
- `WindowConfig`, backend selection, `Input`, and typed app services
- `AppSpec`, `AppSpecBuilder`, `AppInstaller`, and `AppModule` contracts

It does not own a `World`, scene scheduling, Compose UI, editor behavior, Vulkan/WebGPU driver
objects, or game policy.

## Composition boundary

```text
engine:platform   frame/lifecycle/input/window/services; no UI
engine:bootstrap  authored app/module composition DSL
engine:compose     optional ComposeHost integration module
compose:ui          retained Compose UI runtime
scene:*            optional ECS scene-session feature
```

An application that has no UI or no ECS scene can still use Platform. Do not add `ComposeHost` or
scene-specific state here: the composed application root installs those optional features.

## Key types

- `AppLifecycle` receives `ready(renderer)`, `update(frame)`, resize, pause, resume, and dispose.
- `AppFrame` carries the frame delta, viewport, input snapshot, and display density.
- `AwakeAppLifecycle` is one running application session with window configuration and services.
- `GraphicsEngine` owns backend setup, per-frame handoff, idle-before-dispose ordering, and
  backend teardown.
- `AppModule` is a reusable feature installer; it does not own window configuration.

Most consumers use the authoring DSL from
[`awake:engine:bootstrap`](../bootstrap/README.md), rather than constructing an `AppSpecBuilder`
directly.

## Planned direction

The [scene-session simplification plan](../../../docs/tasks/2026-08-25-scene-session-simplification-plan.md)
keeps Platform UI-free. `awake:engine:compose` owns the optional Compose host and root content,
while `SceneSession` becomes an installed feature rather than a second application host.

## Related modules

- [`awake:engine:bootstrap`](../bootstrap/README.md) — `app {}` and `appModule {}` composition.
- [`awake:engine:compose`](../compose/README.md) — optional `composeAppModule` integration.
- [`awake:scene`](../../scene/README.md) — ECS scene facade and session/runtime APIs.
- `awake:compose:ui` — retained Compose UI runtime used by `engine:compose`.
- `awake:engine:render:contract` — `Renderer` and render vocabulary used by lifecycle setup.
