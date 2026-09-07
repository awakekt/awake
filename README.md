<h1 align="center">Awake Engine</h1>
<p align="center">Kotlin Multiplatform first, Vulkan and WebGPU native — a 3D/2D game engine and graphics runtime for developers who want to feel every frame they write.</p>

<p align="center">
  <a href="https://github.com/awakekt/awake/actions/workflows/build-and-publish.yml"><img src="https://github.com/awakekt/awake/actions/workflows/build-and-publish.yml/badge.svg" alt="Build And Publish"></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-2.4.10-blue.svg?logo=kotlin" alt="Kotlin"></a>
  <a href="https://www.jetbrains.com/lp/compose-multiplatform/"><img src="https://img.shields.io/badge/Compose_Multiplatform-1.11.1-purple.svg" alt="Compose Multiplatform"></a>
  <a href="https://opensource.org/licenses/Apache-2.0"><img src="https://img.shields.io/badge/License-Apache_2.0-blue.svg" alt="License"></a>
  <a href="https://www.patreon.com/awakekt"><img src="https://img.shields.io/badge/donate-Patreon-f96854.svg?logo=patreon" alt="Patreon"></a>
</p>

---

## What It Does

### Graphics & Rendering

- **Multi-Backend Renderer** — Vulkan (Desktop JVM, Android, iOS via MoltenVK) and WebGPU (
  Web/Wasm).
- **Modern Shader & Mesh Pipeline** — Runtime WGSL shader compilation via Naga, cascaded shadows,
  PBR materials, and glTF GPU skeletal animation.

### Engine & Project Runtime

- **Sparse-Set ECS Runtime** — Cache-friendly entity iteration, archetype queries, and
  zero-allocation 3D transform math.
- **Formal Project System & Standalone Runtime** — Typed `awake.project.json` specification with
  SemVer compatibility gates and standalone `AwakeProjectLauncher` execution without editor
  overhead.
- **Microkernel Plugin Architecture** — `GamePlugin` (runtime systems) and `EditorPlugin` (Shadcn
  studio panels) SPIs allow modular engine extensions.

### Declarative UI & Studio IDE

- **Compose Multiplatform UI Stack** — Declarative UI powered by Compose runtime, custom design
  tokens, and a complete Shadcn component suite.
- **Studio IDE & Hierarchical Outliner** — Interactive 3D editor (`app:studio`) featuring expandable
  file trees, path breadcrumb navigation, live scene inspection, and gizmos.

### Gameplay, Physics & AI

- **Jolt Physics Engine** — Hardware-accelerated physics bridge with static heightfield colliders,
  raycasts, and kinematic character controllers.
- **Behavior Tree AI & Keybindings** — 60fps code-first AI runtime with sequences/selectors, plus
  type-safe `KeybindingProfile<A>` controls.
- **Modular Character Equipment & Sockets** — Skinned mesh modular slot composition (
  `ModularCharacterComponent`) and zero-allocation bone socket tracking.

---

## Quickstart

Run **Awake Studio** (interactive 3D editor with live scene switching, glTF previewing, and ECS
inspector):

```bash
# Desktop JVM Target
./gradlew :app:studio:run

# Web Target (Chrome/Edge 113+ with WebGPU)
./gradlew :app:studio:wasmJsBrowserDevelopmentRun
```

Run the **UI Showcase Gallery** (complete catalog of Compose Shadcn components):

```bash
./gradlew :samples:ui-showcase:run
```

---

## Code-First Scene Authoring

### Option A: Pure 3D Engine (Without Compose UI)

For high-performance 3D games, simulations, or dedicated headless servers that do not require a UI
overlay:

```kotlin
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.scene.authoring.dsl.scene
import com.awakekt.awake.scene.authoring.dsl.sun

fun main() = app {
    window("Awake 3D Game (Engine Only)", 1280, 720)

    scene {
        defaultOrbitCamera()

        entity("rotating_cube") {
            with(SpinControl(speedX = 0.5f, speedY = 1.0f))
            mesh(generate { cube(size = 2f, colored = true) })
        }

        sun()
    }
}
```

### Option B: 3D Scene + Declarative HUD (With Compose UI)

For games requiring in-game HUDs, health bars, inventory panels, or editor overlays:

