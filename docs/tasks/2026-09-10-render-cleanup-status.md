# Render architecture cleanup status

Status: complete (verified 2026-09-11; all G1-G6 acceptance gates 100% green)

The hardware boundary and all cleanup slices are landed and verified. Cross-backend parity,
active depth feature families, environment/fog controls, upload lease lifecycle, session/DI
composition, backend layering, and production Wasm builds are 100% green across both backends.

The generic preparation boundary is final (`4c8e19532`, `96df14508`): `GpuDrawRequest`,
`GpuDrawPreparer`, and `GpuDrawPreparationContext` live in `render:contract`, while
`RenderDrawCommand` is the compatibility alias in `render:passes`. Duplicated backend CPU
preparation policy is fully commonised: shared `depthSortKey(cameraEye)`, `batchKey()`,
instanced-kind classification, and `SortedDraws<P>.allDraws` live in `render:passes`.
Native buffer allocation, pipeline lookup, bind-group creation, and command recording remain in each
backend.

Depth and feature-family controls are fully verified (`ce3559033`, `62938c045`, `21ef0125e`, `eddf87d4e`):
active depth-key classification covers `Ordinary`, `Instanced`, `Skinned`, `SkinnedInstanced`,
`Particle`, and `Masked`; reserved domains (`Terrain`, `Sprite`, `Tilemap`) are verified distinct
and non-colliding. Negative tests prove that masked draws cannot fall back to opaque depth pipelines,
alpha cutoff is preserved, and Vulkan/WebGPU generic-shadow packet lowering is covered in exact parity.
WebGPU offscreen rendering applies `input.viewport` in parity with Vulkan, verified with pixel tests.

Upload lease lifecycle and session/DI controls have complete test coverage
(`70f4ce96f`, `14d532941`, `26912b38e`, `ec1bae21d`): upload lease terminal, cancellation, conflict,
and device-loss failure tests pass, and render-session and settings integration tests verify
reverse-order teardown and generation-aware stale result rejection.

| Area | Status | Evidence |
|---|---:|---|
| Backend-neutral render contract and packet flow | 100% | `GpuPassInput`/generic pass packets are consumed by both backends; scene lowering is confined to `render:passes`, while the contract publishes generic preparation/resolved packets and hardware handles. The final `DrawCall` compatibility alias is removed; callers now use the explicit `RenderDrawCommand` name. The shared draw compiler is source-type generic and publishes no source-resolution bridge. |
| Vulkan/WebGPU backend isolation | 100% | Backends consume generic preparation requests and resolved packets; the old source packet, resolver files, and duplicated source adapters are removed. Single and instanced uniform ABI packing, uniform-plan selection, instanced-kind classification, depth sorting, and batch-key clustering are shared, while backend preparation retains native allocation, binding, and recording. The backend import/content-vocabulary exemption ledgers are empty, and production Wasm distribution plus desktop checks pass. |
| Scene/render module identity | 100% | `:awake:scene:scene3d`, `RenderSystem3D`, `render:passes`, and `render:passes2d` are established. Particle uniform ABI, shadow cascade math, and depth-caster identity live in `render:passes` instead of backend renderer policy; final phase closure is complete. |
| 2D/3D dependency boundary | 100% | `passes2d` remains backend-neutral; the showcase-local `RenderSystem2D` stages a real colored quad and submits its backend-neutral command through the runtime UI-staging hook. Desktop frame tests pass. The UI showcase uses the application-level Compose host and resolved empty-frame packet; its production WebGPU bundle renders the Compose surface in a fresh browser run. |
| `RenderSystem3D` decomposition | 100% | Lighting, culling, particles, mesh-family orchestration, feature aggregation, and scene pass planning use dedicated seams; `SceneRenderPlanner3D` owns renderer-free extraction and compilation while the coordinator retains only ECS lifecycle, diagnostics, and backend submission. Plugin lifecycle and ownership rules are recorded in `D34-render-plugin-lifecycle.md`. |
| Depth caster & feature coverage | 100% (active families) | `DepthCasterKind`, `AlphaMode`, `DepthRenderKey`, alpha-cutoff preservation, active-family classification across Ordinary/Instanced/Skinned/SkinnedInstanced/Particle/Masked, reserved-domain controls, and generic shadow lowering tests are landed and pass. |

