# Awake Engine

![Awake Banner](https://github.com/awakekt/awake/actions/workflows/build-and-publish.yml/badge.svg)

**The modern, code-first 3D game engine in Kotlin.**  
Fast, type-safe, and zero-allocation — build your game once and ship to Desktop (macOS, Windows, Linux), Mobile (Android, iOS), and the Web (Wasm) with native Vulkan and WebGPU performance.

## Key Features

- **Next-Gen Graphics** — Physically-Based Rendering (PBR), cascaded shadows, GPU skinning, and runtime Naga shaders powered by native Vulkan and WebGPU.
- **Zero-Allocation ECS** — Cache-friendly sparse-set entity-component-system designed for rock-solid 120 FPS game loops.
- **Declarative HUDs & Menus** — Build reactive in-game interfaces and developer tools with Compose and 23+ ready-to-use Shadcn components.
- **Fast Physics & Collision** — Powered by Jolt Physics with real-time rigid bodies, terrain heightfield colliders, raycasting, and character controllers.
- **Code-First Workflow** — Author scenes, systems, and assets in pure Kotlin without reflection, runtime bytecode manipulation, or editor lock-in.

## Engine Subsystems

- `awake:core:*` — High-performance 3D math (`Vec3f`, `Mat4`, `Quat`), geometry, animation, and image loaders.
- `awake:ecs:*` — High-performance sparse-set ECS and archetype queries.
- `awake:scene:*` — Scene runtime, Jolt physics, cameras, lighting, modular characters, and Behavior Tree AI.
- `awake:engine:bootstrap` — Application bootstrap, windowing, and frame loop lifecycle.
- `awake:engine:render:contract` — Backend-neutral Render Hardware Interface (`GpuDevice`, `Renderer`, `GpuPassInput`).
- `awake:compose:*` & `awake:ui:shadcn` — Retained Compose UI engine and complete Shadcn design system.
- `awake:backend:*` — Native graphics and physics drivers (`vulkan`, `webgpu`, `jolt`).

[Getting Started](getting-started.md){ .md-button .md-button--primary }
[Roadmap & Milestones](roadmap.md){ .md-button }