```kotlin
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.scene.authoring.dsl.scene
import com.awakekt.awake.scene.authoring.dsl.sun
import com.awakekt.awake.scene.authoring.dsl.ui
import com.awakekt.awake.ui.shadcn.components.ShadcnButton
import com.awakekt.awake.ui.shadcn.components.ShadcnText

fun main() = app {
    window("Awake 3D Game + Shadcn HUD", 1280, 720)

    scene {
        defaultOrbitCamera()
        sun()

        // Declarative Compose UI Overlay on top of the 3D scene
        ui {
            ShadcnButton(onClick = { println("HUD Action Triggered") }) {
                ShadcnText("Start Game")
            }
        }
    }
}
```

### Standalone Project Execution (`AwakeProjectLauncher`)

Run a game project directly from its `awake.project.json` manifest without editor or Studio memory
footprint:

```kotlin
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.runtime.project.AwakeProjectLauncher

fun main(args: Array<String>) {
    val projectJson = File("awake.project.json").readText()
    val sceneJson = File("scenes/main.scene.json").readText()

    val config = AwakeProjectLauncher.parseConfig(projectJson)
    val world = World()

    // Instantiates default scene & installs declared game plugins (e.g., Awake Pro)
    val scene = AwakeProjectLauncher.loadScene(world, sceneJson)
}
```

### Plugin Architecture & Setup (`GamePlugin`)

Awake's microkernel architecture allows engine and game extensions (such as commercial **Awake Pro** plugins like `physics-ragdoll` or `worldstream`) to install systems and component bindings cleanly.

#### 1. Declare Plugins in `awake.project.json`
List required plugin IDs in your project manifest:

```json
{
  "name": "MyAwakeGame",
  "id": "com.example.mygame",
  "defaultScene": "scenes/main.scene.json",
  "plugins": [
    "com.awakekt.pro.physics-ragdoll",
    "com.awakekt.pro.worldstream"
  ]
}
```

#### 2. Implement or Register `GamePlugin`

```kotlin
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.plugin.GamePlugin
import com.awakekt.awake.scene.core.plugin.PluginId
import com.awakekt.awake.scene.core.plugin.PluginMetadata

class CustomGameplayPlugin : GamePlugin {
    override val metadata = PluginMetadata(
        id = PluginId("com.example.custom-gameplay"),
        displayName = "Custom Gameplay Systems",
        version = "1.0.0",
    )

    override fun install(world: World) {
        // Register custom ECS systems and component bindings
        world.addSystem(CustomMovementSystem())
    }
}
```

#### 3. Execute Plugins in `AwakeProjectLauncher`

```kotlin
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.plugin.GamePluginRegistry
import com.awakekt.awake.scene.runtime.project.AwakeProjectLauncher

fun main() {
    val world = World()
    val registry = GamePluginRegistry()

    // Register plugin instances
    registry.register(CustomGameplayPlugin())

    // Instantiates scene and installs declared plugins before scene deserialization
    val scene = AwakeProjectLauncher.loadScene(world, sceneJson, registry)
}
```

## Gradle Plugin Setup (`awake { ... }`)

Awake provides a unified Gradle convention plugin `awake.app-convention` that exposes a type-safe `awake { ... }` extension DSL for application runners, XCFramework exports, MoltenVK native linking, and test environments:

```kotlin
// build.gradle.kts
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("awake.app-convention")
}

awake {
    // Configures the desktop application runner ("run" task)
    desktopApp {
        mainClass = "com.example.mygame.MainKt"
        description = "Run My Awake Game."
    }

    // Configures XCFramework export & MoltenVK static native linking for iOS
    xcframework("MyGameFramework") {
        includeMoltenVK = true
    }

    // Configures desktopTest execution environment
    test {
        useVulkanNatives = true
        useNagaShaders = true
        exclusiveGpu = true
    }
}
```

---

## Dependency Selection Guide

Add the core mandatory modules and optional feature extensions to your `build.gradle.kts`:

