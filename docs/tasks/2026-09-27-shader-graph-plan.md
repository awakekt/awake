# Shader graph plan

Date: 2026-09-27
Status: **proposed** — step 6 of the [node-graph sequence](2026-09-27-node-graph-plan.md). It
waits on node-graph phases 1–2, on shader hot reload (done on Vulkan; WebGPU is #214), and
on a per-material shader render plan that is not yet written.

## Goal

Node-based shader authoring in Studio for coders and non-coders. A graph compiles to ASL, then to
WGSL, and is lit by the engine's own shading. It launches with both terrain and mesh targets.

## Current state

See the "Shaders" section of the [node-graph plan](2026-09-27-node-graph-plan.md).

## Design

### Modules

| Module | Holds | Depends on |
|---|---|---|
| `:awake:asset:shader-graph` | shader node library, type inference, graph to ASL compiler, targets | `node-graph`, `shader-dsl`, `shader-pack` |
| `:awake:kit:shader-graph` | graph asset loading, terrain and mesh providers | `asset:shader-graph`, `scene:scene3d`, `core:io` |

### Node levels

| Level | What | Role |
|---|---|---|
| Pack shaders (`litShadowShader`, skybox, depth fog, terrain) | Complete programs, each owning its vertex stage, bindings, uniform layout and pipeline | Not nodes. They become **targets**: the frame a graph fills in. |
| Pack functions | Pure ASL functions: `distributionGgx`, `geometrySmith`, `fresnelSchlick`, `linearToSrgb`, `perturbNormal` | **Function nodes.** Each emits one call. |
| ASL builtins | `mix`, `smoothstep`, `dot`, `textureSample`, … | **Math nodes.** The base layer. |

- **Lighting belongs to the target.** A graph outputs surface properties, and the target lights
  them with the pack's own code.
- **Only pure functions become nodes.** `applyFog` and `sampleShadow` read the shader's own
  uniforms and bindings, so they stay inside targets.

### Compiler

- **Targets.** A target is a vertex stage plus the inputs it exposes to a graph and the outputs
  it requires.
- **Terrain target.**
  - Inputs: world normal, world position and sun direction.
  - Output: base colour. The target adds `TerrainShader`'s lighting.
  - World position is exported only when the graph reads it, because ASL rejects dead varyings.
  - Graph textures bind from 3.
  - Parameters are baked in as constants.
- **Mesh target.**
  - Outputs: base colour, normal, roughness and metallic, lit by the lit-shadow path.
  - Parameters become material uniform fields.
- **Node library.** Built only on what ASL already emits:
  - constants and target inputs;
  - vector construct, split and swizzle;
  - the ASL math builtins;
  - arithmetic;
  - 2D texture sampling.
- **Types.** Every port carries a shape (`f32`, `vec2`, `vec3`, `vec4`), and a scalar broadcasts
  where WGSL allows it. The compiler rejects any mismatch naga would reject, before emitting,
  and reports it against the node id.
- **Compilation.** Each node output becomes `let("n_<node>_<port>", …)`, so the evaluator trace
  maps one-to-one onto nodes. Previews run `traceFragment` over a UV grid.
- **Direction.** Conversion is one-way, graph to ASL.

## Phases

- **S1 — compiler and terrain target.**
  - Gate: a constant-grey graph evaluates equal to `TerrainShader`, with a positive control.
  - Gate: every node, fed every input shape the graph layer accepts, passes naga validation.
    Stated rule: a graph that passes validation never produces a naga error.
- **S2 — terrain provider.** A `*.shadergraph.json` asset and a provider modelled on
  `TerrainLayersSurfaceProvider`.
  - Gate: headless pixel tests on Vulkan and WebGPU. A slope/flat two-colour graph renders both
    regions, and swapping the colours swaps them.
- **S3 — pack function nodes.** Extract the pure functions, which today are copied across
  `AslShadowShaders.kt` and `AslTexturedShader.kt`, into shared helpers.
  - Gate: every `PackShaderSets` entry emits byte-identical WGSL before and after, for both clip
    spaces. Record the emitted text before extracting and compare after; no committed `.wgsl`
    exists to diff against.
- **S4 — mesh target,** after the per-material shader render plan lands.
  - Gate: two materials sharing one graph render their own parameter values, and swapping the
    values swaps the colours.
  - Gate: graph meshes cast the same shadows as default meshes.
- **S5 — Studio shader graph editor.** Palette, inspector, per-node thumbnails, and a live
  preview through shader hot reload (`ShaderReplacement`).
  - Gate: editing a node updates the terrain and mesh previews on desktop and on the web.

## Limits and follow-ups

- **Texture previews.** Shader previews stop at texture sampling on the CPU. Nodes fed by
  textures need GPU thumbnails later.

## Open decisions

1. **Terrain parameters.** Baked constants, which need a recompile per change (recommended for
   v1). The alternative is a parameter texture like terrain-layers' layer table.
2. **Studio plugin home.** A new `shader-graph` plugin (recommended).
