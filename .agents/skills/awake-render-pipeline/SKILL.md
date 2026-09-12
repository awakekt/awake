---
name: awake-render-pipeline
description: Rules for structuring render features, pipelines, materials and the application bootstrap in Awake's backend renderers (Vulkan today, WebGPU later). Read before adding a new render feature (shadow/opaque/skybox/UI-style pass), before wiring a new RenderPipeline into Renderer, before touching draw-call sorting/batching in RendererDraw3D, or before changing GameApplication/Game wiring. Trigger keywords - RenderFeature, RenderPipeline, Material, PipelineTable, vkCmdBindPipeline, vkCmdBindDescriptorSets, draw call sorting, state batching, recordCommandBuffer, groupBy pipeline, GameApplication, Game.ready, Game.render, mediator.
---

# Render feature / pipeline / material architecture in Awake

Pairs with [render-extensibility.md](../../docs/reference/render-extensibility.md), which
governs *whether* a pipeline is opt-in content vs an always-available capability. This skill
governs *how* render features, pipelines and materials are structured and composed once that
call is made.

Four patterns apply here (§1-4). All hold today and must not regress; §5 is the cross-backend
rule that governs where any of them may live.

## 0. Shader source of truth

UI shader math is authored in `awake:asset:shader-pack` ASL definitions, not separately in
Vulkan GLSL and WebGPU WGSL. Run `./gradlew :awake:asset:shader-pack:generateAslShaders` after
changing an ASL definition; the existing validation/sync pipeline then emits WGSL and derives
Vulkan SPIR-V through naga. Generated files under `src/commonMain/resources/shaders`,
`src/wasmJsMain/resources`, and `src/appMain/resources` are reviewable artifacts, never manual
authoring locations.

Do not port a shader by copying one backend's source into the other. First compare its uniform
layout, vertex locations, varyings, bindings, and entry points with the live backend resource;
then add or update the ASL definition and its drift test. If an existing shader cannot yet be
represented without an ABI change, keep the current resource and record the migration gap rather
than silently generating an incompatible replacement. The current exception is
`ui_target_composite`, whose Vulkan and WebGPU binding layouts still need a shared contract
decision. `fwidth` and other fragment derivatives are valid in generated GPU shaders, but are not
meaningful in the CPU ASL evaluator.

## 0.5 Two-Layer Rendering Model — READ BEFORE TOUCHING `Renderer.kt`

Awake rendering has exactly two layers. Crossing the boundary is the primary source
of architectural debt in this codebase.

**LAYER 1 — Hardware Abstraction Layer (HAL)**
- Module: `awake:engine:render:contract` (`GpuDevice` / `Renderer`)
- Owns: pipelines, buffers, textures, samplers, command recording, swapchain, viewport scissors, pixel readback, `GpuPassInput`, `GpuSubPass`, `GpuDrawCommand`
- NEVER owns: `SceneLight`, `PointLight`, `RenderDrawCommand`, `Lens`, `EnvironmentUniforms`, `ScenePassDescriptor`, `ShadowCascades`, `DirectionalShadowBox`, `ShadowCascadeUniforms`, `SkyboxUniforms`, `SkyboxFields`, `ParticleUniforms`, `DepthFogFields`, `InfiniteGridFields`, or any scene default constants

**LAYER 2 — Render Graph / Scene System**
- Modules: `awake:engine:render:passes`, `awake:asset:shader-pack`, `awake:scene:rendering`
- Owns: `SceneLight`, `RenderDrawCommand`, `Lens`, `EnvironmentUniforms`, `ScenePassDescriptor`, all shadow cascade algorithms, all content-specific uniform layouts, all scene default constants, `RenderFeature` list and dispatch
- Translates: scene data → raw GPU passes and pre-packed byte/float buffers (`GpuPassInput`) BEFORE calling `GpuDevice`

**The test:** *"Could a third backend implement this type unchanged, without knowing what scene content it serves?"* A type that fails this test does not belong in `render:contract`.

