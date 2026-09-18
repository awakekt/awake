# Awake Engine

Awake is a code-first Kotlin Multiplatform runtime for interactive 2D and 3D applications. It
combines application lifecycle, scenes, rendering, physics, and retained UI while keeping
platform-specific code at the edge of the application.

> Awake is alpha software. APIs and published artifacts can change between releases.

This site is versioned with the Awake library. Use the version selector in the header when you
need documentation for a different release.

## What you can build

- Share application and scene code across supported Kotlin Multiplatform targets.
- Compose ECS worlds, scene components, render passes, physics, and UI as independent modules.
- Use Vulkan on native desktop targets and WebGPU in the browser.
- Author game and tool behavior in Kotlin without a required editor project format.

## Main modules

| Area | Modules | Purpose |
|---|---|---|
| Runtime | `engine:bootstrap`, `engine:platform` | Application lifecycle, windowing, and frame scheduling |
| ECS | `ecs` | Entities, components, systems, and queries |
| Scenes | `scene:scene-core`, `scene:scene3d`, `scene:authoring` | Transforms, cameras, lights, meshes, and scene DSLs |
| Rendering | `engine:render:*`, `backend:vulkan`, `backend:webgpu` | Backend-neutral render contracts and platform backends |
| Physics | `physics:api`, `backend:jolt` | Physics contracts and Jolt integration |
| UI | `compose:*`, `ui:shadcn`, `ui:material3` | Retained UI runtime and component families |

[Getting Started](getting-started.md){ .md-button .md-button--primary }
[Releases and compatibility](releases.md){ .md-button }
