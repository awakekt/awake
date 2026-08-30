# Terrain Clipmap Draw Plan

Date: 2026-08-29
Status: complete — heightfield draws; splat deferred to the starter-kit

Implements [D28](../decisions/D28-open-world-framework-boundary.md) item 4. Follows
[the content feature textures plan](./2026-08-29-content-feature-textures-plan.md), which made a
pass able to own and bind a texture — the thing terrain needs and could not do.

## Goal

Draw a heightfield. A `TerrainComponent` with a `Heightmap` should produce pixels, which is the
first time anything in D28 renders.

## What already exists

More than expected, and none of it wired to anything.

- **CPU geometry is complete.** `TerrainClipmapGeometry.buildAllClipmapMeshes(config)` returns
  the core plus every ring as `MeshGeometry` in `PositionNormalColorUv`. 203 lines, tested.
- **Ring tracking is complete.** `TerrainClipmapTracker` snaps each ring's origin to its own
  spacing and computes a morph factor per position. `TerrainClipmapSystem` drives it per frame.
- **Nothing draws.** `TerrainClipmapSystem` updates trackers and emits no draw call; `RenderSystem`
  has no `TerrainComponent` branch.

## Two findings that change the shape of this

### `TerrainSplatShaderSource` is not usable as it stands

It is a hand-written WGSL string in a Kotlin object, not ASL — the only shader in the repo that
still is, now that the probe was converted. Beyond the inconsistency:

- it samples a `texture_2d_array<f32>`, which is **2b**, explicitly deferred behind a JNI change;
- its entry points are `vs_main`/`fs_main`, not the `vertexMain`/`fragmentMain` every pipeline
  in the engine now assumes;
- it has never been compiled by anything, so none of the above has ever failed loudly.

### A content feature owns one uniform block, and terrain needs one origin per ring

`UniformBlock.write(frameIndex, ...)` fills a single buffer per frame. Six rings need six snapped
origins, and six sequential writes to one buffer leave only the last. The shader's own
`RingUniforms` struct assumes per-ring data that the current block cannot deliver.

Three ways out, in increasing cost:

1. **One instanced draw**, ring index from `@builtin(instance_index)`, origins in a fixed-size
   array in the single uniform block. `CommandRecorder.draw(vertexCount, instanceCount)` already
   takes an instance count, and the ring count is small and bounded by `TerrainClipmapConfig`.
2. **A storage buffer** of ring records, indexed by instance. Needs `ResourceKind.StorageBuffer`
   honoured in a content feature's group, which Phase 1 explicitly rejected as unsupplied.
3. **N uniform blocks per feature**, one per ring. Largest change to `ContentFeature`.

**Recommendation: option 1.** It fits the block a content feature already owns, needs no new
resource kind, and the bound is real rather than arbitrary.

## The scope split this suggests

D28 already puts "splat layer choice, tiling and terrain material authoring" in the starter-kit
and keeps "clipmap geometry upload and draw emission from a `Heightmap`" in Awake. Reading that
literally resolves both findings at once:

- **Awake draws the heightfield.** Clipmap rings, positioned from the tracker, displaced in the
  vertex stage by sampling the heightmap, shaded by the existing directional light. One sampled
  texture, read in the vertex stage — exactly the case `ShaderStage` was added for in the
  previous plan, and **no texture array, so 2b stays deferred**.
- **The starter-kit splats it.** Four-layer weightmap blending is authored world policy. It
  wants `texture_2d_array`, which is 2b, and it can arrive whenever a consumer needs it.

That makes item 4 shippable now instead of behind a native change.

## Phases

### Phase 1 — A terrain shader in ASL — **done**

`AslTerrainShader` in `awake:asset:shader-pack`: a uniform block, a heightmap `texture_2d` and
its sampler, both read in the **vertex** stage. `TerrainShaderCompileTest` validates the emitted
WGSL through naga and asserts the binding triple that `bindingsForGroup` derives.

**The named risk is retired.** Vertex-stage texture sampling had never executed on either
backend, and it does not work the obvious way: a vertex stage has no implicit derivatives, so a
plain `textureSample` there is a naga validation error rather than a wrong picture. The read is
`textureSampleLevel` at mip 0, and the test asserts that rather than trusting it.

**A correction to this plan's own option 1.** "One instanced draw" does not work:
`buildAllClipmapMeshes` returns *distinct geometry per ring*, and instancing draws one mesh N
times. The shape that does work is one **merged** mesh with each vertex tagged by its ring level,
indexing a bounded `ringParams` array in the single uniform block. The colour channel carries the
tag — `TerrainClipmapGeometry` writes a constant `(1, 1, 1)` into every vertex and nothing reads
it — so this needs no new `VertexFormat` and no `firstInstance` support in the recorder. Phase 2
now has to merge rather than upload N meshes.

**Two removals.** `TerrainSplatShaderSource` and its compile test are gone. The shader was the
last hand-written WGSL in the repo, sampled a `texture_2d_array` (2b, deferred), used
`vs_main`/`fs_main` entry points nothing else accepts, and had never been drawn with — the same
"never ran, so nothing is lost" reasoning D28 applied to water and atmosphere. Four-layer
splatting returns as starter-kit content.

