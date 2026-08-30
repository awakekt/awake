# ASL procedural shaders: module, DSL shape, generation wiring

Implementation plan for the vision in
[2026-08-19-asl-single-source-shader-sketch.md](../audits/2026-08-19-asl-single-source-shader-sketch.md):
shaders authored procedurally in Kotlin, emitted as WGSL text, fed into the existing
`validateAwakeShaders`/`syncAwakeShaders` naga pipeline unchanged. ASL never touches GLSL or
SPIR-V; naga stays the type checker.

What changed since the sketch: the shader module split landed. `awake:asset:shaders` is now
contract-only (`ShaderSet`/`ShaderStages`/`ShaderSource`), `awake:asset:shader-pack` holds the
7 authored `.wgsl` plus their `UniformLayout`s. That settles the sketch's module question —
authoring machinery is a third capability and gets its own flat sibling.

## Why WGSL is the base language, not GLSL or SPIR-V

- **WGSL** — one emitter serves both backends: WebGPU consumes it raw, Vulkan gets SPIR-V
  from naga, and that conversion already runs in this build (`syncAwakeShaders`). The naga
  validator already wired into `check` is ASL's type checker for free. Committed output is
  human-readable, so diffs stay reviewable. naga also emits MSL, so a hypothetical Metal
  backend costs nothing at this layer.
- **GLSL** — rejected. Needs glslang/shaderc for SPIR-V (new toolchain) *plus* a GLSL→WGSL
  conversion for WebGPU, through naga's weakest, least-maintained frontend. Two conversions
  instead of one, plus the 330/450/ES dialect zoo. Only wins if an OpenGL/GLES backend is
  planned; none is.
- **SPIR-V** — rejected. Emitting it directly means writing a real compiler backend (SSA
  form, binary encoding, capability declarations) — an order of magnitude more emitter than
  WGSL text — with unreviewable binary as the committed artifact, and WebGPU still needs a
  SPIR-V→WGSL conversion on top.
- **Slang** — not a base but a rival strategy: authoring in Slang would *replace* ASL (its
  compiler already targets SPIR-V/WGSL/MSL). Rejected because permutation logic would live in
  Slang generics instead of Kotlin, and single-source sharing with CPU-side layouts
  (`UniformLayout` derivation, `VertexFormat` inputs) is exactly the payoff being built.

Known ceiling: WGSL is capped at WebGPU's feature set — no geometry/tessellation stages, no
Vulkan-only extensions. If one shader ever needs those, it's hand-written GLSL/SPIR-V beside
the pipeline for that shader, not a base-language change.

## Module location and packages

```
awake:asset:shader-dsl          (new)
└── commonMain
    └── io.github.awakelab.awake.asset.shaderdsl
```

- Flat sibling, not a nested `shaders:asl` submodule — the asset group is flat
  (`gltf`, `mesh-optimizer`, `shaders`, `shader-pack`), and the package collapses the hyphen
  the way `shaderpack` already does.
- Dependencies: `api(":awake:core:geometry")` for `GpuDataShape`
  ([VertexAttribute.kt:15](../../awake/core/geometry/src/commonMain/kotlin/io/github/awakelab/awake/core/geometry/VertexAttribute.kt))
  — shapes are reused, not redeclared. Nothing else: the module turns Kotlin definitions into
  WGSL text; it needs no `ShaderSet`, no render contract, no backend.
- Group/package follow the current `io.github.awakelab` convention; the pre-publish
  `awake-lab` rename is one repo-wide pass and this module rides along.

## Interface shape

The sketch's DSL, refined into buildable Kotlin. The key change from the sketch: struct and
input fields use `by` property delegation, so `uniforms.mvp` works without codegen — the
property name *is* the WGSL field name.

```kotlin
val TriangleShader = shader("triangle") {
    val uniforms = uniformBlock("Uniforms", group = 0, binding = 0)
    val mvp by uniforms.field(GpuDataShape.Mat4)
    // vec4, not vec3 -- sidesteps WGSL's 16-byte alignment padding, same reasoning as the
    // hand-written file's own comment.
    val lightDirection by uniforms.field(GpuDataShape.Vec4)
    val lightColor by uniforms.field(GpuDataShape.Vec4)

    val out = varyings("VertexOutput")
    val color by out.varying(GpuDataShape.Vec3, location = 0)
    val normal by out.varying(GpuDataShape.Vec3, location = 1)

    vertex("vertexMain") {
        val inPosition by input(GpuDataShape.Vec3, location = 0)
        val inNormal by input(GpuDataShape.Vec3, location = 1)
        val inColor by input(GpuDataShape.Vec3, location = 2)

        out.position set (mvp * vec4(inPosition, 1f.lit))
        color set inColor
        normal set inNormal
    }

    val ambient = const("AMBIENT_STRENGTH", 0.08f)

    fragment("fragmentMain") {
        val n = let("n", normalize(normal))
        val l = let("l", normalize(lightDirection.xyz))
        val diffuse = let("diffuse", max(dot(n, l), 0f.lit))
        val shade = let("shade", ambient + (1f.lit - ambient) * diffuse)
        val lit = let("lit", color * shade * lightColor.xyz)
        val mapped = let("mapped", lit / (lit + vec3(1f.lit)))
        colorOutput(vec4(mapped, 1f.lit))
    }
}

TriangleShader.emitWgsl() // -> String
```

