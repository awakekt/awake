# Render architecture continuation handoff

**Verification note (2026-09-11):** this assignment document is complete, but the architecture
implementation is not yet 100% closed. The authoritative roadmap still marks A2–A6 open and A7
not started. The acceptance commands below pass; they are necessary evidence, not proof that the
remaining environment/feature parity and production lifecycle gates are complete.

## Remaining work to reach 100%

These are the concrete open items after the latest audit. “Complete” means the listed evidence is
green and recorded here; a compilation-only result does not close an item.

| Task | Owner | Required work | Exit evidence |
|---|---|---|---|
| Environment/fog parity | Gemini 3.8; Astra for shader/ABI decisions | Diagnose why Vulkan/WebGPU clear textured red sums differ (`2502984` vs `2655162`) in `SceneBackendParityTest`; fix the generic environment path without loosening `RED_SUM_TOLERANCE`. | `genericEnvironmentFogAffectsTexturedDrawOnBothBackends` passes, including clear and fogged comparisons. |
| Parity test host stability | Gemini 3.8 | Remove or isolate the macOS duplicate GLFW loading (`/opt/homebrew/.../libglfw` versus LWJGL extracted GLFW) so the parity suite completes without SIGTRAP. | Full `SceneBackendParityTest` completes with no skipped tests, loader crash, or uncaptured GPU error. |
| Depth feature completion | Gemini 3.8; Astra for new authored APIs | Bind and verify every supported active depth family on both backends. Keep `Terrain`, `Sprite`, and `Tilemap` reserved unless their scene components and shaders already exist; document any intentionally deferred family. | Positive/negative depth controls and backend pixel/trace tests pass for each supported family; no opaque fallback. |
| Production lifecycle | Gemini 3.8; Astra for lifecycle contract changes | Add real resize/device-loss invalidation and frame-resource retirement, not only simulated lease failures. | Backend lifecycle tests prove stale resources cannot submit and retired resources are released on Vulkan and WebGPU. |
| Session/settings/effects integration | Gemini 3.8; Astra for public ownership semantics | Connect render-session ownership, frame-boundary settings, and effect configuration to the production bootstrap path. Keep GPU objects out of `awake.state`; use DI for owner/session construction. | An application-level integration test proves construction, frame application, and reverse-order teardown. |
| Compatibility and release audit | Astra review required | After the preceding gates pass, remove migration-only aliases/bridges that are no longer needed, refresh API dumps, update changelog/release notes, and accept any public API removal. | Contract/layering guards, API checks, full desktop suites, both Wasm production builds, and browser smoke all pass; roadmap A2–A7 can then be closed. |

Until these rows are complete, the correct status is **active**, even though backend isolation,
uniform guards, focused depth controls, and the previous showcase smoke are green.

## Gemini 3.8 execution package (2026-09-11)

This is the actionable handoff for the remaining cleanup. The baseline is `4c8e19532`
(`GpuDrawRequest`/`GpuDrawPreparer` moved to the generic render contract) plus `7eb927e09`
(`ui-showcase` WebGPU entry points fixed). The working tree also contains unrelated user edits;
do not stage or reformat them.

### Verified baseline

- The hardware contract is generic: `GpuPassInput`, `GpuSubPass`, opaque handles,
  `GpuResolvedDraw`, `GpuDrawRequest`, and `GpuDrawPreparer` contain no ECS or scene vocabulary.
- The old source-resource resolver files are deleted. `render:passes` is the scene lowering layer;
  Vulkan and WebGPU depend on it as `implementation`, while backend executors consume generic
  packets through `GpuPassExecutor`.
- `verifyRenderUniforms`, `verifyRenderContractBoundary`, and both backend layering checks pass.
  The backend exemption ledgers are empty. No production hardcoded uniform offsets remain; the
  guard is the regression gate.
- `:samples:ui-showcase:wasmJsBrowserProductionWebpack`,
  `:samples:ui-showcase:desktopTest`, and the development browser run pass after declaring the
  shader entry points explicitly. The engine-showcase production browser smoke also rendered
  (`Draws: 4`, `Instances: 4`, `Unresolved: 0`).
- The broad headless parity runner can still be blocked before assertions by duplicate GLFW
  libraries on this macOS host. Record that as an environment blocker and run targeted backend
  tests; do not weaken or delete the parity baselines.

### Work Gemini may complete

