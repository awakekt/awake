# `GpuDevice`: finish the render hardware interface (option B)

Status: **in progress.** Written 2026-08-23. Phases 0, 1, 1b, 2, 3 and 4 are DONE; phase 4b step 1
shipped in `134cdeee5` and its steps 2-3 are superseded by
[backend-content-split](2026-08-23-backend-content-split-plan.md). Remaining: phase 5 (`mesh/`
packing) and the `verifyBackendLayering` import ledger, which phase 2 established needs its own
phase rather than riding along with stage 5.

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

1. **Pipeline resolution -- DONE.** `PipelineTable.resolve`. It was duplicated three times, not
   two: Vulkan's `pipelineFor` plus two open-coded sites in WebGPU. Also collapsed WebGPU's
   two-step pre-resolve-then-select into one per-draw call.
2. **Uniform assembly -- DONE.** `texturedUniforms`. Also removed a per-textured-draw
   `FloatArray` allocation on Vulkan, which built the block without mvp and concatenated. Nothing
   covered this path (no renderer test uses a `PositionNormalColorUv` mesh), so
   `TexturedUniformsTest` now asserts field offsets directly.
3. **Sorting and grouping.** `groupBy { pipeline }`, transparent back-to-front depth sort. Pure
   arithmetic over already-shared types — move wholesale.
4. **Instance packing -- DONE.** `instancedDrawKind` + `resolveInstanced`. The arithmetic was
   already shared via `InstancePacker`; the classification was not. Fixed a divergence where
   `PipelineTable.particlePipelines` was populated on WebGPU and empty on Vulkan.
5. **Frame orchestration -- ALREADY DONE, before this plan.** The stage was written on the
   belief that "Vulkan has a `RenderFeature` list; WebGPU's is partial". That is no longer true:
   both dispatch through the shared `recordPassFeatures`, and both register the identical list
   (skybox when supplied, then opaque + debug lines, then UI). What remains in each frame body
   is genuinely per-backend -- `vkBeginCommandBuffer`/`vkCmdBeginRenderPass` against an encoder
   and `beginRenderPass` -- and belongs below the line.

   Checking it was still worth doing: it turned up a duplicated `buildList` in
   `VulkanEngine.buildRenderFeatures`, left behind by this session's own detekt decomposition,
   which built the whole feature list twice and discarded the first (whose features were never
   destroyed).

Each stage: move logic, leave the recording call behind `CommandRecorder`, re-run Vulkan's
headless suite, and record the LOC delta in the survey doc.

**Ledger status after stages 1-5: still 9 files, unchanged.** Worth stating plainly, because an
earlier note here predicted stage 5 would empty it. It does not, and no stage in this phase
could: these stages moved the *decisions* (pipeline choice, uniform layout, ordering,
instanced classification) into shared code, but each backend still owns the *loop* that walks
`List<DrawCall>`. Emptying the ledger needs shared code to hand the backend something
backend-neutral per draw, so `DrawCall` never crosses the boundary at all -- a further step,
not a leftover of this one. Scope it as its own phase rather than pretending stage 5 covers it.

**Risk.** This is the pass most likely to expose a genuine asymmetry — WebGPU has no shadow
pass at all, and its non-textured path writes fewer uniforms than Vulkan's. Do not paper over
those with a shared branch that one backend never takes. Where the two genuinely differ,
document the divergence and leave it per-backend; the goal is removing *duplication*, not
manufacturing false symmetry.

## Phase 3 — pipeline registry: collapse the engine bootstrap

### The problem

`VulkanEngine`'s constructor predicts every pipeline the app will ever need, at launch:

```kotlin
VulkanEngine(
    vertexShaderResourcePath, fragmentShaderResourcePath, vertexFormat, appLifecycle,
    vertexShaderEntryPoint, fragmentShaderEntryPoint,
    additionalPipelines: Map<VertexFormat, ShaderSet> = emptyMap(),
    wireframeSupport: Boolean = false,
    shadowShaderSet: ShaderSet? = null,
    instancedShaderSet: ShaderSet? = null,
    skinnedInstancedShaderSet: ShaderSet? = null,
    skyboxShaderSet: ShaderSet? = null,
    particleShaderSet: ShaderSet? = null,
)
```

Seven optional feature parameters, mirrored on `WebGpuEngine`. Every one is a branch
`createBackendResources` has to take, which is most of why that function reached 222 lines at
cyclomatic complexity 27 before it was decomposed. Adding a render feature means editing both
engines' constructors, both bootstraps, and both `createBackendResources` bodies -- the engine
has to know about a capability it does not use.

