# Content Feature Textures Plan

Date: 2026-08-29
Status: complete — 2a done; 2b deferred

Implements [D28](../decisions/D28-open-world-framework-boundary.md) item 2. Follows
[the material binding declaration plan](./2026-08-29-material-binding-declaration-plan.md),
which made a binding set *expressible*; nothing yet supplies resources for one.

## Goal

Let a consumer-registered `ContentFeature` own and bind its own textures, so a terrain splat
pass can sample a weightmap and its layer textures. This is what unblocks D28 item 4.

## The scope split this investigation forced

D28 states item 2 as "`texture2DArray` upload and binding". That is two separable capabilities,
and only the first blocks terrain:

- **2a — a content feature can own textures at all.** `ContentFeature.build` receives a
  `PipelineHandle` and a `UniformBlock`, nothing else. `UniformBlock.binding(frameIndex)` hands
  back one `MaterialBinding` — a whole descriptor set or bind group — whose contents the backend
  builds, and today that is a uniform buffer alone. A splat pass cannot bind a weightmap even as
  an ordinary 2D texture.
- **2b — those textures can be array textures.** Strictly an increase in how many layers one
  pass can splat.

**Recommendation: do 2a, defer 2b.** Terrain can ship with up to four layers as separate 2D
textures — that is exactly what the existing 4-channel `TerrainSplatWeightMap` addresses — and
2b carries a native-code change 2a does not. Deferring keeps D28 item 4 reachable without
touching JNI.

## Why 2b is expensive, specifically

The Vulkan struct bindings are already array-capable: `VkImageCreateInfo.arrayLayers`,
`VkBufferImageCopy.baseArrayLayer`/`layerCount`, `VkImageSubresourceRange.baseArrayLayer`/
`layerCount`, and `VkImageViewType.VK_IMAGE_VIEW_TYPE_2D_ARRAY` all exist.

One thing does not. `vkTransitionImageLayout(commandBuffer, image, oldLayout, newLayout,
levelCount)` has no `layerCount`, so the layout barrier only ever covers layer 0. Layers 1..N-1
would stay `UNDEFINED` and sample as garbage. Adding it means, per
[D11](../decisions/D11-jni-native-implementation-boundary.md):

1. the `commonMain` expect plus the `desktopMain`/`androidMain` `@JniNative` actuals,
2. a regenerated `VulkanImages_jni.gen.cpp`,
3. the barrier body in `VulkanImages_native.cpp` (currently `jint levelCount` only),
4. the iOS cinterop actual, which is Kotlin rather than C++,
5. a native rebuild per platform.

That is the D11 one-function migration shape and it is tractable, but it is not this plan's
critical path, and it needs a working `cmake` — which in this environment only succeeds under
`--no-daemon`.

## Phases (2a)

### Phase 1 — Let a group carry textures in the render contract — **done**

`ContentFeature` gained `textures: Map<Int, TextureAsset>`, keyed by binding index.

**`build` did not need widening.** This plan proposed changing its signature so a feature could
receive "the binding it can record with"; reading `UniformBlock` closely made that unnecessary.
`UniformBlock.binding(frameIndex)` already answers with the whole descriptor set or bind group,
not just the buffer inside it, so a texture the backend writes into that same group is bound by
the call a feature already makes. The parameter sits before `build` so the trailing-lambda call
shape is unchanged, and `skyboxContentFeature` compiled untouched.

The constructor rejects a mismatch in **both** directions against the pipeline's declared
sampled-texture bindings: a declared binding with no supplied texture leaves a descriptor the
shader samples unwritten, and a supplied texture with no declared binding is written nowhere.
Neither is visible at the call site, and each fails late and unclearly — undefined reads on
Vulkan, a bind-group rejection on WebGPU. Declared samplers are exempt: a backend creates those
rather than being handed pixel data.

Contract only. Nothing uploads or binds these yet — that is Phases 2 and 3.

### Phase 2 — Vulkan: write texture descriptors into a content feature's set — **done**

It was not only the write half. A content pipeline's group is `PerFrameUniformSlots`, not
`Material`, and that class hardcoded its own layout and pool — one uniform buffer at binding 0 —
independently of the work the previous plan did. It now takes a `GroupBindings` and derives both
through the same `materialLayoutBindings`/`materialPoolSizes` the material path uses. Null keeps
the uniform-only shape, which is still every caller but a texture-carrying content feature.

**Textures are written after the pipeline exists, not during construction.** The layout comes
from a `PipelineSpec` the registry compiles up front; the pixels come from a `ContentFeature` the
engine only reaches afterwards. Putting `TextureAsset`s in the spec was the alternative and is
wrong twice over: `PipelineSpec` is the registry's map key, so a `ByteArray` would make every
lookup hash a whole image. So `RenderPipeline.writeContentTextures` fills the descriptors once,
into every frame slot, after the lookup.

