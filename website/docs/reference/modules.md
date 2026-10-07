# Modules

<p class="awake-lede">Every published AwakeKt Engine module: its Maven coordinate, a catalog alias for it, and what it holds.</p>

## How to read this page

| Item | Value |
| --- | --- |
| Group | `com.awakekt.awake`, plus the module's parent path: `:awake:scene:runtime` publishes `com.awakekt.awake.scene:runtime`. The Vulkan bindings are the exception: `com.awakekt.awake:vulkan-kmp`. |
| Version | Core modules share one version; the newest is `{{ awake_version }}`. Vulkan family: `{{ awake_vulkan_version }}`, built against Core `{{ awake_vulkan_core_version }}`. See [Releases and compatibility](releases.md). |
| Catalog alias | `awake-` plus the group after `com.awakekt.awake`, then the name, joined with `-`. The accessor replaces `-` with `.`: `awake-scene-runtime` is `libs.awake.scene.runtime`. The alias is your choice; this is the convention these docs use. |
| Published modules | 81: 78 in Core, 3 in the Vulkan family. |
| Transitive modules | Declare only the modules your code uses. Each module's published metadata brings the modules it depends on. |

A catalog entry looks like this:

```toml title="gradle/libs.versions.toml"
[versions]
awake = "{{ awake_vulkan_core_version }}"
awake-vulkan = "{{ awake_vulkan_version }}"

[libraries]
awake-scene-runtime = { module = "com.awakekt.awake.scene:runtime", version.ref = "awake" }
awake-backend-vulkan = { module = "com.awakekt.awake.backend:vulkan", version.ref = "awake-vulkan" }
```

## Modules

### Core math and data

| Maven coordinate | Catalog alias | What it is |
| --- | --- | --- |
| `com.awakekt.awake.core:math` | `awake-core-math` | Vectors, matrices, quaternions, rays, frustums and camera lenses. |
| `com.awakekt.awake.core:pool` | `awake-core-pool` | Frame-scoped object pools that reuse instances without allocating. |
| `com.awakekt.awake.core:math2d` | `awake-core-math2d` | Screen-space 2D primitives: `Vec2`, `Rectangle`, `Size2D`, `Dp`. |
| `com.awakekt.awake.core:geometry` | `awake-core-geometry` | Mesh geometry data and math: vertex formats, decoding, simplification. |
| `com.awakekt.awake.core:color` | `awake-core-color` | The `Color` value type. |
| `com.awakekt.awake.core:graphics2d` | `awake-core-graphics2d` | The CPU 2D draw vocabulary: draw commands, paths, gradients, meshes. |
| `com.awakekt.awake.core:image` | `awake-core-image` | Bitmap decoding and RGBA8 pixel buffers. |
| `com.awakekt.awake.core:input` | `awake-core-input` | Pointer, key and text input state. |
| `com.awakekt.awake.core:host` | `awake-core-host` | Host platform services: frame loop, resource bytes. |
| `com.awakekt.awake.core:io` | `awake-core-io` | Asynchronous file, resource and byte-stream access. |
| `com.awakekt.awake.core:schema` | `awake-core-schema` | Property schemas and authoring annotations read from serialization descriptors. |
| `com.awakekt.awake.core:text` | `awake-core-text` | Font atlases, glyph metrics and text shaping. |
| `com.awakekt.awake.core:audio` | `awake-core-audio` | Audio playback, WAV decoding, synthesis and 3D attenuation. |
| `com.awakekt.awake.core:animation` | `awake-core-animation` | Skeletal animation: skeletons, clips, pose sampling, crossfades. |
| `com.awakekt.awake.core:logging` | `awake-core-logging` | Levelled, frame-stamped logging with a bounded buffer. |
| `com.awakekt.awake.core:di` | `awake-core-di` | Dependency injection container, no reflection. |
| `com.awakekt.awake.core:config` | `awake-core-config` | Typed environment and configuration values. |
| `com.awakekt.awake.core:state` | `awake-core-state` | Reactive, unidirectional state management. |

