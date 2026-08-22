# 3D render path: bug fixes, then shader module split

Two correctness bugs in the WebGPU 3D path, then the authored-shader module separation.
Bugs first — one of them makes the Studio orientation gizmo render wrong today.

Audit source: cross-backend 3D audit, 2026-08-21. Prior related work: per-mesh `CullMode`
(commit `2c58bfc2f`), `ShaderSet2` → `ShaderStages` (`6ce046586`).

---

## Phase 1 — Rounded-quad stride drift

`webgpu/ui/DynamicMesh.kt:86` hardcodes `ROUNDED_QUAD_FLOATS_PER_VERTEX = 15`. The shared
truth is `16` (`render/passes/ui/UiVertexLayout.kt:62`), which Vulkan aliases
(`vulkan/ui/DynamicMesh.kt:191`). The constant only sizes the buffer, so WebGPU's is 1/16th
short: `DynamicMesh.kt:52`'s `require` throws above ~240 quads with a misleading
"exceeds capacity (256 quads)" message.

**Fix:** delete the literal, alias the shared constant as Vulkan does. Drift becomes
structurally impossible rather than something to re-verify. Fix the doc comment too — it
omits `smoothing` and wrongly claims it mirrors Vulkan.

**Test:** assertion mirroring `RendererVertexWritersTest.kt:86`.

**Risk:** none — only grows a buffer.

---

## Phase 2 — WebGPU uniform-buffer clobber

`webgpu/renderer/Renderer.kt:77-79` documents the defect in its own class KDoc:

> "This only actually works correctly for a single draw call per frame -- multiple draw
> calls sharing one uniform buffer within one render pass clobber each other's MVP matrix"

Vulkan solved this with per-material, per-frame, per-draw-slot buffers
(`Material.uniformSlot(frameIndex, drawSlotIndex)`, `nextSlot` at `RendererDraw3D.kt:580`).
WebGPU has no equivalent — its `Material.updateUniformBuffer` takes no slot index at all.

One root cause, three call sites:

| Site | Status |
|---|---|
| `RendererOpaqueDraws.kt:157` `primaryDraw` | Masked today — `rotating-cube.scene.json:31` gives ground `cullMode: Back` and the cube `None`, routing them to different buffers |
| `RendererOffscreen.kt:122` `performRenderToTexture` | **Reproduces now** — see below |
| `RendererDraw3D.kt:248` `textureQuadMesh` | Vulkan already fixed via `textureMeshForPrimitive(index)`; WebGPU's pool manager omits that function |

**Repro:** `StudioOrientationGizmo.kt:94` draws 3 axis boxes, all `PositionNormalColor`, all
default `CullMode.None`, one shared material, through `performRenderToTexture`. All three
render with the Z axis's MVP. Vulkan renders it correctly.

### Approach

| | A — per-draw slots | B — dynamic offsets |
|---|---|---|
| Shape | Pool of (buffer, bindGroup) indexed by draw slot | One buffer, `hasDynamicOffset: true`, offset per draw |
| Precedent | Vulkan already does exactly this | None — zero `dynamicOffset` usage in repo |
| Blast radius | WebGPU-local | Changes the shared `CommandRecorder` port → touches both backends |
| Cost | N buffers + N bind groups | 1 buffer, needs 256-byte alignment |

**Take A.** Proven in-repo, stays inside one backend, no port change. Leave a `ponytail:`
comment naming B as the upgrade path if draw counts ever make N buffers a real cost.

Apply at all three sites — one decision, not three patches. Delete the now-false class KDoc
at `Renderer.kt:77-79`.

**Verification:**
- Unit-test the slot allocator in isolation (distinct draws → distinct slots; reset per
  frame). The only genuinely automatable part.
- Visual: orientation gizmo in-browser — 3 axes must show 3 different orientations.
- Un-mask check: temporarily set the ground back to `cullMode: None` in
  `rotating-cube.scene.json`, confirm cube + ground both render correctly, revert.

---

## Phase 3 — Particle per-frame allocations