```kotlin
dependencies {
    // Core Engine (Mandatory for 3D Games)
    implementation("com.awakekt.awake.core:math:<version>")
    implementation("com.awakekt.awake:ecs:<version>")
    implementation("com.awakekt.awake.scene:scene-core:<version>")
    implementation("com.awakekt.awake.engine:bootstrap:<version>")

    // Optional Rendering Backends (Choose Vulkan, WebGPU, or Both)
    implementation("com.awakekt.awake.backend:vulkan:<version>")
    implementation("com.awakekt.awake.backend:webgpu:<version>")

    // Optional Physics Engine
    implementation("com.awakekt.awake.backend:jolt:<version>")

    // Optional Asset Importers & Terrain
    implementation("com.awakekt.awake.asset:gltf:<version>")
    implementation("com.awakekt.awake.asset:terrain:<version>")

    // Optional AI & Navigation Pathfinding
    implementation("com.awakekt.awake:ai:<version>")
    implementation("com.awakekt.awake:navigation:<version>")

    // Optional 3D Audio Subsystem
    implementation("com.awakekt.awake.core:audio:<version>")

    // Optional Compose UI Overlay Stack
    implementation("com.awakekt.awake.compose:foundation:<version>")
    implementation("com.awakekt.awake.ui:shadcn:<version>")
}
```

---

## Subsystem Architecture

Awake is organized into clean, modular subprojects:

- **[`awake:core:*`](awake/core)** — Vector math (`Mat4`, `Vec3f`, `Quat`), geometry, animation, and
  image loaders.
- **[`awake:ecs:*`](awake/ecs)** — High-performance sparse-set ECS and archetypes.
- **[`awake:compose:*`](awake/compose)** — Compose Multiplatform UI runtime, layout nodes, and
  rendering passes.
- **[`awake:asset:*`](awake/asset)** — glTF parser, Naga runtime shader compiler, and texture
  loaders.
- **[`awake:engine:*`](awake/engine)** — Frame loops, render-pass orchestration, and window
  lifecycle.
- **[`awake:scene:*`](awake/scene)** — Microkernel `GamePlugin` SPI, transforms, Jolt physics,
  `KeybindingProfile`, Behavior Tree AI, and modular slot rendering.
- **[`awake:editor:*`](awake/editor)** — Studio editor shell, docking layout, scene hierarchy,
  inspector, and `EditorPlugin` SPI.
- **[`awake:backend:*`](awake/backend)** — Platform renderers (`vulkan`, `webgpu`, `jolt`).

---

## Commercial Pro Extensions (`com.awakekt.pro`)

Advanced algorithmic and enterprise-grade simulation modules are maintained separately under the
private commercial [`awakekt/awake-pro`](https://github.com/awakekt/awake-pro) repository:

- **`com.awakekt.pro:physics-ragdoll`** — Multi-body humanoid ragdoll solvers, cone-twist joint
  angle limits, and vehicle physics.
- **`com.awakekt.pro:worldstream`** — Multi-tile clipmap infinite terrain streaming and GPU virtual
  texturing.
- **`com.awakekt.pro:navigation`** — 2-level hierarchical A* pathfinder and streamed navmesh graphs.
- **`com.awakekt.pro:character-studio`** — Boundary-locked QEM mesh decimation and dynamic UV atlas
  packing.
- **`com.awakekt.pro:visual-blueprints`** — Drag-and-drop node graph canvas and live execution wire
  debugger.

---

## Documentation

All documentation is indexed and self-healed in **[`docs/README.md`](docs/README.md)**:

| Section                                                             | Description                                                          |
|:--------------------------------------------------------------------|:---------------------------------------------------------------------|
| **[Release Process & Milestones](docs/release-process.md)**         | Canonical branching rules, SemVer lifecycle, and 7-milestone roadmap |
| **[Sitemap & Status](docs/README.md)**                              | Live index of all 170 architecture docs, tasks, and audits           |
| **[Roadmap & Strategy](docs/mmorpg-roadmap.md)**                    | Long-term MMORPG architecture and engine roadmap                     |
| **[Architecture Decisions (ADRs)](docs/decisions/)**                | Architectural decision records                                       |
| **[Audits & Health Reports](docs/audits/)**                         | Architecture audit snapshots and backlog                             |
| **[Framework Boundary](docs/reference/framework-game-boundary.md)** | Core engine vs starter kits separation                               |

---

## Contributing & Community

- **License**: Distributed under the [Apache License 2.0](LICENSE.md).
- **GitHub**: [github.com/awakekt/awake](https://github.com/awakekt/awake).
- **Support**: Funded independently via [Patreon](https://www.patreon.com/awakekt)
  and [GitHub Sponsors](https://github.com/sponsors/awakekt).