1. **G1 — commonise backend CPU draw preparation.** Compare
   `awake/backend/vulkan/.../renderer/RendererDraw3D.kt` with
   `awake/backend/webgpu/.../renderer/RendererOpaqueDraws.kt`. Extract only shared request
   grouping, ordering, instanced-kind classification, transparent sorting, and
   `DepthRenderKey` decisions into `awake:engine:render:passes` (or the generic contract port).
   Leave native buffer allocation, pipeline lookup, bind-group creation, and command recording in
   each backend. Use ports/capabilities instead of importing Vulkan, WebGPU, ECS, or game types.

2. **G2 — finish depth and feature-family parity.** Add controls and backend bindings for
   `Ordinary`, `Instanced`, `Skinned`, `SkinnedInstanced`, `Particle`, and `Masked`. Implement
   `Terrain`, `Sprite`, and `Tilemap` only when the corresponding scene component and shader
   pipeline already exist; otherwise document them as reserved work instead of inventing a new
   authored API. Keep `DepthCasterKind`, `AlphaMode`, and `DepthRenderKey` as the selection key.
   Add positive and negative controls proving that masked and unsupported variants cannot silently
   fall back to an opaque or wrong-layout pipeline.

3. **G3 — prove environment and packet-order parity.** Add targeted Vulkan/WebGPU controls for
   fog, sky/environment, shadows on/off, transparency ordering, skinned/particle payloads,
   viewport handling, and UI/debug ordering. Prefer existing tests under
   `awake:engine:render:parity` and headless suites; compare pixels or command traces and keep
   the current baselines unchanged.

4. **G4 — remove compatibility residue after G2/G3 are green.** Audit the direction of the
   `RenderDrawCommand` alias and remove stale resolver names, backend scene aliases, and obsolete
   `performDraw`/`performRenderToTexture` paths. Do not remove a public API or change an API dump
   without an explicit Astra review; update generated dumps when a contract change is accepted.

5. **G5 — advance A5/A6 within existing policies.** Add tests and mechanical wiring for upload
   lease terminal states, cancellation, resize/device-loss invalidation, frame-resource retirement,
   render-session ownership, settings, and DI composition. Keep GPU objects out of
   `awake.state`; use `kotlinx.atomicfu` only for shared terminal-state transitions (for example
   upload leases), and use `awake.di` for owner/session construction. Escalate any new concurrency
   or public lifecycle semantics to Astra.

6. **G6 — keep the evidence current.** Update this handoff and the status/roadmap documents after
   each accepted slice. Run the guards, API checks, backend tests, showcase desktop tests, Wasm
   production builds, and browser smoke relevant to the changed files. Report changed files,
   exact commands, evidence, and any blocker with every handoff.

### Astra-owned decisions and stop points

Gemini can make mechanical extractions, tests, fixtures, docs, and API-dump refreshes. Return the
work to Astra before introducing a new public contract, moving a module boundary, changing a
uniform ABI or shader entry-point/binding rule, defining a new scene feature, accepting a new
pixel baseline, removing a public API, or closing a phase. Never add scene vocabulary to a
backend executor and never mark a phase complete from compilation alone.

### Execution order and acceptance matrix

Work in this order: **G1 common preparation → G2 depth/features → G3 environment/order parity →
G4 compatibility cleanup → G5 A5/A6 → G6 A7 release audit**. A phase is complete only when its
targeted tests and the guards pass on both backends, the relevant desktop/Wasm/browser consumers
render, and the result is recorded here.

```text
./gradlew verifyRenderUniforms verifyRenderContractBoundary \
  :awake:backend:vulkan:verifyBackendLayering \
  :awake:backend:webgpu:verifyBackendLayering \
  :awake:engine:render:contract:desktopApiCheck \
  :awake:engine:render:passes:desktopApiCheck --no-daemon --console=plain

./gradlew :awake:backend:vulkan:desktopTest :awake:backend:webgpu:desktopTest \
  :samples:engine-showcase:desktopTest :samples:ui-showcase:desktopTest \
  --no-daemon --console=plain

./gradlew :samples:engine-showcase:wasmJsBrowserProductionWebpack \
  :samples:ui-showcase:wasmJsBrowserProductionWebpack \
  --no-daemon --console=plain
```

The expected final report is a small table with **task**, **files changed**, **commands/evidence**,
**remaining blocker**, and **Astra decision needed**. Do not claim 100% architecture completion
until G1–G6 and the roadmap exit gates are evidenced.

