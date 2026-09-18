# Application bootstrap

`awake:engine:bootstrap` provides the application DSL used to describe a window, backend choice,
modules, and per-frame work.

## Minimal application

```kotlin
import com.awakekt.awake.engine.bootstrap.dsl.app

val game = app {
    window {
        title = "My Awake App"
        size(1280, 720)
        backend.vulkan()
    }
}
```

The lifecycle is shared code. A platform entry point supplies the matching host, such as
`runVulkanDesktopGame` on desktop or the WebGPU launch function in a WasmJs source set.

## What bootstrap owns

- `app { }`, `appSpec { }`, and `appModule { }` composition.
- Window configuration and backend selection.
- Lifecycle hooks consumed by platform hosts.

Rendering, scene systems, physics, and UI remain optional modules.
