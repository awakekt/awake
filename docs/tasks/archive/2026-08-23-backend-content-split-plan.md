# Split game-authored content out of the GPU backends

Status: **every phase done. `contentExemptBackendFiles` is empty, 14 files to 0.** Written
2026-08-23, status current 2026-08-24. The rule it enforces is live (D27,
`verifyBackendLayering`), and no backend file declares content vocabulary any more.

**Read the caveat before treating that as finished.** The check matches vocabulary against
*declared names* only. `RendererDraw3D.kt` mentions shadow ~20 times and declares
`lightViewProjection`, which builds a directional shadow box — real content, no forbidden word
in a declared name, never on the list. An empty list means "no content in a declared name", not
"no content in a backend". Closing that is not scoped here; widening the check would flag every
doc comment that explains what a consumer uses a capability for, which is the prose that makes a
hardware type readable. Supersedes steps 2-3 of [rhi-gpudevice](2026-08-23-rhi-gpudevice-plan.md) phase 4b.

## The rule

**A graphics backend knows hardware only.** Pipelines, buffers, textures, samplers, command
recording. It must never know what a skybox or a shadow *is*.

Stated the way it was raised: the backend modules are becoming a game instead of a GPU library.
`awake:backend:vulkan` currently declares `SkyboxRenderPipeline`, `ShadowMap` and `ShadowFeature`
— three answers to "what is being drawn", which is the app's question, not the driver's.

Enforced, not just written: `verifyBackendLayering` now rejects content vocabulary in any backend
declaration (`awake.backend-layering-convention.gradle.kts`). It runs on `check`.

## What the check found

14 files, symmetric across backends because both hand-wrote the same two features:

| Backend | Files | Declarations |
|---|---|---|
| vulkan | 8 | `SkyboxRenderPipeline`, `skyboxContentFeature`, `VulkanSkyboxPass`, `ShadowRenderPipeline`, `ShadowFeature`, `ShadowMap`, `buildShadowFeature`, `recordShadowPass` |
| webgpu | 6 | the same minus the two Vulkan-only shadow call sites |

All 14 are in the exemption list (`contentExemptBackendFiles`): the files allowed to break the
rule, tracked as debt. It shrinks; it never grows.

## The missing piece is not a mediator

The instinct that something sits between authored content and hardware is right, and the GoF name
for that shape is **Bridge** — decouple an abstraction (the feature) from its implementation (the
backend) so both vary independently. Awake already has the machinery for it: `PipelineFactory<P>`
is an Abstract Factory, `PipelineRegistry` is its resolver, `CommandRecorder` is the recording
port, and `PipelineHandle`/`MaterialBinding` are already backend-neutral handles.

But adding a mediator class now would relocate the duplication rather than delete it, because the
actual blocker is lower down: **two hardware primitives are not reachable from a `PipelineSpec`.**
Every content feature hand-writes them per backend, and that hand-writing is the 14 files.

