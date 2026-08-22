# `GpuDevice`: finish the render hardware interface (option B)

Status: draft, not started. Written 2026-08-23.

Concept and naming rationale: [render-hardware-interface.md](../reference/render-hardware-interface.md).
Measured duplication survey: [backend-commonisation.md](../reference/backend-commonisation.md).

## Goal

Name the rendering boundary and finish it, so engine code above the line is authored once and
each backend implements only translation. Target **~44%** of the render stack commonised, from
22.0% today — see the per-phase table in the reference doc. (An earlier draft said 55–65%; phase
modelling corrected it, since ~7,400 lines remain per-backend after every planned phase.)

Explicitly **not** a rewrite. Every phase below is independently shippable and independently
provable, and the interface is discovered by migrating real subsystems rather than designed up
front in one go. What changes versus the incremental status quo is that the boundary now has a
name, a capability-tiered shape rule, and an acceptance test.

## Why option B and not A, C, or D

- **A (ad-hoc ports, status quo).** Same mechanics, no named boundary. Works, but each pass
  re-decides where the line is, so the line drifts. B is A plus a fixed shape.
- **C (delete the Vulkan backend, run wgpu4k everywhere).** The only route past ~44%, because you
  stop having two backends. **Evaluated and rejected in Phase 0 — on strategy, not on a technical
  wall.** wgpu4k does cover every Awake target. Rejected because it discards the hand-written
  Vulkan/JNI layer and the headless pixel harness built on it, and stakes the engine's floor on a
  `0.2.0-SNAPSHOT`'s ceiling and cadence. See decision-log D26.
- **D (codegen/IR).** `PipelineSpec` is already a miniature IR. Generalising it makes the
  generator the thing you maintain. No.

## Phase 0 — DONE (2026-08-23)

Settled "abstract two backends" versus "delete one". Full reasoning in
[decision-log.md](../reference/decision-log.md) D26.

**Target coverage checked, and it went the opposite way to expectation:** wgpu4k publishes a
variant for every Awake target. `wgpu4k-native`/`kffi` publish `androidJvm` (the main `wgpu4k`
artifact shows only Kotlin/Native `android_arm64`/`android_x64`, which makes a first look
misleading), plus `jvm`, `ios_arm64`/`ios_simulator_arm64` and `wasm`. **Option C was feasible.**

**Rejected on strategy anyway.** Keeping the hand-written Vulkan backend keeps the JNI layer and
the headless pixel harness built on it — the only pixel-level render coverage the engine has —
and avoids staking the engine's floor on a `0.2.0-SNAPSHOT`'s performance ceiling and cadence.
Vulkan is primary; WebGPU exists because browsers cannot run Vulkan, and is expected to lag.

The benchmark step was not run: it existed to break a tie, and the decision was made on grounds
the benchmark would not have changed. If C is ever revisited, run it first.

**Consequence for this plan:** the RHI is capability-tiered. The shared core is the intersection
(WebGPU-shaped by arithmetic, since WebGPU is a capability subset of Vulkan); Vulkan-only
capability goes behind optional interfaces engine code feature-detects. The core may never
*require* what WebGPU cannot do, but Vulkan is never held back for parity.

## Phase 1 — DONE (2026-08-23)

`GpuDevice` declared in `render:contract`, carrying the six hardware-facing members `Renderer`
already had: `clipSpace`, `createMesh`, `createMaterial`, `createRenderTarget`, `waitIdle`,
`destroy`. `Renderer` now extends it, so every backend satisfied it without a line of backend
change. Zero behavioural change; 35/35 Vulkan tests green, both samples compile on desktop and
wasmJs, detekt clean.

The split it makes visible is the point. `Renderer`'s remaining surface is now purely render
*runtime* — `draw(camera, drawCalls, light)`, `renderToTexture`, `drawUi`, `drawDebugLines`, and
frame state (`clearColor`, fog, `wireframe`, `sceneViewport`). None of that is hardware
vocabulary, and none of it belongs in a backend's view of the world.

Deliberately absent, arriving with Phase 2: command recording reachable from the device, and the
capability accessor for Vulkan-only features (D26).

## Phase 1b — DONE (2026-08-23): WebGPU runs on the desktop JVM

WebGPU code was compile-checked and never executed outside a browser. It now runs on the desktop
JVM: `WebGpuDesktopDeviceSmokeTest` creates a real GPU device through wgpu4k, the first test in
the repo to execute WebGPU code at all. Production still ships Vulkan on desktop and Android, so
this costs nothing at runtime.

**Far cheaper than estimated.** 37 of the module's 38 files were already backend-neutral wgpu4k
code and moved straight to `commonMain`; only `WebGpuCanvasHost.kt` touches JS APIs. And the
planned "GLFW bootstrap to replace the wasm-only toolkit" was unnecessary — `wgpu4k-toolkit`
publishes a **jvm** variant carrying the same `WGPUContext` type wasmJs uses, plus
`glfwContextRenderer`, which already hints `GLFW_VISIBLE = FALSE`. No abstraction over the
context was needed, so the 79-usage sweep that looked unavoidable never happened.

Four environmental snags, each recorded at the site that fixes it:
- `webgpu-ktypes-descriptors` reaches wasm via the wasm-only toolkit; desktop declares it directly.
- `wgpu4k-jvm` ships JVM-target-25 bytecode, so this module needs `jvmToolchain(25)` (contained:
  its desktop target is test-only and nothing consumes its desktop output).
- GLFW refuses to initialise off thread 0 on macOS -> `-XstartOnFirstThread`, applied per-host.
- Rococoa's cglib cannot `defineClass` under JPMS -> `--add-opens java.base/java.lang=ALL-UNNAMED`.