Types behind it — small and closed:

- `AslExpr` — sealed tree: `Literal`, `VarRef`, `Swizzle`, `Binary`, `Call`, `Construct`.
  Every node carries a `GpuDataShape`. Kotlin operator overloads (`times`, `plus`, `minus`,
  `div`) build `Binary`; swizzles are `val` extension properties (`.xyz`, `.x`); builtins are
  top-level functions (`normalize`, `dot`, `max`, `vec3`, `vec4`) building `Call`/`Construct`.
- Statements per stage body: `set` (infix assignment), `let` (named local), `const`
  (module-scope constant), `colorOutput`.
- Builders (`ShaderBuilder`, stage builders, `uniformBlock`) carry a shared `@DslMarker`
  annotation — same nested-builder rule the scene DSL follows.
- No phantom generics (`Expr<Vec3>`). The shape field powers *structural* checks only;
  mismatched math types stay naga's job, per the sketch's division of labor.

Structural checks at emit time, each a fail-fast on real drift classes:
duplicate `(group, binding)`, varying location collisions, varying read in fragment but never
written in vertex, constructor arity vs. shape (`vec4(vec3, f)` = 4 components).

The procedural payoff is Kotlin itself: loops, constants, and helper functions run at
*generation* time and unroll into WGSL. WGSL *runtime* control flow (`for`/`if` nodes in the
tree) is deliberately absent until a proof shader needs it.

## Generation wiring — where output lands, how it runs

**Output is committed**, per the sketch's lean and the `vulkan_generator` precedent: emitted
`.wgsl` files land in the consuming module's existing scanned shader directory
(`src/commonMain/shaders/` in samples), reviewable in diffs, with the pipeline downstream of
them untouched.

**The sketch's `GenerateAslShadersTask` cannot work as drawn**: build-logic is an included
build and cannot depend on main-build modules, so a convention-plugin task can't link
`shader-dsl`. Two real options:

| | a — record-mode test | b — JavaExec generator |
|---|---|---|
| Shape | Definitions + golden `.wgsl` in the consumer; desktop test regenerates when `AWAKE_RECORD_SHADERS=1`, asserts byte-equality otherwise | `main()` in consumer's jvm source set, convention plugin wires `JavaExec` before `validateAwakeShaders` |
| Precedent | `AWAKE_RECORD_SNAPSHOTS` | none |
| Gradle plumbing | zero | new task class + classpath wiring |
| Drift guard | free — CI fails when committed WGSL ≠ DSL output | needs a separate check |

**Take (a).** It converts the sketch's "two sources of truth" worry into a CI-guarded
invariant for free. (b) is the upgrade path once more than one module authors in ASL and
re-recording by hand gets old.

## naga, and the runtime question

