# D33: render feature plugin boundary

Status: accepted

`RenderSystem3D` may be composed from feature plugins, but a plugin is a scene/render-pipeline
producer rather than a second renderer. The coordinator owns frame ordering and the single backend
submission point.

```kotlin
interface RenderFeature3D {
    fun collect(world: World, context: RenderFrameContext3D): RenderContribution
}

data class RenderContribution(
    val draws: List<DrawCall> = emptyList(),
    val lights: SceneLight? = null,
    val environment: EnvironmentUniforms? = null,
)
```

Feature implementations may read ECS data, cull, sort, and pack backend-neutral buffers. They must
not call `Renderer.draw`, own a render pass, or expose Vulkan/WebGPU types. `RenderSystem3D` merges
their contributions, invokes `ScenePassCompiler`, and submits one `GpuPassInput` to the renderer.

The current `SceneLightingCompiler`, `SceneCullingCompiler`, `SceneDrawCollector`, and
`SceneParticleCompiler` remain the built-in extraction seams. `RenderFeature3D` is now the public
extension seam for additional authored features; those collaborators stay coordinator-owned until
their contribution shapes stabilize. A lifecycle with mutable `extract`/`prepare`/`cleanup` state
remains intentionally avoided because it obscures dependencies and complicates parallel recording.

This keeps parallel work possible: a future feature can return immutable or frame-owned data from
`collect`, while the coordinator retains deterministic pass ordering and backend submission.
