# One plan an app declares, one seam that runs it

Status: **done.** Written 2026-08-24.

Raised as an instinct while reading `VulkanEngine`: *this should be a config, hand-authored
outside vulkan and webgpu.* Correct, and it had a name already — the render-pipeline skill's
"a one-backend capability is fine; a one-backend **decision** is a defect."

## What was actually wrong

Both engines took the same four backend-neutral parameters — `primary`, `scenePipelines`,
`contentFeatures`, `depthPrePassShaderSet`. That **was** the config. Only the *declaration* was
duplicated: `samples/studio` wrote it in `StudioVulkanBootstrap.kt` (`appMain`) and
`StudioWebGpuBootstrap.kt` (`wasmJsMain`), source sets that cannot see each other.

They had already drifted. The WebGPU list was missing the skinned pipeline, and the reason
survived only as a comment in a file nothing could compare against.

**One small thing forced it.** `ScenePipeline` stores a whole `ShaderSet` and defers the backend
half to `toRequests(stages)`. `skyboxContentFeature` took the selector at *construction*, so
`ShaderSet::vulkan` had to appear at the call site — and `commonMain` cannot write that without
also writing `ShaderSet::webGpu` somewhere else. One eager resolution, two bootstraps.

## What the block that raised this actually is

```kotlin
val renderer = Renderer(graphicsDevice, swapchainManager, pipelines = pipelineTable(), ...)
```

This is **resolution, not declaration, and it stays.** Every argument is either a GPU object that
cannot exist before a window (`graphicsDevice`, `swapchainManager`, `transferContext`) or the plan
resolved against one (`pipelineTable()`, `renderFeatures`, `uiShaderPairs()`, `depthPrePass`).
Nothing in it is authorable outside a backend, and there is exactly one per backend, which is
correct. Even `MAX_FRAMES_IN_FLIGHT` differs for real — 2 on Vulkan, 1 on WebGPU, where the
browser owns the swapchain.

The lesson worth keeping: *a duplicated declaration is a defect; a duplicated resolution is the
job.* Check which one you are looking at before deleting it.

## Steps

**1 — `RenderPlan`. Done (`2130782a5`).** Holds the four fields; both engines take one; Studio
declares one in `commonMain`. Engines went from five constructor parameters to two.

`ContentFeatureSource` defers the backend half the way `ScenePipeline` already did, and the engine
supplies a `RenderBackend` it already knows. That is the whole unlock.

Lives in `asset:shaders`, **not** `engine:app` — the backends must see it and `engine:app` depends
on them. Needs `asset:shaders -> render:passes` for `ContentFeature`; acyclic, and `shader-pack`
already does the same.

Also retired `expect class AwakeApplication`, which had zero call sites and **had not compiled**
since the engines moved to `ScenePipeline`. Nothing depends on `:awake:engine:app`, so nothing
built it and nothing reported it — the same failure mode as a test source set that stops
compiling.

**2 — capabilities. Done (`e8ae30102`).** `WebGpuEngine` used to `require` a null depth-pre-pass
shader set: it *rejected* a plan rather than narrowing one, which is what forced Studio to keep a
second hand-narrowed copy. `RenderCapabilities` lets a backend declare its own gaps and
`narrowedTo` reports every omission by name.

The trade-off, decided: **loud dropping**. Silent under-rendering is worse — ship a skinned mesh,
watch nothing draw, no signal. An unsupported *primary* throws instead, since every mesh falls
back to it and narrowing it away renders nothing at all.

**2b — the UI shader list. Done.** `UiShaderSet<T>` was already shared in `render:contract`
(Vulkan aliases `<ShaderPair>`, WebGPU `<ByteArray>`) and the identities were already
single-sourced in `EngineShaderSets` (`da2b427da`) — but each backend still spelled out the same
four slots to fill it. `uiShaderSet { load(it) }` makes *which four* one decision and leaves each
backend only the payload: `uiShaderSet { loadShaderPair(it) }` and
`uiShaderSet { readResourceBytes(it.wgsl()) }`. A fifth UI shader is now impossible to add to one
backend and forget in the other.

**3 — the last per-target line. Investigated, then dropped. It cannot work, and would not help.**

Both hosts demand a *concrete* engine:

```kotlin
runVulkanDesktopGame(applicationFactory: (AwakeAppLifecycle) -> VulkanEngine)
launchWebGpuGame(applicationFactory: () -> WebGpuEngine)
```

An `expect fun awakeApplication(...): GraphicsEngine` cannot be passed to either without also
widening both signatures — and that buys nothing, because `Main.kt` is per-target *anyway*:
different `fun main()`, different launcher, different host API. The per-target seam already
exists and is irreducible.

What is left in each sample is one factory per backend FAMILY, not per target: `appMain` covers
android + iOS + desktop, `wasmJsMain` covers web. Two files for two families, each about ten
lines, both handing over the same `RenderPlan`. That is the floor.

The `expect`/`actual` reversal question is therefore moot. It never had to be answered.

**4 — `:awake:engine:app` deleted.** Step 1 emptied it; step 3 was the only job it could have
had, and step 3 is not a job. Nothing depended on it, its `README` described work the backends
actually do (`VulkanDesktopHost`, `WebGpuCanvasHost`), and it had sat broken and unbuilt.

## Not doing

- ~~Collapsing `uiShaderPairs()`.~~ **Done** — see step 2b above.
- **Moving pipeline/renderer construction out of the backends.** See the section above — that is
  resolution, and it belongs there.

## Verification bar

```bash
./gradlew :awake:asset:shaders:desktopTest :awake:backend:vulkan:desktopTest \
  :awake:backend:webgpu:desktopTest :awake:backend:vulkan:detekt :awake:backend:webgpu:detekt \
  :samples:studio:compileKotlinDesktop :samples:studio:compileKotlinWasmJs \
  :samples:ui-showcase:compileKotlinDesktop :samples:ui-showcase:compileKotlinWasmJs \
  :awake:backend:vulkan:verifyBackendLayering :awake:backend:webgpu:verifyBackendLayering
```

The lesson that outlived the module: `:awake:engine:app` sat broken with nothing reporting it,
because no module depended on it. **A module with no consumers is only as compiled as someone's
task list says it is** -- the same failure as a test source set that stops compiling and takes
its whole suite out of every run. Deleting it removed the instance, not the class of problem.
