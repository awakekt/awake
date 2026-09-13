<h1 align="center">Awake Engine</h1>
<p align="center">A code-first Kotlin Multiplatform engine for building interactive 2D and 3D applications.</p>

<p align="center">
  <a href="https://github.com/awakekt/awake/releases/tag/v0.1.0-alpha.2"><img src="https://img.shields.io/badge/Release-v0.1.0--alpha.2-blue.svg" alt="Release v0.1.0-alpha.2"></a>
  <a href="https://github.com/awakekt/awake/actions/workflows/build-and-publish.yml"><img src="https://github.com/awakekt/awake/actions/workflows/build-and-publish.yml/badge.svg" alt="Build and publish"></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-2.4.10-blue.svg?logo=kotlin" alt="Kotlin"></a>
  <a href="https://opensource.org/licenses/Apache-2.0"><img src="https://img.shields.io/badge/License-Apache_2.0-blue.svg" alt="Apache 2.0 license"></a>
</p>

Awake gives Kotlin applications a small, composable runtime for scenes, rendering, physics, and
retained UI. Author your application in Kotlin, keep platform code at the edge, and share the same
game or tool code across desktop, mobile, and the web.

> Awake is early alpha software. APIs and published artifacts can change between releases.

## Why Awake

- **Kotlin Multiplatform** — share application and scene code across supported targets.
- **Code-first scenes** — author entities, systems, assets, and lifecycle in Kotlin.
- **Composable runtime** — combine ECS, rendering, physics, input, and UI as needed.
- **Multiple backends** — Vulkan for native targets and WebGPU for the web.
- **Native UI runtime** — build retained UI with Awake's own UI and Shadcn component modules.

## Try the examples

Clone the repository and run the engine showcase:

```bash
./gradlew :samples:engine-showcase:run
```

The repository also includes a [UI component showcase](samples/ui-showcase/). See the
[engine showcase README](samples/engine-showcase/README.md) for the available demos and web
commands.

## Install

Awake artifacts are published to Maven Central. The latest version currently available there is
`0.1.0-alpha.1`; add it and only the modules your application uses to `gradle/libs.versions.toml`:

```toml
[versions]
awake = "0.1.0-alpha.1"

[libraries]
awake-bootstrap = { group = "com.awakekt.awake.engine", name = "bootstrap", version.ref = "awake" }
awake-shaders = { group = "com.awakekt.awake.asset", name = "shaders", version.ref = "awake" }
awake-vulkan = { group = "com.awakekt.awake.backend", name = "vulkan", version.ref = "awake" }
```

For a desktop JVM target, use the lifecycle and shared shader contract in `commonMain`, and the
Vulkan host in `desktopMain`:

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

Create an application with the bootstrap DSL:

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

The [getting started guide](website/docs/getting-started.md) shows the desktop host and render-plan
setup. The [engine showcase](samples/engine-showcase/README.md) is the complete working example.

## Choose the pieces you need

| Area | Modules | Purpose |
|---|---|---|
| Runtime | `engine:bootstrap`, `engine:platform` | Application lifecycle and platform startup |
| ECS | `ecs` | Entity storage, components, and systems |
| Scenes | `scene:scene-core`, `scene:scene3d`, `scene:authoring` | Transforms, cameras, lights, and scene DSL |
| Rendering | `engine:render:*`, `backend:vulkan`, `backend:webgpu` | Backend-neutral passes and platform backends |
| Content | `asset:gltf`, `asset:shaders`, `asset:shader-pack` | Models, textures, and shader definitions |
| Physics | `physics:api`, `backend:jolt` | Physics contracts and Jolt integration |
| UI | `ui:ui-core`, `ui:headless`, `ui:shadcn` | Retained UI, controls, styling, and components |

You do not need every module. Start with the smallest runtime for your target, then add scene,
rendering, physics, asset, or UI modules as your application grows. The [module guide](awake/README.md)
contains the complete module map.

## Supported targets

Awake is designed for Kotlin Multiplatform projects targeting:

- Desktop JVM
- Android
- iOS
- WebAssembly/browser

Backend and platform availability is still expanding during the alpha releases. Use the examples
and [release notes](https://github.com/awakekt/awake/releases) as the compatibility reference for
the version you are using.

## Documentation

- [Getting started](website/docs/getting-started.md)
- [Documentation index](docs/README.md)
- [Module guide](awake/README.md)
- [Release process](docs/release-process.md)
- [Architecture decisions and references](docs/reference/)

## Contributing

Issues and pull requests are welcome on [GitHub](https://github.com/awakekt/awake). Please read the
repository's contribution and release guidance before making a change.

Awake is available under the [Apache License 2.0](LICENSE.md).
