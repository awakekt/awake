# Awake Engine — Library KDoc Completeness Audit

**Date:** 2026-10-01  
**Scope:** All 77 modules defined in [`settings.gradle.kts`](file:///Users/ronvaldoz/StudioProjects/awaken/settings.gradle.kts) (68 Core Engine & Editor Contract libraries, 9 Tools/Benchmarks/Test Harnesses)

---

## 1. Executive Summary

A comprehensive audit was performed across all libraries in the Awake repository to measure public API KDoc coverage and identify libraries with incomplete or missing documentation.

| Category | Modules | Total Public Declarations | Documented | Undocumented | Coverage |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Core Engine Libraries (Layer 1 & 2)** | **68** | **11,544** | **3,195** | **8,349** | **27.7%** |
| **Tools, Benchmarks & Test Fixtures** | **9** | **1,444** | **145** | **1,299** | **10.0%** |
| **Total Across All Modules** | **77** | **12,988** | **3,340** | **9,648** | **25.7%** |

> [!WARNING]
> **63 out of 68 (92.6%) core engine libraries currently fail to meet complete KDoc standards.**  
> Only **5 libraries (7.4%)** have achieved $\ge 75\%$ coverage. Over **8,300 public API declarations** in core engine modules lack any top-level summary or tag documentation.

---

## 2. Root Cause Analysis

Why is KDoc coverage low despite strict rules in [`docs/kdoc-guidelines.md`](file:///Users/ronvaldoz/StudioProjects/awaken/docs/kdoc-guidelines.md)?

1. **Detekt Undocumented Rules are Disabled**:
   In [`config/detekt/detekt.yml`](file:///Users/ronvaldoz/StudioProjects/awaken/config/detekt/detekt.yml#L96-L112), the enforcement rules for public API documentation are explicitly turned off:
   ```yaml
   UndocumentedPublicClass:
     active: false
   UndocumentedPublicFunction:
     active: false
   UndocumentedPublicProperty:
     active: false
   ```
   Only `OutdatedDocumentation` is enabled, which only validates *existing* KDoc tags on parameters/properties, but does not flag missing KDocs on public declarations.

2. **Dokka Does Not Fail on Warnings**:
   In [`build-logic/src/main/kotlin/com.awakekt.awake.plugin.dokka.gradle.kts`](file:///Users/ronvaldoz/StudioProjects/awaken/build-logic/src/main/kotlin/com.awakekt.awake.plugin.dokka.gradle.kts#L24-L54):
   ```kotlin
   dokkaPublications.html {
       failOnWarning.set(false)
   }
   dokkaSourceSets.configureEach {
       reportUndocumented.set(true)
   }
   ```
   Dokka flags undocumented elements during generation, but `failOnWarning.set(false)` prevents build or CI failures.

---

## 3. Library Tiers & Breakdown

### Tier 1: Critically Incomplete ($< 25\%$ Coverage)
*28 Core Libraries — Massive public surfaces with minimal documentation.*

| Library | Files | Declarations | Documented | Missing | Coverage | Key Undocumented API Areas |
| :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| [`:awake:core:image`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/core/image) | 4 | 25 | 1 | 24 | **4.0%** | Image format decoding, pixel buffers, MIP generation |
| [`:awake:backend:jolt`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/backend/jolt) | 9 | 138 | 9 | 129 | **6.5%** | Native Jolt bridge, shape constructors, collision listeners |
| [`:awake:core:color`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/core/color) | 1 | 14 | 1 | 13 | **7.1%** | Color conversion formulas, RGBA / Oklch constants |
| [`:awake:backend:vulkan:bindings`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/backend/vulkan/bindings) | 145 | 1,277 | 100 | 1,177 | **7.8%** | Low-level Vulkan struct mappings, handles, enums |
| [`:awake:core:graphics2d`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/core/graphics2d) | 13 | 504 | 42 | 462 | **8.3%** | Path geometry, canvas stroke/fill styles, 2D transforms |
| [`:awake:asset:shader-pack`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/asset/shader-pack) | 25 | 764 | 65 | 699 | **8.5%** | Shader packages, standard PBR pipeline bindings, uniforms |
| [`:awake:engine:compose`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/engine/compose) | 2 | 31 | 3 | 28 | **9.7%** | Compose host integration, frame loop dispatchers |
| [`:awake:engine:bootstrap`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/engine/bootstrap) | 4 | 59 | 6 | 53 | **10.2%** | Game launcher, boot configuration, main application entry |
| [`:awake:asset:mesh-optimizer`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/asset/mesh-optimizer) | 2 | 17 | 2 | 15 | **11.8%** | Mesh simplification, index cache remap, vertex fetch |
| [`:awake:core:text`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/core/text) | 18 | 158 | 21 | 137 | **13.3%** | Font rasterization, glyph layout, text shaping metrics |
| [`:awake:ui:node-graph-canvas`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/ui/node-graph-canvas) | 7 | 215 | 30 | 185 | **14.0%** | Node editor canvas, pin connections, wire rendering |
| [`:awake:scene:canvas`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/scene/canvas) | 4 | 50 | 7 | 43 | **14.0%** | Scene canvas 2D overlay, billboard render nodes |
| [`:awake:scene:audio`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/scene/audio) | 2 | 21 | 3 | 18 | **14.3%** | Spatial 3D audio emitter components, audio listener ECS |
| [`:awake:core:io`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/core/io) | 10 | 202 | 29 | 173 | **14.4%** | Byte buffers, streams, asset loaders, virtual file system |
| [`:awake:backend:vulkan`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/backend/vulkan) | 56 | 736 | 132 | 604 | **17.9%** | Vulkan device, command buffers, descriptor sets, swapchain |
| [`:awake:kit:terrain-layers`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/kit/terrain-layers) | 7 | 163 | 31 | 132 | **19.0%** | Multi-layer splatting, procedural biome blend rules |
| [`:awake:core:audio`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/core/audio) | 6 | 92 | 18 | 74 | **19.6%** | Audio decoders, playback controllers, sound instances |
| [`:awake:project`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/project) | 3 | 80 | 16 | 64 | **20.0%** | Project manifests, package descriptors, scene index |
| [`:awake:engine:render:passes2d`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/engine/render/passes2d) | 11 | 187 | 38 | 149 | **20.3%** | 2D quad batching, font glyph pass, sprite rendering |
| [`:awake:ui:material3`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/ui/material3) | 2 | 28 | 6 | 22 | **21.4%** | Material3 adapters, theme tokens, styling wrappers |
| [`:awake:asset:shaders`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/asset/shaders) | 9 | 218 | 47 | 171 | **21.6%** | Embedded shader assets, SPIR-V reflection metadata |
| [`:awake:backend:webgpu`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/backend/webgpu) | 50 | 435 | 94 | 341 | **21.6%** | WebGPU pipeline layout, bind groups, canvas surface |
| [`:awake:net:api`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/net/api) | 3 | 80 | 18 | 62 | **22.5%** | Client/server packet contracts, replication protocol |
| [`:awake:scene:scene3d`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/scene/scene3d) | 65 | 601 | 137 | 464 | **22.8%** | Camera components, mesh instances, scene graph systems |
| [`:awake:core:math`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/core/math) | 22 | 588 | 136 | 452 | **23.1%** | `Vec3`, `Mat4`, `Quat`, raycasting math, projection matrices |
| [`:awake:ui:shadcn`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/ui/shadcn) | 92 | 895 | 210 | 685 | **23.5%** | Button, Card, Dialog, Table, Form, Menu widgets |
| [`:awake:compose:foundation`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/compose/foundation) | 42 | 663 | 158 | 505 | **23.8%** | Retained layout containers, gestures, text selection |
| [`:awake:core:geometry`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/core/geometry) | 13 | 248 | 60 | 188 | **24.2%** | Mesh buffers, bounding boxes, bounding spheres, vertex layouts |

---

### Tier 2: Substantially Incomplete ($25\% - 49.9\%$ Coverage)
*27 Core Libraries — Moderate documentation coverage, but extensive missing symbol docs.*

| Library | Files | Declarations | Documented | Missing | Coverage | Key Undocumented API Areas |
| :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| [`:awake:asset:shader-compiler`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/asset/shader-compiler) | 6 | 27 | 7 | 20 | **25.9%** | WGSL-to-SPIRV compilation, validation flags |
| [`:awake:scene:runtime`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/scene/runtime) | 25 | 257 | 67 | 190 | **26.1%** | Scene navigation routes, scene life cycle activation |
| [`:awake:scene:worldstream`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/scene/worldstream) | 5 | 78 | 21 | 57 | **26.9%** | Chunk loading, world streaming grid, tile caches |
| [`:awake:editor:contract`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/editor/contract) | 6 | 112 | 31 | 81 | **27.7%** | Editor plugin API, inspector provider contracts |
| [`:awake:scene:scene-core`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/scene/scene-core) | 9 | 35 | 10 | 25 | **28.6%** | Core entity identifiers, hierarchy metadata components |
| [`:awake:blueprint`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/blueprint) | 8 | 166 | 48 | 118 | **28.9%** | Node graph evaluation, pins, script connections |
| [`:awake:scene:physics`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/scene/physics) | 6 | 107 | 31 | 76 | **29.0%** | Rigid body components, collider attachments, contact queries |
| [`:awake:asset:terrain`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/asset/terrain) | 9 | 207 | 60 | 147 | **29.0%** | Clipmap generation, heightfield data loader, normals |
| [`:awake:core:animation`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/core/animation) | 6 | 84 | 25 | 59 | **29.8%** | Keyframe tracks, skeletal skinning, curve evaluation |
| [`:awake:compose:ui`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/compose/ui) | 53 | 867 | 259 | 608 | **29.9%** | Retained UI nodes, layout modifiers, measure policy |
| [`:awake:engine:platform`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/engine/platform) | 15 | 119 | 36 | 83 | **30.3%** | Windowing contract, platform host abstractions, input events |
| [`:awake:core:math2d`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/core/math2d) | 5 | 49 | 15 | 34 | **30.6%** | Rect, Point, Size, 2D matrix utilities |
| [`:awake:physics:ragdoll`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/physics/ragdoll) | 5 | 81 | 25 | 56 | **30.9%** | Ragdoll joint constraints, bone collider bindings |
| [`:awake:navigation`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/navigation) | 16 | 242 | 75 | 167 | **31.0%** | Navmesh queries, A* pathfinder, crowd avoidance |
| [`:awake:scene:controls`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/scene/controls) | 14 | 168 | 54 | 114 | **32.1%** | Orbit camera controller, first-person camera controller |
| [`:awake:ai:behavior`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/ai/behavior) | 14 | 108 | 35 | 73 | **32.4%** | Behavior tree nodes, selectors, decorators, actions |
| [`:awake:core:input`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/core/input) | 6 | 70 | 25 | 45 | **35.7%** | Keyboard, pointer, gamepad event data structures |
| [`:awake:ecs`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/ecs) | 30 | 379 | 137 | 242 | **36.1%** | Component storage, family queries, entity archetypes |
| [`:awake:core:config`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/core/config) | 5 | 35 | 14 | 21 | **40.0%** | App configuration, engine setting properties |
| [`:awake:core:host`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/core/host) | 9 | 39 | 16 | 23 | **41.0%** | Fixed timestep frame loop, engine ticker |
| [`:awake:engine:render:passes`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/engine/render/passes) | 43 | 416 | 172 | 244 | **41.3%** | Shadow map pass, forward PBR pass, skybox pass |
| [`:awake:scene:authoring`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/scene/authoring) | 12 | 101 | 42 | 59 | **41.6%** | GameModule DSL, scene flow builder, test authoring |
| [`:awake:scene:world`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/scene/world) | 8 | 67 | 28 | 39 | **41.8%** | Large-world coordinate offsets, floating origin |
| [`:awake:node-graph`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/node-graph) | 6 | 81 | 36 | 45 | **44.4%** | Graph serialization, catalogue definition |
| [`:awake:tailwind`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/tailwind) | 4 | 101 | 45 | 56 | **44.6%** | Tailwind class mapper, design tokens |
| [`:awake:scene:document`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/scene/document) | 14 | 150 | 70 | 80 | **46.7%** | Scene JSON schema, entity serialization |
| [`:awake:engine:render:contract`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/engine/render/contract) | 48 | 491 | 237 | 254 | **48.3%** | Hardware Abstraction Layer, texture descriptors, pipelines |

---

### Tier 3: Moderately Incomplete ($50\% - 74.9\%$ Coverage)
*8 Core Libraries — Solid baseline, but still incomplete on secondary functions/properties.*

| Library | Files | Declarations | Documented | Missing | Coverage | Key Undocumented API Areas |
| :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| [`:awake:core:logging`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/core/logging) | 3 | 42 | 22 | 20 | **52.4%** | Ring buffer log appender, log sinks |
| [`:awake:ai`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/ai) | 21 | 99 | 53 | 46 | **53.5%** | Blackboard state, steering behaviors |
| [`:awake:asset:shader-dsl`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/asset/shader-dsl) | 22 | 393 | 211 | 182 | **53.7%** | WGSL AST builder, stage built-ins |
| [`:awake:compose:runtime`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/compose/runtime) | 5 | 61 | 33 | 28 | **54.1%** | State snapshots, CompositionLocal scope |
| [`:awake:asset:gltf`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/asset/gltf) | 7 | 268 | 158 | 110 | **59.0%** | GLTF JSON parser, node hierarchy unpacker |
| [`:awake:scene:blueprint`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/scene/blueprint) | 4 | 36 | 23 | 13 | **63.9%** | Blueprint scene system wiring |
| [`:awake:physics:api`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/physics/api) | 13 | 126 | 83 | 43 | **65.9%** | Heightfield colliders, raycast hit results |
| [`:awake:core:di`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/core/di) | 5 | 44 | 29 | 15 | **65.9%** | Service container, module injection DSL |

---

### Tier 4: Well Documented ($\ge 75\%$ Coverage)
*5 Core Libraries — High completeness meeting public API documentation standards.*

| Library | Files | Declarations | Documented | Missing | Coverage | Notes |
| :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| [`:awake:compose:state`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/compose/state) | 1 | 8 | 6 | 2 | **75.0%** | Only 2 helper properties lack KDoc |
| [`:awake:core:state`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/core/state) | 6 | 24 | 19 | 5 | **79.2%** | Store and effect contracts well-described |
| [`:awake:scene:binding`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/scene/binding) | 2 | 27 | 25 | 2 | **92.6%** | ECS component binding reflection helpers |
| [`:awake:heroicons`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/heroicons) | 1 | 9 | 9 | 0 | **100.0%** | Complete |
| [`:awake:compose:di`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/compose/di) | 1 | 7 | 7 | 0 | **100.0%** | Complete |

---

### Tools, Benchmarks & Test Harnesses
*Auxiliary modules (not shipped as consumer runtime libraries).*

| Module | Type | Total Declarations | Documented | Missing | Coverage |
| :--- | :---: | :---: | :---: | :---: | :---: |
| [`:awake:backend:vulkan:generator`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/backend/vulkan/generator) | Tool / Generator | 267 | 0 | 267 | **0.0%** |
| [`:awake:ui:font-atlas-generator`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/ui/font-atlas-generator) | Tool / Generator | 72 | 1 | 71 | **1.4%** |
| [`:awake:ui:benchmark`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/ui/benchmark) | Benchmark | 27 | 2 | 25 | **7.4%** |
| [`:awake:ecs:benchmark`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/ecs/benchmark) | Benchmark | 514 | 53 | 461 | **10.3%** |
| [`:awake:tailwind-generator`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/tailwind-generator) | Tool / Generator | 12 | 2 | 10 | **16.7%** |
| [`:awake:compose:ui-testing`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/compose/ui-testing) | Test Fixture | 199 | 36 | 163 | **18.1%** |
| [`:awake:engine:render:parity`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/engine/render/parity) | Test Fixture | 66 | 21 | 45 | **31.8%** |
| [`:awake:engine:render:testing`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/engine/render/testing) | Test Fixture | 58 | 20 | 38 | **34.5%** |
| [`:awake:scene:scene3d:benchmark`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/scene/scene3d/benchmark) | Benchmark | 29 | 10 | 19 | **34.5%** |

---

## 4. Priority Remediation Plan

To bring libraries up to complete KDoc compliance according to [`docs/kdoc-guidelines.md`](file:///Users/ronvaldoz/StudioProjects/awaken/docs/kdoc-guidelines.md):

1. **Phase 1: High-Impact Foundation Libraries**
   - [`:awake:core:math`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/core/math) & [`:awake:core:geometry`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/core/geometry) (Vectors, Matrices, Bounds, Mesh Buffers)
   - [`:awake:engine:bootstrap`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/engine/bootstrap) & [`:awake:engine:platform`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/engine/platform) (App lifecycle & configuration)
   - [`:awake:ecs`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/ecs) (Component storage, queries, archetypes)
   - [`:awake:physics:api`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/physics/api) (Rigid body, shapes, contact listener)

2. **Phase 2: Rendering & Asset Core**
   - [`:awake:engine:render:contract`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/engine/render/contract) & [`:awake:engine:render:passes`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/engine/render/passes)
   - [`:awake:backend:vulkan`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/backend/vulkan) & [`:awake:backend:webgpu`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/backend/webgpu)
   - [`:awake:asset:gltf`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/asset/gltf) & [`:awake:asset:shaders`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/asset/shaders)

3. **Phase 3: Compose & UI Design System**
   - [`:awake:compose:ui`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/compose/ui) & [`:awake:compose:foundation`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/compose/foundation)
   - [`:awake:ui:shadcn`](file:///Users/ronvaldoz/StudioProjects/awaken/awake/ui/shadcn)

4. **Phase 4: Tooling & Continuous Verification**
   - Enable `UndocumentedPublicClass`, `UndocumentedPublicFunction`, and `UndocumentedPublicProperty` in [`config/detekt/detekt.yml`](file:///Users/ronvaldoz/StudioProjects/awaken/config/detekt/detekt.yml) with per-module baselines (`detekt-baseline.xml`) so new code cannot merge without KDocs.
   - Configure Dokka's `failOnWarning.set(true)` on stable published releases.
