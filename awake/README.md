# Awake modules

A map of the engine's modules. For *why* they are grouped this way, and where a new one should
go, see [docs/reference/module-architecture.md](../docs/reference/module-architecture.md) — the
source of truth for module decisions.

Paths below are Gradle coordinates minus the `:awake:` prefix.

## core — foundation, no engine concepts

| Module | What it is |
|---|---|
| `core:color` | `Color`. Zero dependencies; the most-depended-on type in the group |
| `core:math` | `Vec3/4`, `Mat3/4`, `Quat`, `Ray`, `Frustum`, `Aabb`, `Plane`, camera projections |
| `core:pool` | `ScratchPool`: frame-scoped object reuse without per-frame allocation |
| `core:math2d` | `Vec2`, `Rectangle`, `Size2D`, `Dp`, `Sp` — screen-space primitives and units |
| `core:geometry` | Portable mesh geometry math, no file I/O |
| `core:graphics2d` | The CPU 2D draw vocabulary between 2D producers and renderers |
| `core:animation` | Skeletons, skin bindings, animation clips, crossfades |
| `core:image` | Per-platform bitmap decode, `BitmapRgba8` |
| `core:text` | Font atlases, glyph metrics, bundled UI fonts |
| `core:input` | Pointer, key, and text input state, plus the Android IME bridges |
| `core:audio` | `AudioClip`, `AudioPlayer`, WAV decoding, a simple synthesizer |
| `core:host` | Host services: `FrameLoop`, `FixedTimestepLoop`, resource bytes |
| `core:io` | Asynchronous, root-relative file and byte-source contracts |
| `core:config` | Typed configuration from environment and platform sources |
| `core:logging` | `Log`, sinks, and an in-memory ring buffer |
| `core:schema` | Property schemas and authoring annotations read from `kotlinx.serialization` descriptors |
| `core:di` | A small dependency-injection container: modules, keys, bindings |
| `core:state` | Reactive stores and unidirectional state contracts |

## ecs — entity storage and iteration

| Module | What it is |
|---|---|
| `ecs` | Dependency-free sparse-set Entity Component System |
| `ecs:benchmark` | Benchmarks (not published) |

## engine — application lifecycle and backend-neutral rendering

> `engine` and `backend` are layer names, not subsystems, and rendering is split across them. See
> the architecture doc's [target grouping](../docs/reference/module-architecture.md#target-grouping--decided).

| Module | What it is |
|---|---|
| `engine:platform` | Turns the host's frame callbacks into a portable application lifecycle |
| `engine:bootstrap` | The `app { }` builders on top of `engine:platform` |
| `engine:compose` | Hosts Compose UI on the engine; the platform never depends on it |
| `engine:render:contract` | Cross-backend rendering vocabulary and contracts |
| `engine:render:passes` | Shared 3D render-pass logic and draw recording |
| `engine:render:passes2d` | Shared 2D render-pass logic |
| `engine:render:testing` | Headless render sessions, `NoopRenderer`, pixel and timing baselines |
| `engine:render:parity` | Cross-backend pixel parity tests (not published) |

## backend — platform implementations

| Module | What it is |
|---|---|
| `backend:vulkan` | Vulkan renderer for desktop, Android, and iOS (MoltenVK). **Versioned separately** |
| `backend:vulkan:bindings` | Kotlin Multiplatform Vulkan API bindings. Versioned with `backend:vulkan` |
| `backend:vulkan:bindings:android-native` | The Android JNI bridge. Versioned with `backend:vulkan` |
| `backend:vulkan:generator` | Generates the bindings (not published) |
| `backend:webgpu` | WebGPU renderer for WasmJs. Snapshot-only while it depends on a wgpu4k snapshot |
| `backend:jolt` | Jolt Physics implementation of `physics:api` |

## physics

| Module | What it is |
|---|---|
| `physics:api` | Rigid bodies, collision shapes, motion types, queries |

## scene — ECS components and systems on top of the engine

