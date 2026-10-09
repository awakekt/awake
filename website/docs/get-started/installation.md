# Installation

<p class="awake-lede">Add AwakeKt Engine to a Kotlin Multiplatform project: what to install first, the version catalog entries, and which Core and Vulkan versions go together.</p>

## Before you start

- **Kotlin 2.4.10** with the Kotlin Multiplatform Gradle plugin. AwakeKt is built with this version.
- **JDK 17 or newer** for the desktop target.
- **Vulkan and GLFW on the desktop.** The Vulkan backend's jar carries AwakeKt's native library for
  macOS (arm64 and x86_64) and Linux (x86_64). That library links against the system Vulkan loader
  and GLFW, so those must be installed:
    - macOS: `brew install vulkan-loader molten-vk glfw`. MoltenVK runs Vulkan on Metal.
    - Linux: a Vulkan driver and loader, and GLFW 3 from your distribution.

!!! warning "Windows"
    The desktop native library is not built for Windows yet. A Windows app fails at startup with a
    "not found for platform" error.

## Add the version catalog entries

Add these to `gradle/libs.versions.toml`:

```toml title="gradle/libs.versions.toml"
[versions]
awake = "{{ awake_vulkan_core_version }}"
awake-vulkan = "{{ awake_vulkan_version }}"

[libraries]
awake-engine-bootstrap = { module = "com.awakekt.awake.engine:bootstrap", version.ref = "awake" }
awake-asset-shader-pack = { module = "com.awakekt.awake.asset:shader-pack", version.ref = "awake" }
awake-scene-authoring = { module = "com.awakekt.awake.scene:authoring", version.ref = "awake" }
awake-backend-vulkan = { module = "com.awakekt.awake.backend:vulkan", version.ref = "awake-vulkan" }
```

| Alias | What it gives you | Source set |
| --- | --- | --- |
| `awake-engine-bootstrap` | The `app { }` DSL: window, lifecycle hooks, modules. | `commonMain` |
| `awake-asset-shader-pack` | Ready-made shaders (`PackShaderSets`) and the `RenderPlan` types. | `commonMain` |
| `awake-scene-authoring` | The scene DSL, ECS, scene documents and the scene runtime. | `commonMain` |
| `awake-backend-vulkan` | The Vulkan backend and `runVulkanDesktopGame`. | `desktopMain` |

`awake-scene-authoring` already brings `awake-engine-bootstrap` and `awake-asset-shader-pack` with it. They are
listed on their own because [Your first window](first-window.md) needs only those two.

## Add the dependencies

```kotlin title="build.gradle.kts"
kotlin {
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(libs.awake.engine.bootstrap)
            implementation(libs.awake.asset.shader.pack)
            implementation(libs.awake.scene.authoring)
        }
        named("desktopMain").dependencies {
            implementation(libs.awake.backend.vulkan)
        }
    }
}
```

The browser backend is added separately, in [Run in the browser](run-in-the-browser.md).

## Core and Vulkan versions

Every Core module, everything except the Vulkan backend, shares one version: `awake` above. Keep
all of them on that version.

The Vulkan backend has its own release train, and each Vulkan release is built against one exact
Core release. The catalog above pairs Vulkan `{{ awake_vulkan_version }}` with the Core release it
was built against, `{{ awake_vulkan_core_version }}`. Keep that pair for a desktop app. A
newer Core next to an older Vulkan release is a combination nobody has tested.

The newest Core release is `{{ awake_version }}`. When it is newer than the pair, an app that runs
only in the browser can use it; a desktop app waits for a Vulkan release built on it. See
[Releases and compatibility](../reference/releases.md).

## Android release builds

An Android release shrunk by R8 (`isMinifyEnabled = true`) needs no keep rules of its own for Awake.
The Jolt and Vulkan backends and the shader compiler ship consumer rules in their Android artifacts,
which keep the classes, fields and methods their native code finds by name.

## Next

[Your first window](first-window.md) opens a window with the app DSL and the Vulkan backend.
