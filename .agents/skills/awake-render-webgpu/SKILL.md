---
name: awake-render-webgpu
description: >
  Rules and invariants for Awake's WebGPU rendering backend (`awake:backend:webgpu` and wgpu4k/Dawn integration).
  Read before touching WebGPU pipelines, WGSL/SPIR-V shader bindings, WASM browser canvas resizing, or buffer upload paths.
  Trigger keywords - WebGPU, wgpu4k, Dawn, WGSL, GPUTexture, GPUBuffer, GPURenderPipeline, WASM canvas, canvas resize.
---

# WebGPU Backend Engineering in Awake

Awake's WebGPU backend (`:awake:backend:webgpu`) targets cross-platform GPU execution on Web (WasmJs) and native desktop via `wgpu4k`/Dawn.

Read [docs/architecture.md](../../docs/architecture.md), [docs/mvp-plan.md](../../docs/mvp-plan.md), and [skills/awake-render-pipeline/SKILL.md](../awake-render-pipeline/SKILL.md) first.

## Hardware only

This backend knows pipelines, buffers, textures, samplers and command recording. It must never
know what a skybox or a shadow *is* -- that is content, and content lives in the shared layer or
`samples/<game>/`. `verifyBackendLayering` enforces this on `check`; its scene-import exemption
ledger is empty. Before adding a content-shaped class here, read
`docs/reference/render-extensibility.md`.

## 1. Multiplatform Contract (WASM + Native)

- WebGPU backend uses `wgpu4k` bindings for unified WASM browser and native desktop execution.
- Web targets run on top of HTML5 Canvas (`wasmJs`); desktop targets use native window handles.
- **Rule**: Keep all WebGPU adapter, device, and pipeline initialization code backend-agnostic. Platform-specific surface acquisition stays isolated in platform adapters.

## 2. Canvas Sizing & High-DPI Handling

- In browser environments, the HTML5 canvas CSS size (`clientWidth`/`clientHeight`) differs from its backing buffer size (`width`/`height`).
- Always scale backing buffer dimensions by `window.devicePixelRatio` to maintain crisp rendering on Retina / high-DPI screens.
- Re-configure `GPUCanvasContext` on canvas resize events before beginning a new render pass.

## 3. Shader & Uniform Layout Agreement

- WebGPU and Vulkan backends share common uniform layout definitions in `awake:asset:shaders` (`LitShadowUniformLayout`, `TexturedUniformLayout`).
- **Rule**: Any uniform layout change must be verified against both backends to ensure binding indices and byte alignment match perfectly.
- UI shader source is authored in `:awake:asset:shader-pack` ASL and emitted as WGSL. Do not
  hand-edit `src/wasmJsMain/resources/assets/shader/webgpu/ui_*.wgsl`; update the ASL definition,
  run `./gradlew :awake:asset:shader-pack:generateAslShaders`, and run the ASL drift tests.
  Generated WGSL is also the input to naga for Vulkan, keeping the two backends on one shader
  definition.
- Every resource-path shader declaration must provide its `bindingsByGroup` map explicitly. The
  WebGPU factory rejects missing metadata, and pipeline code must never infer group usage by
  searching WGSL text for `@group(...)`.

## 4. Buffer Upload & Lifetime Management

- Use staging buffers or `queue.writeBuffer` with explicit byte offsets and sizes.
- Ensure all created `GPUTexture` and `GPUBuffer` objects have matching `.destroy()` calls when their owning scene or material is disposed.

## 5. Backend boundary guard

`verifyBackendLayering` (in
`build-logic/src/main/kotlin/com.awakekt.awake.plugin.backend-layering.gradle.kts`) bans
`RenderDrawCommand`, `SceneLight`, `Lens`, and `EnvironmentUniforms` imports from every
`awake:backend:webgpu` source file. The ledger is intentionally empty. Scene lowering happens
in `render:passes`; WebGPU receives resolved `GpuPassInput` packets and hardware handles.

Any new scene import is a build failure and must be moved above the HAL rather than added to an
exemption.

See `docs/reference/render-hardware-interface.md` § HAL vs Render Graph for the full
vocabulary boundary and `awake-render-pipeline/SKILL.md` §0.5 for the two-layer model.
