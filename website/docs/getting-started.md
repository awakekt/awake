# Installation

Awake is published to Maven Central. Use one Awake version across every module; the version shown
here follows the selected documentation version.

## Add the desktop runtime

Add the version and base artifacts to `gradle/libs.versions.toml`:

```toml
[versions]
awake = "{{ awake_version }}"

[libraries]
awake-bootstrap = { group = "com.awakekt.awake.engine", name = "bootstrap", version.ref = "awake" }
awake-shaders = { group = "com.awakekt.awake.asset", name = "shaders", version.ref = "awake" }
awake-vulkan = { group = "com.awakekt.awake.backend", name = "vulkan", version.ref = "awake" }
```

Add dependencies to the source sets that use them:

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

## Create an application

Define the lifecycle in shared code. This window configuration is extracted from the compiled
bootstrap tests:

```kotlin
import com.awakekt.awake.engine.bootstrap.dsl.app

--8<-- "awake/engine/bootstrap/src/commonTest/kotlin/com/awakekt/awake/engine/bootstrap/AppLifecycleDslTest.kt:desktop-app-window"
```

On desktop, pass the lifecycle and a render plan to `runVulkanDesktopGame` from `desktopMain`.
For the complete compiled application entry point, see the
[Engine Showcase](https://github.com/awakekt/awake/blob/main/samples/engine-showcase/src/desktopMain/kotlin/com/awakekt/awake/showcase/app/Main.kt).

## Add feature modules

Add the relevant alias to the `[libraries]` section above and use it from the indicated source set.
Gradle resolves each module's published transitive dependencies.

### Core Math and ECS

```toml
awake-math = { group = "com.awakekt.awake.core", name = "math", version.ref = "awake" }
awake-ecs = { group = "com.awakekt.awake", name = "ecs", version.ref = "awake" }
```

Use `libs.awake.math` and/or `libs.awake.ecs` in `commonMain`.

### Scene authoring

```toml
awake-scene-authoring = { group = "com.awakekt.awake.scene", name = "authoring", version.ref = "awake" }
awake-scene-3d = { group = "com.awakekt.awake.scene", name = "scene3d", version.ref = "awake" }
awake-navigation = { group = "com.awakekt.awake", name = "navigation", version.ref = "awake" }
awake-ai = { group = "com.awakekt.awake", name = "ai", version.ref = "awake" }
awake-scene-world = { group = "com.awakekt.awake.scene", name = "world", version.ref = "awake" }
```

Use `libs.awake.scene.authoring` in `commonMain`. This artifact brings its public scene API
dependencies. Add the `scene3d`, `navigation`, `ai`, or `scene.world` alias only when you use that
capability directly.

### Graphics backends

The desktop Vulkan artifact is included in the base setup. For WasmJs, add this alias and use
`libs.awake.webgpu` in `wasmJsMain`:

```toml
awake-webgpu = { group = "com.awakekt.awake.backend", name = "webgpu", version.ref = "awake" }
```

For shader authoring, add `libs.awake.asset.shader.dsl` using this alias:

```toml
awake-asset-shader-dsl = { group = "com.awakekt.awake.asset", name = "shader-dsl", version.ref = "awake" }
```

### Physics

```toml
awake-physics-api = { group = "com.awakekt.awake.physics", name = "api", version.ref = "awake" }
awake-jolt = { group = "com.awakekt.awake.backend", name = "jolt", version.ref = "awake" }
```

Use `libs.awake.physics.api` in shared code and `libs.awake.jolt` only on supported native targets.

### UI

```toml
awake-compose-foundation = { group = "com.awakekt.awake.compose", name = "foundation", version.ref = "awake" }
awake-ui-shadcn = { group = "com.awakekt.awake.ui", name = "shadcn", version.ref = "awake" }
```

Use `libs.awake.compose.foundation` and `libs.awake.ui.shadcn` in `commonMain`. Shadcn depends on
the foundation APIs. See [Releases and compatibility](releases.md) for target availability and
the exact published module set.

### Terrain assets

```toml
awake-terrain = { group = "com.awakekt.awake.asset", name = "terrain", version.ref = "awake" }
```

Use `libs.awake.terrain` in the source set that creates terrain data.
