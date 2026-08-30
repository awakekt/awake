# Compose destination-colour blend modes

## Decision

`Screen`, `Overlay`, `Darken`, `Lighten`, and related `graphicsLayer` blend modes must be
implemented as sampled composites, not mapped to a fixed blend state. Their formula reads the
already-painted destination colour; `SourceOver` and `Plus` remain the only fixed-function modes.

## Required render contract

1. Add a renderer-neutral UI composite operation that takes a source render target and a sampled
   destination render target, writes a distinct output target, and names the blend equation.
2. Back it with one common pass description and backend-owned shader/pipeline resources on Vulkan
   and WebGPU. Neither backend may define Compose-layer semantics.
3. Give the Compose host a full-frame UI target pair when a frame contains any destination-colour
   layer. Paint preceding primitives into the current target, composite the layer into the other,
   then swap. Fixed-function-only frames keep their direct path.
4. Preserve clip, layer transform, alpha, and premultiplied-alpha semantics. A source must never be
   sampled from the same attachment it is being written to.

## Verification

- Structural test: a destination-colour layer requests the target-pair path only for a mode that
  needs destination sampling.
- Vulkan and desktop WebGPU headless pixels: red over blue must distinguish `SourceOver`, `Plus`,
  `Screen`, and `Overlay` at an overlap sample.
- Nested-layer test: a destination-colour child resolves against the already-composited parent,
  not transparent black.
- One direct-path regression test proves ordinary `SourceOver` frames do not allocate a full-frame
  target pair.

## Non-goals

- Do not expose unimplemented blend enum values first.
- Do not read back to the CPU or emulate the equation per pixel on the CPU.
- Do not make `ui` or `compose` depend on Vulkan or WebGPU types.