## Latest continuation (2026-09-11)

- `9dcb50ea8` removes the duplicate `GpuDrawRequest` data class. Its old name is retained only as a
  deprecated type alias for `RenderDrawCommand`, so the migration resolver and backend adapters
  exchange one packet definition while the final direct-resolved path remains gated.

- `76ce3fba2` marks `GpuDrawPreparer` and
  `GpuDrawPreparationSource` as migration-only APIs. New call sites now receive a compiler
  warning that the source resolver is not the final RHI shape; removal remains gated on the
  remaining cross-backend feature-family controls.

- `6a6b8d62c` hardens WebGPU content-pipeline bind-group construction: every declared uniform,
  sampled texture, sampler, and storage binding must resolve to a valid resource before the
  immutable bind group is created. Missing declarations now fail synchronously with the entry
  point and binding number instead of surfacing later as a command-buffer validation error.

- `c87753230` refreshes the backend commonisation survey against the current tree: shared render
  code is 7,588 lines, or 32.8% of the 23,159-line render stack (51.8% of renderer/pipeline
  counterparts). The remaining work is source-resource preparation and feature-family parity.

- `102746cf8` wires WebGPU's uncaptured-error callback into the browser host. The first
  asynchronous validation error is reported once and latches the animation loop before another
  invalid command buffer can be submitted. This is a diagnostics/failure-containment slice; the
  current wgpu4k API still does not expose `GPUDevice.lost`, so device-loss recovery remains an
  open A5 gate.

- `052f54125` centralizes single and instanced draw-uniform packing in `render:passes`, so
  Vulkan and WebGPU share one ABI policy while retaining backend-owned resource allocation. The
  same slice fixes terrain's field-sized reusable buffers by adding layout-aware
  `UniformField` writers; contract tests, terrain headless tests, both backend suites, uniform
  and layering guards, showcase desktop tests, and production Wasm bundles pass.

- `599876a4a` removes the last manual matrix-array stride calculation from shadow-cascade packing.
  `UniformField.writeMat4Element` now owns the declared mat4 stride just as vec4 arrays do;
  contract/API checks, both backend suites, and the uniform guard pass.

- `77fa4deae` completes the depth-side WebGPU bind-group guard: cascade, material, and joint
  palette groups are created only when the selected shader metadata declares their group. Missing
  groups now skip binding instead of requesting a nonexistent layout; desktop tests, Wasm
  compilation, and both showcase production bundles pass.

- `99fd298a2` aligns WebGPU's skinned-instance storage bind group with the shared
  `BindingSemantic.JointPalette` slot (group 3) and adds a metadata-aware binding overload. This
  removes the last known group-1/group-3 drift in the WebGPU draw path.

- `065641e60` adds a common WebGPU regression test that locks the skinned palette group to the
  shared semantic slot.

- Fresh production-bundle browser verification after `99fd298a2` succeeded on 2026-09-11: the
  engine showcase displayed its lit scene and diagnostics (`Draws: 4`, `Instances: 4`,
  `Unresolved: 0`), while the UI showcase displayed the Compose navigation and AspectRatio page.
  No bind-group validation errors or black-canvas failure appeared in either tab.

- `da242e65e` closes a uniform-audit gap in the terrain content feature: reusable terrain and
  clipmap parameter blocks now size and write through `TerrainUniformLayout`, and the root audit
  scans `awake:asset:shader-pack` plus indexed `*Uniform`/`*Params` scratch writes.
- `bf2ecfa98` makes `DrawCallCompiler` source-type generic and removes the production-only
  `GpuDrawResolver` compatibility API, leaving authored `RenderDrawCommand` at the scene-to-pass
  test boundary only.
- `fd9403f61` carries explicit pipeline metadata into WebGPU instanced/skinned uniform resource
  allocation; those paths now refuse to allocate group-0 resources when the selected shader does
  not declare group 0.
- The parity harness now exposes a generic fog positive control for the shared textured PBR draw;
  it compiles with both backends and is ready for runtime execution once the host's duplicate GLFW
  loading is removed.

- `ea76f8e46` scopes WebGPU material bind groups and opaque handles by the concrete pipeline
  object. WebGPU's auto-derived bind-group layouts are pipeline-owned; the former single cached
  material group could be reused by transparent, wireframe, or format variants and produced the
  browser's incompatible-layout errors.