**Remaining, not done here:** the pixel harness itself. `WGPUContext` requires a non-null
`Surface`, so a truly surface-less device needs `GraphicsDevice`/`SwapchainManager` to grow an
offscreen mode. The hidden 1x1 GLFW window is sufficient for device-level tests today; render
tests either use it or that offscreen mode gets built.

**What it does not cover.** Desktop wgpu4k runs wgpu-native over Vulkan/Metal. It exercises
Awake's WebGPU code path, not a browser's WebGPU implementation — canvas sizing, JS interop,
wasm memory and browser driver quirks stay uncovered. It replaces most web testing, not all; keep
a thin browser smoke test.

Unblocks cross-backend pixel-parity tests: the same scene rendered through both backends and
diffed, which is the strongest possible guard against the drift this whole plan exists to
prevent.

## Phase 2 — `renderer/` draw preparation (the large one, ~3,494 lines)

The biggest duplicated subsystem and the reason this plan exists. Vulkan's `RendererDraw3D` and
WebGPU's `RendererOpaqueDraws` are parallel implementations of one algorithm: walk draw calls,
resolve a pipeline, pack uniforms, sort, record.

Migrate one stage at a time, each with its own commit and its own headless gate:

1. **Pipeline resolution.** Both sides now have identical precedence (wireframe, transparent,
   back-culled, fill) written twice. One shared `resolvePipeline(drawCall, table, flags)`.
   Gated by `RendererHeadlessTransparencyTest`, which already pins that precedence.
2. **Uniform assembly.** `SceneUniforms`/`UniformWriter` are shared; the per-draw assembly around
   them is not. Move the assembly, leave the buffer write per-backend.
3. **Sorting and grouping.** `groupBy { pipeline }`, transparent back-to-front depth sort. Pure
   arithmetic over already-shared types — move wholesale.
4. **Instance packing.** `InstancePacker` exists in `render:passes`; both backends still do
   packing around it.
5. **Frame orchestration.** Feature dispatch order. Vulkan has a `RenderFeature` list; WebGPU's
   is partial. Converge on the shared one.

Each stage: move logic, leave the recording call behind `CommandRecorder`, re-run Vulkan's
headless suite, and record the LOC delta in the survey doc.

**Risk.** This is the pass most likely to expose a genuine asymmetry — WebGPU has no shadow
pass at all, and its non-textured path writes fewer uniforms than Vulkan's. Do not paper over
those with a shared branch that one backend never takes. Where the two genuinely differ,
document the divergence and leave it per-backend; the goal is removing *duplication*, not
manufacturing false symmetry.

## Phase 3 — resource creation behind the facade (medium)

`createMesh`, `createMaterial`, `createTexture`, `createRenderTarget` are already backend-neutral
in *signature* (they return `render:contract` interfaces) but are declared per-`Renderer`. Move
the declarations onto `GpuDevice`. Mostly mechanical; the real work is auditing which creation
parameters are Vulkan-shaped and re-spelling them WebGPU-shaped per the shape rule.

## Phase 4 — `debug/` pipelines (small, ~1,029 lines)

Line, skybox and particle pipelines are the same shape `PipelineSpec`/`PipelineFactory` already
handles. They were left out of the pipeline pass deliberately. Cheap once Phase 1 exists.

## Phase 5 — `mesh/` packing (~1,211 lines)

Vertex and instance buffer packing is arithmetic; only the allocation call differs. Move the
arithmetic, leave allocation below the line.

## Verification

Each phase must hold this bar before the next starts:

- `:awake:backend:vulkan:desktopTest` green (35 tests today, including the transparency gate).
- `:awake:backend:webgpu:compileKotlinWasmJs` and `:samples:studio:compileKotlinWasmJs` green.
- `detekt` clean on every changed file.
- The LOC survey in `backend-commonisation.md` re-measured and updated, including when a phase
  moves the number *less* than projected — the pipeline pass grew WebGPU by 46 lines against a
  projected shrink, and that is worth recording rather than quietly restating the estimate.

**WebGPU's gap is the standing risk.** It has no headless render harness, so every WebGPU change
in this plan is compile-checked but not pixel-checked. Two options, neither free: build a
headless wgpu4k harness (real work, unlocks permanent WebGPU coverage), or drive the browser
pane and diff screenshots (cheaper, flakier). Until one exists, WebGPU correctness in this plan
rests on symmetry-with-Vulkan plus manual inspection — say so in each phase's commit rather than
implying parity of proof.

## Non-goals

- No `expect`/`actual` for the backend boundary — see the reference doc for why.
- **No module split *up front*** — but the runtime/RHI split IS the end state, contrary to an
  earlier draft of this plan which ruled out a new module entirely. That was wrong: `DrawCall`,
  `SceneLight` and `Lens` currently sit in the same module the backends depend on, which is why
  both backends import them (6, 4 and 3 files each). A backend should never see a scene light.
  The split is sequenced as the *outcome* of Phase 2 rather than its precondition, because until
  draw preparation moves up, a carved-out `render:rhi` would still need `DrawCall` to compile.
  Phase 2's completion test is now machine-checked: `verifyBackendLayering` (build-logic,
  wired into `check`) bans `DrawCall`/`SceneLight`/`Lens` imports in both backends outside a
  9-file exemption list, and the phase is finished exactly when that list is empty.
- No performance work. This is a structural change and should be performance-neutral; if a phase
  regresses a benchmark, that is a defect in the phase, not an accepted cost.
- No shadow-pass parity for WebGPU. Pre-existing gap, tracked separately, deliberately untouched.