Every declared sampler binding is written with the lowest-numbered texture's sampler. `Texture`
builds all of them from the same default `VkSamplerCreateInfo`, so the choice cannot matter
today; a feature wanting distinct filtering per texture is what would force a real one.

`VulkanEngine` owns the uploaded `Texture`s and frees them in `destroyBackend` — a pipeline's
descriptor set references an image without owning it, so nothing else would.

Verified by the headless suite staying green (20 classes, 54 tests), which proves the skybox
content feature still builds and draws through the `bindings = null` path. The texture path
itself has no coverage until Phase 4, which is the test for it.

### Phase 3 — WebGPU: same, via bind group entries — **done**

`RenderPipeline`'s own bind group now builds its entries from `materialBindings`, with the same
resource rule Vulkan uses: uniform buffer, each declared sampled texture from its supplied
image, each declared sampler from the lowest-numbered texture's. No declaration keeps the single
buffer entry it always had. The layout still comes from the shader via `getBindGroupLayout(0u)`.

**The ordering is the opposite of Vulkan's, and that is forced.** A Vulkan descriptor set is
written after it exists; a `GPUBindGroup` is immutable once built, so textures have to arrive
*before* the first bind rather than after. The group is already `by lazy`, so supplying them
during engine setup is naturally in time — and `writeContentTextures` fails loudly if the group
was somehow built first, rather than silently producing a texture-less bind group.

Both engines now own their uploaded textures and free them in `destroyBackend`; a bind group
references a view without owning it.

Verified: WebGPU desktop suite green (5 classes, 14 tests), and
`:awake:backend:webgpu:compileKotlinWasmJs` passes — the change is entirely in `commonMain`, so
the browser target gets it from the same source.

### Phase 4 — The completion test the previous plan left unmet — **done**

`RendererHeadlessContentTextureTest` draws a vertex-less full-screen pass that samples a texture
its own pipeline declared, and asserts four quadrants of real pixels on lavapipe.

**Authored in ASL, compiled at runtime, with nothing committed.** The first draft hand-wrote a
`.wgsl` and committed two `.spv` files beside it — which is the drift class the ASL work exists
to remove, and was rightly challenged. The probe is now an `AslShaderDefinition`, compiled to
SPIR-V in-test through the same `NagaShaderCompiler` binding `VulkanShaderResolver` already uses
in production. WGSL still exists as an in-memory intermediate because ASL emits WGSL and naga
consumes it; no file is written and there is nothing to regenerate. `:awake:backend:vulkan`'s
`desktopTest` gained the `awake.naga.library` property and the `buildNagaDesktop` dependency
that `:awake:asset:shader-compiler`'s own tests already set.

That closes the chain end to end in one run: ASL declares the bindings, `bindingsForGroup` turns
them into a `GroupBindings`, `PerFrameUniformSlots` derives layout and pool from it,
`writeContentTextures` fills it, and the sampled result reaches the framebuffer. A second test
asserts the derived declaration on its own, so a failure says which half broke.

Two findings the test produced rather than confirmed:

- **A 2x2 probe texture cannot work.** Every texel centre sits a quarter of the image from every
  sample point, so the default linear filter blends all four colours everywhere — it read
  `(94, 31, 193)` where pure red was expected. Four solid quadrants of a 64x64 image confine the
  blend to the seams.
- **The clip-space convention is the opposite of what `background_probe.wgsl`'s comment implies
  here.** NDC y = +1 lands on the read-back image's top row, so v has to be flipped for the
  texture's first row to appear at the top. Measured, not reasoned — the same trap that shader's
  own comment records having fallen into.

## Deferred (2b), when a pass needs more than four layers

- `ResourceKind.SampledTexture2dArray`, landing with the code that honours it.
- A layered `TextureAsset` form and the `Texture` upload path for it.
- The `vkTransitionImageLayout` `layerCount` change above.
- `AslType.Texture2dArrayF32` plus its `texture_2d_array<f32>` WGSL emission, and
  `bindingsForGroup` mapping it to the new `ResourceKind`.

## Non-goals

- No per-pipeline `Material` layouts. That is follow-up 1 of the previous plan and is
  independent: a content feature owns its own group and never goes through `Material`.
- No terrain rendering. This plan makes the resources bindable; emitting clipmap draws is D28
  item 4.
- No asset manager, streaming, or VRAM budget — D28 defers all three until one cell renders.

## Risks

A content feature's descriptor set is built once at feature-build time, unlike a `Material`'s
per-frame slots. A feature that wants to swap a texture per frame has no path here, and should
be told so explicitly rather than discovering a stale binding — worth a `require` or a doc
comment naming the ceiling.

Vulkan will accept a descriptor set whose declared texture binding was never written and only
fault when a shader samples it; WebGPU rejects the mismatch at bind-group creation. Same
asymmetry as the previous plan, same advice: run WebGPU first when debugging.
