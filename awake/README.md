# Awake modules

A map of the engine's modules. For *why* they are grouped this way, and where a new one should
go, see [docs/reference/module-architecture.md](../docs/reference/module-architecture.md) — the
source of truth for module decisions.

Paths below are Gradle coordinates minus the `:awake:` prefix.

## core — foundation, no engine concepts

`awake:core` itself no longer exists; it was five independent packages wearing one module name.

| Module | What it is |
|---|---|
| `core:color` | `Color`. One file, zero dependencies, 17 consumers -- the most-depended-on type in the group |
| `core:input` | Pointer/key/text input state, plus the Android IME bridges |
| `core:image` | `Bitmap` decode per platform, `BitmapRgba8` |
| `core:host` | Services the host platform provides: `FrameLoop`, `FixedTimestepLoop`, resource bytes |
| `core:math` | `Vec3/4`, `Mat3/4`, `Quat`, `Ray`, `Frustum`, `Aabb`, `Plane`, `Lens`, `ClipSpace`, `Angle`. 3D math. Depends only on `core:math2d`, for the `Vec2` its projections return |
| `core:math2d` | `Vec2`, `Rectangle`, `Size2D`, `Dp`, `Sp`, `UiDensity` — screen-space 2D primitives and units. Zero dependencies |
| `core:graphics2d` | `UiDrawPrimitive`, `UiPath`, `UiGradient`, the CPU tessellators, `RendererVertexWriters`, `UiVertexLayout` — the 2D draw vocabulary both UI engines emit and both backends consume |
| `core:geometry` | `MeshGeometry`, `VertexFormat`, `VertexAttribute`, `GpuDataShape`, `VertexSemantic`, `MeshSimplifier`, `NormalizedInt` — the CPU mesh-data vocabulary. One dependency (`core:math`), no UI |
| `core:animation` | Skeleton, skin, clip sampling, crossfade blending |

## ecs — entity storage and iteration

| Module | What it is |
|---|---|
| `ecs` | Sparse-set component storage, families, tag columns, `System` |
| `ecs:benchmark` | kotlinx-benchmark harness comparing against Fleks |

## engine + backend — rendering and application lifecycle

> Both group names are layer names, not subsystems, and rendering is split across them. See the
> architecture doc's [target grouping](../docs/reference/module-architecture.md#target-grouping--decided).

| Module | What it is |
|---|---|
| `engine:render:contract` | `Renderer`, `DrawCall`, `Material`, `Mesh` (GPU handle), uniform layouts. CPU mesh data lives in `core:geometry` |
| `engine:render:passes` | Backend-neutral **3D** pass logic: feature dispatch, opaque/sky recording, instance packing, uniform packers, pools |
| `engine:render:passes2d` | Backend-neutral **2D** pass logic: run coalescing, mesh upload, command recording, `UiPass`/`UiRenderFeature` |
| `engine:platform` | `GraphicsEngine` bootstrap shared by both backends |
| `engine:bootstrap` | App lifecycle DSL |
| `engine:app` | `AwakeApplication` — the `expect`/`actual` seam picking a backend per target |
| `backend:vulkan` | Vulkan renderer. Android, desktop, iOS. MoltenVK on Apple platforms |
| `backend:vulkan:bindings` | JNI/cinterop surface, plus its generator |
| `backend:webgpu` | WebGPU renderer via wgpu4k. wasmJs only |
| `backend:jolt` | Jolt physics bridge |

## physics

| Module | What it is |
|---|---|
| `physics:api` | `PhysicsWorld`, `BodyHandle` — backend-neutral |

## scene — ECS components and systems on top of the engine

| Module | What it is |
|---|---|
| `scene:scene-core` | `Transform`, `Name`, hierarchy, `TransformSystem`, `SpinControl`. What every scene has |
| `scene:rendering` | `MeshRenderer`, `Camera`, `Light`, `ParticleEmitter`, `RenderSystem` |
| `scene:controls` | `camera` (rig, modes, input) and `movement` (control, player input, matrix-relative) |
| `scene:physics` | `PhysicsBody` and `PhysicsSystem`, plus `character` and `streaming` |
| `scene:navigation` | `NavMesh` and `PathRequest` (the contract), plus `NavGridTile`, slope bake, A* + smoothing |
| `scene:world` | Cell coordinates, partitioning, async streaming, floating origin |
| `scene:ai` | `PatrolBehavior`, `ChaseBehavior`, `FleeBehavior`, `RouteFollower` and their systems. Depends on navigation; navigation does not depend on it |
| `scene:runtime` | Scene JSON load/save, entity instantiation |
| `scene:authoring` | The `scene { }` DSL |
| `scene` | Aggregator re-exporting the modules above |

## editor — the scene editor and its extension points

| Module | What it is |
|---|---|
| `editor` | Store, selection, tools, undo, providers, `EditorPlugin`. Knows nothing of ECS — `EditorEntityId` is a string, and `EditorFieldScope`/`EditorInspector<T>` name no entity or world |
| `editor:scene` | The ECS adapter: gizmo, hierarchy, inspector sections, `SceneComponentInspector` |
| `editor:physics` | Makes `PhysicsBody` inspectable. Exists because `editor:scene` must not depend on the physics backend and `scene:physics` must not depend on an editor |
| `editor:ai` | Inspectors for `PatrolBehavior`, `ChaseBehavior`, `FleeBehavior` |
| `editor:render` | Inspectors for `PbrMaterial`, `Light`, `MeshRenderer`, `Camera` |

## ui — component families and UI support

Hand-written, not Compose. See [ui/README.md](ui/README.md) and
[docs/reference/ui-ownership.md](../docs/reference/ui-ownership.md) for which layer owns what.

| Module | What it is |
|---|---|
| `ui:graphics` | Draw primitives, `Rectangle`, geometry |
| `ui:text` | Font atlas, glyph metrics, shaping |
| `ui:ui-core` | Layout engine, modifiers, measurement — Compose-UI-shaped |
| `ui:headless` | Unstyled controls and layout — Foundation-shaped |
| `ui:material3` | Material 3 color schemes and components, beginning with `Scaffold` |
| `ui:shadcn` | Shadcn recipes and theme tokens |
| `ui:animation` | Tweening and transitions |
| `ui:tailwind`, `ui:tailwind-generator` | Tailwind-style token generation |
| `ui:heroicons` | Generated icon vectors |
| `ui:testing` | Rasterizer, semantic inspection, snapshot harness |

## asset — content pipelines

| Module | What it is |
|---|---|
| `asset:gltf` | glTF parsing: meshes, skins, animations, PBR materials |
| `asset:shaders` | `ShaderSet`/`ShaderSource` contract only |
| `asset:shader-pack` | The shipped `.wgsl` set and its uniform layouts — opt-in content |
| `asset:mesh-optimizer` | Decimation CLI over `core:geometry` |

## compose

| Module | What it is |
|---|---|
| `compose:runtime`, `compose:ui`, `compose:foundation` | Retained layout engine, in design. See [docs/reference/compose-engine/](../docs/reference/compose-engine/) |

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
