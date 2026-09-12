<h1 align="center">Awake Engine</h1>
<p align="center">Kotlin Multiplatform first, Vulkan and WebGPU native — a 3D/2D game engine and graphics runtime for developers who want to feel every frame they write.</p>

<p align="center">
  <a href="https://github.com/awakekt/awake/releases/tag/v0.1.0-alpha.1"><img src="https://img.shields.io/badge/Release-v0.1.0--alpha.1-blue.svg" alt="Release v0.1.0-alpha.1"></a>
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
- **Extensible Scene Authoring DSL** — Modular scene authoring with typed `SceneSystemsDsl` and
  `SceneAssetsDsl` for clean system scheduling and asset resolving without reflection.

### Declarative UI & Design System

- **Compose Multiplatform UI Stack** — Declarative UI powered by Compose runtime, custom design
  tokens, and a complete Shadcn component suite (`awake:ui:shadcn`).
- **UI Builder & HUD DSL** — Extensible component architecture (`awake:ui:builder`) for in-game HUDs,
  debug inspectors, and declarative overlays.

### Gameplay, Physics & AI

- **Jolt Physics Engine** — Hardware-accelerated physics bridge with static heightfield colliders,
  raycasts, and kinematic character controllers.
- **Behavior Tree AI & Keybindings** — 60fps code-first AI runtime with sequences/selectors, plus
  type-safe `KeybindingProfile<A>` controls.
- **Modular Character Equipment & Sockets** — Skinned mesh modular slot composition (
  `ModularCharacterComponent`) and zero-allocation bone socket tracking.

---

## Quickstart

Run the **Engine Showcase** (3D rendering with camera controls, lighting, and physics):

```bash
# Desktop JVM (Vulkan)
./gradlew :samples:engine-showcase:desktopRun

# Web / Wasm (WebGPU)
./gradlew :samples:engine-showcase:wasmJsBrowserDevelopmentRun

# Android (Vulkan)
./gradlew :samples:engine-showcase:androidApp:assembleDebug

# iOS (MoltenVK) - open in Xcode or run from Android Studio / Fleet
open samples/engine-showcase/iosApp/iosApp.xcodeproj
```

Run the **UI Showcase Gallery** (complete catalog of 23+ Compose Shadcn components):

```bash
# Desktop JVM
./gradlew :samples:ui-showcase:desktopRun

# Web / Wasm
./gradlew :samples:ui-showcase:wasmJsBrowserDevelopmentRun
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

### Scene Authoring & Systems Scheduling (`SceneSystemsDsl`)

Awake scenes declare their simulation systems, assets, and entities using a clean, typed DSL without reflection or runtime plugin overhead:

```kotlin
import com.awakekt.awake.scene.authoring.scene

val myScene = scene {
    assets {
        // Register asset loaders / resolvers
    }
    systems {
        frameSystem("movement") { PlayerMovementSystem() }
        fixedSystem("physics") { PhysicsSimulationSystem() }
    }
    entities {
        // Author entities, components, and transforms
    }
}
```

Standalone project launches execute scenes directly via `AwakeProjectLauncher`:

```kotlin
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.runtime.project.AwakeProjectLauncher

