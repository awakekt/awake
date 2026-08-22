---
name: awake-render-pipeline
description: Rules for structuring render features, pipelines, materials and the application bootstrap in Awake's backend renderers (Vulkan today, WebGPU later). Read before adding a new render feature (shadow/opaque/skybox/UI-style pass), before wiring a new RenderPipeline into Renderer, before touching draw-call sorting/batching in RendererDraw3D, or before changing GameApplication/Game wiring. Trigger keywords - RenderFeature, RenderPipeline, Material, PipelineTable, vkCmdBindPipeline, vkCmdBindDescriptorSets, draw call sorting, state batching, recordCommandBuffer, groupBy pipeline, GameApplication, Game.ready, Game.render, mediator.
---

# Render feature / pipeline / material architecture in Awake

Pairs with [render-extensibility.md](../../docs/reference/render-extensibility.md), which
governs *whether* a pipeline is opt-in content vs an always-available capability. This skill
governs *how* render features, pipelines and materials are structured and composed once that
call is made.

Three patterns apply here. Only the first is a gap today — the other two already hold and
must not regress.

## 1. Strategy pattern for render features (target state, not yet in place)

`Renderer` today wires each pass as its own nullable field (`skyboxRenderPipeline`,
`shadowRenderPipeline`, lazily-built UI pipelines) and `RendererDraw3D.recordCommandBuffer`
hardcodes the pass order: skybox, then 3D, then debug lines, then a `when` over UI runs.
Adding a pass means editing the frame-loop function itself.

Target: a `RenderFeature` interface every pass implements, held as an ordered
`List<RenderFeature>` on `Renderer`. Adding a feature (e.g. post-process blur) means adding
a class to that list, not touching `recordCommandBuffer`.

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

**Shadow is not a `RenderFeature`.** It already owns its own render pass today
(`performShadowPass` is a wholly separate function from `recordCommandBuffer`, since the
shadow map's render pass is not the scene pass), so `RenderFeature` only covers the 3
features that genuinely share a pass (`Opaque`, `Skybox`, `UI`). `ShadowFeature` stays its
own class with its own signature. Don't generalize this into a shared pass-ownership
interface until a second standalone-pass feature (e.g. a future post-process blur) actually
exists — building that abstraction for a hypothetical is speculative generality. See
[docs/audits/2026-08-19-render-feature-strategy-plan.md](../../docs/audits/2026-08-19-render-feature-strategy-plan.md)
for the full worked design, including why the receiver-on-`Renderer` shortcut and an earlier
3-way sealed hierarchy were both rejected.

Rules when doing this refactor:

- Wrap existing pipelines (`ShadowRenderPipeline`, `SkyboxRenderPipeline`, `LineRenderPipeline`,
  the UI pipelines) behind `RenderFeature` implementations — do not merge their internals.
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

### The 3-layer rule for rendering code:
1. **`awake:engine:render:contract`**: All backend-neutral contracts, interfaces, GPU data shapes (`GpuDataShape`), vertex semantics (`VertexSemantic`), vertex attribute descriptions (`VertexAttribute`), and canonical `VertexFormat` objects.
2. **`awake:engine:render:passes`**: All shared rendering algorithms, vertex buffer writers (`RendererVertexWriters.kt`), batch coalescing (`UiRunCoalescer.kt`), vertex layout registries (`UiVertexLayout.kt`), light/shadow view-projection calculations, and uniform packing.
3. **`awake:backend:vulkan` / `awake:backend:webgpu`**: ONLY driver-specific bindings and GPU resource allocation (e.g. Vulkan JNI handles, WebGPU wgpu4k wrappers, command buffer encoders, descriptor set / bind group caching).

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
  compatibility target and is expected to lag (it has no shadow pass today). Do not hold a Vulkan
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
- **A backend must never import scene vocabulary.** `DrawCall`, `SceneLight` and `Lens` are render
  *runtime* concepts; a backend receives pipelines, buffers and recorded commands. Both backends
  violate this today (6, 4 and 3 files each) — that is tracked work, not a precedent to copy. Do
  not add a new one.
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

## Subsystem / pattern map (naming differs from generic examples — mapped to Awake's real types)

| Layer | Awake class | Pattern | Note |
|---|---|---|---|
| Top orchestrator | `Game` (interface, injected into `GameApplication`) | Strategy | Not Template Method — `Game` is a swapped-in behavior object, not a base class a game subclasses. |
| System lifecycle | `GameApplication` (abstract, `engine/game`) | Template Method + Mediator | `create`/`update`/`resize`/`dispose` are `final`, calling the abstract `createBackendResources`/`destroyBackend` hooks — that's Template Method. It's *also* the Mediator described in §4: the same class keeps window, `Renderer` and `Game` from referencing each other. Both readings are correct, different axes of the same class. |
| Backend construction | `VulkanGameApplication` / `WebGpuGameApplication` | Facade | Each hides `GraphicsDevice`/`SwapchainManager`/pipeline-table construction behind one `createBackendResources` call — this is the Template Method *hook implementation*, not a separate top-level class. |
| Window & OS | `WindowApplication` (`core/graphics`), platform `expect`/`actual` window glue | Bridge | Matches — abstraction (`WindowApplication`) decoupled from per-platform implementation. |
| Engine logic | **Mismatch — no `Scene`/`SceneNode` composite exists.** Awake is ECS-based (`World`, `Entity`, `System`, the `scene { }` DSL from `awake-ecs-authoring`), not a retained scene graph. | N/A | Do not introduce a `SceneNode` Composite/Command layer to match a generic diagram — it would duplicate what `World`/`queryEach`/`System` already do. If scene-graph-shaped structure (parenting, hierarchical transforms) is genuinely needed, that is `Transform.parent` + `TransformSystem`, still queried, not a Command-pattern object. |
| Graphics | `Renderer` (backend-neutral interface) + per-pass `RenderFeature` (target state, §1) | Strategy | Matches — this is the pattern §1 above is closing the gap on. |

## Checklist

- [ ] New render pass implements `RenderFeature`, registered in `Renderer`'s ordered list —
      no new hardcoded call site in `recordCommandBuffer`.
- [ ] Feature ordering documented at the registration site if it depends on another feature's
      output (e.g. reads a texture another feature wrote).
- [ ] Authored-content features stay nullable/opt-in per `render-extensibility.md`; only
      capability features may be non-null.
- [ ] `Material` gains no pipeline back-reference; `VertexFormat` stays the mesh→pipeline key.
- [ ] Any new draw-call sort/batch step preserves the existing `groupBy { pipeline }` and is
      never applied to the UI pass.
- [ ] All vertex attributes and buffer offsets are derived dynamically from `VertexFormat` —
      no hardcoded attribute arrays or offsets in backend pipeline classes.
- [ ] Logic shared or symmetric between Vulkan and WebGPU is moved to `render:contract` or `render:passes`.
- [ ] Anything added to the shared layer passes the third-backend test and is WebGPU-shaped —
      no `Vk*` type, descriptor-set index or explicit barrier in shared code.
- [ ] No new backend import of `DrawCall`/`SceneLight`/`Lens`.
- [ ] No `expect`/`actual` used to enforce backend symmetry.
- [ ] A new pipeline/pass/companion is decided once in the shared layer, or its one-backend-only
      status is documented at the declaration site with the reason.