`RenderSystem.kt:233` runs `Mat4().translate(...).scale(...)` per particle per frame. Each op
returns `new * this` (`Matrix.kt:153-167`), so that's ~5 `Mat4` allocations per particle per
frame — the exact anti-pattern `skills/awake-core-math/SKILL.md:93` names.

Root cause is in `Matrix.kt`: no in-place `translate`/`scale`, only `multiplyInPlace`
(`:313`). Add in-place variants, then use a reusable per-particle matrix buffer in
`RenderSystem` — the same pattern the particle path already uses for colors/frames
(`RenderSystem.kt:207-213`), currently inconsistent with the matrix path beside it.

Also `RenderSystem.kt:225`: `sortByDescending { (it.position - eye).length3() }` allocates a
`Vec3` and boxes a `Float` per comparison. Use squared distance and a primitive-keyed sort.

**Benchmark this one** (unlike shader loading, which is startup-only): genuine per-frame hot
path with a documented rule. Copy the `awake:ecs:benchmark` kotlinx-benchmark setup.

---

## Phase 4 — Shader module split

Only authored content still in a library module: 7 `.wgsl` in
`awake/asset/shaders/src/commonMain/resources/shaders/` (instanced, particle, shadow_depth,
skinned, skinned_instanced, skybox, textured) plus `TexturedUniformLayout` and
`LitShadowUniformLayout`. `LitShadowUniformLayout` describes a shader that isn't even in that
module — the only `lit_shadow.wgsl` copies live in `samples/studio`.

Verified NOT leaks: `triangle.*.spv` under `backend/vulkan/src/desktopTest/resources` is a
test fixture; `triangle.wgsl` lives in samples; the UI/debug-line shaders bundled per backend
are genuine engine internals.

```
awake:asset:shaders        → contract only: ShaderSet/ShaderStages/ShaderSource/shaderSet()
awake:render:shader-pack   → the 7 .wgsl + the 2 uniform layouts
```

Backends depend on the contract only — they already inject content via nullable `ShaderSet?`
params, so this compiles unchanged. A game opts into the pack or ships its own shaders.

Cheap because the `.wgsl` files are consumed as a static Gradle file path (`syncAwakeShaders`,
`samples/studio/build.gradle.kts:13-23`), not a code dependency — moving them is a
build-script path update.

Frame it as awaken's **shader stdlib**: optional, shipped, opt-in — not junk in the library.

**Guard:** add `verifyRenderExtensibility`, mirroring `verifyUiOwnership`
(`build-logic/src/main/kotlin/awake.ui-ownership-convention.gradle.kts:128`).
`docs/reference/render-extensibility.md` already documents the policy and names this exact
missing check.

---

## Phase 5 — Plugin seam (done, 2026-08-21)

`RenderFeature` is already a plugin interface — `SkyboxRenderFeature`, `OpaqueRenderFeature`,
`UiRenderFeature`, `ShadowFeature` implement it, registered as a `List<RenderFeature>`. But
it's `internal` and Vulkan-only (`vulkan/pipeline/RenderFrameContext.kt:85`).

Promoting it is **not** a visibility change: `RenderFrameContext` exposes Vulkan types
(`commandBuffer: Long`, `LineMesh`, `DynamicMesh`, `RenderPipeline`, `UiRenderPipeline`,
`:26-59`). Abstracting those behind the existing `CommandRecorder` port is the real work.

Precedent points that way already — `SharedOpaqueRenderFeature`/`SharedSkyboxRenderFeature`
live in `render:passes` and are shared verbatim, so the *bodies* are backend-neutral; only
the *interface* is still Vulkan-local.

Once hoisted, a "pipeline plugin" is just `ShaderSet` + `RenderFeature` + `UniformLayout`
bundled — which is what skybox/particle/shadow already are, unpackaged.

