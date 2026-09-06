# Getting Started

The smallest dependency-only desktop app uses the published engine bootstrap and Vulkan backend.
Awake owns the GLFW window, frame loop, input polling, and standard `VulkanEngine` construction;
your project only supplies the app lifecycle and render plan.

## Installation

Add the following to your `libs.versions.toml`:

```toml
[versions]
awake = "1.0.0-SNAPSHOT"

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

## Creating a Game

Define the lifecycle in `commonMain`, then launch it from `desktopMain`:

```kotlin
// commonMain
val game = app {
    window {
        title = "My Awake App"
        size(1280, 720)
        backend.vulkan()
    }
    render { frame ->
        // Update your game state here.
    }
}
```

```kotlin
// desktopMain
fun main() = runVulkanDesktopGame(game, MyRenderPlan)
```

`MyRenderPlan` is the app's shader and pipeline declaration. Apps that need custom backend
construction can use the existing `applicationFactory` overload instead.

For a scene or Compose UI, add those published feature modules and install them in the same
`app {}` root; they remain optional rather than hidden engine policy.

The repository's executable example is:

```kotlin
--8<-- "samples/engine-showcase/src/commonMain/kotlin/com/awakekt/awake/showcase/app/EngineShowcaseApp.kt"
```

> [!NOTE]
> The example above is pulled directly from the `samples/hello-cube` module, ensuring it always compiles and stays up-to-date with the latest Engine APIs.
