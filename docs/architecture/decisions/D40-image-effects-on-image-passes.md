# D40: image effects on image passes

Status: accepted (2026-10-19)

## Decision

Multi-pass 2D and post-processing effects (backdrop blur, layer blur, bloom, colour grading,
tonemapping, vignette, and custom fullscreen or region filters) are authored once in
`:awake:engine:render:effects` and lowered into a backend-neutral `ImagePassChain` defined in
`:awake:engine:render:contract`. Neither `:awake:engine:render:contract` nor either graphics
backend (`:awake:backend:vulkan`, `:awake:backend:webgpu`) names any specific effect.

1. **The hardware contract owns only generic pass execution.** `:awake:engine:render:contract`
   declares `ImagePassChain`, `ImagePassStep`, `ImagePassTargetSpec`, `ImagePassSource`,
   `ImagePassDestination`, `ImagePassShaderProgram`, `ImagePassPlacement`, `ImagePassTargetContext`,
   `GpuDeviceClass`, and `EffectQuality`. It knows how a chain declares transient render targets,
   samples `DestinationSnapshot` or a `TransientTarget`, binds packed uniform floats, and draws a
   quad into either a transient target or the resumed `ActiveTarget`.
2. **Each backend implements one generic executor.** `VulkanImagePassExecutor` and
   `WebGpuImagePassExecutor` each execute arbitrary `ImagePassChain` instances. When a UI or post
   pass encounters an `ImagePassPlacement` at `primitiveIndex`, the backend ends its current render
   pass, snapshots the active attachment when requested (`DestinationSnapshot`), acquires pooled
   transient targets keyed by `(id, width, height, format)`, runs each `ImagePassStep`, and resumes
   the parent pass with `loadOp = LOAD` so subsequent primitives draw on top in paint order.
3. **Effects live above the RHI in `:awake:engine:render:effects`.** Every effect owns its ASL
   shaders (`ShaderProgram`), uniform layouts (`UniformLayout` / `UniformWriter`), quality-tier
   table (`EffectQuality` → downscale divisor, pass count, or `Off` fallback), and `ImagePassChain`
   builder, plus a deterministic CPU reference implementation for unit testing.
4. **Build-time layering guards enforce the boundary.** `VerifyBackendLayeringTask` and
   `verifyRenderContractBoundary` fail the build if `render:contract`, `backend:vulkan`, or
   `backend:webgpu` main sources import `com.awakekt.awake.render.effects.*` or declare effect
   identifiers (`Blur`, `Backdrop`, `Kawase`, `Bloom`, `Glass`, `Tonemap`, `Grade`, `Fxaa`).

## Why

Before this decision, adding a single effect (such as backdrop blur) tempted authors to add
effect-named primitives, shaders, pipelines, and executors directly to `core:graphics2d`,
`render:contract`, `backend:vulkan`, and `backend:webgpu`. That violated D31 ("neither side may
name content") and multiplied every new effect across both backends (~1,100+ lines of duplicated
Vulkan and WebGPU boilerplate per effect). With one generic `ImagePassChain` executor per backend,
a new image effect is a single shared file in `:awake:engine:render:effects` and zero backend edits.
