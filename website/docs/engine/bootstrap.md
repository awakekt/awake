# Application bootstrap

`awake:engine:bootstrap` provides the application DSL used to describe a window, backend choice,
modules, and per-frame work.

## Minimal application

Add `awake:engine:bootstrap` to shared code and a backend artifact to the platform source set as
shown in [Installation](../getting-started.md#installation). This window setup is extracted from
the compiled bootstrap test suite:

```kotlin
--8<-- "awake/engine/bootstrap/src/commonTest/kotlin/com/awakekt/awake/engine/bootstrap/AppLifecycleDslTest.kt:desktop-app-window"
```

The lifecycle is shared code. A platform entry point supplies the matching host, such as
`runVulkanDesktopGame` on desktop or the WebGPU launch function in a WasmJs source set.

## What bootstrap owns

- `app { }`, `appSpec { }`, and `appModule { }` composition.
- Window configuration and backend selection.
- Lifecycle hooks consumed by platform hosts.

Rendering, scene systems, physics, and UI remain optional modules.