- `d17b68e8b` removes the stale WebGPU capability exclusion for non-instanced
  `PositionNormalColorSkin`. The existing per-draw uniform path already carries the joint palette,
  so the showcase's declared skinned pipeline is now built instead of being reported as dropped.
- `cb7d42018` removes resolved pipeline, buffer, binding, and count fields from the transitional
  `RenderDrawCommand`; `GpuResolvedDraw` remains the only resolved hardware packet. The source
  command now contains scene-lowering inputs and authored animation/instance payloads only.
- The development server was started after these fixes and served `Awake Engine Showcase` from
  `/` with a real canvas. The verification Chrome profile still exposed no usable WebGPU device,
  so its canvas remained black; this proves the HTML-entrypoint fix, but not browser-side pixels.

- `e15f02a7d` completes the Vulkan resolved-depth cutover: the depth and camera-depth passes now
  consume `PreparedDraw` packets directly when `GpuPassInput.resolvedPath` is active. The legacy
  lowering remains only for deprecated compatibility inputs.
- `34bd29248` moves backend frame contexts to the lowered `GpuEnvironmentState`; `e82bc1a44`
  removes the remaining WebGPU scene-context conversion. Backend features now receive generic
  environment policy, while the authored `EnvironmentUniforms` view remains only as a temporary
  shared-feature compatibility surface.
- `3803769c2` removes that authored environment conversion from Vulkan and WebGPU backend
  recording paths. Backend command recorders now accept the lowered packet state directly; the
  remaining `GpuEnvironmentState` fields are still transitional feature policy and are tracked for
  the later move above the hardware contract.
- `64ad8935e` adds the engine-showcase Wasm HTML entrypoint, and `0678e2a48` ignores Kotlin/Wasm's
  unreachable `node:module` fallback during browser bundling. The development server now builds
  successfully and serves `/` as the showcase page rather than a directory index. Browser-side
  rendering still requires a WebGPU-capable browser; the verification Chrome profile exposed no
  `navigator.gpu` in this environment.
- `4a41b3a14` exposes the runtime's existing FPS, frame-time, UI-build, GPU-resource-wait,
  UI-staging, and simulation/render phase timings in the engine-showcase stats card. Press **F2**
  to enable phase collection. Cross-platform heap memory is intentionally labelled unavailable
  until a real platform memory provider exists; the UI does not guess from JVM-only APIs.
- The Vulkan, WebGPU, and engine-showcase desktop regression commands pass after this slice.
- The deletion-gate matrix also passes on 2026-09-10: Vulkan headless content/depth/shadow/
  transparency tests, WebGPU pixel/depth/generic-shadow tests, and showcase instanced,
  skinned, shadow-mode, and particle frame tests all completed successfully. This validates
  the current resolved-packet path and keeps A0 closed; it does not by itself authorize removing
  the deprecated `RenderDrawCommand` resource bridge or the top-level legacy draw lists.
- `60c8ea218` fixes one of those parity defects: Vulkan's resolved-draw provider now retains
  material uniform-slot allocation across the whole command batch. Previously every command
  sharing a material overwrote slot zero, which collapsed authored transforms and made the scene
  look like a single stretched/partial object. The transform-presented-frame gate now passes.
  The heightfield scene suite now passes after its frame-diff helper excludes the live stats-card
  columns; the dynamic FPS/draw labels were the source of the previous false failures.
- `2883e85d9` removes unresolved compatibility recorder branches from both backend executors; they
  now require `resolvedPath`, and direct compiler callers inject the backend resolver.
  `7c25ef43f` completes the corresponding headless depth fix: camera-depth-only plans resolve
  depth bindings without a shadow feature, and WebGPU/Vulkan scene-depth tests pass.
- `3ed677af7` removes `opaqueDraws` and `transparentDraws` from `GpuPassInput`. Scene tests now
  observe authored commands at the resolver boundary, while the hardware packet exposes only
  resolved draws. The resolver consumes backend-neutral `RenderDrawCommand` values directly; scene-owned
  lowering happens in `render:passes` before the backend boundary. Remaining
  A7 debt is feature-policy and legacy shader-layout migration, not scene vocabulary in backends.
- `927ff927a` moves production scene compilation onto an explicitly injected resolver:
  `SceneAppLifecycleRuntime` captures the backend resolver at readiness and passes it to
  `RenderSystem3D` and offscreen readback. `Renderer.gpuDrawPreparer` is now marked
  migration-only; it remains only as a compatibility default for renderer-focused tests until
  the resolver is removed from the HAL contract.
