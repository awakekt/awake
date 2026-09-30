# Render hardware interface

<p class="awake-lede">The backend-neutral boundary between engine code and the GPU: which module holds which part, the types on each side, and the backends that implement it.</p>

The full design, including the capability tiers and what may cross the boundary, is in
[render-hardware-interface.md](https://github.com/awakekt/awake/blob/main/docs/reference/render-hardware-interface.md).

## Layers

| Layer | Module | Package | Holds |
| --- | --- | --- | --- |
| Render contract | `com.awakekt.awake.engine.render:contract` | `com.awakekt.awake.render.*` (`renderer`, `pipeline`, `command`, `mesh`, `material`, `texture`) | The hardware vocabulary: resources, pipelines, vertex layouts, resolved pass input. No scene types. |
| Render passes | `com.awakekt.awake.engine.render:passes` | `com.awakekt.awake.render.passes` | Shared render features and the preparation, batching and packing that turn scene input into resolved passes. |
| 2D render passes | `com.awakekt.awake.engine.render:passes2d` | `com.awakekt.awake.render.passes2d` | Backend-neutral 2D pass logic: run coalescing, mesh upload, command recording. |
| Shader contract | `com.awakekt.awake.asset:shaders` | `com.awakekt.awake.asset.shaders` | What an app renders with: `RenderPlan`, `ScenePipeline`, `ShaderSet`, `RenderCapabilities`. |
| Backend | `com.awakekt.awake.backend:vulkan`, `com.awakekt.awake.backend:webgpu` | — | Translates the contract into Vulkan or WebGPU calls. |
| Render testing | `com.awakekt.awake.engine.render:testing` | `com.awakekt.awake.render.testing` | Headless render sessions, pixel baselines, frame capture. For tests, not production code. |

## Contract types

| Type | Package | What it is |
| --- | --- | --- |
| `GpuDevice` | `render.pipeline` | The RHI facade: `clipSpace`, `createMesh`, `createMaterial`, `createRenderTarget`, `waitIdle`, `capability(kind)`, `destroy`. |
| `Renderer` | `render.renderer` | A `GpuDevice` that also draws: `draw(GpuPassInput)`, `renderToTexture`, `presentWithoutScene`, `drawUi`, pixel readback, `clearColor`, `wireframe`. |
| `GpuPassInput` | `render.command` | One frame's resolved input: view-projection, camera, pre-passes, resolved opaque and transparent draws, post-passes, pass uniforms. No camera, light or scene types. |
| `Mesh`, `Material` | `render.mesh`, `render.material` | GPU mesh and material handles. The caller that creates one destroys it. |
| `RenderTarget` | `render.texture` | An offscreen color and depth destination. |
| `PipelineSpec` | `render.pipeline` | The backend-neutral description a pipeline is compiled from. |
| `PipelineKey` | `render.pipeline` | What a pipeline is for: `Primary`, `Format`, `Instanced`, `InstancedFormat`, `SkinnedInstanced`, `Particle`, `Content`. |
| `GpuCapability` | `render.pipeline` | An optional capability a backend may offer; `capability(kind)` returns `null` when it does not. |
| `ClipSpace` | `core.math` (`core:math`) | The clip-space convention a backend's projection matrices target. |

## Backends

| Backend | Module | Version | Targets | Clip space |
| --- | --- | --- | --- | --- |
| Vulkan | `com.awakekt.awake.backend:vulkan` | `{{ awake_vulkan_version }}`, its own release train | Desktop JVM, Android, iOS (MoltenVK) | Reported by `GpuDevice.clipSpace` |
| WebGPU | `com.awakekt.awake.backend:webgpu` | Snapshot only | Web (`wasmJs`); desktop JVM over wgpu-native | Reported by `GpuDevice.clipSpace` |

## Plans and backends

| Item | Where | Behavior |
| --- | --- | --- |
| `RenderPlan` | `asset:shaders` | The pipelines an app renders with, written once for every backend. |
| `RenderCapabilities` | `asset:shaders` | What one backend can run: `backend`, `depthPrePass`, `supportsPipeline`. |
| `RenderPlan.narrowedTo(capabilities)` | `asset:shaders` | Drops what the backend cannot run and reports each drop. Throws if the primary pipeline is unsupported. |

## See also

- [Modules](modules.md)
- [Releases and compatibility](releases.md)
- [Glossary](glossary.md)