### ECS and application

| Maven coordinate | Catalog alias | What it is |
| --- | --- | --- |
| `com.awakekt.awake:ecs` | `awake-ecs` | The ECS: sparse-set `World`, entities, components, systems, queries. |
| `com.awakekt.awake:particles` | `awake-particles` | Particle emitters: simulation, ground bounce, burst scheduling and instanced draw packets, with no scene needed. |
| `com.awakekt.awake.engine:bootstrap` | `awake-engine-bootstrap` | Application and window bootstrap: `app { }`, the entry point a game calls. |
| `com.awakekt.awake.engine:platform` | `awake-engine-platform` | Per-platform app lifecycle, windowing and surfaces behind one contract. |
| `com.awakekt.awake.engine:compose` | `awake-engine-compose` | Hosts AwakeKt Compose inside an engine frame and paints it through the renderer. |

### Rendering

| Maven coordinate | Catalog alias | What it is |
| --- | --- | --- |
| `com.awakekt.awake.engine.render:contract` | `awake-engine-render-contract` | The render hardware interface: `GpuDevice`, `Renderer`, `Mesh`, `Material`, `RenderTarget`. See [Render hardware interface](rhi.md). |
| `com.awakekt.awake.engine.render:passes` | `awake-engine-render-passes` | Render features and pipelines: shadow, opaque, transparent, skybox and content passes. |
| `com.awakekt.awake.engine.render:passes2d` | `awake-engine-render-passes2d` | Backend-neutral 2D pass logic: run coalescing, mesh upload, command recording. |
| `com.awakekt.awake.engine.render:testing` | `awake-engine-render-testing` | Headless render sessions, pixel baselines and frame capture for tests. |
| `com.awakekt.awake.asset:shaders` | `awake-asset-shaders` | Backend-neutral shader contract: `RenderPlan`, `ScenePipeline`, `ShaderSet`. |
| `com.awakekt.awake.asset:shader-pack` | `awake-asset-shader-pack` | The engine's built-in WGSL shaders and their uniform layouts. |
| `com.awakekt.awake.asset:shader-dsl` | `awake-asset-shader-dsl` | ASL: shaders written in Kotlin that emit WGSL. |
| `com.awakekt.awake.asset:shader-document` | `awake-asset-shader-document` | Shaders as project data: a checked JSON subset of ASL, compiled to WGSL and drawn as a content feature. |
| `com.awakekt.awake.asset:shader-compiler` | `awake-asset-shader-compiler` | Runtime WGSL-to-SPIR-V compilation through naga. |

### Assets

| Maven coordinate | Catalog alias | What it is |
| --- | --- | --- |
| `com.awakekt.awake.asset:gltf` | `awake-asset-gltf` | glTF 2.0 mesh, scene and skinning importer. |
| `com.awakekt.awake.asset:sprite` | `awake-asset-sprite` | Regular-grid sprite metadata import and named frame clips. |
| `com.awakekt.awake.asset:terrain` | `awake-asset-terrain` | Heightmap terrain assets and grid mesh generation. |
| `com.awakekt.awake:terrain` | `awake-terrain` | The terrain surface seam: `TerrainSurfaceReference`, `TerrainSurfaceProvider` and `TerrainSurface`, with no scene dependency. |
| `com.awakekt.awake.kit:terrain-layers` | `awake-kit-terrain-layers` | Layered terrain surface: layer palette, control map and its shader. |

### Scenes