The registry inverts that: a feature declares the pipelines it needs, and the bootstrap stops
enumerating.

### Design

**The cache key already exists.** `PipelineSpec` is a `data class` over `vertexFormat`, shader
resource **paths**, entry points, `variant`, `cullMode` and `wireframe` -- value-equal, and
already the input to `PipelineFactory`. No new key type.

**Do not key on `ShaderSet`.** `ShaderStages` is a plain `class`, not a `data class`, so it has
identity equality. A key holding one compares shader sets by reference, and two structurally
identical sets become two entries that compile the same pipeline twice, silently.
`PipelineSpec` holding `String` paths is what avoids this, and is the reason it is shaped that
way.

**Do not key on `variant` alone.** `InstancedMeshRenderer` and `InstancedSkinnedMeshRenderer`
both use `PipelineVariant.Instanced` with different vertex formats and shaders; a
`getOrPut(variant)` hands the second caller the first's pipeline. Wrong output, not a compile
error.

```kotlin
class PipelineRegistry(private val factory: PipelineFactory<P>) {
    private val compiled = mutableMapOf<PipelineSpec, P>()

    /** Declares pipelines a feature needs. Compiles them now, off the frame path. */
    suspend fun register(requests: List<PipelineRequest>)

    /** Frame-path lookup. Never compiles -- see "Why not lazy" below. */
    operator fun get(spec: PipelineSpec): P?

    fun destroyAll()
}
```

`buildPipelineTable`'s existing fan-out (fill + wireframe + back-culled + transparent per
request) becomes how `register` expands a request into specs.

### Why not lazy compilation at draw time

The obvious shape -- `getOrCreate(spec)` called from the draw loop -- does not work here, for two
independent reasons:

1. **`PipelineFactory.create` is `suspend`**, because shader loading is IO. A draw loop cannot
   suspend.
2. **Pipeline compilation costs roughly 1-50ms.** The frame budget at 60Hz is 16ms, so a single
   cache miss mid-frame is a dropped frame. This is why Unreal and Unity ship PSO precaching
   rather than compiling on demand.