naga-cli stays the build-time transpiler/validator — already wired, proven, nothing to build.
tint (Dawn's compiler) is the only credible alternative and there's no reason to switch;
it matters below only because it's already *inside* every WebGPU implementation.

ASL is pure Kotlin, so `emitWgsl()` can also run *on device* — runtime-generated shaders
(hot reload, data-driven materials, permutations picked at runtime). The runtime story
splits by backend:

| Tier | Mechanism | Cost |
|---|---|---|
| WebGPU runtime | `createShaderModule(wgsl)` — the API takes WGSL text natively (browser/Dawn embed tint) | zero, works day one |
| Vulkan dev-time hot reload | desktop only: shell out to the same `naga` binary the build uses, load the emitted `.spv` | small, dev-machine tool |
| Vulkan shipped-runtime compile | naga (Rust) or tint (C++) linked as a library — JNI on desktop/Android, cinterop on iOS, per-target native builds | large FFI project |

First two tiers cover every current need: shipped Vulkan builds precompile at build time
(status quo), WebGPU gets runtime generation free, desktop dev loop gets hot reload cheap.
The third tier is deferred until a real consumer needs on-device shader generation on
Vulkan — it is the only expensive row, and nothing in the phases below depends on it.

## Phases

1. **Emitter spike** — **landed 2026-08-23.** `awake:asset:shader-dsl` exists: expression
   tree, builders, `WgslEmitter`, structural checks, plus `AslEvaluator` (CPU fragment
   interpreter serving as test oracle and headless-preview engine — not in the original plan,
   added because the preview and the oracle are the same walk). `TriangleShader` regenerates
   `triangle.wgsl` code-identically (minus prose comments); goldens are full-text asserts in
   `WgslEmissionTest`, and `NagaValidationTest` runs the real `naga` binary over emitted
   output on desktop (skips where naga is absent).
2. **Procedural proof** — **landed, narrower than planned.** `CheckerShader` is the
   new-content proof (no hand-written original), previewable headlessly via
   `:awake:asset:shader-dsl:previewShader` (ANSI half-blocks, no file written). The
   generation-time *loop* demo (noise octaves) did not make the cut — checker needed none, so
   the loop-unroll claim is still undemonstrated. Both proof definitions live in the module
   itself as documented fixtures, not `samples/studio` — they move when studio actually
   consumes one.
3. **Record-mode wiring** — **landed 2026-08-23.** `samples/studio`'s `triangle.wgsl` is now
   generated: `AslShaderDriftTest` (studio desktopTest) re-emits `TriangleShader` and asserts
   the committed file matches, re-recording under `AWAKE_RECORD_SHADERS=1` (environment
   variable, not `-D` — a Gradle CLI `-D` never reaches the forked test JVM). The swap was
   provably behavior-neutral: after `syncAwakeShaders`, the Vulkan `.spv` came out
   byte-identical (naga discards comments), so only the WebGPU text copy changed. The
   definition still lives in `shader-dsl` as the shared fixture; it moves to studio when
   studio authors a shader of its own.
4. **Shadow pair — landed 2026-08-24.** The gaps the shadow incident exposed (array uniform
   fields, then runtime control flow) are closed, and both shaders are generated:
   - The DSL grew `AslType` (i32/u32/bool/texture/sampler alongside `GpuDataShape` data),
     `fieldArray` (+ `array<vec4f, N>` emission and indexing), `var`/`assign`/`iff`/
     `continue`/early-`return`, `loopI32`/`loopU32`, module functions (`fn` + typed call
     handles), `texture2d`/`sampler` bindings, `textureSampleLevel`/`textureDimensions`, a
     no-varyings `returnPosition` vertex form and empty fragment (shadow_depth's shape), and
     the extra builtins/casts/comparisons lit_shadow's PBR needs. naga caught one real
     emitter bug during bring-up (swizzle on an unparenthesized binary) — exactly the
     division of labor the plan intended.
   - `AslShadowShaders.kt` declares both shaders; the `Uniforms` struct comes from ONE
     shared field list (`shadowUniforms(includeLitTail)`), so the offset-24/56 drift class
     is now unrepresentable, not merely tested. `AslShadowDriftTest`
     (`awake:asset:shader-pack`, desktop, `AWAKE_RECORD_SHADERS=1` to re-record) pins the
     committed `.wgsl` to the definitions.
   - Behavior-neutral by construction: after re-recording both `.wgsl`, `syncAwakeShaders`
     produced byte-identical Vulkan `.spv` for all three shadow stages, and
     `RendererHeadlessShadowMapTest` (real device) passes.
   - **Layout derivation closed the loop (2026-08-24), in the opposite direction from the
     one sketched here.** ASL's field list was itself a hand-mirror of
     `MaterialUniformLayouts.LitShadow` (the drift moved, not died), so `fieldsFrom(layout)`
     now declares the WGSL struct straight from the layout the renderer packs, the shadow
     definitions moved into `shader-pack` beside their `.wgsl` (pack gained an `api` on the
     DSL), and the loop bound reads `MAX_POINT_LIGHTS` itself — `POINT_LIGHT_SLOTS` is gone.
     The layout stays the source of truth because the renderer's packing code already reads
     it; deriving the other way would have left two Kotlin descriptions again.
     `ShaderUniformStructTest` stays as the one witness reading the synced artifact.
   - Every emitted file now opens with a generated-by header (Awake Lab ASL, shader name,
     re-record instructions) — deliberately undated so record runs stay byte-reproducible.

5. **`VertexFormat`-driven inputs — landed 2026-08-24.** `inputsFrom(format)` declares the
   vertex stage's inputs from the same `VertexFormat` the pipeline is keyed on — locations,
   shapes, and names (`"in" + semantic`) all derive, and semantic-keyed handles throw on an
   attribute the format lacks. The definition records the format
   (`AslShaderDefinition.vertexFormat`) so a consumer can key its `PipelineSpec` from the
   same declaration. Triangle and both shadow shaders switched over; every drift test passed
   **without re-recording** — the derivation reproduces the hand-numbered emission
   byte-for-byte. Checker deliberately stays hand-declared (its position+uv layout has no
   named format), demonstrating both forms.

6. **Permutation generation — landed 2026-08-24.** `instanced.wgsl`/`skinned.wgsl`/
   `skinned_instanced.wgsl` are emitted from ONE parameterized definition
   (`AslMeshVariantShaders.kt`, `meshVariant(instanced, skinned)`) — the hand-maintained
   cross-product is gone, and adding an axis is a flag, not a fourth file. The DSL grew what
   the trio needed: `vec4<u32>` inputs (UInt4 mapping + uint component swizzles),
   expression-valued consts (`LIGHT_DIRECTION : vec3f`), `mat4()` construction,
   `@builtin(instance_index)`, `instanceModelMatrix()` for instance-rate columns, and a
   read-only storage palette binding (`palettes[instance].joints[joint]`, no pointer sugar).
   `MAX_JOINTS` hoisted to `render:contract` beside `MAX_POINT_LIGHTS`; both backends alias
   it, killing the third twin constant. Pack `.spv` came out byte-identical (naga
   canonicalizes the name/order differences); studio's stale copies re-synced; full Vulkan +
   WebGPU + studio desktop suites pass. Preserved quirk, verbatim: non-instanced skinned has
   no light uniforms — const light direction, no light-colour multiply — because that is
   what its CPU packer feeds.

7. **Full sweep — landed 2026-08-24.** All 9 shaders are generated. The mesh trio's uniform
   structs now derive from `InstancedUniformLayout`/`SkinnedUniformLayout` (the latter new in
   `render:contract` beside `MAX_JOINTS`) — closing the same hand-mirror class the shadow
   issue exposed, which `meshVariant` had quietly reintroduced. The last three hand-written
   shaders ported: `skybox` (`vertex_index` builtin, local const arrays), `textured`
   (`textureSample`, screen-space-derivative TBN with the `mat3 * v` expanded to its column
   sum — no mat3 type needed; also fixed the committed file's `material`-vs-`pbrFactors`
   name drift from its own layout), and `particle` (`ParticleUniformLayout` derivation,
   matrix-column reads, per-instance color/frame attributes). `AslPackDriftTest` pins all 8
   pack shaders. Verification note: unlike earlier phases, the `textured`/`skybox`/`particle`
   `.spv` changed bytes (field renames to match layouts plus reordered statements), so the
   equivalence evidence at land time was naga validation + transcription review — and once
   `scene:runtime` compiled again the full Vulkan, WebGPU, and studio desktop suites ran
   green on the regenerated shaders, closing that gap.

8. **Generator task + layout adoption + dev loop — landed 2026-08-24.**
   `:awake:asset:shader-pack:generateAslShaders` re-emits all 9 generated `.wgsl` in one
   command (classpath bound to the compile task's outputs, not `classesDirs` — those are
   builtBy the classes lifecycle whose processResources leg depends on `syncAwakeShaders`,
   which the generator finalizes; binding to the compile task is what avoids the cycle), then
   naga-validates and re-syncs `.spv` via `finalizedBy`. Proven byte-stable: a run against
   the committed tree produces zero diff. With `--continuous` it is the dev loop — edit a
   definition, shaders and `.spv` refresh; restart the app to pick them up. In-process
   pipeline reload stays a renderer feature ASL does not own. The studio drivers also stopped
   hand-summing buffer sizes: `createMaterial(SkinnedUniformLayout/InstancedUniformLayout)`
   and `ParticleUniformLayout.total` replace three literal constants — one of which
   (`16 + 64 * 16`) was yet another `MAX_JOINTS` twin.

9. **naga-as-library — landed 2026-08-24, full KMP.** `awake:asset:shader-compiler`:
   `NagaShaderCompiler.wgslToSpirv/validate` backed by a ~150-line Rust shim over the `naga`
   crate (`rust-native/`). Desktop/Android bind through JNI symbols exported straight from
   Rust (`jni` crate — no C glue); iOS links a static library through cinterop with the
   jolt-style generated `.def`; wasmJs throws by design (browsers take WGSL directly).
   Native builds are on-demand cargo tasks (`buildNagaDesktop/Android/IosArm64/...`), not
   part of `check`; the crate pins naga and must track the pipeline's `naga-cli` version.
   Verified: real JNI round-trip tests on desktop, per-ABI `.so` builds + compile on
   Android, cinterop compile + framework link on iOS. Not yet: an instrumented Android run,
   a device iOS run, or CI Rust-toolchain wiring — each waits on the first real consumer.

## Not doing

- GLSL or SPIR-V emission — naga owns both, unchanged.
- Emitting into `awake:asset:shader-pack` — the shader stdlib stays hand-written until ASL
  has proven itself in samples.
- A full Kotlin-side WGSL type system — structural checks only; naga validates for real.