| Maven coordinate | Catalog alias | What it is |
| --- | --- | --- |
| `com.awakekt.awake.scene:scene-core` | `awake-scene-scene-core` | Transforms, names and the systems every scene runs. |
| `com.awakekt.awake.scene:scene3d` | `awake-scene-scene3d` | Cameras, lights, mesh renderers and the systems that draw a scene. |
| `com.awakekt.awake.scene:document` | `awake-scene-document` | Scene documents: `SceneDocument`, `SceneLoader`, validation, prefabs, extensions. |
| `com.awakekt.awake.scene:binding` | `awake-scene-binding` | Bindings between scene document components and ECS components. |
| `com.awakekt.awake.scene:runtime` | `awake-scene-runtime` | The scene runtime: `SceneManager`, sessions, asset library, scheduling, app lifecycle. |
| `com.awakekt.awake.scene:authoring` | `awake-scene-authoring` | The scene DSL and application DSL: entities, systems, assets and UI in Kotlin. |
| `com.awakekt.awake.scene:controls` | `awake-scene-controls` | Camera rigs and input-driven movement. |
| `com.awakekt.awake.scene:audio` | `awake-scene-audio` | Audio source and listener components and the spatial audio system. |
| `com.awakekt.awake.scene:particles` | `awake-scene-particles` | The `particle_emitter` scene component and the systems that run its emitters in a scene. |
| `com.awakekt.awake.scene:ai` | `awake-scene-ai` | The `patrol`, `chase` and `flee` scene components, and `TransformAgentPlacement` for the behaviour systems. |
| `com.awakekt.awake.scene:navigation` | `awake-scene-navigation` | The `navigation` scene component: the walkable grid a scene document carries. |
| `com.awakekt.awake.scene:shader` | `awake-scene-shader` | The `shader_effect` scene component: a project's shader documents loaded, attached and run over a scene. |
| `com.awakekt.awake.scene:scene2d` | `awake-scene-scene2d` | The 2D scene components, starting with `sprite`: schema, binding and the systems that run them. |
| `com.awakekt.awake.scene:physics` | `awake-scene-physics` | Physics components and systems that bind bodies to scene transforms. |
| `com.awakekt.awake.scene:character` | `awake-scene-character` | A walking, jumping character moved by the physics character controller. |
| `com.awakekt.awake.scene:canvas` | `awake-scene-canvas` | Screen-anchored game UI (text, panels, bars, buttons) stored in scenes. |
| `com.awakekt.awake.scene:gltf` | `awake-scene-gltf` | Resolves `.gltf` and `.glb` model paths in scenes to meshes and materials. |
| `com.awakekt.awake.scene:blueprint` | `awake-scene-blueprint` | Runs blueprints on scene entities: the `blueprint` component and `BlueprintSystem`. |
| `com.awakekt.awake.scene:world` | `awake-scene-world` | Open-world plumbing: partitioning, streaming and the floating origin, over `awake:world`. |
| `com.awakekt.awake.scene:worldstream` | `awake-scene-worldstream` | Cell streamers for meshes, physics, heightfield tiles and terrain texture tiles. |

### Physics

| Maven coordinate | Catalog alias | What it is |
| --- | --- | --- |
| `com.awakekt.awake.physics:api` | `awake-physics-api` | The physics facade: bodies, shapes, queries and stepping, with no backend. |
| `com.awakekt.awake.physics:ragdoll` | `awake-physics-ragdoll` | Ragdolls built from bodies and constraints, and the skeleton drive. |
| `com.awakekt.awake.backend:jolt` | `awake-backend-jolt` | Jolt Physics backend for `physics:api`. |

### Gameplay

| Maven coordinate | Catalog alias | What it is |
| --- | --- | --- |
| `com.awakekt.awake:navigation` | `awake-navigation` | Heightmap-derived navigation grids, streamed navigation and path requests. |
| `com.awakekt.awake:world` | `awake-world` | World cells: coordinates, partition radii and the cell streaming contract, with no scene dependency. |
| `com.awakekt.awake:ai` | `awake-ai` | Behavior tree and finite state machine primitives. |
| `com.awakekt.awake.ai:behavior` | `awake-ai-behavior` | Starter behaviors that follow navigation paths: `patrol`, `chase`, `flee`, with no scene dependency. |
| `com.awakekt.awake:blueprint` | `awake-blueprint` | Runtime for event-driven game logic authored as node graphs. |
| `com.awakekt.awake:node-graph` | `awake-node-graph` | Node graph documents, node registry and validation. |
| `com.awakekt.awake.net:api` | `awake-net-api` | The transport facade: connections, delivery channels and packet buffers, with no transport. |