**COMMON MISTAKE — "I need to pass fog/light/shadows to the backend":**
- ❌ WRONG: add a field or parameter to `Renderer` / `GpuDevice`
- ✅ RIGHT: pack the data into a `FloatArray`/`ByteArray` in `render:passes`, pass raw bytes and generic `GpuSubPass` executions to the backend via `GpuPassInput`

**Phase 2 status (D31):** `Renderer` consumes `GpuPassInput`; `render:passes` invokes the
contract-owned generic `GpuDrawPreparer` with `GpuDrawRequest` values. The request exposes only
`GpuMesh`/`GpuMaterial` handles and generic draw state, never richer authored resource interfaces.
The backend layering ledger is empty, so no backend source file imports scene-authored vocabulary.
Each backend supplies only its resource-handle preparation implementation and returns resolved
packets to the renderer.

Uniform ABI writes are guarded by the root `verifyRenderUniforms` task. It scans the complete
production source text, including `awake:asset:shader-pack` and multiline calls, and rejects
literal indices, hand-sliced matrix/light payloads, numeric `copyOfRange` payloads, direct indexed
`*Uniform`/`*Params` scratch writes, and WGSL binding-text inference. Use
`UniformLayout`/`UniformWriter` and named fields for every new uniform write.
The root `verifyRenderContractBoundary` task is also wired into `check`; it rejects scene or
render-pipeline imports in `render:contract` and prevents source packets from regressing to
authored `Mesh`/`Material` fields.

## 1. Strategy pattern for render features — in place, keep it that way

`Renderer` used to wire each pass as its own nullable field (`skyboxRenderPipeline`,
`shadowRenderPipeline`, lazily-built UI pipelines) while `RendererDraw3D.recordCommandBuffer`
hardcoded the pass order, so adding a pass meant editing the frame-loop function itself.

Both backends now hold an ordered `renderFeatures: List<RenderFeature<*RenderFrameContext>>`,
and `SkyboxRenderPipeline` no longer exists on either — a content feature declares a
`PipelineSpec` and the shared `ContentFeature` builds it. Adding a feature (e.g. post-process
blur) means adding to that list, not touching `recordCommandBuffer`. Do not reintroduce a
per-pass nullable field.

**`RenderFeature` depends on a `RenderFrameContext` port, never on `Renderer` directly.**
An extension function shaped `fun Renderer.recordCommands(...)` (matching the sibling-file
`internal fun Renderer.xxx(...)` convention `RendererDraw3D.kt`/`RendererUiPipelines.kt`
already use) looks natural but isn't real Strategy — every feature would still see the whole
`Renderer` surface, so the god class doesn't actually shrink. Use a narrow interface instead:

```kotlin
interface RenderFrameContext {
    val commandBuffer: Long
    val frameIndex: Int
    val groupedDrawCalls: Map<RenderPipeline, List<PreparedDrawCall>>
    val primaryPipeline: RenderPipeline
    fun recordDrawCalls(drawCalls: List<PreparedDrawCall>)
    // + narrow, purpose-built accessors for anything else a feature needs (e.g. lazily
    // built UI pipelines, a pooled mesh allocator) -- never the whole Renderer.
}

interface RenderFeature {
    fun recordCommands(context: RenderFrameContext)
    fun destroy()
}
```

Only one small adapter class implements `RenderFrameContext` by delegating into `Renderer`'s
real internals; every feature depends on the interface, not that adapter or `Renderer`
itself.