So compilation happens at `register` time -- scene load, feature activation, level transition --
and the frame path only ever looks up. A miss is a defect: log it and skip the draw, exactly as
both backends already do for a mesh whose vertex format has no pipeline (see
`Renderer.pipelinesByFormat`'s skip-on-mismatch guard). Rendering wrong-format vertex data
through the wrong pipeline is worse than rendering nothing.

### What collapses

Both engines lose all seven optional shader parameters. `samples/studio`'s two bootstraps
(`StudioVulkanBootstrap`, `StudioWebGpuBootstrap`) stop passing them and instead register the
requests their features need. `createBackendResources` loses one branch per optional feature.

`wireframeSupport: Boolean` disappears entirely rather than moving -- it is already
`PipelineRequest.buildWireframe`, and a request is only registered by a feature that wants it.

### Resource creation -- DONE (2026-08-23)

The creation *parameters* audited clean: `MeshGeometry`, `TextureAsset`, `PbrTextureSet`,
`RenderTarget` and a float count. No `Vk*` or `GPU*` type is reachable from them.

The audit found the violation one level down instead, on the interfaces those factories
**return**:

```kotlin
interface Mesh     { fun bind(commandBuffer: Long); fun draw(commandBuffer: Long); fun drawInstanced(commandBuffer: Long, instanceCount: Int) }
interface Material { fun bind(commandBuffer: Long, pipelineLayout: Long) }
```

Those `Long`s are a raw `VkCommandBuffer` and `VkPipelineLayout` -- a Vulkan-shaped API sitting
in the core tier, exactly what the shape rule forbids. WebGPU could not implement any of them
and answered all three with `TODO()`. `Material.bind` and `Mesh.drawInstanced` had **zero
callers anywhere**; the other two were called only by Vulkan's own `ShadowFeature`.

All four are removed from the shared interfaces. Recording is `CommandRecorder`'s job; Vulkan
keeps `bind`/`draw` on its concrete `Mesh`, where the handle type is honest, and `ShadowFeature`
casts to it -- the same already-documented cast the rest of that backend uses. Twelve test
doubles and `NoopRenderer` shed the dead overrides with it.

Worth noting how it hid: three `TODO()` bodies on a live code path are normally loud, but
nothing ever called them, so the stubs read as "not implemented yet" rather than "cannot be
implemented here".

### Engines wired to the registry -- DONE (2026-08-23)

Both engines construct a `PipelineRegistry` and call `register` instead of `buildPipelineTable`
directly, and both tear down through `destroyAll`. That last part is the real gain: the table
can name one pipeline under several keys, so destroying per-entry would double-free a pipeline
two requests happened to share. Nothing shares one today; the registry makes it safe when
something does.

### Non-goals

- **No lazy compilation**, for the reasons above. If a future need genuinely requires it (an
  editor authoring materials live), it belongs behind an explicit async warm-up with a visible
  placeholder, not silently in the draw loop.
- **No `Variant` enum.** `PipelineVariant` stays a sealed interface of structural booleans.
  Wireframe and shadow are not variants -- wireframe is a polygon-mode override that combines
  with any variant, and shadow is a different render pass -- which is why `PipelineSpec` keeps
  them as separate fields and why one request can fan out into four pipelines.
- **No registration from inside a frame.** `register` is `suspend` and compiles; calling it
  mid-frame is the hitch this design exists to prevent.

### Verification

- Both engines' constructors lose all seven optional shader parameters; both Studio bootstraps
  compile without passing them.
- A registry test: the same `PipelineSpec` registered twice compiles once (proves value
  equality, and would fail if the key ever admitted `ShaderStages`); two specs differing only in
  `vertexFormat` compile separately (proves the variant-collision case stays fixed).
- Vulkan `desktopTest` green, both samples compile on desktop and wasmJs, detekt green.

## Phase 4 — DONE (2026-08-23), and mostly already done before it started

Scoped as "line, skybox and particle pipelines are the same shape
`PipelineSpec`/`PipelineFactory` already handles -- cheap once that machinery exists". That
premise was wrong, and checking it first is what saved the work.

**They are not scene pipelines.** Skybox has no vertex input at all (a full-screen triangle from
`gl_VertexIndex`), so `PipelineSpec`'s `vertexFormat` is meaningless for it. Both own their own
uniform buffers and descriptor sets rather than taking a `Material`. And Vulkan's skybox is
per-frame-in-flight (`PerFrameUniformSlots`) while WebGPU's is a single bind group -- a real
structural difference, not an accident. Forcing them into `PipelineSpec` would have manufactured
exactly the false symmetry Phase 2's risk note warns against.

**Most of the shareable content was already shared** by earlier work: `lineSegmentVertices` and
`DebugLineLayout` (both backends' `LineMesh` already use them), `SkyboxUniforms` in the contract
with its own test, and `SharedSkyboxRenderFeature` in `render:passes`. What remains in `debug/`
is Vk-vs-GPU API surface -- 44 and 55 `Vk*` references in the two Vulkan files -- which is below
the line by the shape rule.

**What the audit did find:** both `LineRenderPipeline`s **hardcoded** their vertex attributes --
locations, formats and byte offsets -- while `DebugLineLayout` already described them. The same
layout existed in three places and agreed only by luck. That directly violates
`awake-render-pipeline`'s "never hardcode vertex attribute descriptions or byte offsets in
backend pipelines" rule, which names lines specifically. Both now derive from
`DebugLineLayout.Format.entries` through the same `toVkFormat`/`toGpuVertexFormat` helpers the
scene pipelines already use, and `DebugLineLayoutTest` pins the numbers now that one place owns
them.

**Not done, and deliberately:** `shadowShaderSet` and `skyboxShaderSet` stay as engine
constructor parameters. Three real branches each, and the two build genuinely different things
(a shadow map with its own render pass; a pipeline against the scene pass). A declaration type
for two cases with three branches is speculative generality, not a collapse -- revisit if a
third such feature appears.

## Phase 4b — the engine should not know what a skybox is

**Step 1 DONE (`134cdeee5`). Steps 2-3 SUPERSEDED by
[2026-08-23-backend-content-split-plan.md](2026-08-23-backend-content-split-plan.md)** — see the
status note at the end of this phase before working from the sequence below.

Named while reviewing Phase 3's leftovers. `VulkanEngine.createBackendResources` constructs seven
concrete feature types by name -- `ShadowMap`, `ShadowRenderPipeline`, `ShadowFeature`,
`SkyboxRenderPipeline`, `SkyboxRenderFeature`, `OpaqueRenderFeature`, `UiRenderFeature`. Adding an
eighth means editing both engines, both bootstraps and both create bodies. That is the coupling,
and it is not addressed by grouping the two shader-set parameters into a config object: an
`EnvironmentConfig(shadow, skybox)` was written and reverted for exactly that reason -- it renames
parameters and leaves all seven constructions in place.

### The three kinds, which need different treatment

**Content features -- skybox.** Builds one pipeline against the existing scene render pass and
wraps it in a `RenderFeature`. Anything holding `(device, swapchain, renderPass)` can build it.
Belongs in an app-supplied ordered list of providers; the engine should see only `RenderFeature`.

**Capability features -- opaque, UI.** Always present, and they need engine internals
(`lineRenderPipeline`, the lazily-built UI pipelines). The engine providing these is correct. The
content/capability split is already the rule in
[render-extensibility.md](../reference/render-extensibility.md), which the feature-list comment
cites.

**Shadow -- movable, but blocked by one thing.** It is NOT inherently special. The blocker is
concrete and fixable: `Material.createDescriptorSetLayout(graphicsDevice, shadowMap)` conditionally
appends bindings 3 and 4 -- the shadow texture and its sampler -- into **set 0, the per-material
descriptor set**. So the shadow map has to exist before any material layout is built, which is
before the render pass and before any pipeline (`VulkanEngine` lines 315-318, 327). A feature that
must exist before everything else cannot be an entry in a feature list.

### Why that blocker is worth removing on its own merits

The shadow map is ONE per-frame global resource, and it is currently duplicated into every
material's descriptor set. Moving it to its own group -- bound once per frame -- is the correct
scoping regardless of decoupling, and there is precedent: `skinned_instanced.wgsl` already puts a
feature-owned resource at `@binding(0) @group(1)` for joint palettes.

Once the shadow map leaves set 0, `Material.createDescriptorSetLayout` stops taking it, the
ordering dependency disappears, and shadow becomes an ordinary content feature beside skybox.

**Real cost, stated up front:** this touches shaders, not just Kotlin. `lit_shadow.wgsl` declares
`@binding(3) @group(0) shadowMap` and `@binding(4) @group(0) shadowMapSampler`; both move to a new
group, and the descriptor plumbing and bind site move with them. Vulkan-only today -- WebGPU
rejects a non-null shadow shader set outright.

### Sequence

1. Content-feature provider list; skybox moves out of both engines. Cheap, and it removes "the
   engine knows what a skybox is" for everything except shadow.
2. Re-scope the shadow map out of the per-material set into its own group. Shader change.
3. Shadow joins the provider list. `shadowShaderSet` leaves both constructors.

After 3: `VulkanEngine(appLifecycle, primary, scenePipelines, contentFeatures)`.

### Status — step 1 shipped, the rest was reframed

**Step 1 landed in `134cdeee5`.** Both engines now take
`contentFeatures: List<*ContentFeature>` and name no skybox;
`VulkanEngine(appLifecycle, primary, scenePipelines, shadowShaderSet, contentFeatures)`.

**Steps 2 and 3 are superseded, because this phase aimed at the wrong target.** Its premise was
that the *engine class* should not know what a skybox is. Reviewing step 1's result showed the
real problem is one level up: the **backend module** must not know, and moving the construction
from `VulkanEngine` into `awake:backend:vulkan/debug/SkyboxContentFeature.kt` satisfies this phase
while leaving the driver layer still declaring `SkyboxRenderPipeline`. The rule is now "a graphics
backend knows hardware only" (D27), enforced by `verifyBackendLayering`, and step 1's own output
sits in that check's tracked-debt ledger.

Step 2's analysis below is still correct and is carried forward verbatim as phase 4 of the new
plan — the shadow map's set-0 scoping really is the blocker, and it really is a shader change.

**Closed 2026-08-24.** The content-split plan finished all of it. Step 2 landed as `dd7a7d683`
(the depth target owns its own descriptor set); step 3 landed as `21e820127`, though not the way
this sequence predicted — `shadowShaderSet` did not join the provider list, it became
`depthPrePassShaderSet`, because the types it built were already capabilities and only their
names declared content (`render-extensibility.md` has the names table). So
`VulkanEngine(appLifecycle, primary, scenePipelines, depthPrePassShaderSet, contentFeatures)`,
and the content exemption list is empty.

Names below are pre-rename and left as written: `ShadowMap` is now `DepthTarget`,
`ShadowRenderPipeline` is `DepthOnlyPipeline`, `ShadowFeature` is `DepthPrePassFeature`. The
*import* ledger this plan's phase 2 owns is untouched and still the open item.
What changed is the destination: shadow does not become "an ordinary content feature beside
skybox" inside a backend, because skybox does not stay in a backend either. Both become shared
declarations built from a `PipelineSpec`, which needs two RHI primitives that do not exist yet
(vertex-less pipeline, standalone uniform block). Sequence in the new plan.

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
