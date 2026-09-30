# Glossary

<p class="awake-lede">Short meanings for the words these docs use in a narrower sense than everyday English.</p>

## Products and authoring

| Term | Meaning |
| --- | --- |
| **AwakeKt** | The project. **AwakeKt Engine** is the runtime these docs describe; **AwakeKt Studio** is the desktop editor. |
| **ECS** | Entity component system: a `World` of entities, the components attached to them, and the `System`s that update them. |
| **scene document** | A `*.scene.json` file (`SceneDocument`), loaded by `SceneLoader` and saved by AwakeKt Studio. See [Scene document schema](scene-document-schema.md). |
| **scene DSL** | Kotlin that builds ECS content: `world.scene { entity("…") { … } }` or `app { scene("…") { … } }`. |
| **component id** | The name of a component inside a scene document, such as `light` or `mesh_renderer`. See [Scene document components](scene-document-components.md). |
| **scene runtime** | The `awake:scene:runtime` module: `SceneManager`, scene sessions, the scene asset library, scheduling. Not the whole scene module family. |
| **binding** | The two-way link between a component id and an ECS component (`SceneComponentBinding`). It attaches the component on load and exports it on save. |
| **registry** | `SceneComponentRegistry`, the set of bindings a load uses. A component id loads only once its binding is registered. |
| **prefab** | A reusable node tree (`ScenePrefab`) with a GUID. A node links to one with `prefab_link`. |
| **extension record** | Data in a scene document's `extensions` array, owned by an extension and kept as written when that extension is absent. |

## Rendering

| Term | Meaning |
| --- | --- |
| **render hardware interface (RHI)** | The backend-neutral boundary to the GPU. The type is `GpuDevice`. See [Render hardware interface](rhi.md). |
| **backend** | The layer that translates the render contract to one graphics API: Vulkan or WebGPU. |
| **render contract** | `engine:render:contract`: the vocabulary a backend and its callers share. It names no scene content. |
| **render passes** | `engine:render:passes`: shared features that turn scene input into ordered GPU work. No Vulkan or WebGPU types. |
| **capability** | Something a backend can do, such as a depth pre-pass or an optional `GpuCapability`. |
| **content** | What is drawn, such as a sky or fog. Content is never named inside a backend. |
| **declaration** | Saying what an app renders with, independent of any GPU: `RenderPlan`, `ScenePipeline`, `PipelineSpec`. |
| **resolution** | Turning a declaration into GPU objects on a real device. Each backend does its own. |
| **render plan** | `RenderPlan`: the pipelines an app renders with, written once for every backend. |
| **narrowing** | `RenderPlan.narrowedTo(capabilities)`: dropping what a backend cannot run, and reporting each drop. |
| **pipeline** | Compiled GPU state: shaders, vertex layout, blend, depth, cull. Described by a `PipelineSpec`, looked up by a `PipelineKey`. |
| **shader set** | `ShaderSet`: one shader with a half for each backend. |
| **vertex format** | `VertexFormat`: a mesh's vertex attribute layout, and the key that matches a mesh to a pipeline. |
| **pass** | A span of GPU recording with one set of attachments, such as the scene pass or a depth pre-pass. |
| **render feature** | `RenderFeature`: one unit of work inside a pass, run in order. |
| **clip space** | The coordinates a projection matrix outputs. It differs between Vulkan and WebGPU, so `GpuDevice.clipSpace` reports it. |
| **shadow cascade** | One slice of the view, from the camera out to the shadow distance, with its own part of the shadow map. |

## Physics

| Term | Meaning |
| --- | --- |
| **collision shape** | A geometric description used for contacts and ray hits, passed when a body is created. Not a render mesh. |
| **heightfield** | `HeightFieldShape`: a collision surface sampled on a square, row-major grid. |
| **sensor** | A body that detects what passes through it instead of blocking it. |
| **capability gap** | A shape or query one physics backend cannot run. It throws `PhysicsCapabilityException` rather than doing something else. |

## UI

| Term | Meaning |
| --- | --- |
| **AwakeKt Compose** | The `awake:compose:*` UI runtime. Compose-shaped, but its own engine, not Jetpack Compose. |
| **Compose Foundation** | `awake:compose:foundation`: `Row`, `Column`, `Box`, scrolling and layout modifiers. |
| **shadcn components** | `awake:ui:shadcn`: themed component recipes built on Compose Foundation. `Shadcn*` is only the code prefix. |
| **game UI** | Screen-space UI stored in a scene as `canvas_element` components (`awake:scene:canvas`). |
| **`Dp`** | Density-independent pixel: a size that looks the same at any screen density. |
| **constraints** | The minimum and maximum size a parent offers a child during layout. |
| **semantics** | The meaning of UI for tests and accessibility: ids, roles, labels, bounds. Not pixels. |

## Testing

| Term | Meaning |
| --- | --- |
| **golden** or **baseline** | A saved expected image or value a test compares against. It shows output changed, not that it is correct. |
| **headless** | Rendering without a window, for tests. See `engine:render:testing`. |
| **docs sample test** | A test named `…DocsSampleTest` whose code regions are included in these docs, so every sample compiles and runs. |

## See also

- [Scene document schema](scene-document-schema.md)
- [Render hardware interface](rhi.md)
- [Modules](modules.md)
