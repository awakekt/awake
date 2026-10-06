# D38: shaders as project data

Status: proposed (2026-10-06)

## Decision

A published project can bring its own shader, and Core builds it, with no change to Core or a
backend. awakekt/awake#456 asked how. No code was run for this record: every finding below comes
from reading the code, and the ones that need a first implementation to confirm are marked.

| Question | Answer |
|---|---|
| **Format** | A **shader document**: a restricted, serializable expression graph (a subset of ASL), compiled to WGSL by Core's own emitter. Raw WGSL is **not** accepted from a published project in the first release. |
| **How a project uses it** | **Stage 1:** a scene component names a shader document, and a system attaches it as a *content feature* through `ContentFeatureHost` when the scene loads, the way `TerrainContentSystem` attaches terrain. No backend change. **Stage 2** (a shader on any scene mesh) needs draw routing in both backends, and is decided separately when a project needs it. |
| **Parameters** | The document declares a uniform block (name, type, default). The scene component sets values by name. A mismatch fails the load with a message naming the parameter. |
| **Limits for untrusted input** | Checked before anything compiles: only the restricted node set; no storage bindings, no functions, no compute; loops only with literal bounds under a cap; caps on node count and texture samples. A document that fails is not attached, an error is logged, and the scene plays on, as terrain does when its feature cannot attach. |
| **Live preview in Studio** | Detach and re-attach the content feature. awakekt/awake#214 (WebGPU shader replacement) and #217 (UI pipelines) are **not** prerequisites. |
| **Home** | A capability module, `awake:asset:shader-document` (the model, the validator, the conversion to ASL), depending on `awake:asset:shader-dsl` and kotlinx-serialization and on no `awake:scene` module. The scene wrapper is `awake:scene:shader` (component, binding, system). `awake:project:runtime` loads the documents with the project. |

## Why: what the code shows

**Shaders are Kotlin only today.** The shaders guide says "Render plans and shaders are Kotlin only.
A scene document and AwakeKt Studio pick components; which pipelines exist is decided by the app's
plan." No scene or project field names a shader, and `loadPlayableProject` loads models, physics,
particle sprites and collision meshes, not shaders.

**A scene can already bring a pipeline after the engine starts.** `ContentFeatureHost` takes a
content feature "for content that arrives with a scene rather than with the `RenderPlan`". Both
backends implement it, and `TerrainContentSystem` is the working model: scene component, system,
`host.attachContentFeature(...)`, and a failure is logged and leaves the frame alone. A content
feature declares a `PipelineSpec`, so the same shader serves both backends. Its limits, from the
source: a whole-frame pass with its own geometry and its own uniform block; no scene-depth sampling
(the set layout is fixed when the engine starts); one feature per pipeline spec; textures uploaded
once; and a pipeline compile at attach, which `PipelineRegistry` puts at 1 to 50 ms.

**A draw cannot name a pipeline.** `GpuDrawRequest` holds a mesh, a material and flags (`cullMode`,
`alphaMode`, `transparent`, `additive`) but no pipeline. The engines route by vertex format and
variant. `ShaderReplacement` swaps the code inside an existing pipeline, requires identical bindings,
and exists on Vulkan only. So a shader on an arbitrary mesh is a backend change, not a loader: that
is Stage 2.

**ASL is the right base but is not data yet.** It is an expression and statement model (`AslExpr`,
`AslStatement`) with no `@Serializable` type. It also has `for` loops whose bounds are
**expressions** (`AslForI32`, `AslForU32`), functions and storage bindings, so emitting ASL as it
stands would let a document carry a data-dependent loop. The document model is therefore a
restricted subset with its own serialization, converted to ASL for emission.

**Why not raw WGSL.** Nothing in the compile path bounds run time. Vulkan goes through naga to SPIR-V
(which checks types and bindings), and WebGPU hands the WGSL to the browser. A shader that never
finishes can hang a GPU or reset a driver; the browser and the OS add watchdogs but they differ per
platform, and a published project reaches every person who plays it. Core controlling the emitted
WGSL, from a document it has validated, is what makes a limit enforceable. Raw WGSL can come later
for a local, trusted project, behind an explicit choice.

**Live preview already works without #214.** The issue records that "the Studio web build previews
shaders by re-attaching the content feature". Detach and attach recompiles the pipeline (the 1 to 50
ms above) and needs no in-place swap. #214 would remove that hitch; it is not needed.

## Left out until needed

| Left out | Add it when |
|---|---|
| **Stage 2**, a shader on any scene mesh | A reference project needs a custom look on a character or a prop. Needs a pipeline field on `GpuDrawRequest` and both backends honouring it, plus per-material layouts |
| Raw WGSL in a project | A local, trusted project asks for it, with an explicit opt-in |
| Scene-depth sampling by a document shader | The engine can fix set layouts after start, or the plan opts in at start |
| Per-frame texture replacement | Content features gain a way to rewrite their group |
| Compute shaders and storage buffers | Not for published projects |
| A visual graph editor | The document is already a graph, so this is a Studio issue (awake-studio#320), not Core |

## To confirm in the first implementation

- A content feature built by a system can animate: the system writes parameters, including a
  clock, each frame, and the feature copies them into its `UniformBlock` when it records. The frame
  context carries a frame index; whether it carries a clock was not checked.
- The loop cap and the node and texture-sample caps: the right numbers are measured, not chosen here.

## What this changes in the work

- awakekt/awake#456 splits into three issues:
  1. the shader document model, validator and emission (`awake:asset:shader-document`);
  2. the `awake:scene:shader` component, binding and attach system, the loader hook and a Vulkan and
     WebGPU parity test (Stage 1);
  3. the docs: the shaders guide stops saying shaders are Kotlin only.
  Stage 2 is not scheduled.
- awake-studio#320 (shader authoring in Studio) no longer lists #214 and #217 as prerequisites. It
  depends on the document format and the Stage 1 system.
- awake-studio#319 (the reference projects): the custom shader in each must be Stage 1 shaped, with
  its own geometry or a full-screen effect (a sky, a water plane, a screen effect), unless Stage 2 is
  scheduled.