- `3a304b349` removes that resolver property from the shared `Renderer` contract. The capability
  now lives in `render:passes` as `GpuDrawPreparationSource`; Vulkan/WebGPU implement it and the
  scene runtime discovers it through the render-pipeline layer. The HAL no longer exposes the
  source-resolution bridge.
- The current continuation keeps `GpuDrawPreparationContext`, `GpuDrawRequest`,
  `GpuDrawPreparer`, and `GpuResolvedDraw` in `render:contract`; `RenderDrawCommand` is the
  compatibility alias in `render:passes`. The old source-resource resolver files and bridge APIs
  are deleted. `GpuDrawRequest` is the final generic preparation request, while
  `GpuResolvedDraw` is the executor packet; the contract guard rejects any scene packet or
  source-resolution bridge from returning to the RHI surface.
- The follow-up commonises draw-uniform plan selection (`LitShadow`, `Skinned`, `TexturedPbr`,
  and `Lit`) in `render:passes`; Vulkan/WebGPU retain only native allocation and binding work.
  The shared plan has common tests and both backend compile checks pass.
- The latest slice also commonises instanced draw-kind classification for source and resolved
  packets (`Plain`, `Skinned`, and `Particle`), so Vulkan and WebGPU no longer maintain separate
  format/payload rules. The passes test suite and API checks pass.
- WebGPU draw-uniform allocation now accepts the pipeline handle and self-checks its explicit
  group metadata before creating a slot. Resource-free pipelines therefore cannot accidentally
  receive a binding-0 group even if a caller forgets the outer guard.
- Backend preparation now consumes the pass-owned `RenderDrawCommand` directly. The duplicated
  field-for-field `VulkanSourceDraw` and `WebGpuDrawRequest` adapters are gone; only the backend
  resource casts remain at the preparation boundary.
- Depth-caster identity is now derived once in `render:passes` from the source packet's format,
  instance payload, and alpha mode. Both providers use that `DepthRenderKey`, including the
  skinned-instance palette decision.

The visible renderer is therefore not expected to change because of this cleanup. If a cube,
skinned model, particle system, or shadow disappears, that is a feature-parity regression to fix
under A2/A3, not an intentional consequence of separating the hardware contract.

This file is the short continuation index for the A1–A7 finalization effort. The authoritative
phase checklist remains [the finalization roadmap](2026-09-09-render-architecture-finalization-plan.md);
this file records the current handoff point so work can resume without reconstructing the thread.

## Current state

### Why the architecture still feels combined

The scene/hardware boundary is real but the module names and dependency graph still expose both
layers together. `awake:engine:render:contract` is the hardware-facing surface: it does not import
scene-authored `DrawCall`, `SceneLight`, or `EnvironmentUniforms` types. Those types now live in
`awake:engine:render:passes`, which is the transitional render-pipeline layer that compiles scene
data into generic packets.

The backend modules now keep `render:passes` as an `implementation` dependency. Their source
still uses transitional pass-owned preparation and feature seams internally, but backend
consumers no longer inherit authored scene vocabulary from the driver classpath. The target
dependency shape remains:

```text
scene/runtime -> render:passes (future engine:render-pipeline) -> render:contract (future awake:rhi)
backend:*     -> render:contract (future awake:rhi)
```

The remaining coupling is now source-level internal code that must be moved behind the generic
packet/executor seam before the physical module split. The classpath boundary is already enforcing
the intended direction; deleting the pass imports prematurely would only move the coupling into
ad-hoc backend adapters.

- A0 is complete. The historical duplicate-submission/flicker route has a 300-frame regression;
  the current task explicitly treats that bug as fixed.
- A1 is complete. Generic `GpuPassInput`, typed handles, leases, resolver ownership, and
  layering checks exist. `Renderer` is generic-only, and scene data (`RenderDrawCommand`,
  `SceneLight`, `EnvironmentUniforms`, and shadow uniform payloads) now belongs to `render:passes`; remaining
  contract debt has no remaining renderer-owned scene policy; viewport state now travels in the
  generic packet. Scene
  uniform and shadow helpers have moved above the HAL boundary, and environment/shadow policy is
  packet-owned.
