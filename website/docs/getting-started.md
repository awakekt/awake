# Getting Started

The smallest published desktop setup uses Awake's bootstrap and Vulkan modules. Awake owns the
GLFW window and frame loop; your application supplies its lifecycle and render plan.

## Installation

Add the following to your `libs.versions.toml`:

```toml
[versions]
awake = "{{ awake_version }}"

[libraries]
awake-bootstrap = { group = "com.awakekt.awake.engine", name = "bootstrap", version.ref = "awake" }
awake-shaders = { group = "com.awakekt.awake.asset", name = "shaders", version.ref = "awake" }
awake-vulkan = { group = "com.awakekt.awake.backend", name = "vulkan", version.ref = "awake" }
```

For a JVM desktop target:

```kotlin
kotlin {
    jvm("desktop")
    sourceSets {
        commonMain.dependencies {
            implementation(libs.awake.bootstrap)
            implementation(libs.awake.shaders)
        }
        named("desktopMain").dependencies {
            implementation(libs.awake.vulkan)
        }
    }
}
```

## Creating an Application

Define the lifecycle in `commonMain`, then launch it from `desktopMain`:

```kotlin
// commonMain
val game = app {
    window {
        title = "My Awake App"
        size(1280, 720)
        backend.vulkan()
    }
}
```

The `app { }` block is shared code. A desktop entry point calls
`runVulkanDesktopGame(game, renderPlan)` from `desktopMain`; the render plan is the application’s
shader and pipeline declaration.

Apps that need custom backend construction can use the `applicationFactory` overload instead.

For scenes, physics, or UI, add the corresponding published feature modules and install them in the
same application root. These capabilities remain optional.

The repository’s compiler-checked showcase entry point is
[`EngineShowcaseApp.kt`](https://github.com/awakekt/awake/blob/main/samples/engine-showcase/src/commonMain/kotlin/com/awakekt/awake/showcase/app/EngineShowcaseApp.kt).

Use [Releases](releases.md) to select a compatible artifact version before adding more modules.