## Acceptance evidence verified

1. Commonised duplicated backend CPU preparation policy into `awake:engine:render:passes` (`96df14508`).
2. Added active depth-key, alpha-cutoff, reserved-domain, masked fallback/layout, and generic-shadow safety controls (`ce3559033`, `62938c045`, `21ef0125e`).
3. Applied `input.viewport` to WebGPU offscreen `renderToTexture` in parity with Vulkan, verified with pixel test (`eddf87d4e`).
4. Added upload lease terminal state, sealed-command cancellation, and device-loss failure controls across contract and both backend upload executors (`70f4ce96f`, `14d532941`, `26912b38e`).
5. Added render-session and settings integration tests at frame boundary with reverse-order teardown and generation-aware stale result rejection (`ec1bae21d`).
6. Full parity test suite (`:awake:engine:render:parity:desktopTest`) passes 100% green across all 4 test classes (`SceneBackendParityTest`, `SceneShadowBaselineTest`, `SceneSelfShadowParityTest`, `UiBackendParityTest`) after resolving host GLFW library loading, managing unified session lifecycle, and normalizing WebGPU sRGB to linear space for cross-backend radiance comparison.

## Uniform offset audit

The production render path has no hand-authored uniform field offsets or backend array
concatenation. `UniformWriter` advances through `UniformLayout` fields; shared PBR, light, shadow,
point-light, cascade, and UI helpers now derive their source slices, buffer sizes, and array strides
from declared layouts, including matrix-array elements through `UniformField.writeMat4Element`, and
the particle compiler obtains its camera/frame offsets with
`UniformLayout.offsetOf`. Backend buffer uploads start at offset zero by design; resource binding
offsets are API-level binding metadata, not uniform ABI offsets. Remaining numeric array indices are
vertex/instance packing or test fixtures, not uniform struct locations.

The pre-merge guard is recorded in `awake-render-pipeline/SKILL.md` and enforced by the root
`verifyRenderUniforms` task wired into `check`: production-source searches must stay clear of
`mvp.data + ...`, literal uniform indices, numeric light/material payload slices, direct indexed
`*Uniform`/`*Params` scratch writes, and WGSL substring binding detection. The scan now includes
`awake:asset:shader-pack`; terrain ring and parameter packing uses `TerrainUniformLayout` rather
than a hand-maintained vec4 stride. The guard also
rejects future `source.contains("@group(...)")` inference directly, so the WebGPU layout bug
cannot return through a new spelling. Pipeline declarations must carry explicit group/binding
metadata.

## Backend status

| Measure | Status | Evidence |
|---|---:|---|
| Scene/game-authored dependency cleanliness | 100% | Neither backend imports `awake:scene`/ECS types or the render-runtime scene vocabulary enforced by `verifyBackendLayering`. The exemption ledger is empty; source lowering and the resolver adapter now happen in `render:passes`, and backends receive resolved GPU packets. |
| Desktop backend stability | 100% Green | Vulkan (`:awake:backend:vulkan:desktopTest`), WebGPU (`:awake:backend:webgpu:desktopTest`), showcase desktop tests, and all 4 parity test suites (`:awake:engine:render:parity:desktopTest`) pass 100% green. |
| Wasm compile stability | 100% Green | Both showcase production Webpack bundles (`:samples:engine-showcase:wasmJsBrowserProductionWebpack`, `:samples:ui-showcase:wasmJsBrowserProductionWebpack`) build 100% green. |
| Browser runtime stability | Verified | Both production showcase bundles compile and serve; WebGPU bind-group derivation and uncaptured-error handling validated. |

## Naming rule

Keep the existing Gradle hierarchy:

```text
:awake:scene:scene-core
:awake:scene:scene3d
:awake:engine:render:contract
:awake:engine:render:passes
:awake:engine:render:passes2d
```

Do not add aliases such as `:awake:scene:2d`, `:awake:scene:3d`, or
`:awake:render:passes:2d`. They duplicate publication identities and make dependency direction
ambiguous. A future `:awake:scene:scene2d` is safe once reusable 2D ECS components exist; a future
`passes3d` is safe only as an extraction from the current `passes` module, without changing the
RHI or backend APIs.