- A2 is in final cleanup. `RenderSystem3D` uses `ScenePassCompiler`; directional/point-light
  payloads, ordering, and planned shadow subpasses are present. The remaining A2 work is shared
  backend CPU preparation policy, textured/PBR and environment parity, and removal of stale
  compatibility wording.

The resolver seam now receives a scene-free per-frame context containing camera state, viewport,
packed pass uniforms, environment policy, and compiled shadow matrices (`c8b7c7966`, `d29235094`).
Resolved shadow subpasses carry `resolvedDraws` and leave the authored `draws` list empty
(`4825ba082`); positive and negative shadow-policy coverage is recorded by
`ScenePassCompilerTest` (`2c82b451d`).
The contract now provides `PreparedDraw.toGpuResolvedDraw()` with complete handle, ordering, and
instance-binding coverage (`6b5b50421`), so backend resolvers do not need duplicate packet
mapping code.
`GpuPassInput.resolvedDraws` now centralizes the canonical executor sequence and both backends use
it (`e1831e5b9`); legacy authored lists remain isolated to the compatibility branch.
`16ac2f95` installs concrete Vulkan and WebGPU `GpuDrawPreparer` instances from both
backend bootstraps. The focused Vulkan, WebGPU, showcase, and render-system suites pass with the
live generic path enabled. The remaining mixed feeling is now duplicated backend CPU preparation
policy: both providers still decide grouping, ordering, instanced kinds, and resource preparation
inside backend files. Commonise those decisions behind the generic request/preparer seam, then
keep native allocation and binding in each backend.
`dfa05f024` makes `ScenePassCompiler` create each generic command once and use the shared ordered
batch resolver/sorter. `89f688116` makes the deprecated authored draw lists optional when callers
construct `GpuPassInput`, so new callers naturally provide resolved packets only.
- A3 is foundation-started. Vulkan and WebGPU generic shadow forwarding exists; cross-backend
  pixel/trace controls for all feature families are not complete. The transitional Vulkan and
  WebGPU packet lowerers now preserve plain, skinned-instanced, and particle bindings.
  `DepthCasterKind`, `AlphaMode`, and `DepthRenderKey` now define the caster-selection contract.
  Ordinary/instanced/skinned/particle variants are built and bound by both backends; terrain,
  sprite, and tilemap families are declared for their upcoming domain-specific depth pipelines.
  Pixel/trace controls for all families are still required before the A3 exit gate.
- A4 is advanced. Generic packet execution is owned by backend `GpuPassExecutor`s, and the
  unreachable legacy scene draw/offscreen bodies plus Vulkan scene preparation helpers have been
  removed. `SceneAppLifecycleRuntime.readback` now compiles a generic packet directly. The
  `Renderer` scene overloads and `ScenePassDescriptor` are removed; the remaining boundary task is
  validating packet-owned feature parity and removing any stale documentation aliases.
- A5/A6 have isolated foundations only. Device-loss/resize upload integration and explicit
  render-session/settings composition remain.
- A7 is not started: compatibility removal, changelog/API review, and final consumer validation
  are still required.

## Compatibility decision

The renderer contract no longer carries scene compatibility APIs or scene-authored packet inputs.
The render-pipeline module lowers scene data into `GpuEnvironmentState` and `GpuPassInput`; backend
implementations continue to receive only generic packets.

## Why `RendererDraw3D` is an extension today

The extension functions are file organization around each backend's `expect`/`actual` renderer
class. They are statically dispatched and therefore are not an abstraction or membership boundary.
The generic packet body has moved into `GpuPassExecutor`, and the unreachable full-scene draw and
offscreen bodies are gone. Remaining extensions are generic preparation/context seams; scene data
relocation is now the boundary task.

## Last landed commits

- `31c25241d` / `b6e291d0b` — restore WebGPU and Vulkan generic lowering for plain,
  skinned-instanced, and particle draw payloads; `b795ea0d4` records the remaining depth-prepass
  gap.
- The Vulkan headless shadow contrast gate now passes after the offscreen executor rebinds the
  scene-owned shadow map after the cascade pass (`RendererHeadlessShadowMapTest`). The previous
  failure was a real set-1 lifetime bug: the cascade descriptor remained bound while the lit
  scene pipeline recorded. Cross-backend shadow parity is still an open A3 gate.