| Module | What it is |
|---|---|
| `scene:scene-core` | `Transform`, names, and their systems |
| `scene:scene3d` | Cameras, lights, meshes, and 3D scene rendering |
| `scene:scene3d:benchmark` | Benchmarks (not published) |
| `scene:authoring` | Type-safe DSL for building entity hierarchies, lights, and cameras |
| `scene:document` | Serializable scene documents, prefabs, and catalogs |
| `scene:binding` | Resolves document components onto live entities |
| `scene:runtime` | Scene app lifecycle, asset resolution, and project launching |
| `scene:controls` | Camera rigs, input processing, gameplay key bindings |
| `scene:physics` | Physics bodies, character controller, and the physics system |
| `scene:audio` | Audio sources and the audio system |
| `scene:particles` | The `particle_emitter` scene component, over `particles` |
| `scene:canvas` | Game UI in scenes: anchored text, panels, bars and buttons drawn over the game |
| `scene:character` | A walking, jumping character moved by the physics character controller, saved in scenes |
| `scene:gltf` | glTF and GLB models in scenes: meshes, textured materials and skins |
| `scene:world` | Open-world cells, partitioning, streaming, floating origin |

## gameplay

| Module | What it is |
|---|---|
| `ai` | Behavior trees and finite state machines |
| `ai:behavior` | Navmesh-backed patrol, chase, and flee behaviors |
| `navigation` | A* grid pathfinding, field search, path smoothing |
| `particles` | Particle emitters, simulation and draw packets; no scene dependency |
| `net:api` | Transport and packet-buffer contracts |

## project and tooling data

| Module | What it is |
|---|---|
| `project` | The `awake.project.json` contract, content validation, and project index |
| `project:runtime` | Plays a project without the editor, running the systems its scene's components call for |
| `node-graph` | Graph documents, node registry, and validation shared by node-graph editors and runtimes |
| `editor:contract` | Vendor-neutral editor plugin, provider, and asset-converter contracts |

## compose — retained UI runtime

See [docs/reference/compose-engine/](../docs/reference/compose-engine/).

| Module | What it is |
|---|---|
| `compose:runtime` | Composition, recomposition, and state |
| `compose:ui` | Layout, modifiers, measurement, drawing, semantics |
| `compose:foundation` | Basic layouts, text, and interaction building blocks |
| `compose:state` | Bridge from `core:state` stores into composition |
| `compose:di` | Ambient `core:di` injection through the UI tree |
| `compose:ui-testing` | Rasterizer, semantics queries, component frames for tests |

## ui — component families and UI tools

| Module | What it is |
|---|---|
| `ui:shadcn` | shadcn/ui themes, tokens, variants, and component recipes |
| `ui:material3` | Material 3 components and theme contracts (not yet published) |
| `ui:node-graph-canvas` | The editing surface for `node-graph` documents |
| `ui:benchmark` | UI frame benchmarks (not published) |
| `ui:font-atlas-generator` | Generates the bundled font atlases (not published) |
| `tailwind` | Tailwind-style tokens (`Tw`), OKLCH colors, layout helpers |
| `tailwind-generator` | Generates the `tailwind` tokens (not published) |

## asset — content pipelines

| Module | What it is |
|---|---|
| `asset:gltf` | glTF 2.0 (`.glb`/`.gltf`) parsing: meshes, skins, animations, PBR materials |
| `asset:shaders` | `ShaderSet`/`ShaderSource` contract, uniform and descriptor layouts |
| `asset:shader-pack` | The shipped WGSL shader set and its uniform layouts — opt-in content |
| `asset:shader-dsl` | Author shaders in Kotlin |
| `asset:shader-compiler` | Runtime WGSL→SPIR-V through naga |
| `asset:terrain` | Terrain data, geometry generation, heightfield sculpting, splatting |
| `asset:mesh-optimizer` | Mesh decimation CLI over `core:geometry` (not published) |

## kit — optional feature kits

| Module | What it is |
|---|---|
| `kit:terrain-layers` | Layered terrain: layer palettes, control maps, surface shading |

---

## Adding a module

The rules, in full, are in
[module-architecture.md](../docs/reference/module-architecture.md#adding-a-new-module). The short
version:

1. Name it for the **subsystem**, never the layer. `engine`, `backend`, `common`, `shared`,
   `impl`, `util` are all layer names.
2. Prefer `implementation` over `api` — an `api` edge becomes every downstream consumer's
   dependency.
3. Check the transitive cost. A module that only describes data should have a closure near zero.
4. Give it a README and add it to the table above in the same commit.
