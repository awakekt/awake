# Awake Engine

![Awake Banner](https://github.com/awakekt/awake/actions/workflows/build-and-publish.yml/badge.svg)

Awake is a Kotlin Multiplatform 3D/2D game engine and graphics runtime powered by Vulkan and WebGPU, featuring a shared ECS runtime, Compose Multiplatform UI, and Jolt Physics, targeting Android, iOS (via MoltenVK), Desktop (macOS/Windows/Linux), and the Web (Wasm/WebGPU) from one `commonMain` codebase.

## Features

- **Vulkan & WebGPU RHI** — Native Vulkan (Android, Desktop, iOS via MoltenVK) and WebGPU (Web / Wasm) sharing a strict hardware abstraction layer (`awake:engine:render:contract`).
- **Shared Sparse-Set ECS** (`awake:ecs`) — Cache-friendly component storage, archetype queries, and zero-allocation per-frame transform math.
- **Compose Multiplatform UI** (`awake:compose:*`, `awake:ui:shadcn`) — Retained Compose runtime with a full Shadcn component suite for in-game HUDs and tools.
- **Jolt Physics Integration** (`awake:backend:jolt`) — Hardware-accelerated rigid bodies, heightfield terrain colliders, and character controllers.
- **Runtime Shader Compilation** (`awake:asset:shaders`) — WebGPU WGSL and Naga SPIR-V pipeline compilation.

## Modules

- `awake:core:*` — Vector math (`Vec3f`, `Mat4`, `Quat`), geometry, animation, and image loaders.
- `awake:ecs:*` — High-performance sparse-set ECS and archetypes.
- `awake:compose:*` — Compose Multiplatform UI runtime and layout nodes.
- `awake:ui:shadcn` — Complete Shadcn Compose design system.
- `awake:engine:bootstrap` — Application bootstrap, windowing, and frame loop lifecycle.
- `awake:engine:render:contract` — Backend-neutral Render Hardware Interface (`GpuDevice`, `Renderer`, `GpuPassInput`).
- `awake:scene:*` — Scene runtime, Jolt physics, cameras, lighting, and AI.
- `awake:backend:*` — Platform native backends (`vulkan`, `webgpu`, `jolt`).

[Getting Started](getting-started.md){ .md-button .md-button--primary }
[Roadmap & Milestones](roadmap.md){ .md-button }
