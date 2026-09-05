# UI Antialiasing Capability Plan

Date: 2026-08-28  
Status: open

## Finding

Awake does not currently have a selectable AA preset. UI edges use two existing mechanisms:

- `fwidth` in generated ASL shaders for rounded quads and glyph coverage.
- A CPU-generated geometric fringe for filled and stroked paths before backend upload.

Both are real UI AA paths and are already exercised by shader and backend pixel tests. Vulkan UI
pipelines currently use `VK_SAMPLE_COUNT_1_BIT`; WebGPU has no multisampled UI target or resolve
path. FXAA, SMAA, TAA, and SSAA are not implemented.

Adding a selector that merely changes a label while all modes render identically would make the
API dishonest. TAA and FXAA/SMAA are also poor defaults for UI because their post-process filtering
softens text and one-pixel borders.

## Recommendation

Keep analytic/geometric AA as the default UI strategy. Add a selectable mode only through a
capability-backed contract whose selected mode changes render-target creation, pipeline state,
shader behavior, or a real post-process pass on every supported backend.

The first production mode should remain `Analytic` (the current path). A future `Msaa` mode can be
added only after both backends support multisampled UI attachments and an explicit resolve. FXAA,
SMAA, TAA, and SSAA belong to a scene/post-process policy, not the default UI pipeline.

## Delivery Sequence

### 1. Name the current strategy

Introduce a backend-neutral UI AA strategy in `render:contract` only if it carries behavior rather
than being a cosmetic enum. Its initial supported value must map to the existing analytic shader
and geometric fringe paths. Unsupported values must fail at construction with the requested mode
and backend capability in the message.

Acceptance criteria:

- The default strategy produces the current output on Vulkan and WebGPU.
- No backend-specific type appears in the contract.
- A request for an unimplemented strategy cannot silently fall back.

### 2. Verify the current paths at native resolution

Keep separate tests for shader derivative AA and path-fringe AA. Add a thin rounded border and an
open rounded-cap stroke to the backend preview matrix so regressions are visible at 1x and at a
supersampled diagnostic scale. Compare pixel probes, not only resized screenshots.

Acceptance criteria:

- Every tested edge contains both solid coverage and intermediate coverage.
- The 1 px border has no duplicate or overlapping fringe that removes its opaque core.
- Spinner and icon strokes retain round caps and joins.

### 3. Add multisampling only as a complete backend feature

If MSAA is needed after analytic AA is measured, add sample-count capability discovery, multisampled
color attachments, render-pass/framebuffer compatibility, and resolve handling in both backends.
The shared contract should express intent; it must not contain Vulkan sample-count flags or WebGPU
objects.

Acceptance criteria:

- Vulkan and WebGPU report the actual supported sample counts.
- The selected sample count is used consistently by the color attachment and UI pipeline.
- Readback and target-composite paths resolve correctly.
- A cross-backend pixel test proves the selected mode is not a no-op.

## Non-goals

- Do not use FXAA, SMAA, or TAA to repair a geometry or density bug.
- Do not add a fake preset menu before the selected mode changes rendering.
- Do not replace glyph `fwidth` coverage with a post-process filter.
- Do not move backend render-target or resolve ownership into `render:contract` or `passes2d`.

## Current Next Step

Measure the existing analytic/geometric paths with the native-resolution stroke diagnostic and
focused-input preview. Only if those measurements show a remaining edge-quality defect should the
MSAA capability work be opened; otherwise the defect belongs in path geometry, coordinate density,
or blending rather than in an AA preset.