### Projects and editor

| Maven coordinate | Catalog alias | What it is |
| --- | --- | --- |
| `com.awakekt.awake:project` | `awake-project` | The AwakeKt project manifest format and its validation. |
| `com.awakekt.awake.project:runtime` | `awake-project-runtime` | Plays a project without the editor: `loadProject`, `runProject`. |
| `com.awakekt.awake.editor:contract` | `awake-editor-contract` | Public extension points and plugin contracts for AwakeKt Studio. |

### UI

| Maven coordinate | Catalog alias | What it is |
| --- | --- | --- |
| `com.awakekt.awake.compose:runtime` | `awake-compose-runtime` | AwakeKt Compose runtime: the composer, composition locals, `remember` and state. |
| `com.awakekt.awake.compose:ui` | `awake-compose-ui` | Modifiers, layout nodes, input dispatch and semantics. |
| `com.awakekt.awake.compose:foundation` | `awake-compose-foundation` | Compose Foundation: `Row`, `Column`, `Box`, scrolling and layout modifiers. |
| `com.awakekt.awake.compose:state` | `awake-compose-state` | State management integration for AwakeKt Compose. |
| `com.awakekt.awake.compose:di` | `awake-compose-di` | Dependency injection bridge for AwakeKt Compose. |
| `com.awakekt.awake.compose:ui-testing` | `awake-compose-ui-testing` | Test helpers for AwakeKt Compose: semantics queries and a headless rasterizer. |
| `com.awakekt.awake:tailwind` | `awake-tailwind` | Tailwind-style design tokens and modifiers for Compose Foundation. |
| `com.awakekt.awake.ui:shadcn` | `awake-ui-shadcn` | shadcn components: themes, tokens, variants and component recipes. |
| `com.awakekt.awake.ui:node-graph-canvas` | `awake-ui-node-graph-canvas` | Pan-and-zoom node graph editing surface. |

### WebGPU backend

| Maven coordinate | Catalog alias | What it is |
| --- | --- | --- |
| `com.awakekt.awake.backend:webgpu` | `awake-backend-webgpu` | The WebGPU renderer, running WGSL. |

### Vulkan family

| Maven coordinate | Catalog alias | What it is |
| --- | --- | --- |
| `com.awakekt.awake.backend:vulkan` | `awake-backend-vulkan` | The Vulkan renderer for desktop, Android and iOS (MoltenVK). |
| `com.awakekt.awake:vulkan-kmp` | `awake-vulkan-kmp` | Raw Vulkan API bindings: desktop JVM, Android and iOS (MoltenVK). |
| `com.awakekt.awake:vulkan-kmp-android-native` | `awake-vulkan-kmp-android-native` | Android native library (NDK build and bundled validation layers) for the bindings. |

## Release trains

| Family | Modules | Version | Notes |
| --- | --- | --- | --- |
| Core | Every module above except the Vulkan family | `{{ awake_version }}` | One version for all. Tagged `v*`. |
| Vulkan | `backend:vulkan`, `vulkan-kmp`, `vulkan-kmp-android-native` | `{{ awake_vulkan_version }}` | Its own release train, tagged `vulkan-v*`. Each release names the Core release it was built against, here `{{ awake_vulkan_core_version }}`. |
| WebGPU | `backend:webgpu` | A `-SNAPSHOT` version | Snapshot-only: it builds on a wgpu4k snapshot, and Maven Central releases cannot depend on a snapshot. Use the snapshot repository `https://central.sonatype.com/repository/maven-snapshots/`. |

## Targets

The target of every module is in [Releases and compatibility](releases.md#targets).

## See also

- [Releases and compatibility](releases.md)
- [Installation](../get-started/installation.md)
