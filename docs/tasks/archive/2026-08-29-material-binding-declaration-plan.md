# Material Binding Declaration Plan

Date: 2026-08-29
Status: complete — all five phases done

Implements [D28](../decisions/D28-open-world-framework-boundary.md) item 1. Blocks items 2
(`texture2DArray`) and 4 (clipmap draw emission); nothing in D28 can proceed until a pipeline
can declare what occupies its own group 0.

## Goal

Let a pipeline declare the resource bindings in its material group, so a consumer-registered
`ContentFeature` can add a splat weightmap, a layer texture array, or a depth sampler. Today
the set is fixed to one glTF metallic-roughness material, in engine code, on both backends.

## Current State

**Vulkan hardcodes the layout.** `Material.createDescriptorSetLayout(graphicsDevice)`
(`awake/backend/vulkan/.../material/Material.kt:227`) takes a device and nothing else, and
always builds the same six bindings: 0 uniform, 1 sampled image, 2 sampler, then
`PBR_TEXTURE_BINDINGS = listOf(5, 6, 7, 8)`. The descriptor pool is sized to match at
`Material.kt:336`. `VulkanEngine.kt:327` is the only production caller.

**WebGPU hardcodes the entries.** The layout is not hardcoded there —
`pipeline.getBindGroupLayout(0u)` (`awake/backend/webgpu/.../material/Material.kt:81`) derives
it from the shader. What is fixed is the entry list built against it: 0, 1, 2 and then PBR at
5–8. WebGPU validates entries against the derived layout, so a shader that declares a seventh
binding fails at bind-group creation until the entry list grows too.

So the two backends need opposite halves of the same fix: Vulkan must stop inventing the
layout, WebGPU must stop assuming the contents.

**The declaration already exists.** ASL models resource bindings as
`AslTextureBinding(name, group, binding, type)` (`awake/asset/shader-dsl/.../AslModel.kt:155`)
and uniform blocks as `AslUniformBlock(group, binding, ...)`. Every shipped `.wgsl` is emitted
from ASL by `GenerateShaders.kt`, with `AslPackDriftTest` failing the build if a checked-in
file drifts from its ASL source. ASL is already the single source both backends' shaders come
from — it just is not plumbed to Vulkan's descriptor-set layout.

**`PipelineSpec` carries only half the information.** `bindingLayout: BindingLayout` maps a
`BindingSemantic` to a group index. Nothing describes what sits inside a group.

## Why not SPIR-V reflection

`docs/mmorpg-roadmap.md` lists "Spirv-Reflect Automated Pipeline Layout Compilation" (P1,
MVP1b), which solves the same problem by reading the compiled module instead of a declaration.
Deferred here, not rejected: it adds a Vulkan-side reflection dependency, gives WebGPU nothing
it does not already do, and re-derives what ASL already stated. Revisit if a pipeline ever
needs a Vulkan layout for hand-written WGSL that has no ASL source — no such pipeline exists
today.

## Phases

### Phase 1 — Describe a group's contents in the render contract — **done**

Added `GroupBindings`, `ResourceBinding`, `ResourceKind` and `ShaderStage` to
`:awake:engine:render:contract` alongside `BindingLayout`, plus
`PipelineSpec.materialBindings: GroupBindings? = null`. Null means the fixed glTF
metallic-roughness shape, so every existing spec keeps its exact layout and both backends
compiled unchanged.

Two deviations from this plan as written:

- **Stages are declared, not inferred.** The plan did not mention them. Vulkan rejects a
  pipeline whose layout omits a stage its shader reads
  (`VUID-VkGraphicsPipelineCreateInfo-layout-07988`), and the backends' implicit rule —
  uniforms span both stages, textures and samplers are fragment-only — breaks on the first
  terrain shader that samples a heightmap in the vertex stage to displace it. That is the
  clipmap case in D28 item 4, so inferring stages would have to be undone two phases later.
- **No texture dimensionality yet.** The plan wanted texture entries to carry it so D28 item 2
  could add an array case without a contract change. Adding an unused `SampledTexture2dArray`
  that no backend handles would produce a silently wrong layout rather than a compile error, so
  `ResourceKind` gets the array case in item 2, together with the code that honours it. Adding
  an enum constant is an additive change either way.

