<h1 align="center">Awake Engine</h1>
<p align="center">A code-first Kotlin Multiplatform engine for building interactive 2D and 3D applications.</p>

<p align="center">
  <a href="https://github.com/awakekt/awake/releases"><img src="https://img.shields.io/github/v/release/awakekt/awake?filter=v*&label=Release" alt="Latest Awake Core release"></a>
  <a href="https://github.com/awakekt/awake/actions/workflows/build-and-publish.yml"><img src="https://github.com/awakekt/awake/actions/workflows/build-and-publish.yml/badge.svg" alt="Build and publish"></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-2.4.20-blue.svg?logo=kotlin" alt="Kotlin"></a>
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

## Awake Studio

[Awake Studio](https://studio.awakekt.com/) is the visual editor for Awake Engine. It edits 2D and
3D scenes, game UI, node-based logic and shader graphs without code, and publishes games that play
in the browser. [Open it in the browser](https://studio.awakekt.com/web/),
[download it](https://studio.awakekt.com/download/), or read the
[user guide](https://studio.awakekt.com/docs/).

Studio is a separate product built on Awake. This repository stays code-first and free under
Apache-2.0: you never need Studio to use the engine, and the scene documents it saves are plain
files the engine loads.

## Try the examples

Clone the repository and run the engine showcase:

```bash
./gradlew :samples:engine-showcase:run
```

The repository also includes a [UI component showcase](samples/ui-showcase/). See the
[engine showcase README](samples/engine-showcase/README.md) for the available demos and web
commands.

## Install

Awake artifacts are published to Maven Central. Core modules share one version (latest
`0.16.0`); the Vulkan backend has its own release train (latest `0.1.33`, built against Core
`0.16.0`). Add only the modules your application uses to `gradle/libs.versions.toml`:

```toml
[versions]
awake = "0.16.0"
awake-vulkan = "0.1.33"

[libraries]
awake-bootstrap = { group = "com.awakekt.awake.engine", name = "bootstrap", version.ref = "awake" }
awake-shaders = { group = "com.awakekt.awake.asset", name = "shaders", version.ref = "awake" }
awake-vulkan = { group = "com.awakekt.awake.backend", name = "vulkan", version.ref = "awake-vulkan" }
```

The WebGPU backend is not in Maven Central releases yet: it builds on a wgpu4k snapshot, so it is
published only as a snapshot to `https://central.sonatype.com/repository/maven-snapshots/`, and its
wgpu4k builds (`io.ygdrasil`) come from `https://awakekt.github.io/awake/`, which hosts the exact
builds it was tested with. Pin it to your Core version with `-SNAPSHOT` appended. Every Core release after 0.8.5 publishes that build
of WebGPU from the release commit, with every Core module held at the release. For 0.8.5 and
earlier, `<core>-SNAPSHOT` is a plain `main` snapshot whose Core modules are at `<core>-SNAPSHOT`
and are not held, so a web build that must stay on one Core needs a later release.

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
| Runtime | `engine:bootstrap`, `engine:platform`, `engine:compose` | Application lifecycle, platform startup, UI host |
| ECS | `ecs` | Entity storage, components, and systems |
| Scenes | `scene:scene-core`, `scene:scene3d`, `scene:authoring`, `scene:document`, `scene:runtime` | Transforms, cameras, lights, scene DSL and documents |
| Rendering | `engine:render:*`, `backend:vulkan`, `backend:webgpu` | Backend-neutral passes and platform backends |
| Content | `asset:gltf`, `asset:shaders`, `asset:shader-pack`, `asset:shader-document`, `asset:terrain`, `terrain`, `kit:terrain-layers` | Models, textures, shaders, and terrain |
| Physics | `physics:api`, `backend:jolt` | Physics contracts and Jolt integration |
| UI | `compose:runtime`, `compose:ui`, `compose:foundation`, `ui:shadcn` | Retained Compose-shaped UI and shadcn components |
| Projects | `project`, `node-graph`, `editor:contract` | Project manifests, node graphs, editor plugin contracts |

You do not need every module. Start with the smallest runtime for your target, then add scene,
rendering, physics, asset, or UI modules as your application grows. The [module guide](awake/README.md)
contains the complete module map.

## Supported targets

Awake is designed for Kotlin Multiplatform projects targeting:

- Desktop JVM
- Android
- iOS
- WebAssembly/browser

Desktop uses Vulkan (MoltenVK on macOS and iOS), Android uses Vulkan, and the browser uses
WebGPU. Backend and platform availability is still expanding during the alpha releases. Use the examples
and [release notes](https://github.com/awakekt/awake/releases) as the compatibility reference for
the version you are using.

## Documentation

- [Online documentation](https://docs.awakekt.com/) and [live samples](https://demo.awakekt.com/)
- [Getting started](website/docs/getting-started.md)
- [Documentation index](docs/README.md)
- [Module guide](awake/README.md)
- [Release process](docs/release-process.md)
- [Architecture decisions and references](docs/reference/)
- [AwakeKt brand system](docs/reference/brand-system.md)
- [`llms.txt`](https://awakekt.com/llms.txt), a plain-text index of the docs for AI tools

## Contributing

Issues and pull requests are welcome on [GitHub](https://github.com/awakekt/awake). Please read the
[contribution guide](CONTRIBUTING.md) and [release guidance](docs/release-process.md) before making
a change. Security issues should follow the [security policy](SECURITY.md).

Awake is available under the [Apache License 2.0](LICENSE.md). Third-party components keep their own
licenses, listed in [NOTICE.md](NOTICE.md): the Roboto font, and the UI font atlases drawn from it,
are under the [SIL Open Font License 1.1](LICENSES/OFL-1.1.txt).
The AwakeKt and Awake names, and the Ember Signal mark, are governed separately by the
[trademark notice](TRADEMARKS.md).