**Landed.** `RenderFeature`/`RenderPassSlot`/`RenderFrameContext` are in `render:passes`, and
both backends dispatch through one `recordPassFeatures` loop. The features themselves moved
too, behind three ports (`SkyboxPass`/`LinePass`/`UiPass`) — a first attempt that mirrored
Vulkan's feature classes into WebGPU was scrapped, since it only relocated the duplication.
Vulkan's skybox also joined `SharedSkyboxRenderFeature`; it had kept its own inline draw.
`RenderFeature` is not yet *public*, which is now a visibility change rather than a redesign.

---

## Also found, not scheduled

Re-verified 2026-08-21 after Phases 1-5 landed. Every entry is now struck through — three
were already stale when re-checked, three were fixed in the passes that followed. Kept rather
than deleted so a reader who remembers one knows it was checked and what came of it.

- ~~**WebGPU shadows are dead GPU work.**~~ — premise was wrong, now **resolved**. The pass
  never ran: `shadowFeature` is built only from `shadowShaderSet`, and `StudioVulkanBootstrap.kt:30`
  is the sole site in the repo that passes it — Vulkan. So zero GPU cost, not "dead work".
  The real defect was a silent trap plus three comments describing a state that did not exist:
  `AwakeApplication`'s doc claimed the wasmJs `actual` *ignores* `additionalPipelines`/
  `shadowShaderSet` (it forwards both), and `webgpu/Renderer.kt` called `shadowsEnabled` an
  unused compile-only stub (`RendererDraw3D:101` reads it). Fixed by rejecting a non-null
  `shadowShaderSet` on WebGPU rather than silently costing a depth pass for no pixels, and by
  correcting all three comments. `ShadowFeature`/`ShadowRenderPipeline`/`ShadowMap` are kept —
  they are the working depth half. Finishing needs a WGSL variant carrying the
  `shadowMap`/`shadowMapSampler` bindings plus `LitShadowUniformLayout`'s 68 floats through
  `primaryDraw`; `lit_shadow.wgsl` declares itself Vulkan-only in its own header.
- ~~**`webgpu/RendererDraw3D.performDraw` is 220 lines**~~ — largely **resolved**. The inlined
  UI loop moved to `SharedUiRenderFeature` and the skybox/opaque orchestration to the shared
  feature list, so it is ~137 lines and now mirrors Vulkan's `recordCommandBuffer`. Still over
  detekt's 60-line threshold; what remains is genuine per-frame setup (surface sync, pipeline
  resolution, pass descriptors), not duplicated logic.
- ~~**`lightFloats` packed 3×**~~ — **done**. `sceneLightFloats` now lives in
  `passes/uniforms/MaterialUniforms.kt` alongside the pbr/fog packers, with the Vulkan/WebGPU
  difference (`shadowTexelDepthScale()` vs `0f` in `direction.w`) as a defaulted parameter.
- ~~**5 near-copy `ensure*UniformResources`**~~ — stale. Phase 2 deleted three of them; two
  remain (`webgpu/RendererUiPipelines.kt:87,112`), and their doc comment now explains why they
  can't share a bind group (WebGPU's `auto` layout derives one per pipeline).
- ~~**`ShaderSource.InlineText`/`PrecompiledBinary` are dead** — both backends `error()` on
  them.~~ — stale. Neither backend references `ShaderSource` variants at all now;
  `asset/shaders/ShaderProgramResources.kt` handles every case generically and
  `ShaderProgramResourcesTest.kt` covers both. Still no production caller, but no error path.
- ~~**Leftover duplication:** `WebGpuEngine.kt` private `resourcePath`/`withPipelineLoadContext`~~
  — stale, fixed. Both backends use the shared `asset/shaders/ShaderSetLoading.kt`.

## Explicitly not doing

- **Splitting `Matrix.kt`** despite the HIGH god-class audit finding — it's a math type where
  the methods are the type. The real issue is the missing in-place variants (Phase 3).
- **Dynamic offsets** — deferred, noted as Phase 2's upgrade path.
- **A render/shader benchmark module** — shader and pipeline construction is startup-only.
  Phase 3's particle path is the one place with a real per-frame hot path.
- **Adding Koin** — constructor injection with nullable optionals plus function-type factories
  is the existing, documented, working pattern. No DI container needed.