`GroupBindings.StandardMaterial` is asserted against the exact bindings both backends build
today, including the 3/4 gap, so Phases 2 and 3 have a fixed target to replace them with.

### Phase 2 — Derive the Vulkan layout from the declaration — **done**

`Material.createDescriptorSetLayout` and the descriptor pool are both derived from a
`GroupBindings` the `Material` constructor takes, defaulting to
`GroupBindings.StandardMaterial`. The 3/4 gap survives because the declaration reproduces it.

Both derivations were extracted as `materialLayoutBindings` and `materialPoolSizes` so the
plan's top risk — pool and layout disagreeing, which surfaces as a draw-time
`VK_ERROR_OUT_OF_POOL_MEMORY` rather than a build failure — is asserted in
`MaterialBindingDerivationTest` without needing a device.

`PBR_TEXTURE_BINDINGS` is now derived from `StandardMaterial` (every sampled texture past the
base-color one) instead of the literal `listOf(5, 6, 7, 8)`, so the descriptor writes and the
layout cannot drift apart. A test pins it to the old literal.

Also removed: a dead branch writing shadow image/sampler descriptors at bindings 3 and 4. Both
parameters defaulted to null with no caller ever setting them, and the layout deliberately
does not declare 3/4, so the branch could only ever have written to undeclared bindings.

**Not** done here, and intentionally: the descriptor *write* path still writes exactly the
standard set. Declaring a binding shapes the layout; a draw that reads an unwritten binding is
still invalid. Nothing can supply resources for a custom declaration until D28 item 2 adds the
upload path, and the completion test below is what will prove it.

### Phase 3 — Build WebGPU entries from the declaration — **done**

`Material.bindGroupFor` builds its entries from the same `GroupBindings` the constructor takes.
The layout still comes from `getBindGroupLayout(0u)`. The bind-group cache and its ponytail
note are unchanged — nothing here made two-pipeline use more likely.

Resource selection per declared entry: uniform buffer, base-color sampler, base-color view at
binding 1, and every sampled texture past it filled positionally from `createResources`' PBR
list. That positional step is the only non-lookup, so it is extracted as `materialPbrBindings`
and asserted by binding order in `MaterialPbrBindingsTest` — a wrong order binds the normal map
where the shader reads occlusion, which renders a wrong picture rather than failing.

Two guards replace what was previously impossible to get wrong: a declaration needing more PBR
textures than `createResources` supplied fails with both counts named, and a declared storage
buffer fails outright, since a `Material` has no storage resource to bind.

**Coverage gap, stated rather than papered over.** The WebGPU headless suite passes (5 classes,
14 tests), but `WebGpuHeadlessPixelTest` drives the UI-compositing path
(`createMaterial(renderTarget = ...)`, whose bind groups belong to `UiTextureRenderPipeline`)
and an untextured material — neither reaches `bindGroupFor`. Its only callers are
`RendererOpaqueDraws`' 3D draws, which no automated WebGPU test in this environment exercises.
So Phase 3 is verified by compilation, the extracted-mapping test, and review — not by a
rendered pixel, unlike Phase 2 on Vulkan. The completion test below is what closes this.

### Phase 4 — Open the semantic vocabulary — **done**

`BindingSemantic` is a sealed interface: `Material`, `ShadowDepth` and `JointPalette` are data
objects keeping their exact names, plus `Custom(name)` for a group the engine does not name.
`BindingLayout` needed no change at all — it was already generic over its key type, and data
objects and data classes give it the value equality its map wants.

Every call site compiled untouched, as predicted: no exhaustive `when`, and each use is either
`BindingSemantic.X` by name or a `slot(semantic)` lookup. The one `.name` hit found while
checking for enum-only API belonged to a vertex-attribute semantic, an unrelated type.

`Custom` rejects a blank name, and two `Custom`s with equal names collide in `BindingLayout.of`
exactly as two `Material`s would — the same rule `PipelineKey.Content` already applies to
content-feature names.

### Phase 5 — Emit the declaration from ASL — **done, narrower than planned**

