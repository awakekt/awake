# Shader hot reload plan

Date: 2026-09-27
Status: **proposed** — phase 0 can start now. It must land before the
[shader graph](2026-09-27-shader-graph-plan.md) editor, whose live preview is its first consumer. Tracked in [#106](https://github.com/awakekt/awake/issues/106).

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
- **The ASL README is stale.** It still documents `generateAslShaders`, which was removed in dev.7.

## Design

- **API.** `replaceShader(old: ShaderSet, new: ShaderSet)`, offered as a `GpuCapability`. WebGPU
  returns null until it can swap.
  - The caller builds `new` from a runtime ASL definition with `aslShaderSet`.
  - The engine matches pipelines by the old set's WGSL text, so the backend never names a shader
    or any content.
- **Compile off the render thread.** The new WGSL is compiled with `NagaShaderCompiler` before
  anything is queued, so a syntax error never reaches the render thread and is returned to the
  caller.
- **Swap on the render thread,** at the start of a frame:
  1. Find every pipeline whose spec uses the old text: registry pipelines, depth-only pipelines
     and the line pipeline.
  2. Reject the swap if the new set's bindings or vertex inputs differ from the spec. That kind of
     change needs a new layout, not a swap.
  3. Run `vkDeviceWaitIdle`.
  4. Create the new `VkPipeline` from the retained inputs.
  5. Swap it in and destroy the old one.

  A failed create keeps the old pipeline and reports the error.
- **Retained inputs.** `RenderPipeline`, `DepthOnlyPipeline` and `LineRenderPipeline` keep what
  they need to rebuild.
- **Stale registry keys.** The registry keeps its original spec as the key. Detach still removes
  by that spec, so it stays consistent.
- **Replacements chain.** The swapped-in set becomes the `old` for the next replacement, so an
  editor can replace the same shader repeatedly.

## Phases

### Phase 0 — spike

Replace one `VkPipeline` inside its wrapper, under 2 frames in flight, with validation layers on.

**Gate:** a clean validation log. If it isn't clean, the reason is recorded.

### Phase 1 — Vulkan swap primitive

**Gate:** a headless Vulkan test with two pipelines, one drawing red and one drawing blue, plus
validation layers.
- Replacing red's set with a runtime-built green definition turns only red's region green.
- Positive control: blue's region is unchanged. A primitive that swaps the wrong pipeline fails
  here.
- Replacing green again with yellow works, which proves chaining.
- Invalid WGSL is rejected before queueing, and red stays.
- A binding change is rejected, and red stays.
- Validation layers report nothing.

### Phase 2 — sample and docs

- `engine-showcase` desktop builds a variant of a pack shader at runtime and swaps it on a key
  press.
- Replace the ASL README's stale `generateAslShaders` section with the runtime replacement
  workflow.

**Gate:**
- The sample's pixel changes after the key press.
- A deliberately broken variant is logged, and the sample keeps running on the old shader.

## Limits and follow-ups

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