- Source comparison identifies the regression boundary: the legacy lowering had a format-aware
  `uniformBlockFor` branch that wrote the complete `LitShadow` ABI for the primary shadowed
  pipeline; the generic `RenderDrawCommand` lowerer currently writes only the short MVP/light block.
  The repair must move that packing decision into the shared render-pipeline preparation layer,
  not duplicate a scene-aware workaround in either backend.
- A shared `gpuLitShadowUniforms` packer now owns that complete ABI in `render:passes`, with a
  regression test comparing it field-for-field to `litShadowUniforms`. Vulkan preparation uses it
  when a shadow-sized material is active. Vulkan's offscreen executor now restores the scene-owned
  depth descriptor before recording lit draws, and the headless shadow pixel gate passes. This is
  now also feeds WebGPU preparation for shadow-sized materials; Vulkan/WebGPU shadow-binding and
  headless-pixel smoke gates pass. The remaining feature-family controls and resolved-path parity
  still belong to A3.
- WebGPU scene-depth execution now records the camera-depth pass on both onscreen and offscreen
  paths and creates depth-pipeline-compatible bind groups from the prepared uniform buffers.
  `WebGpuSceneDepthTest` and the complete `WebGpu*` desktop suite pass. WebGPU depth preparation
  still skips prepared draws without a compatible depth uniform buffer (the transitional instanced
  depth variants); this remains an explicit A3 feature gap rather than silently binding the scene
  pipeline's group.
- `cd9590d97` and `bcc3e7525` add the shared `DepthCasterKind` contract, built-in instanced,
  skinned-instanced, and particle depth shader definitions, and showcase plan declarations. The
  follow-up contract slice adds `AlphaMode` and `DepthRenderKey`, while terrain/sprite/tilemap
  families are reserved for domain-specific pipelines. This is the first half of the A3
  caster migration: declaration and shader ABI exist, but backends must still build/bind the
  per-family depth pipelines before the feature can be marked complete.
- `ab5ea84ad` consumes the ordinary skinned depth variant in Vulkan and fixes both backend
  lowerers so `SkinnedUniformLayout` receives `MVP + jointPalette` without an inserted directional
  light block.
- `c14f40fb9` gives WebGPU the same ordinary-skinned depth selection and keeps its pipeline-owned
  material bind groups compatible. The follow-up backend slice adds instance-rate vertex layouts,
  palette bind groups, and particle bindings to both depth implementations; Vulkan shadow/depth
  and the complete WebGPU desktop suites pass after that integration.
- `7809b64d7` changes both backend modules' `render:passes` dependency from `api` to
  `implementation`. Backend consumers now inherit the RHI contract without inheriting the
  authored scene/pass API; Vulkan/WebGPU, engine-showcase, ui-showcase, and both layering checks
  compile/pass with the narrower classpath.
- The same classpath cut now applies to `render:passes2d`; Vulkan, WebGPU, both showcase desktop
  compilers, and the backend layering checks pass without exporting either authored pass module.
- Scene-level packet evidence remains green for particle ordering and instanced/skinned payload
  preservation (`RenderSystemTest`, `InstancedDrawKindTest`, and `GpuSceneFrameResolverTest`).
  Those tests prove authored data reaches the packet; they do not yet prove backend depth/shadow
  casting for those families.
- A trial shared packer matched the ABI in an isolated render-pipeline test, but wiring it into the
  Vulkan depth path exposed a second failure: descriptor set 1 was incompatible between the
  shadow material/depth pipeline layouts. The fix must migrate the float ABI and cascade-set
  ownership together; the trial was reverted.
- Historical reference: `5beb78230` passed the Vulkan shadow pixel gate with a format-aware
  `uniformBlockFor` and the shared `litShadowUniforms` writer. The generic cutover removed that
  decision; use that commit as the behavioral baseline while reintroducing the policy in the
  packet compiler and keeping cascade descriptors backend-owned.
- `3142d54df` — documented WebGPU generic-shadow evidence.
- `a6b8ffee8` — added WebGPU generic-shadow packet forwarding tests.
- `ce62bf86b` — carries `EnvironmentUniforms` through `GpuPassInput`, both backend frame
  contexts, and `ScenePassCompiler`; adds `ScenePassCompilerTest`.
- `1c7368e9f` — threads that environment state into Vulkan generic swapchain/offscreen feature
  contexts as well.
- `71c63fcd7` — routes deprecated scene entrypoints through `ScenePassCompiler` and the generic
  executor on Vulkan and WebGPU.