`toU32` was added to the DSL beside the existing `toF32`: WGSL array subscripts take `i32`/`u32`,
so a ring level carried through a float vertex channel cannot index `ringParams` without it.

### Phase 2 — Merge the clipmap levels into one mesh — **done**

`TerrainClipmapGeometry.buildMergedClipmapMesh(config)` concatenates every level's vertices and
rebases every level's indices past the vertices before it, tagging each vertex with its ring
level in the colour channel's red component.

Renamed from "upload the clipmap meshes once" because Phase 1 established the merge is required,
not an optimisation: distinct geometry per level cannot be instanced, and one draw per level
would need one uniform block per level.

`MergedClipmapMeshTest` covers the two ways this goes wrong *by rendering* rather than throwing —
an index left local to its own level addresses level 0's vertices and draws its triangles again,
and an untagged vertex reads ring 0's parameters. Both are silent; neither would fail a
"did it produce a mesh" check.

GPU upload is not here. It belongs with the feature that owns the mesh, which is Phase 3.

### Phase 3 — `terrainContentFeature(...)` — **done**

A `ContentFeatureSource` beside `skyboxContentFeature`: declares the pipeline with bindings read
back from `TerrainShader`, carries the encoded heightmap as its texture and the merged clipmap as
its geometry, and records one indexed draw per frame after writing that frame's ring origins.

**The open question resolved without touching the ECS.** `RenderFrameContext` already carries
`cameraEye`, `viewProjection` and `light`, so the feature owns its own `TerrainClipmapTracker`
and drives it from the frame. `TerrainClipmapSystem` stays for a consumer that wants
entity-driven terrain; nothing here reads a `World`.

**`ContentFeature.build` did need widening after all,** which contradicts the note the previous
plan left. That note was right about textures and wrong as a general claim: a texture lands in
the descriptor set a feature already binds, so it needs no new argument, but a *vertex buffer
does not*, and nothing a feature holds can reach one. `geometry: MeshGeometry?` is declared
alongside `textures` and `build` receives the uploaded `ContentGeometry` as a third argument —
null for a vertex-less feature, which is every existing one.

`ContentGeometry` needs no widening of the render contract's `Mesh`, which deliberately hides
its buffers: `BufferHandle` is already a marker interface, and both backends' `Mesh` already
expose neutral `vertexBinding`/`indexBinding`.

**A heightmap needed an encoding the plan did not anticipate.** An 8-bit texture cannot hold
arbitrary float heights, so samples are normalised across the map's own range into the red
channel and the shader reconstructs `red * scale + bias`. That added `terrainParams.w` as the
bias; a flat heightmap encodes as constant zero rather than dividing by a zero range.

Both backends build and test green, and `compileKotlinWasmJs` passes — the whole feature is
`commonMain`.

### Phase 4 — Prove it on pixels — **done**

`RendererHeadlessTerrainTest` runs the real `TerrainRenderFeature` against lavapipe, so the whole
chain holds at once: ASL declares the bindings, the descriptor set derives from them, the
heightmap uploads and is sampled in the vertex stage, the merged clipmap's per-vertex ring tags
index the uniform array, and displaced geometry rasterises.

**It asserts coverage, not colour, and that is forced.** `TerrainClipmapGeometry` gives every
vertex the same upward normal, so displacement moves geometry without changing its shading --
every terrain pixel is one grey, and a colour assertion would pass on a shader that ignored the
heightmap entirely. What displacement changes is which pixels terrain covers, so the test
compares a raised heightmap's covered-pixel count against a flat one, requiring a shift of at
least a tenth of the baseline rather than mere inequality.

**Two constraints the environment imposed.** A second `GraphicsDevice` in one process aborts the
JVM with SIGABRT, which is why every headless test here caches one renderer per class; two
heightmaps therefore mean two pipelines behind a switching feature, not two renderers. And the
terrain shader has no committed `.spv`, so the pipeline compiles `TerrainShader.emitWgsl()`
through naga at test time -- the same runtime path the content-texture probe uses.

Vulkan suite: 23 classes, 60 tests, green.

## Non-goals

- No splat layers, no texture arrays, no 2b. Stated above.
- No streaming. `WorldPartitionSystem` exists and stays unwired; D28 defers it until one cell
  renders, which is what this plan produces.
- No LOD stitching between rings beyond the morph already in the tracker. Cracks at ring
  boundaries are a known follow-up, not a blocker for first pixels.
- No `TerrainComponent`-to-feature binding through the ECS. Phase 3's open question names the
  seam; wiring `RenderSystem` to content features is a separate decision.

## Risks

`TerrainSplatShaderSource` becomes dead once Phase 1 lands. It should move to the starter-kit
with the other authored content or be deleted outright, on the same reasoning D28 applied to
water and atmosphere — it has never run, so nothing is lost either way. Leaving it in the engine
is the one outcome to avoid.

Vertex-stage texture sampling is declared but has never executed on either backend. The previous
plan's probe sampled in the fragment stage only. If a driver rejects it, that surfaces in Phase 1
rather than at the end.
