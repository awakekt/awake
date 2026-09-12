# D34: render plugin lifecycle

Status: accepted

Render plugins are scene/render-pipeline features. They are not alternate renderers and do not
own a backend submission loop.

```kotlin
interface RenderFeature3D {
    fun collect(world: World, context: RenderFeatureContext3D): RenderContribution
}
```

`collect` is the public boundary. A feature may internally split its work into extraction,
culling, sorting, packing, and buffer preparation, but those steps remain private to the feature.
The returned contribution contains only backend-neutral render data such as draw calls, lights,
environment values, or feature-owned buffers.

The coordinator owns the frame protocol:

1. establish the frame and camera snapshot;
2. collect built-in and authored features in deterministic order;
3. compile the merged scene into `GpuPassInput`;
4. submit one frame to the selected backend; and
5. present or recover the surface.

Plugins therefore must not:

- accept or retain `Renderer`;
- call `beginFrame`, `draw`, `endFrame`, or `present`;
- own render passes or command submission;
- expose Vulkan, WebGPU, or native handles in their public API; or
- depend on mutable `extract`/`prepare`/`cleanup` callbacks managed by the coordinator.

The broader lifecycle proposed in the initial plugin sketch remains valid as an implementation
technique inside a complex feature, but making it the engine contract would hide ordering and
resource ownership. Keeping one contribution boundary also makes parallel preparation possible:
a feature can return immutable or frame-owned data without changing the backend or coordinator
protocol.

Lighting, particles, culling, and future authored systems can use this seam. Hardware-specific
pipeline selection and command recording remain below the render-pipeline/RHI boundary.