**The depth pre-pass is not a `RenderFeature`.** It owns its own render pass
(`recordDepthPrePass` is a wholly separate function from `recordCommandBuffer`, since a
`DepthTarget`'s render pass is not the scene pass), so `RenderFeature` only covers the 3
features that genuinely share a pass (`Opaque`, `Skybox`, `UI`). `DepthPrePassFeature` stays
its own class with its own signature. This rule applies **only** to `RenderFeature`
registration and pass dispatch ordering — don't create a shared pass-ownership interface just
because you can. It does **not** mean "keep adding scene vocabulary to `GpuDevice`/`Renderer`".
Those are separate concerns: the HAL boundary (§0.5) is non-negotiable regardless of how many
passes exist. See
[docs/audits/2026-08-19-render-feature-strategy-plan.md](../../docs/audits/2026-08-19-render-feature-strategy-plan.md)
for the full worked design, including why the receiver-on-`Renderer` shortcut and an earlier
3-way sealed hierarchy were both rejected.

Rules that keep this shape:

- Wrap existing pipelines (`DepthOnlyPipeline`, `LineRenderPipeline`, the UI pipelines)
  behind `RenderFeature` implementations — do not merge their internals.
  Each keeps its own `bind()`/`destroy()`; the feature wrapper is what standardizes the
  outward-facing contract.
- The opt-in-content-vs-capability rule from `render-extensibility.md` still applies to each
  feature. A `RenderFeature` for authored content (skybox) is still constructed only when the
  consumer supplies one; it does not become non-null just because it is now list-managed.
- Order in the list is behavior — shadow must run before opaque (opaque samples the shadow
  map), opaque before UI. If a feature depends on another's output, say so at the
  registration site, same convention as ECS system registration order.
- `PipelineTable` (`RendererResources.kt`) stays as the per-`VertexFormat` pipeline registry
  *inside* the opaque/3D `RenderFeature` — it is not itself a list of features, don't conflate
  the two.

## 2. Material is data, Pipeline is shader execution — already correct, keep it that way

`Material` (`material/Material.kt`) owns the descriptor set, uniform buffer and per-frame
uniform slots. It implements the cross-backend `RenderMaterial` interface and knows nothing
about which `RenderPipeline` it will be bound into.

The join key between mesh geometry and pipeline is **`VertexFormat`**, not `Material`.
`DrawCall.mesh.format` resolves which `RenderPipeline` runs; `Material` only supplies the
descriptor set bound into whatever pipeline layout was already chosen. Do not add a
`material.pipeline` back-reference or let `Material` pick its own pipeline — that recouples
what this split deliberately keeps apart, and breaks the case where two materials with
different textures share one pipeline.

## 3. Draw-call batching — pipeline-level sorting exists, descriptor-set churn does not (yet)

`recordCommandBuffer` and `renderToTexture` both do `drawCalls.groupBy { it.pipeline }` before
recording, so `vkCmdBindPipeline` is called once per pipeline per frame, not once per draw
call. Preserve this grouping in any new 3D `RenderFeature` — don't flatten back to
per-draw-call pipeline binds.

Within a pipeline group, `Material` (i.e. descriptor set) is rebound on every draw call —
`vkCmdBindDescriptorSets` churn is not currently minimized. If a future change sorts draw
calls by `Material` inside a pipeline group to cut that churn, it must stay 3D-only:

- **UI draw order is intentionally NOT pipeline/material-sorted.** `RendererDraw3D.kt`
  documents this at the UI dispatch site — UI elements can overlap, and pipeline batching
  would silently reorder paint order. Never apply material/pipeline sorting to the UI
  `RenderFeature`.

## 4. Mediator pattern for the application bootstrap — already correct, keep it that way

`GameApplication` (`engine/game/GameApplication.kt`) is the mediator between three parties
that must never reference each other directly: the platform window
(`WindowApplication`/`create`/`resize`/`dispose` callbacks), the backend `Renderer`
(constructed by each subclass's `createBackendResources`), and the injected `Game` (the
actual scene/gameplay logic, via `Game.ready(renderer)` / `Game.render(delta, w, h)` /
`resize` / `pause` / `resume` / `dispose`).

- `Game` never touches the window or backend GPU types directly — it only ever sees the
  backend-neutral `Renderer` interface handed to it in `ready(renderer)`.
- `VulkanGameApplication`/`WebGpuGameApplication` never know what the game draws — they only
  build GPU resources (`createBackendResources`) and forward lifecycle calls
  (`update`/`resize`/`pause`/`resume`/`dispose`) to `game`. This is the same boundary
  `render-extensibility.md` enforces from the content side: backend subclasses supply
  capabilities/resources, never authored scene content.
- Do not let a backend subclass reach into `Game`'s internals, and do not let `Game`
  construct or hold backend-concrete types (`GraphicsDevice`, `SwapchainManager`,
  `RenderPipeline`) — only the `Renderer` interface crosses that boundary. If a new backend
  capability needs exposing to games, add it to the shared `Renderer` interface
  (`render/renderer/Renderer.kt`), not as a backend-specific escape hatch reached through
  casting.
- One `GameApplication` instance owns exactly one `Game` and one `Renderer` for its whole
  lifecycle — it is a per-session mediator, not a registry. A game that needs multiple
  scenes swaps `Game` implementations or manages that internally; it doesn't ask
  `GameApplication` to hold a list.

## 5. Cross-backend commonization and reusability rule (Vulkan ↔ WebGPU)

Awake maintains two graphics backends: Vulkan (`awake:backend:vulkan`) and WebGPU (`awake:backend:webgpu`).
Duplicating rendering math, vertex layout definitions, buffer packing, batching, pool logic, frame orchestration (pass order, feature dispatch), or paint-order loops across both backends is
strictly forbidden. Treat the list as illustrative: if the same decision is being made in both
backends, it belongs in the shared layer. Two defects reached `main` because they fell outside an
earlier, shorter version of this list -- the UI paint-order loop existed in both backends, and only
Vulkan had a `RenderFeature` list at all.

The boundary these layers describe has a name: the **RHI**, faced by `GpuDevice` in
`render:contract`. Read `docs/reference/render-hardware-interface.md` before adding anything to
the shared layer, and `docs/reference/backend-commonisation.md` for what is still duplicated.

### Module placement rule for rendering code

| Module | Owns | Must not own |
|---|---|---|
| **`awake:engine:render:contract`** | Stable backend-neutral contracts, interfaces, GPU data shapes (`GpuDataShape`), vertex semantics (`VertexSemantic`), canonical `VertexFormat`, resource handles, and low-level layout vocabulary. | Backend types, driver calls, rendering policy, source draw packets, source-resolution bridges, filesystem output, scene lowering, or test helpers. |
| **`awake:engine:render:passes`** | Shared scene/general rendering algorithms and `RenderFeature` bodies: source draw packets and resolution, draw preparation, vertex/uniform writers, batching, and pass ordering. | Vulkan/WebGPU types, UI-framework vocabulary, or desktop/platform IO. |
| **`awake:engine:render:passes2d`** | Shared 2D primitive coalescing, mesh-upload/recording ports, and 2D pass algorithms. It is intentionally UI-framework-free. | Widgets, layout/state/theme code, backend objects, or paint-order re-sorting. |
| **`awake:engine:render:testing`** | KMP test/diagnostic helpers built on the contract: pixel buffers, offscreen capture lifetime, assertions, and platform-specific diagnostic writers in their platform source set. | Production renderer APIs, backend construction, or application/game content. |
| **`awake:backend:vulkan` / `awake:backend:webgpu`** | Only driver-specific bindings and GPU resource allocation/recording (e.g. Vulkan JNI handles, WebGPU wgpu4k wrappers, command buffer encoders, descriptor set / bind group caching). Backend test source sets may adapt shared `render:testing` helpers to a real backend fixture. | Shared decisions, CPU rendering algorithms, UI/game content, or reusable cross-backend test helpers. |

The deciding test is: **could a second backend use the code unchanged?** Put a stable shape or
port in `contract`, an algorithm using that port in `passes`/`passes2d`, and a diagnostic in
`render:testing`. Only code that must spell Vulkan/WebGPU belongs in a backend.

### Hard rules for agents:
- **Never hardcode a size, stride, offset or colour that a type already derives.** Four typed
  vocabularies exist for this; a bare literal or `FloatArray` where one of them fits is the defect,
  not a shortcut. Each was added *after* the duplication it prevents had already shipped, so the
  literals keep coming back:

  | Use | Type | Never write |
  |---|---|---|
  | Vertex attributes, strides, offsets | `VertexFormat.entries` / `GpuDataShape` | a hardcoded attribute array, `stride = 15`, or `FLOATS_PER_INSTANCE = 4` |
  | Uniform block sizes | `UniformLayout.total`, `UniformFields.*` | `UNIFORM_FLOAT_COUNT = 24`, or any hand-summed count |
  | Uniform block *contents* | `UniformWriter` | `mvp.data + lightFloats + model.data + ...` |
  | Any colour | `core.colors.Color` | `floatArrayOf(r, g, b, a)` as a colour parameter or field |

  `GpuDataShape` sizes both vertex data (`componentCount` / `vertexByteSize`, unpadded) and
  uniform fields (`uniformFloats`, std140 vec3->vec4 padded) -- pick by which buffer it lands in.

  Why each rule exists, in one line: a duplicated stride literal made WebGPU's rounded quad 15
  where the shared truth was 16; two constants both hardcoded 24 with one's KDoc admitting it
  "mirrors" the other; hand-concatenated uniform blocks were kept in step by a *comment* saying
  the order matched the shader; and `FloatArray` colours forced six defensive `.copyOf()` calls
  plus a padding helper for the 3-vs-4 channel case.

- **Never hardcode vertex attribute descriptions or byte offsets in backend pipelines.** All pipelines (3D, shadow, UI, lines) MUST dynamically derive their GPU vertex attribute descriptions directly from `VertexFormat.entries` (`location = entry.attribute.location`, `format = entry.attribute.format.toVkFormat()` / `toGpuVertexFormat()`, `offset = entry.offsetBytes`).
- **If logic is identical or symmetric between Vulkan and WebGPU, move it to common immediately.** Do not maintain two copies of vertex writers, uniform packers, or batch coalescers.
- **Backend architecture must remain symmetric.** If Vulkan has `PipelineTable` or `GpuBufferPoolManager`, WebGPU must mirror the same architecture and typed parameter grouping.
- **Vulkan is the primary backend; WebGPU exists because browsers cannot run Vulkan.** It is a
  compatibility target and is expected to lag (it records a depth pre-pass but cannot sample one:
  `WebGpuEngine` rejects a non-null `depthPrePassShaderSet`, since no WGSL shader declares the
  bindings). Do not hold a Vulkan
  feature back for parity — see the capability rule below for where it goes instead.
- **The shared CORE is the intersection of both backends, which is WebGPU-shaped by arithmetic.**
  WebGPU is a capability subset of Vulkan, so the intersection simply *is* WebGPU-shaped — this is
  not a preference and not "settling for WebGPU". A WebGPU-shaped core is always implementable on
  Vulkan (Dawn and wgpu are the proof); a Vulkan-shaped one is never implementable on WebGPU. When
  a concept exists on both sides but is spelled differently, take the WebGPU spelling and let
  Vulkan translate: `wireframe: Boolean` not `VkPolygonMode`, the contract's `CullMode` not
  `VkCullModeFlagBits`. A raw `VkRenderPass`, descriptor-set index or explicit barrier in the
  shared core is the defect.
- **Vulkan-only capability goes behind an optional interface, not into the core and not into the
  bin.** Explicit barriers, subpasses, bindless, multi-queue: expose them as a capability engine
  code feature-detects and never requires, so web takes a fallback path or does without. The rule
  is **not** "never use Vulkan features" — it is "the core may never *require* what WebGPU cannot
  do". See `docs/reference/decision-log.md` D26.
- **Apply the third-backend test before adding to the shared layer.** *Could a third backend
  implement this without changing shared code?* If not, the concept belongs below the line.
- **A graphics backend knows hardware only.** Pipelines, buffers, textures, samplers, command
  recording. It must never know what a skybox or a shadow *is*. The moment a backend declares
  `SkyboxRenderPipeline`, the driver layer has become a game: a fourth content feature can no
  longer be added without editing a backend, twice, once per backend. Enforced by
  `verifyBackendLayering` on `check`, which rejects `Skybox`/`Shadow`/`Particle`/`Fog`/`Terrain`/
  `Water`/`Decal`/`Billboard`/`Occlusion` in any backend *declaration* — doc comments and shader
  paths naming them stay legal. Its exemption list ran 14 files to **0** (2026-08-24) -- never
  grow it. Most of that resolved as renames: the types were already capabilities and only their
  names answered "what is being drawn", so `ShadowMap` became `DepthTarget`,
  `ShadowRenderPipeline` became `DepthOnlyPipeline`, `ShadowFeature` became `DepthPrePassFeature`.
  Check a type's members before assuming a rename is cosmetic -- and before assuming it is not
  enough. **An empty list does not mean a backend carries no content:** the check reads *declared
  names* only, so content in a call, a local or a well-chosen function name is invisible to it.
  `RendererDraw3D` declared `lightViewProjection`, which decided what volume a directional light
  covers, and never appeared on any list. See `docs/reference/render-extensibility.md` for the
  content-versus-capability test and the names table, and
  `docs/tasks/2026-08-23-backend-content-split-plan.md` for what the split did and did not close.
- **Opt-in is not the same as absent.** A nullable `skyboxRenderPipeline: SkyboxRenderPipeline?`
  constructor param still means the backend knows what a skybox is. This rule superseded the older
  "authored content is a nullable param" bar, which is why that phrasing may still appear in older
  code comments.
- **Before adding an indirection layer for content, check the RHI has the primitive.** Content
  features used to hand-roll per backend because `PipelineSpec` could express neither a
  vertex-less pipeline nor a standalone uniform block — missing hardware primitives, not a
  missing mediator. A Bridge or adapter layer added without them relocates the duplication
  instead of deleting it. Both primitives landed (`VertexFormat.None`, `PipelineSpec.uniforms`),
  and the rule stands for the next one: check the primitive before reaching for the layer.
- **Do not add new backend imports of scene vocabulary (D31).** `RenderDrawCommand`,
  `SceneLight`, `Lens`, `EnvironmentUniforms`, `ShadowCascadeUniforms`, `DirectionalShadowBox`,
  `SkyboxUniforms`, and `ParticleUniforms` are render *runtime* concepts; a backend receives
  pipelines, buffers and recorded commands. The verifier now has an empty import-exemption
  ledger for both backends. Any reintroduction of `RenderDrawCommand`, `SceneLight`, `Lens`, or
  `EnvironmentUniforms` into backend source is a build failure, not tracked debt. The same guard
  rejects authored scene defaults (`DEFAULT_SCENE_LIGHT`, sky/fog color presets) in backend
  production sources. The source command is lowered above the HAL and only `GpuPassInput` crosses
  the backend boundary.
- **Never reach for `expect`/`actual` to enforce backend symmetry.** It makes both sides implement
  matching signatures while both bodies stay hand-written — duplication becomes mandatory and
  compiler-checked instead of removed. It also resolves per KMP *target*, not per backend. Use an
  interface the backend implements (`PipelineFactory`, `CommandRecorder`). `expect`/`actual` stays
  for platform primitives with one-line bodies (`readResourceBytes`).
- **A one-backend capability is fine; a one-backend *decision* is a defect.** The distinction:
  Vulkan having a shadow pass WebGPU lacks is a documented capability gap. Vulkan and WebGPU each
  deciding *which pipelines exist* is duplication, and it silently shipped WebGPU without an
  alpha-blended pipeline for as long as Vulkan had one — transparent draws rendered opaque and
  nothing failed. If a companion, pass or pipeline is added to one backend, either the shared
  layer decides it for both, or it is a declared capability with the gap documented at the
  declaration site and the reason given.

## 6. A decoration is a stroke, not a stack of filled shapes

`compose:foundation`'s `Modifier.border` used to have two branches, both workarounds for the same
missing capability: `DrawScope`/`RoundedQuad` had no stroke mode, only fill. With a known
`fillColor` it drew an outer filled rounded rect then an inset filled rounded rect on top
("ring by subtraction"); with no known fill it drew four independently-filled edge strips, each
requesting the *full* corner radius on a strip only stroke-width px thick -- which cannot
represent a corner arc, so `shadcnSurface(bordered = true)`'s corners rendered as plain squares in
production. Confirmed by rendering the actual PNG, not by reading the code.

The fix was not a new GPU primitive. `DrawCommand.StrokedPath` (path + `DrawStroke` + colour) and
its tessellator (`DrawPath.tessellateStroke` in `core/graphics2d/PathStrokeTessellation.kt`)
already existed and were already wired through both backends -- `DrawRunCoalescer` tessellates a
`StrokedPath` into the same colored `QuadRun` every other flat-colored primitive uses, so neither
Vulkan nor WebGPU has (or needs) a `StrokedPath`-shaped case anywhere; the shared `render:passes2d`
layer already resolved it before either backend sees a draw command. `BorderNode` now builds one
closed rounded-rect outline as a `PathCommand` sequence (`MoveTo`/`LineTo`/`ArcTo` per corner) and
strokes it once, deleting both old branches and the `borderOverFill` API entirely.

**The rule this generalizes:** a decoration (border, ring, outline, underline) is the boundary of a
shape, and a real stroke primitive already exists for exactly that (`StrokedPath`/`tessellateStroke`,
mirroring upstream Compose's `DrawStyle.Stroke`). Assembling one from several independently-filled
rects is always wrong at a corner, because a filled strip has no way to taper into an arc. Before
adding a second filled shape to fake an outline, check whether the existing stroke primitive
already covers it -- it did here, and the fix required zero new backend or shader code as a result.

## Subsystem / pattern map (naming differs from generic examples — mapped to Awake's real types)

| Layer | Awake class | Pattern | Note |
|---|---|---|---|
| Top orchestrator | `Game` (interface, injected into `GameApplication`) | Strategy | Not Template Method — `Game` is a swapped-in behavior object, not a base class a game subclasses. |
| System lifecycle | `GameApplication` (abstract, `engine/game`) | Template Method + Mediator | `create`/`update`/`resize`/`dispose` are `final`, calling the abstract `createBackendResources`/`destroyBackend` hooks — that's Template Method. It's *also* the Mediator described in §4: the same class keeps window, `Renderer` and `Game` from referencing each other. Both readings are correct, different axes of the same class. |
| Backend construction | `VulkanGameApplication` / `WebGpuGameApplication` | Facade | Each hides `GraphicsDevice`/`SwapchainManager`/pipeline-table construction behind one `createBackendResources` call — this is the Template Method *hook implementation*, not a separate top-level class. |
| Window & OS | `WindowApplication` (`core/graphics`), platform `expect`/`actual` window glue | Bridge | Matches — abstraction (`WindowApplication`) decoupled from per-platform implementation. |
| Engine logic | **Mismatch — no `Scene`/`SceneNode` composite exists.** Awake is ECS-based (`World`, `Entity`, `System`, the `scene { }` DSL from `awake-ecs-authoring`), not a retained scene graph. | N/A | Do not introduce a `SceneNode` Composite/Command layer to match a generic diagram — it would duplicate what `World`/`queryEach`/`System` already do. If scene-graph-shaped structure (parenting, hierarchical transforms) is genuinely needed, that is `Transform.parent` + `TransformSystem`, still queried, not a Command-pattern object. |
| Graphics | `Renderer` (backend-neutral interface) + per-pass `RenderFeature` (§1) | Strategy | Matches — in place on both backends. |

## Checklist

- [ ] Before adding a new draw primitive, modifier, or `DrawScope` capability: grepped the whole
      tree for the concept by name (`Stroke`, `Path`, `Shape`, ...) and by shape (what does
      upstream Compose call this?), not just extended the file already open. Found only because a
      user asked "why isn't `PathCommand` used" after a whole capability had already been designed
      from scratch — the search should happen before designing, not after a redirect. A "no
      capability exists" conclusion drawn from one file (`DrawScope.kt`'s fill-only
      `drawRoundedRect`) was wrong; the real capability (`StrokedPath`/`tessellateStroke`) was one
      grep away, already backend-wired, already used by `ShadcnIcon.kt`.
- [ ] New render pass implements `RenderFeature`, registered in `Renderer`'s ordered list —
      no new hardcoded call site in `recordCommandBuffer`, no new per-pass nullable field.
- [ ] Feature ordering documented at the registration site if it depends on another feature's
      output (e.g. reads a texture another feature wrote).
- [ ] No content vocabulary (skybox, shadow, fog, particle, ...) declared in a backend module —
      `verifyBackendLayering` enforces this; only capability primitives live there.
- [ ] New content declared once in the shared layer, never once per backend.
- [ ] `Material` gains no pipeline back-reference; `VertexFormat` stays the mesh→pipeline key.
- [ ] Any new draw-call sort/batch step preserves the existing `groupBy { pipeline }` and is
      never applied to the UI pass.
- [ ] All vertex attributes and buffer offsets are derived dynamically from `VertexFormat` —
      no hardcoded attribute arrays or offsets in backend pipeline classes.
- [ ] Logic shared or symmetric between Vulkan and WebGPU is moved to `render:contract` or `render:passes`.
- [ ] Shared compilers are source-type generic; they must not import or expose authored
      `RenderDrawCommand`, ECS, or scene types. Keep authored lowering at the scene-to-passes edge.
- [ ] Uniform blocks are packed through `UniformLayout`/`UniformWriter`; never concatenate matrix,
      light, or extra arrays (`mvp.data + ...`) in a backend. Run the production-source audit
      before review:
      `rg -n "mvp\\.data\\s*\\+|lightUniforms\\s*\\+|shaderLightUniforms\\s*\\+|uniformFloats\\[[0-9]+\\]|extraUniformFloats\\[[0-9]+\\]" awake/backend awake/engine/render awake/asset/shader-pack awake/scene -g '*.kt'`.
      The same audit is now executable with `./gradlew verifyRenderUniforms` and is wired into the
      root `check`; it also rejects numeric `copyOf`/`copyOfRange` slices of light or material
      payloads. Do not weaken its source-set exclusions to hide a production violation.
- [ ] Every pipeline declaration carries explicit group/binding metadata from its shared shader
      definition. Never infer bind-group use from WGSL substring matching, and never submit a
      group-0 bind group when the selected entry points declare no group-0 resources.
- [ ] 2D primitive algorithms live in `render:passes2d`, never beside a UI framework or a backend.
- [ ] Capture/pixel-dump helpers live in `render:testing`; only a backend-specific fixture stays in a backend test source set.
- [ ] Anything added to the shared layer passes the third-backend test and is WebGPU-shaped —
      no `Vk*` type, descriptor-set index or explicit barrier in shared code.
- [ ] No new backend import of `RenderDrawCommand`/`SceneLight`/`Lens` or authored scene defaults;
      the backend import-exemption ledger must remain empty.
- [ ] No `expect`/`actual` used to enforce backend symmetry.
- [ ] A new pipeline/pass/companion is decided once in the shared layer, or its one-backend-only
      status is documented at the declaration site with the reason.
- [ ] A decoration (border/ring/outline) is drawn as one `StrokedPath`, never as several stacked
      filled shapes -- check the existing stroke primitive before adding a fill workaround.