fun main() {
    val world = World()

    // Instantiates default scene and binds systems directly into the runtime frame loop
    val scene = AwakeProjectLauncher.loadScene(world, sceneJson)
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
    implementation("com.awakekt.awake.core:math:0.1.0-alpha.1")
    implementation("com.awakekt.awake:ecs:0.1.0-alpha.1")
    implementation("com.awakekt.awake.scene:scene-core:0.1.0-alpha.1")
    implementation("com.awakekt.awake.engine:bootstrap:0.1.0-alpha.1")

    // Optional Rendering Backends (Choose Vulkan, WebGPU, or Both)
    implementation("com.awakekt.awake.backend:vulkan:0.1.0-alpha.1")
    implementation("com.awakekt.awake.backend:webgpu:0.1.0-alpha.1")

    // Optional Physics Engine
    implementation("com.awakekt.awake.backend:jolt:0.1.0-alpha.1")

    // Optional Asset Importers & Terrain
    implementation("com.awakekt.awake.asset:gltf:0.1.0-alpha.1")
    implementation("com.awakekt.awake.asset:terrain:0.1.0-alpha.1")

    // Optional AI & Navigation Pathfinding
    implementation("com.awakekt.awake:ai:0.1.0-alpha.1")
    implementation("com.awakekt.awake:navigation:0.1.0-alpha.1")

    // Optional 3D Audio Subsystem
    implementation("com.awakekt.awake.core:audio:0.1.0-alpha.1")

    // Optional Compose UI Overlay Stack
    implementation("com.awakekt.awake.compose:foundation:0.1.0-alpha.1")
    implementation("com.awakekt.awake.ui:shadcn:0.1.0-alpha.1")
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
- **[`awake:ui:*`](awake/ui)** — Shadcn Compose design system (`awake:ui:shadcn`) and UI builder (`awake:ui:builder`).
- **[`awake:asset:*`](awake/asset)** — glTF parser, Naga runtime shader compiler, and texture
  loaders.
- **[`awake:engine:*`](awake/engine)** — Frame loops, render-pass orchestration, and window
  lifecycle.
- **[`awake:scene:*`](awake/scene)** — Scene lifecycle, transforms, Jolt physics,
  `KeybindingProfile`, Behavior Tree AI, and modular slot rendering.
- **[`awake:backend:*`](awake/backend)** — Platform renderers (`vulkan`, `webgpu`, `jolt`).

### Rendering Architecture — Two-Layer Model

Awake's renderer is split into two strict layers (D31). Mixing them is the leading source of
architectural debt:

| Layer | Module(s) | Owns |
|---|---|---|
| **HAL** | `awake:engine:render:contract` | `GpuDevice` (`GpuCapability`), `Renderer` (`readPresentedPixels()`), `GpuPassInput` (`postPasses`), `GpuSubPass`, `GpuDrawCommand`, pipelines, buffers, textures, samplers, command recording |
| **Render Graph** | `awake:engine:render:passes`, `awake:asset:shader-pack`, `awake:scene:rendering` | `GpuSceneFrame`, `SceneLight`, `DrawCall`, `Lens`, shadow cascade math, environment uniforms, `RenderFeature` list |

> **The most common defect in this codebase is adding scene vocabulary to `render:contract`.**
> Before adding any type to `render:contract`, apply the test:
> *"Could a third backend implement this type without knowing what scene content it serves?"*
> If no, it belongs in `render:passes` or `scene:rendering`.
> Full audit: [`docs/reference/render-hardware-interface.md`](docs/reference/render-hardware-interface.md#hal-vs-render-graph--the-complete-vocabulary-boundary).

---

## Commercial Pro Extensions (`awakekt/awake-pro`)

Advanced algorithmic, world-streaming, and studio editor modules are maintained separately under the
private commercial [`awakekt/awake-pro`](https://github.com/awakekt/awake-pro) repository:

- **Awake Studio IDE & Editor Shell (`app:studio`, `awake:editor:*`)** — Pluggable docking IDE with
  live ECS outliner, inspector, glTF preview, and visual scene tools.
- **`com.awakekt.awake.pro:worldstream`** — Multi-tile clipmap infinite terrain streaming and GPU virtual
  texturing.
- **`com.awakekt.awake.pro:navigation`** — 2-level hierarchical A* pathfinder and streamed navmesh graphs.
- **`com.awakekt.awake.pro:physics-ragdoll`** — Multi-body humanoid ragdoll solvers, cone-twist joint
  angle limits, and vehicle physics.
- **`com.awakekt.awake.pro:character-studio`** — Boundary-locked QEM mesh decimation and dynamic UV atlas
  packing.
- **`com.awakekt.awake.pro:visual-blueprints`** — Drag-and-drop node graph canvas and live execution wire
  debugger.

---

## Release Milestones & Status

Awake development is tracked through structured GitHub milestones ([Release Process & Milestones](docs/release-process.md)):

| Milestone | Target | Status | Focus |
|---|---|---|---|
| **Milestone 0** | `v0.1.0-dev` | Completed | Engine Foundations, Multiplatform Math, Compose UI, Sparse-Set ECS |
| **Milestone 1** | `v0.1.0-alpha.1` | **Published** | First Public Maven Central release (`com.awakekt.awake:*`), Multi-OS Desktop Vulkan |
| **Milestone 2** | `v0.2.0` | **In Progress** | WebGPU Backend & Studio Web Preview (WasmJs, Naga SPIR-V to WGSL pipeline) |
| **Milestone 3** | `v0.3.0` | Planned | Physics & Character Controller Maturity (Jolt, Heightfield, Raycasting) |
| **Milestone 4** | `v0.4.0` | Planned | Studio IDE Maturity & Prefabs System (`app:studio`, Undo/Redo) |
| **Milestone 5** | `v0.5.0` | Planned | 3D Spatial Audio & Open World Terrain (Geometry Clipmaps, 3D NavMesh) |
| **Milestone 6** | `v0.6.0` | Planned | Multiplayer Synchronization & Networking (Client prediction, Delta serialization) |
| **Milestone 7** | `v1.0.0` | Planned | Production Stable Engine & Ecosystem (Full OS Matrix, Headless Parity Gate) |

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
