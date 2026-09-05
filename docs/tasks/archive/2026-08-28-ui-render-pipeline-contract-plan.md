# UI Render Pipeline Contract Plan

Date: 2026-08-28  
Status: complete

## Goal

Complete the UI render boundary so the shared render modules own stable UI data and draw
preparation, while Vulkan and WebGPU own only GPU resource creation and command recording.
The result must preserve paint order, support the current colored, rounded, glyph, texture, and
composite UI paths, and remain implementable by a third backend.

## Current State

| Area | Current implementation | Gap |
|---|---|---|
| `render:contract` | `UiDrawPrimitive`, `UiFont`, `UiFontSamplingMode`, and general pipeline contracts | `UiFontSamplingInfo` and `UiPipelineDescriptor` are missing |
| `render:passes2d` | `DrawRunCoalescer`, `uploadUiRuns`, `DrawRunRecorder`, and UI typealiases | Coalescing is shared, but the complete UI preparation decision path is not one common API |
| Vulkan | Unified backend `UiRenderPipeline` handles quad, rounded, glyph, texture, and composite variants | It still consumes backend-shaped construction data directly |
| WebGPU | Unified backend `UiRenderPipeline` handles the shared primitive variants; target composite remains a separate backend pipeline | It still duplicates the backend-side preparation and font configuration decisions |

The backend classes do not need to have globally unique names. `VulkanUiRenderPipeline` and
`WebGpuUiRenderPipeline` are useful plan names for ownership, but retaining `UiRenderPipeline` in
each backend package is acceptable if the public construction boundary becomes descriptor-driven.

## Scope Against The Renderer Plan

This plan owns the UI contract and UI preparation boundary. It does not absorb unrelated renderer
cleanup merely because the work is visible from the same frame loop.

| Area | Status | Scope decision |
|---|---|---|
| WebGPU `PipelineTable` alignment | Complete | Prerequisite; preserve and use it, no new work here |
| Unified `UiRenderPipeline` per backend | Mostly complete | In scope for the remaining descriptor-driven construction work |
| Shared/common `UiRenderPipeline` | Not recommended | Explicitly out of scope; GPU pipeline implementations stay backend-specific |
| `GpuBufferPoolManager` | Implemented for Vulkan and WebGPU | Prerequisite; keep allocation and upload ownership in each backend |
| `TextureResourceManager` | Not implemented | Out of scope for this plan; track as a separate renderer-resource plan because it covers all texture users, not only UI |
| `RendererDraw3D` decomposition | Partial | Out of scope for this plan; track as a separate frame-loop plan because it covers scene acquisition, recording, submission, and presentation |

The two deferred items remain legitimate follow-up work. They must not block the common UI
descriptor and preparation boundary unless implementation reveals a concrete ownership issue that
cannot be solved through the existing backend upload ports.

## Delivery Sequence

### 1. Add the common font sampling contract

Add `UiFontSamplingInfo` to `render:contract` as the backend-neutral representation of the data a
glyph pipeline needs from a font. It should express sampling mode and the numeric sampling
parameters needed by the shader, without carrying a texture, GPU handle, shader source, or backend
binding.

Adapt `UiFont` and the existing packed/MSDF font implementations at the boundary. Keep atlas
ownership in the font/asset layer and keep texture upload and descriptor/bind-group creation in
the backend.

Acceptance criteria:

- Vulkan and WebGPU derive their glyph shader configuration from the same `UiFontSamplingInfo`.
- No Vulkan/WebGPU type appears in the contract.
- Coverage-alpha and distance-field fonts retain their current output and regression coverage.

### 2. Add `UiPipelineDescriptor` to `render:contract`

Define a backend-neutral descriptor for a UI pipeline variant. It should describe stable decisions
such as primitive kind, vertex format, texture requirement, blend mode, premultiplied-alpha mode,
and target-composite intent where those decisions are already shared. It must not contain a Vulkan
render pass, WebGPU pipeline descriptor, shader module, descriptor set, bind group, or file path.

Use typed existing vocabulary (`VertexFormat`, `BlendMode`, and the UI pipeline kind contract)
instead of duplicated strides, attribute arrays, or uniform counts.

Acceptance criteria:

- The descriptor can be constructed without importing either backend.
- Both backends translate the same descriptor into their native pipeline state.
- Vertex attributes continue to derive from `VertexFormat.entries`.
- Existing pipeline variants remain behaviorally equivalent.

### 3. Complete shared UI draw preparation in `render:passes2d`

Keep `UiRunCoalescer` as the compatibility alias for `DrawRunCoalescer`, but make the intended
shared path explicit:

```text
UiDrawPrimitive list
  -> shared coalescing and paint-order preservation
  -> shared staged-run representation
  -> backend mesh upload port
  -> backend pipeline binding and recording
```

Move only backend-independent decisions into `passes2d`: run boundaries, primitive-kind
classification, vertex packing inputs, texture/font requirements, and ordering. The shared layer
must not allocate or retain GPU objects. Backend uploaders remain responsible for buffers,
textures, and native command encoders.

Acceptance criteria:

- Vulkan and WebGPU consume the same staged-run decisions for identical primitive input.
- No UI paint-order re-sorting is introduced for batching.
- Existing `DrawRunCoalescerTest` coverage is extended for glyph, texture, rounded, and composite
  transitions where needed.
- Backend `RendererDrawUi` files contain only adapter/upload/recording code.

### 4. Make backend pipelines descriptor-driven

Update the existing Vulkan and WebGPU `UiRenderPipeline` implementations to accept the common
descriptor and the backend-owned shader/resource inputs separately. If clearer names are needed,
introduce `VulkanUiRenderPipeline` and `WebGpuUiRenderPipeline` as internal names during the
migration; do not create a common implementation containing driver code.

Keep target-composite handling as an explicit capability/variant. Do not force WebGPU to emulate
Vulkan render-pass objects in the common layer.

Acceptance criteria:

- Renderer pipeline factories build both backends from the same descriptor values.
- No duplicate UI vertex-layout or blend-policy decisions remain in backend factories.
- The backend layering check remains clean.

### 5. Verify and close the boundary

Run the contract, passes2d, backend, and showcase tests after each stage. Add structural checks for
the new module ownership and a small cross-backend descriptor parity test.

Required verification:

```bash
./gradlew :awake:engine:render:contract:desktopTest \
  :awake:engine:render:passes2d:desktopTest \
  :awake:backend:vulkan:desktopTest \
  :awake:backend:webgpu:desktopTest \
  :samples:ui-showcase:desktopTest --no-daemon
python3 tools/verify_agent_skills_sync.py
```

Also verify the real UI path with the existing showcase and font pixel baselines. A successful
headless CPU snapshot alone is not proof of Vulkan/WebGPU GPU paint fidelity.

## Non-goals

- Do not move Vulkan or WebGPU pipeline implementations into `render:contract` or `passes2d`.
- Do not add a generic runtime shader language or shader compiler to this plan.
- Do not redesign `UiDrawPrimitive` or the Compose layout/authoring API unless a concrete contract
  gap blocks the migration.
- Do not sort UI runs by pipeline or material; paint order is observable behavior.
- Do not add `TextureResourceManager` here; it is a separate renderer-resource plan covering all
  texture lifetimes and users.
- Do not refactor `RendererDraw3D` here; it is a separate frame-loop plan covering 3D and UI frame
  orchestration.

## Done Definition

This plan is complete when both missing contract types exist, the shared preparation path is the
single source of truth for cross-backend UI decisions, both backend pipelines are descriptor-driven,
and the required tests pass on the supported desktop targets without introducing backend imports
into the common modules.