`AslShaderDefinition.bindingsForGroup(group)` returns a `GroupBindings` built from the shader's
own uniform blocks, textures and storage bindings.

**Stages are read out of the statement trees, not declared.** ASL models both stages' full
statement lists, so a binding belongs to a stage when that stage references its name — a
uniform field by its `instanceName.` prefix, a texture or sampler by its own name. References
are followed transitively through helper functions, because a binding used only inside a helper
still belongs to whichever stage calls it, and stopping at the stage body would emit a layout
missing a stage bit. Adding a `stages =` parameter to the DSL was the alternative and was
rejected: it relocates the drift into something an author can state wrongly.

`TexturedShaderBindingsTest` asserts the derivation from the real `TexturedShader` equals
`GroupBindings.StandardMaterial` exactly — bindings, kinds and stages. That is the drift check
this phase existed for, and it passes, so the hand-written contract constant and the shipped
shader genuinely agree today.

**Not wired into production pipeline specs, deliberately.** The plan assumed "an ASL shader's
pipeline gets its layout for free", but a `Material` builds one descriptor set layout and is
bound to whichever pipeline draws it — the "one shared Material shape beats a per-shader
variant" decision the old hardcoded layout was built on. Per-shader layouts break that:
`triangle.wgsl` and `skinned.wgsl` declare no textures, so their derived groups are strictly
smaller than `StandardMaterial`, and a material built for one could not bind to the other.
Making layouts per-pipeline means giving `Material` a per-pipeline layout too, which is a
larger change than this phase and needs its own decision. Until then the derivation earns its
place as the drift check above.

## Validation

- `BindingLayoutTest` and `DepthBindingLayoutTest` keep passing unchanged — Phase 4 must not
  alter their assertions.
- A new contract test: a declaration with a non-PBR binding set produces the expected Vulkan
  layout and WebGPU entry list.
- `AslPackDriftTest` keeps passing — Phase 5 must not change emitted WGSL.
- Both backends' existing headless render tests (`RendererHeadlessShadowMapTest`,
  `RendererHeadlessBackgroundPipelineTest`) keep passing, proving the default path is
  byte-identical to today.
- The real completion test is a throwaway `ContentFeature` declaring one extra sampled texture
  and drawing with it on both backends. Until that exists, the phases above are refactors with
  no proven new capability.

**Outcome.** Every bullet above holds, and `TexturedShaderBindingsTest` added one the plan did
not ask for: the derivation from the real shader equals the hand-written contract constant.
The last bullet stands unmet by design — a declaration can be *expressed* now, not yet *drawn
with*, because no resource path supplies a custom binding. That is D28 item 2's job.

## Follow-ups this work surfaced

1. **Per-pipeline material layouts.** Phase 5's derivation cannot be wired into production
   until `Material` stops owning one layout shared across every pipeline that draws it. Needs
   its own decision; see Phase 5.
2. **WebGPU 3D draw coverage.** Nothing automated reaches `Material.bindGroupFor`, so both the
   old and new entry lists are unverified by a rendered pixel on that backend.
3. **`ResourceKind.SampledTexture2dArray`,** with the upload path that honours it, in D28
   item 2.

## Non-goals

- No `texture2DArray` support. Phase 1 leaves room for it in the resource-kind description;
  adding the Vulkan/WebGPU upload path is D28 item 2.
- No current-frame scene depth resolve. Off the critical path now that water moved to the
  starter-kit — demand-driven, not blocking.
- No bindless descriptor indexing. A declared layout and a bindless one are different designs;
  this plan does not prejudge that migration.
- No change to `Renderer.createMaterial`'s `uniformFloatCount` knob.
- No SPIR-V reflection, per the section above.

## Risks

The Vulkan descriptor pool and the layout are sized in two places from the same constant. Phase
2 must change both or a declaration with more textures than the default will allocate a set the
pool cannot satisfy — a runtime `VK_ERROR_OUT_OF_POOL_MEMORY`, not a compile error.

WebGPU's entry-to-layout validation is stricter than Vulkan's. A declaration that is wrong in
the same way on both backends will surface as a clear WebGPU error and as silent undefined
reads on Vulkan, so run the WebGPU path first when debugging a mismatch.