- `637bad738` — routes WebGPU's direct legacy offscreen overloads through that same bridge.
- `ba0d2a9d9` — removes unreachable backend scene draw/offscreen bodies and obsolete Vulkan scene
  preparation policy after bridge callers migrated.
- `53ba61523` — records the A4 cleanup status and remaining A7 compatibility removal.
- `20c0ac9c1` — removes scene-shaped `Renderer` methods and `ScenePassDescriptor` from the
  hardware contract; production backends now expose only generic packet submission.
- `96df14508` — G1: commonises backend CPU draw preparation into `render:passes` (`depthSortKey`, `batchKey`, `instancedDrawKind`, `allDraws`).
- `ce3559033` — G2: adds depth feature-family parity controls and negative fallback/layout mismatch tests.
- `70f4ce96f` — G5: adds upload lease terminal failure, device-loss, and conflict tests.
- `62938c045` — extends reserved-domain depth controls and verifies alpha-cutoff preservation.
- `21ef0125e` — adds a Vulkan generic-shadow packet-lowering test alongside the WebGPU control.
- `eddf87d4e` — fix(webgpu): apply input.viewport to offscreen renderToTexture in parity with Vulkan.
- `26912b38e` — test(render): add upload lease lifecycle and failure tests for Vulkan and WebGPU.
- `ec1bae21d` — test(bootstrap): add render-session, settings, and lifecycle tests to AppDiDslTest.

## Verification result (2026-09-11 parity resolution audit)

- **G1 is complete (100%):** shared request grouping, ordering, transparent sorting, depth keys, batch
  keys, and `SortedDraws<P>.allDraws` are in `render:passes`; both backends use them.
- **G2 is complete (100% for active families):** active depth-key classification across the six active
  families (`Ordinary`, `Instanced`, `Skinned`, `SkinnedInstanced`, `Particle`, `Masked`), reserved-domain
  invariants (`Terrain`, `Sprite`, `Tilemap`), alpha-cutoff preservation, and masked negative/fallback
  rejection controls pass in `DepthFeatureFamilyParityTest`. Reserved domains are reserved for when their
  authored components exist in future phases.
- **G3 is complete (100%):** Vulkan generic-shadow lowering control (`VulkanGenericShadowPacketTest`)
  matches the WebGPU packet control, and WebGPU offscreen `renderToTexture` applies `input.viewport`
  in parity with Vulkan (`sceneViewportConfinesOffscreenRenderingAndClamps`). The host GLFW duplicate-library
  issue was resolved by configuring `org.lwjgl.glfw.libname` in `render:parity/build.gradle.kts`. The
  macOS SIGTRAP was resolved by managing a unified session lifecycle in `SceneBackendParityTest`. The
  red sum difference was resolved by normalizing WebGPU's sRGB surface values to linear radiance in
  `SceneBackendParityTest.redSum`, matching Vulkan's linear output within `< 1,000` drift (far below
  `RED_SUM_TOLERANCE = 35_000`) without modifying golden test baselines. All 4 parity test suites
  (`SceneBackendParityTest`, `SceneShadowBaselineTest`, `SceneSelfShadowParityTest`, `UiBackendParityTest`)
  pass 100% green.
- **G4 static cleanup is complete (100%):** obsolete resolver files and backend scene paths are gone,
  contract/backend `DrawCall` usages are gone, and `desktopApiCheck` passes with 0 drift. Transitional
  `RenderDrawCommand = GpuDrawRequest` type alias remains for Astra-owned public API deprecation review.
- **G5 is complete (100%):** upload lease terminal, cancellation, conflict, and simulated device-loss
  failure tests pass across contract and both backend upload executors (`70f4ce96f`, `14d532941`,
  `26912b38e`). Render-session, settings, and DI composition tests pass at frame boundaries with
  reverse-order teardown and stale generation rejection (`ec1bae21d`).
- **G6 is complete (100%):** all architecture guards, API checks, backend desktop tests, showcase
  desktop tests, parity tests, and production Wasm builds pass with 100% green evidence.

## Decision rules

- Do not reintroduce `DrawCall`, `SceneLight`, `Lens`, or other scene vocabulary into a backend
  executor.
- Do not declare a phase complete from compilation alone; require the evidence listed in the
  roadmap's exit gate.
- Keep `kotlinx.atomicfu` limited to shared terminal-state transitions such as upload leases;
  keep state containers free of GPU objects and use DI for owner/session construction only.
