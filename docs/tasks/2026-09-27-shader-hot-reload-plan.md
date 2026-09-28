# Shader hot reload plan

Date: 2026-09-27
Status: **done** — phases 0–2 have landed; the limits below are open follow-ups. It had to land
before the [shader graph](2026-09-27-shader-graph-plan.md) editor, whose live preview is its first
consumer. Tracked in [#106](https://github.com/awakekt/awake/issues/106).

## Goal

A running app, whether a game or Studio, replaces a shader with one built at runtime. The flow is
fully in process, with no restart, no files and no Kotlin recompile:

```
ASL definition built at runtime  →  emitWgsl()  →  naga  →  pipeline swapped in place
```

Sources of runtime ASL:
- the Studio shader graph editor's live preview (graph compiled to ASL), which is the first
  consumer;
- material variants picked at runtime;
- procedural shaders.

## Current state

- **Runtime compilation already works.**
  - Every shader set is in-memory ASL. `aslShaderSet` emits WGSL and wraps it as
    `ShaderSource.InlineText`.
  - Vulkan compiles that text with `NagaShaderCompiler` when it creates the pipeline. WebGPU
    takes the WGSL directly.
- **Runtime replacement exists only for content features.** A content feature can be detached
  and attached again with a new shader set, which terrain surfaces do. Mesh pipelines have no
  such path.
- **Pipelines are created once, at engine start.**
  - `PipelineRegistry` plus an immutable `PipelineTable`, built in
    `VulkanEngine.createBackendResources`.
  - `DepthOnlyPipeline`s and `LineRenderPipeline` are built outside the registry. UI pipelines
    are built lazily in `Renderer`.
- **A Vulkan swap in place reaches every holder.**
  - Every long-lived holder keeps the wrapper object (`RenderPipeline`, `DepthOnlyPipeline`,
    `LineRenderPipeline`), never the raw handle.
  - The recorder reads `pipelineHandle` on every bind.
  - Replacing the `VkPipeline` inside a wrapper therefore reaches the registry, the table, the
    renderer's maps, content features and per-frame draws.
- **The wrappers keep no constructor inputs.** Entry points, cull mode, polygon mode, vertex
  format and extra layouts are not retained, so a rebuild has nothing to rebuild from.
- **Pipelines are already destroyed mid-run in two places:**
  - content-feature detach (`vkDeviceWaitIdle`, then destroy);
  - font changes rebuilding the glyph pipelines.

  Vulkan runs 2 frames in flight.
- **WebGPU cannot swap in place.** `WebGpuPipelineHandle.pipeline` is a `val`, and many bind
  groups are built from the pipeline object.
- **Rules that apply** (`docs/reference/render-hardware-interface.md`, `render-extensibility.md`):
  - A Vulkan-only feature goes behind `GpuDevice.capability(kind)`. Neither backend overrides it
    yet.
  - A backend must not name content.
  - `PipelineRegistry` is single-threaded and runs on the render thread.
  - Pipeline decisions stay shared; backends only translate a spec.

## Design

As built in phase 1:

- **API.** `ShaderReplacement`, a `GpuCapability` in `render:contract`:
  `replace(old: ShaderProgram, new: ShaderProgram): Int`. A `ShaderProgram` is a vertex source, a
  fragment source and the bindings per group; `ShaderStages.program()` builds one from an ASL set.
  Vulkan's renderer returns it from `capability(ShaderReplacement)`; WebGPU returns null.
  - The contract cannot name `ShaderSet`, which lives in `asset:shaders` above it, so the API takes
    sources rather than sets.
  - Pipelines are matched by the sources they run, so the backend never names a shader or content.
- **Checks before anything changes.** The new sources compile, and every matched pipeline's bindings
  must equal the new program's; unknown bindings are refused. Then every replacement `VkPipeline` is
  built. A failure at any step destroys what was built and throws `ShaderReplacementException`.
- **Swap.** `vkDeviceWaitIdle`, then each `RenderPipeline` swaps its new `VkPipeline` in and destroys
  the old one. The wrapper keeps its pipeline layout, cache and fixed state, which is all a rebuild
  needs, so everything holding the wrapper draws with the new shaders from the next frame.
- **Where it runs.** On the render thread between frames, like content-feature attach and detach,
  which already wait idle and destroy pipelines mid-run. The compile is synchronous there: there is
  no render-thread task queue, and a naga compile is milliseconds.
- **Stale registry keys.** The registry keeps each pipeline under the spec it was built from, so
  detach still finds it. What a pipeline runs after a replacement is tracked beside the registry.
- **Replacements chain.** The swapped-in program is what the next replacement matches.

## Phases

### Phase 0 — spike (done)

Replace one `VkPipeline` inside its wrapper, under 2 frames in flight, with validation layers on.

**Gate:** a clean validation log. If it isn't clean, the reason is recorded.

**Result:** clean. The replacement runs while both frame slots are still in flight. With the
`vkDeviceWaitIdle` removed, validation reports `VUID-vkDestroyPipeline-pipeline-00765` (a pipeline
destroyed while a command buffer uses it) at every replacement, so the wait is required and the log
check is live.

### Phase 1 — Vulkan swap primitive (done)

**Gate:** a headless Vulkan test with two pipelines, one drawing red and one drawing blue, plus
validation layers.
- Replacing red's set with a runtime-built green definition turns only red's region green.
- Positive control: blue's region is unchanged. A primitive that swaps the wrong pipeline fails
  here.
- Replacing green again with yellow works, which proves chaining.
- Invalid WGSL is rejected before queueing, and red stays.
- A binding change is rejected, and red stays.
- Validation layers report nothing.

**Result:** `RendererHeadlessShaderReplacementTest`, all of the above. Positive controls: matching every
pipeline fails the count; dropping the binding check fails the refusal test, and validation reports
`VUID-VkGraphicsPipelineCreateInfo-layout-07988`.

### Phase 2 — sample and docs (done)

- `engine-showcase` desktop builds a variant of a pack shader at runtime and swaps it on a key
  press.
- Document the runtime replacement workflow in the ASL README's dev-loop section.

**Gate:**
- The sample's pixel changes after the key press.
- A deliberately broken variant is logged, and the sample keeps running on the old shader.

**Result:** `ShowcaseShaderSwap` in the engine showcase. L toggles `litShadowShader(clipSpace,
ambientStrength = 0.6f)`, a variant the pack now exposes; its default emits the shipped WGSL byte for
byte. K tries the shipped WGSL with a syntax error appended. `ShaderSwapSceneFrameTest` runs the
showcase headless with zero-delta frames: L changes the frame, K logs a warning and leaves the frame
identical, and L again restores the shipped frame exactly. Positive control: a variant with the
shipped ambient strength fails the L check. The desktop showcase now prints warnings.

## Limits and follow-ups

- **Depth-only and debug-line pipelines.** Not in the registry, so never replaced. Replacing a lit
  shader leaves the depth pre-pass drawing with its own depth shader, which is what it should do.
- **Vertex inputs are not checked.** An ASL definition records its vertex format only through
  `inputsFrom`, so there is nothing reliable to compare. A replacement reading an input the
  pipeline's format lacks is a caller error that validation reports.
- **Compile on the render thread.** Fine for a preview; move it off-thread if a large shader drops a
  frame.

- **Pipelines are per vertex format today.** Replacing the lit shader changes every mesh of
  that format. Per-material shaders are a separate render plan (step 2 of the node-graph
  sequence).
- **UI pipelines.** The quad pipeline owns the UI render pass that the framebuffers and sibling
  pipelines use. Skipped in v1.
- **WebGPU.** Needs a mutable handle and bind groups rebuilt from the new pipeline. Deferred;
  WebGPU is expected to lag. The Studio web build previews through content-feature re-attach
  until then.
- **Layout-changing shaders.** New bindings, uniform layout or vertex inputs are rejected. They
  need a new spec rather than a swap.
- **Compile cost.** `InlineText` compiles are uncached and run once per stage. A cache keyed by
  text hash helps both startup and repeated previews.
- **Mobile.** Android and iOS compile every shader through naga at startup. Neither has had an
  on-device run yet; see the `shader-compiler` README.
- **Editing Kotlin-authored shaders without a restart.** Out of scope. It would need an external
  recompile (a Gradle dump plus a file watch, or IDE hot-swap) feeding this same API.
