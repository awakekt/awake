# Render packet field inventory

This is the F1 read-only inventory requested by the rendering-finalization roadmap. It records
the observed conversion surface at `68de82de2`; it does not select fallback behavior or add a
new public API.

| Source field | Current conversion destination | Status |
|---|---|---|
| `DrawCall.mesh` | `RenderDrawCommand.mesh` | Preserved |
| `DrawCall.material` | `RenderDrawCommand.material` | Preserved |
| `DrawCall.model` | `RenderDrawCommand.transform` | Preserved |
| `DrawCall.instanceModels` | `RenderDrawCommand.instances` uses the list size | Values unmapped |
| `DrawCall.transparent` | opaque or transparent command list | Preserved, with no sort performed by `GpuSceneFrame` |
| `DrawCall.extraUniformFloats` | — | Unmapped |
| `DrawCall.vertexAnimation` / `timeSeconds` | — | Unmapped |
| `DrawCall.instanceJointPalettes` | — | Unmapped |
| `DrawCall.instanceColors` / `instanceFrames` | — | Unmapped |
| `DrawCall.cullMode` | — | Unmapped |
| `ScenePassDescriptor.camera` | view-projection matrix and camera eye | Preserved by `GpuSceneFrame`; the contract legacy converter fixes aspect at `1f` |
| `ScenePassDescriptor.light` | shadow cascade pre-pass eligibility | Other lighting payload unmapped |
| `ScenePassDescriptor.environment` | shadow cascade pre-pass eligibility | Sky, fog and other environment payload unmapped |

The source conversion sites are:

- `awake/engine/render/passes/.../GpuSceneFrame.kt` converts a complete scene frame into
  `GpuPassInput`.
- `awake/engine/render/contract/.../renderer/Renderer.kt` has a separate legacy converter used
  by the default scene-shaped bridge.
- `awake/backend/vulkan/.../renderer/RendererDraw3D.kt` and
  `awake/backend/webgpu/.../renderer/RendererOpaqueDraws.kt` still prepare the complete legacy
  payload in their temporary P0 execution paths.

A2 owns static opaque and textured/PBR parity. A3 owns the remaining unmapped feature families;
neither field may be treated as intentionally discarded without an explicit capability decision.

The contract now also exposes the target resolved shape in `RenderDrawCommand`: an optional pipeline,
material binding, bounded vertex/index ranges, and explicit first/count draw parameters. These
fields are nullable or zero only during the migration bridge; the backend cutover must populate
them before the legacy mesh/material path is removed.