(The first draft of this section said both primitives were *missing*. Phase 1 disproved half of
that — the table's third column records what each turned out to be.)

| Primitive | Why each content feature hand-rolls it today | Verdict after phase 1 |
|---|---|---|
| **Vertex-less pipeline** | `PipelineSpec` requires a `VertexFormat`. A skybox draws a full-screen triangle from `vertex_index` with no vertex buffer. | **Was not actually missing.** `VertexFormat` is a list of attributes; the empty list already meant this. Only the backends' unconditional binding creation blocked it. ~15 lines, no new type. |
| **Standalone uniform block** | `PipelineSpec` describes shaders and raster state but not a uniform layout. Each content pipeline hand-rolls its own descriptor layout + per-frame buffer + binding — the bulk of both `SkyboxRenderPipeline` classes (211 and 115 lines). | **Half-present.** Vulkan's `PerFrameUniformSlots` already is it, used by two pipelines. What is missing is the *route*: nothing reaches it from a `PipelineSpec`. |

Add those to the RHI and there is nothing left for a `SkyboxRenderPipeline` to be. The recording
half is *already* shared and needs no work: `SharedSkyboxRenderFeature` records against
`PipelineHandle` + `MaterialBinding`, and `SkyboxRenderFeature` already owns the uniform math.

So the sequence is: extend the RHI, then the content types delete themselves. Not: add an
indirection layer, then still write two pipelines behind it.

Worth noting how both estimates moved once the code was read, since the same correction is likely
in phases 3-4: the barrier was smaller than "a missing primitive" in one case and differently
shaped in the other. Neither needed the new abstraction the first draft implied.

## Where content goes

No new module. All three homes exist with the right dependency edges already.

| Tier | Owns | Module |
|---|---|---|
| **Hardware** | pipelines, buffers, textures, command recording. Names no content. | `awake:backend:vulkan`, `awake:backend:webgpu` |
| **The mechanism** | one shared, public `ContentFeature` a consumer writes against without naming a backend. | `awake:engine:render:passes` |
| **Engine-provided optional content** | skybox, shadow — shipped with the engine, off unless an app opts in. Declaration beside its own `.wgsl`; shared recording logic beside the other features. | `awake:asset:shaders` (declaration, where `ScenePipeline` already lives), `awake:engine:render:passes` (recording, where `SharedSkyboxRenderFeature` already lives) |
| **Custom / game-authored content** | a consumer's or one game's own features. | the consumer's own module — `samples/<game>/` in-repo, any app module for a third party. See `skills/awake-framework-boundary/SKILL.md` before promoting any of it |

`expect`/`actual` is not the mechanism for any of this: it resolves per KMP *target*, and desktop
runs both backends. See `docs/reference/render-hardware-interface.md`.

### Custom content features — the consumer case

Awake is a library, so "a consumer builds their own rendering content without forking a backend"
is a requirement, not a nice-to-have. Two facts about the current state:

**Today it is impossible.** `VulkanContentFeature`/`WebGpuContentFeature` are public types with
`internal` constructors — only a backend creates one, via `skyboxContentFeature`. That is the right
trade while skybox is the only instance (it keeps `*RenderFrameContext` internal), but it means a
custom content feature is not yet a supported thing. Opening the constructor is phase 3 scope, not
a separate decision. **Done:** phase 3 replaced both with one shared `ContentFeature` whose
constructor is public, so a consumer can author one for the first time.

**The mechanism belongs in `render:passes`, not `asset:shaders`.** The two are siblings — both
`api(awake:engine:render:contract)`, neither depends on the other. A content feature has to record,
so it needs `CommandRecorder`, which lives in `passes`. Put the type in `asset:shaders` and it
cannot record.

That placement forces one API consequence worth stating up front: the shared type takes a
**`PipelineSpec`** (contract vocabulary), *not* a `ShaderSet` — `ShaderSet` lives in
`asset:shaders`, invisible from `passes`. The consumer converts at their own layer, which is what
`ScenePipeline.toRequests()` already does.

Shaders are not a blocker. A consumer module applies `awake.shader-pipeline-convention`, keeps its
own `src/commonMain/shaders/*.wgsl` and layers the shared directory on top — `samples/studio`
already does exactly this with its own `triangle.wgsl` (`lit_shadow.wgsl` was studio's too until
`c2df5e98c` moved it to the shared pack, where its `LitShadowUniformLayout` already lived).

Target shape, with no backend named anywhere:

```kotlin
// samples/mygame/.../PortalFeature.kt
val portalFeature = contentFeature(
    name = "portal",
    pipeline = PipelineSpec(
        vertexFormat = VertexFormat.None,   // phase 1
        uniforms = PortalUniformLayout,     // phase 2
        // ...shader paths, depth/blend state
    ),
) { recorder, pipeline, binding ->
    recorder.bindPipeline(pipeline)
    recorder.bindMaterial(0, binding)
    recorder.draw(3, 1)
}
```

## Phases

**1 — vertex-less pipelines. DONE (`a017464d3`).** Cheaper than written: `VertexFormat` is already
`data class VertexFormat(val attributes: List<VertexAttribute>)`, so the empty list *is* the
vertex-less case and `PipelineSpec` needed no change at all. `VertexFormat.None` only names it.

What did need fixing was both backends building a binding unconditionally — they now skip the
vertex-rate binding when `attributes` is empty, rather than declaring a stride-0 binding no
attribute reads. Those are different statements to a driver, and only the first says "takes no
vertex buffer". Also fixed a latent crash the two shared: the instanced branch used
`maxOf { it.location }`, which throws on an empty list.

**2 — uniform blocks in the RHI.** Reshaped by what phase 1 found: the primitive is not missing so
much as unreachable.

`PerFrameUniformSlots` (`backend/vulkan/debug/`) **already is** this primitive on Vulkan — one
uniform binding, one buffer/descriptor-set pair per frame in flight, create/write/destroy — and
both `LineRenderPipeline` and `SkyboxRenderPipeline` already use it. So what blocks a content
feature is not "no uniform block exists" but "no route from a `PipelineSpec` to one".

Groundwork landed in `aa7e8db32`: both backends hand-summed `UNIFORM_FLOATS = 40` while
`SkyboxUniformLayout.total` computed it, so a new field in `skybox.wgsl` would have left two
buffers sized for the old block. Both derive it now.

### Exactly what phase 2 changes

Five edits, in this order. Nothing here is a decision left open.

**a. `render:contract` — `PipelineVariant.kt`.** Add one property to the sealed interface and one
preset. The existing four presets override the new property as `true`, so no behaviour moves.

```kotlin
/** `false` disables the depth TEST as well as the write -- for content that must never be
 * occluded and never occludes, drawn before everything (a sky). Distinct from
 * [depthWriteEnabled], which turns off writes only; that KDoc already drew the line. */
val depthTestEnabled: Boolean

data object Background : PipelineVariant {
    override val instanced = false
    override val instanceAlpha = false
    override val instanceFrame = false
    override val blendEnabled = false
    override val depthWriteEnabled = false
    override val depthTestEnabled = false
}
```

**b. `render:contract` — `PipelineSpec.kt`.** One nullable field:

```kotlin
/** The uniform block this pipeline owns, or null for a pipeline whose uniforms come from a
 * per-draw `Material`. Non-null makes the factory allocate one block per frame in flight and
 * return a pipeline implementing `UniformBlock`. */
val uniforms: UniformLayout? = null,
```

**c. `render:passes` — new `command/UniformBlock.kt`,** beside `MaterialBinding`:

```kotlin
/** A pipeline that owns its own uniform block, rather than reading a per-draw material's.
 * Opaque like [MaterialBinding]: shared feature code writes floats and asks for a binding,
 * and never learns whether the backend answered with a descriptor set or a bind group. */
interface UniformBlock {
    fun binding(frameIndex: Int): MaterialBinding
    fun write(frameIndex: Int, floats: FloatArray)
}
```

**d. Both backends — depth test.** Plumb `variant.depthTestEnabled` into the depth-stencil state:
Vulkan's `depthTestEnable`, WebGPU's `depthCompare = Always` when false.

**e. Both backends — allocate the block.** When `spec.uniforms != null`, the factory allocates one
block per frame in flight and the returned pipeline implements `UniformBlock`. Vulkan already has
the machinery (`PerFrameUniformSlots`, sized from `spec.uniforms.total`); WebGPU allocates a buffer
plus bind group, as its `SkyboxRenderPipeline` already does inline.

**Unchanged: `PipelineSet<P>`, `PipelineRegistry<P>`, `buildPipelineTable`, and
`PipelineFactory.create`'s signature.**

> **Correction.** An earlier revision of this section called that last point a blocker — "`PipelineSet<P>`
> has nowhere to carry a binding, so this reshapes the registry in `render:contract`, the widest blast
> radius in this plan." That is wrong, and it was wrong in the direction that inflates scope. The
> binding rides on `P` itself: every backend pipeline type already implements `PipelineHandle`, so
> implementing one more opaque interface beside it costs the registry nothing. `render:contract` gets
> one nullable field and one variant property, not a reshape. Checked by reading
> `PipelineRegistry`/`PipelineSet`/`CommandRecorder` rather than inferring from the generic parameter.

Deliberately **not** doing: mirroring `PerFrameUniformSlots` into WebGPU. That backend runs
`MAX_FRAMES_IN_FLIGHT = 1`, so a per-frame-slots type there would be a one-element case built for a
hypothetical. Symmetry is a rule about *decisions* being made once, not about every class existing
twice — see `skills/awake-render-pipeline/SKILL.md`'s one-backend-capability-vs-decision rule.
Revisit only if WebGPU ever runs more than one frame in flight.

**3 — skybox becomes a declaration, and the type opens.** With 1 and 2 landed, the skybox is
expressible with no new backend code at all:

```kotlin
PipelineSpec(
    vertexFormat = VertexFormat.None,        // phase 1
    uniforms = SkyboxUniformLayout,          // phase 2b
    variant = PipelineVariant.Background,    // phase 2a
    vertexShaderResourcePath = ..., fragmentShaderResourcePath = ...,
    vertexEntryPoint = ..., fragmentEntryPoint = ...,
)
```

Registered through the existing `PipelineRegistry`, which needs no change. The recording half is
already shared and needs none either: `SharedSkyboxRenderFeature` takes a `PipelineHandle` and a
`MaterialBinding`, and `UniformBlock.binding(frameIndex)` is exactly where the second now comes
from.

So these delete rather than move: `SkyboxPass` (its four methods are `UniformBlock` plus
`PipelineHandle`, which the pipeline now implements directly), `VulkanSkyboxPass`,
`WebGpuSkyboxPass`, both `SkyboxRenderPipeline`s, both `skyboxContentFeature`s, and both
`*ContentFeature`/`*FeatureContext` pairs. Ten of the fourteen exemption entries.

One ordering note: `SkyboxRenderFeature` currently reaches its pass through `SkyboxPass`; it must
be repointed at `UniformBlock` + `PipelineHandle` in the same commit that deletes the pass types,
or it will not compile in between.

Same phase, because it is the same edit: the surviving `ContentFeature` moves to `render:passes`
with a **public** constructor over `(PipelineSpec, record lambda)`. That is what makes a custom
consumer feature possible for the first time — see the consumer case above. The internal
constructor exists today only to avoid widening `*RenderFrameContext`; once the type is expressed
in `PipelineHandle`/`MaterialBinding`/`CommandRecorder` — all already backend-neutral — there is
nothing left to hide and no reason to keep it closed.

**4 — shadow.** Harder, and the reason it is last. Split in two once the first half landed.

**4a — re-scope the shadow map out of `Material`. Done (`dd7a7d683`).** The premise was that
`Material.createDescriptorSetLayout(graphicsDevice, shadowMap: ShadowMap? = null)` appended the
shadow texture and sampler into **set 0, the per-material set** — so the shadow map had to exist
before any material layout, before the render pass and before any pipeline, and a feature that
must exist before everything else cannot be an entry in a feature list. `ShadowMap` now owns its
own layout, pool and set, exposes `binding()`, and `lit_shadow.wgsl` reads it at `@group(1)`.
`Material`'s signature is `createDescriptorSetLayout(graphicsDevice)`; bindings 3 and 4 are left
as a deliberate gap rather than renumbered, so a stale shader binds nothing instead of binding
something else.

**4b — shadow as a content feature. Decided: the owned-pass spec. Two steps done, two left.**

Decision (2026-08-23): a backend gains a *capability* — render into an offscreen depth-only
target — and shadow becomes an ordinary content feature that uses it. Not a third
`RenderPassSlot`: a slot orders features inside passes the backend already runs, and what a
shadow needs is a pass it brings with it.

Two steps landed by taking the rule literally — each type was already pure hardware and only its
*name* declared content:

- `ShadowMap` → `DepthTarget` (`24350d091`). A square depth image, view, sampler, render pass,
  framebuffer and descriptor set. A depth pre-pass for occlusion or SSAO is the same object.
- `ShadowRenderPipeline` → `DepthOnlyPipeline` (`ac2ee6765`). A colorless `RenderPipeline` that
  takes the caller's shaders, vertex format and descriptor set layout.

Exemption list 8 → 4: `ShadowFeature` on each backend, plus `VulkanEngine` and `Renderer`.

**Found while doing it, and it changes the done-condition:** `verifyBackendLayering` matches
`\b(class|interface|object|fun)\s+(Name)` against the forbidden vocabulary, so it only sees
content in a *declared name*. `RendererDraw3D.kt` mentions shadow 22 times and declares
`internal fun Renderer.lightViewProjection(light: SceneLight): Mat4` — which builds a
directional shadow box, is unmistakably content, and is invisible to the check because "light"
is not in the vocabulary list and the shadow references are calls and locals. It is not on the
exemption list and never was. So an empty list means "no content in a declared name", not "no
content". Either widen what the check reads, or state the narrower guarantee in D27 — but do not
let an empty list be read as done.

**Done (`21e820127`), and the earlier reading of `ShadowFeature` was wrong.** It looked like
content because of its name and its prose, but the class renders a filtered draw list into a
depth target through a depth-only pipeline and never sees a light: the viewpoint arrives as a
transform the caller's own vertex shader reads out of the per-draw uniform buffer. It is
`DepthPrePassFeature` on both backends now, `shadowShaderSet` is `depthPrePassShaderSet`, and
`recordShadowPass` is `recordDepthPrePass`. `shadowsEnabled` stays -- it is the `Renderer`
interface's own app-facing name, and the backend only reads a flag it did not name.

`contentExemptBackendFiles` is empty: 14 -> 0. See the caveat above about what that does and
does not prove. The owned-pass spec below is no longer needed to reach zero, but it is still the
right shape if a *second* standalone-pass feature ever appears -- at that point the two backends
would otherwise hand-write the pass lifetime twice, which is the duplication this whole plan
exists to delete.

The exact seam, from reading the wiring: a content feature's pipeline is registered by
`feature.spec` and built by the factory against the shared `sceneRenderPass`. An owned-pass
feature needs its pipeline built against **its own target's** render pass instead. So
`ContentFeature` gains `ownedPass: OwnedPassSpec?` (size and depth format — no shadow
vocabulary), and the engine, when it sees one, allocates a `DepthTarget`, registers that
feature's pipeline against that target's render pass, begins and ends the pass around the
feature's recording, and hands `build` the target's `MaterialBinding` alongside the pipeline and
uniform block. Pass *lifetime* stays backend-owned, which is the boundary `CommandRecorder`'s own
doc comment already draws.

That touches `PipelineSpec`, the registry, both pipeline factories, both engines, both
`Renderer`s, and moves the shadow record loop into `shader-pack` — a materially bigger step than
the two renames, with one pixel test as the only gate. Do it as its own change, not as a tail on
something else.

### The gate, and the engine defect it turned out to be reporting

Phase 4 changes `Material`, so a mistake reaches every draw rather than only shadowed ones. That
is why it needs a pixel gate, and nothing covered the 3D shadow map before -- despite its name,
`RendererHeadlessShadowQuadTest` exercises the 2D `UiDrawPrimitive.ShadowQuad`.

Two prerequisites landed. `renderToTexture` now records the Scene feature list (`09def0726`) and
runs the shadow pre-pass (`aad28faaf`); both were drift, and the second is what lets a headless
test reach the shadow map at all. `lit_shadow.wgsl` moved into the shared pack (`c2df5e98c`) so
the gate can use the real shader rather than a stand-in whose own bindings could not catch a
shader/descriptor mismatch.

The gate (`RendererHeadlessShadowMapTest.theCasterDarkensTheGroundItStandsOn`) was written first
and spent a long time failing, because shadows genuinely did not render -- in Studio's swapchain
path either, not only offscreen. It was a **correct test of a broken feature**, not a broken test.

**The defect was in `shadow_depth.wgsl`.** It binds `lit_shadow.wgsl`'s uniform buffer on purpose,
so it mirrors that shader's `Uniforms` struct by hand -- and it was never updated when point
lights were added. `lightMvp` therefore read at float offset 24 instead of 56, so the depth
pre-pass transformed every vertex by a matrix assembled out of light positions and colours,
projected the scene outside the light's frustum, and wrote an empty map. It compiled and it ran.
Fixed by adding the two missing array fields; the gate is enabled and passes.

`ShaderUniformStructTest` (`awake:asset:shader-pack`, desktop) now derives the expected WGSL
declarations from `LitShadowUniformLayout`'s own `type`/`count` and asserts both shaders spell
them out, so the same drift fails a test instead of emptying a shadow map. See
[asl-procedural-shader-plan](2026-08-23-asl-procedural-shader-plan.md) phase 4 for the generator
that would retire it.

Worth carrying forward from that hunt, because each of these cost hours: a "did anything draw"
guard that reads the **alpha** byte passes on a cleared frame; a `uv < 0 || uv > 1` bounds check
is false for NaN; and a depth attachment clears to 1.0, so "wrote 1.0" and "wrote nothing" read
identically on readback. `PixelMap.writePng` and `shadow_sample_probe.wgsl` are both committed --
looking at a frame answered in minutes what assertions had been getting wrong for hours.

**Done when** `contentExemptBackendFiles` is empty in
`awake.backend-layering-convention.gradle.kts`. **It is, as of `21e820127`.**

That condition was set before anyone knew what the check reads, and it is weaker than it sounds
— see the status note. What is genuinely true: no backend file *declares* skybox, shadow,
particle, fog, terrain, water, decal, billboard or occlusion. What is not yet true: no backend
*computes* content. `RendererDraw3D` still builds the light's view-projection and writes
`lit_shadow.wgsl`'s uniform block by hand, through `MaterialUniformLayouts.LitShadowExtra`. That
is the next real target, and it needs the draw-preparation path to take content-supplied uniform
writers rather than knowing the layout itself — the same phase the import list is waiting on.

## Verification bar

Every phase holds this before the next starts — the same bar the RHI plan sets, plus the
exemption list:

```bash
./gradlew :awake:backend:vulkan:desktopTest :awake:backend:webgpu:desktopTest \
  :awake:engine:render:contract:desktopTest :awake:core:geometry:desktopTest \
  :awake:backend:vulkan:detekt :awake:backend:webgpu:detekt \
  :samples:studio:compileKotlinDesktop :samples:studio:compileKotlinWasmJs \
  :awake:backend:vulkan:verifyBackendLayering :awake:backend:webgpu:verifyBackendLayering
```

Two things that are *not* evidence, both learned the hard way in this repo:

- **A green `verifyBackendLayering` proves nothing on its own** — the check is green whenever no
  file breaks the rule *and is not exempt*, so adding an exemption turns it green just as
  reliably as fixing the file does. A phase that claims to remove files must show the list
  shorter. State the exact expected set before editing, then check it.
- **Phase 3 needs a rendered frame, not a compile.** The skybox is the first pipeline built from
  `VertexFormat.None` + a spec-owned uniform block, so a wrong descriptor slot or depth state
  compiles clean and draws nothing. `RendererHeadlessPixelBaselineTest` is the check that a sky
  actually appears; a passing build is not.

## What stays in the backend

The content-versus-capability test, unchanged from `docs/reference/render-extensibility.md`:
a **capability** is a draw primitive an app supplies content to; **content** is the specific thing
drawn. `LineRenderPipeline` and the UI pass stay — nobody is forced to call them with anything, and
the actual gizmo/overlay content lives in `samples:*`. `Skybox` and `Shadow` do not.
