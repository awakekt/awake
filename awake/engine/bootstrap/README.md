# Awake App Bootstrap

`awake:engine:bootstrap` is the authored composition layer over
[`awake:engine:platform`](../platform/README.md). It adds ergonomic builders without moving
application lifecycle contracts, window hosting, or backend behavior out of Platform.

## Owns

- `app { }` and `appSpec { }` root builders
- `appModule { }` reusable feature composition
- `appDefinition { }` for applications that need a fresh state object per created session
- DSL markers and window/backend selection syntax

It does not own a renderer, `ComposeHost`, scene session, ECS system schedule, game rules, or
platform launchers.

## Smallest app — no Compose

```kotlin
val lifecycle = app {
    window {
        title = "Simulation app"
        size(1280, 720)
        backend.vulkan()
    }

    render { frame ->
        simulation.step(frame.delta)
    }
}
```

This is one application lifecycle and has no `ComposeHost` or scene. The platform launcher drives
`lifecycle` with window, input, and backend frames.

## Smallest app — with Compose

`engine:bootstrap` stays independent of Compose. Add `:awake:engine:compose` and
`:awake:compose:foundation` to a UI-bearing application, then install one `composeAppModule`.

```kotlin
val lifecycle = app {
    window {
        title = "Compose app"
        size(1280, 720)
        backend.vulkan()
    }

    module(
        composeAppModule(content = {
            Text("Hello, Awake")
        }),
    )
}
```

`composeAppModule` owns the sole host, stages UI input/primitives, and presents a UI-only frame.
For a scene, install `sceneComposeAppModule` before `sceneSession {}`; it stages UI but leaves
presentation to the scene render pass.

Use `app {}` for a normal root, `appModule {}` for a reusable feature, and `appSpec {}` for tests
or tooling that needs the immutable spec. Use `appDefinition {}` only when its state factory is
real application behavior, not as a default wrapper.

## Planned direction

The [scene-session simplification plan](../../../docs/tasks/2026-08-25-scene-session-simplification-plan.md)
will make an optional Compose app module and `SceneSession` install through this composition root.
Platform remains UI-free. Once consumers migrate, duplicate module-to-root convenience paths are
deprecation candidates; no new construction path should be added meanwhile.

## Related modules

- [`awake:engine:platform`](../platform/README.md) — lifecycle and platform contracts.
- [`awake:scene`](../../scene/README.md) — installed ECS scene feature.
- `awake:engine:compose` — optional `composeAppModule` integration surface.
