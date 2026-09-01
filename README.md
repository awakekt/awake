<h1 align="center">Awake Engine</h1>
<p align="center">Kotlin Multiplatform first, Vulkan and WebGPU native — a 3D/2D game engine and graphics runtime for developers who want to feel every frame they write.</p>

<p align="center">
  <a href="https://github.com/awake-lab/awake/actions/workflows/build-and-publish.yml"><img src="https://github.com/awake-lab/awake/actions/workflows/build-and-publish.yml/badge.svg" alt="Build And Publish"></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-2.4.10-blue.svg?logo=kotlin" alt="Kotlin"></a>
  <a href="https://www.jetbrains.com/lp/compose-multiplatform/"><img src="https://img.shields.io/badge/Compose_Multiplatform-1.11.1-purple.svg" alt="Compose Multiplatform"></a>
  <a href="https://opensource.org/licenses/Apache-2.0"><img src="https://img.shields.io/badge/License-Apache_2.0-blue.svg" alt="License"></a>
  <a href="https://www.patreon.com/cw/awakelab"><img src="https://img.shields.io/badge/donate-Patreon-f96854.svg?logo=patreon" alt="Patreon"></a>
</p>

---

## What It Does

- **High-Performance Rendering** — Vulkan (Desktop JVM, Android, iOS via MoltenVK) and WebGPU (Web/Wasm). Runtime WGSL shader compilation via Naga, cascaded shadows, PBR materials, and glTF GPU skeletal animation.
- **Sparse-Set ECS Runtime** — Cache-friendly entity iteration, archetype queries, and zero-allocation 3D transform math.
- **Compose Multiplatform UI Stack** — Modern declarative UI powered by Compose runtime, custom design tokens, and a complete Shadcn component suite.
- **Physics Engine** — Backend-neutral physics abstraction with a hardware-accelerated Jolt Physics bridge.
- **Extensible Architecture** — Core runtime decoupled from modular game extensions ([`awake-lab/starter-kits`](https://github.com/awake-lab/starter-kits)).

---

## Quickstart

Run **Awake Studio** (interactive 3D editor with live scene switching, glTF previewing, and ECS inspector):

```bash
# Desktop JVM Target
./gradlew :apps:studio:run

# Web Target (Chrome/Edge 113+ with WebGPU)
./gradlew :apps:studio:wasmJsBrowserDevelopmentRun
```

Run the **UI Showcase Gallery** (complete catalog of Compose Shadcn components):
```bash
./gradlew :samples:ui-showcase:run
```

---

## Code-First Scene Authoring

Awake provides a type-safe, declarative DSL for building games and scenes:

```kotlin
fun main() = runVulkanDesktopGame(
    game {
        window {
            title = "Awake 3D Scene"
            size(1280, 720)
        }
        scene {
            defaultOrbitCamera()

            entity("rotating_cube") {
                with(SpinControl(speedX = 0.5f, speedY = 1.0f))
                mesh(generate { cube(size = 2f, colored = true) })
            }

            lighting.singleDirectionalLight(color = Color.WHITE)
        }
    }
)
```

---

## Standalone Libraries

### `vulkan-kmp` — Vulkan API Bindings for Kotlin Multiplatform
Standalone, zero-dependency Vulkan bindings for Kotlin Multiplatform:
* **Desktop JVM (macOS arm64/x86_64, Linux x86_64)**: the native library is bundled in the jar and
  extracted on first use — no `java.library.path` setup. Windows is not shipped yet: nothing has
  compiled the desktop C++ on it, so it is unproven rather than merely untested.
  On macOS you still need MoltenVK and the portability opt-in — see
  [`bindings/README.md`](awake/backend/vulkan/bindings/README.md).
* **Android**: Packaged as an AAR with validation layers (`vulkan-kmp-android-native`).
* **iOS**: Native MoltenVK interop.

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.awake-lab:vulkan-kmp:<version>")
}
```

> Not on Maven Central yet. `vulkan-kmp` was given its own flat artifact id on 2026-08-24, one day
> after `v0.1.0-dev.6` was cut, so no released tag carries it. Until a release publishes it, build
> it yourself: `./gradlew publishToMavenLocal` and consume it from `mavenLocal()`, which is what
> [`tools/vulkan-kmp-smoke`](tools/vulkan-kmp-smoke) does.
>
> `<version>` is deliberate while that is true. The version is derived from `git describe`, so the
> one your local publish produces depends on your checkout — print it with
> `./gradlew :awake:backend:vulkan:bindings:properties | grep version`.
>
> `tools/verify_doc_coordinates.py` checks the group and artifact here against what actually
> publishes; it cannot check a placeholder version, which is the trade for not going stale.

---

## Subsystem Architecture

Awake is organized into clean, modular subprojects:

- **[`awake:core:*`](awake/core)** — Vector math (`Mat4`, `Vec3f`, `Quat`), geometry, animation, and image loaders.
- **[`awake:ecs:*`](awake/ecs)** — High-performance sparse-set ECS and archetypes.
- **[`awake:compose:*`](awake/compose)** — Compose Multiplatform UI runtime, layout nodes, and rendering passes.
- **[`awake:asset:*`](awake/asset)** — glTF parser, Naga runtime shader compiler, and texture loaders.
- **[`awake:engine:*`](awake/engine)** — Frame loops, render-pass orchestration, and window lifecycle.
- **[`awake:backend:*`](awake/backend)** — Platform renderers (`vulkan`, `webgpu`, `jolt`).

---

## Documentation

All documentation is indexed and self-healed in **[`docs/README.md`](docs/README.md)**:

| Section | Description |
| :--- | :--- |
| **[Sitemap & Status](docs/README.md)** | Live index of all 170 architecture docs, tasks, and audits |
| **[Roadmap & Strategy](docs/mmorpg-roadmap.md)** | Milestone targets and engine roadmap |
| **[Architecture Decisions (ADRs)](docs/decisions/)** | Architectural decision records |
| **[Audits & Health Reports](docs/audits/)** | Architecture audit snapshots and backlog |
| **[Framework Boundary](docs/reference/framework-game-boundary.md)** | Core engine vs starter kits separation |

---

## Contributing & Community

- **License**: Distributed under the [Apache License 2.0](LICENSE.md).
- **GitHub**: [github.com/awake-lab/awake](https://github.com/awake-lab/awake).
- **Support**: Funded independently via [Patreon](https://www.patreon.com/cw/awakelab) and [GitHub Sponsors](https://github.com/sponsors/awake-lab).
