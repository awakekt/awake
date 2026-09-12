# Engine Diagnostics: Inventory, Gaps, and Plan (2026-08-30)

**Date:** 2026-08-30
**Status:** Proposed
**Scope:** 2D and 3D runtime diagnostics across Vulkan (desktop/Android/iOS) and WebGPU (wasmJs)

---

## 1. Executive summary

The diagnostic *spine* mostly exists: frame timing, phase attribution, world-space debug lines,
headless pixel capture, and a render-thread-safe remote command channel are all already written.
What is missing is the **numbers**: there are no draw-call counters, no GPU timing, no validation
layers, no debug object names, and no 2D batching visibility.

The recommendation is not a new subsystem per surface. It is **one frame record, three
consumers** — an in-app overlay, a headless CLI/JSON channel, and a Perfetto-compatible trace
export — all reading the same struct.

Estimated total: **11-17 days** (was 9-13 before the 2026-08-30 re-verification). Phases 0 and 2
together are **4-7 days**, not the ~3 first claimed: both need new Vulkan JNI bindings that the
first pass of this document did not check for.

---

## 2. What exists today

| Layer | Have | Where |
| :--- | :--- | :--- |
| CPU frame timing | fps, frameTime, p50/p95/p99 | `awake/engine/platform/.../core/FrameStats.kt` |
| Frame phase split | uiBuild / uiWait / uiStage / simRender, F2 toggle | `awake/scene/runtime/.../SceneAppFrame.kt`, `SceneAppLifecycleRuntime.kt:116` |
| 3D visual debug | frustum, bounds, occluders, lights, shadow box | `awake/scene/rendering/.../systems/DebugVisualizationSystem.kt` |
| Debug line rendering | both backends | `awake/engine/render/contract/.../DebugGeometry.kt` |
| Headless capture | `FrameCapture`, `PixelMap`, `PixelBaseline`, `TimingBaseline` | `awake/engine/render/testing/` |
| Remote control channel | generic WebSocket command queue, render-thread-correct | `samples/server/.../DebugControlServer.kt` |
| Zero-cost counter idiom | `enabled` flag, free when off | `awake/compose/ui/.../layout/LayoutStats.kt` |
| CLI shell | `awake verify`, `awake ui ...` | `scripts/awake_ui.py` |

---

## 3. Gaps

**Re-verified 2026-08-30.** The first version of this table was written from truncated `grep`
output and one row was flatly wrong — it claimed validation layers were not wired when
`GraphicsDevice.setupDebugMessenger()` had been calling them all along. Every row below has since
been re-checked with unfiltered searches over each real source set, and each carries the check that
backs it. Three rows changed.

| Gap | Status | Evidence |
| :--- | :--- | :--- |
| **No GPU timing** | ✅ confirmed, **worse than stated** | Zero `QueryPool`/`WriteTimestamp` hits across `backend/vulkan/src`, `engine`, `scene`, `samples`; zero `timestamp`/`QuerySet` in `backend/webgpu/src`. **`vkCreateQueryPool` is not in the bindings either**, so Vulkan GPU timing needs new JNI first — same blocker as debug-utils, and the plan below did not account for it. |
| ~~No validation layers~~ | ❌ **WRONG — retracted** | `setupDebugMessenger()` (`GraphicsDevice.kt:126`) is called from both `create()` and `createHeadless()`, with a `failOnValidationError` path headless tests rely on. The real defect was different and is fixed in `6901f79f6`: `createInstance` enabled **every layer installed on the host**, not the validation layer. |
| **No `VK_EXT_debug_utils` labels/names** | ✅ confirmed, **worse than stated** | Zero uses in engine code — and zero `DebugUtilsObjectName`/`DebugUtilsLabel` entry points in `bindings/.../Vulkan.kt`. Only the struct types are generated, so this needs new JNI bindings (5 touch points × 3 functions), not a call site. |
| **No draw-call/triangle counters** | ✅ confirmed | `RenderSystem3D.lastOccludedCount` (`RenderSystem3D.kt:55`) is the only one, and it is asserted by `RenderSystemTest`. The `triangleCount` in WebGPU's `Mesh.kt:117` is a local for line-index generation, not a statistic. |
| **2D is blind** | ⚠️ **overstated** | `DrawMeshUploader` already computes `quadRunCount` / `roundedQuadRunCount` / `glyphRunCount` (`DrawMeshUploader.kt:31-33`) as mesh-pool indices, then discards them; `staged.size` is the total run count. The numbers exist at the right place and are unpublished — publishing them is a few lines, not new instrumentation. Batch *breaks* and clip splits genuinely are uncounted. |
| **No GPU memory tracking** | ✅ confirmed | Zero hits for byte/resource accounting across both backends and `engine`. |
| **No physics debug draw** | ✅ confirmed | Zero hits across `backend/jolt`, `scene/physics`, `physics`. |
| **No trace export** | ✅ confirmed | Zero trace-format hits; the only `"ph"` matches are shader locals in `AslTexturedShader.kt` / `AslShadowShaders.kt`. |
| **No visible perf overlay** | ✅ confirmed | `phaseStats()` has exactly one non-test reader: `StudioShell.kt:91`. No `*PerfOverlay*` file exists (a stale detekt baseline entry still names a deleted `AppUiPerfOverlay.kt`). |

Two smaller defects, both re-verified:

- `SceneAppLifecycleRuntime` keeps its own `frameTimesMs` `ArrayDeque` (`:100`) and mean
  (`:142`) rather than using `FrameStats`, whose only two importers are tests. Consolidating gives
  p50/p95/p99 for free.
- `SceneFrameStats.textCacheHits` / `textCacheMisses` are passed `0` at their sole construction
  site (`SceneAppFrame.kt:57-58`) and again in `SceneLocals.kt:38`. Nothing ever writes a non-zero
  value, so `textCacheHitRatePercent` is permanently 0. Wire them or delete them.

---

## 4. Recommended architecture

```
FrameDiagnostics (one struct, per frame, zero-cost when disabled)
   |
   +-- overlay   F2 HUD, in-app, covers 2D and 3D
   +-- CLI/JSON  WebSocket -> `awake diagnose`, headless, agent-drivable, CI-gateable
   +-- trace     Chrome JSON Trace Event Format -> ui.perfetto.dev
```

Three separate diagnostic systems is the failure mode to avoid. One record, three renderings of it.

### Module placement

`:awake:engine:render:contract`, package `render.diagnostics`.

That is the lowest module both backends, `passes`, `passes2d`, `scene:rendering`, and
`engine:platform` already depend on — so counters can be written from every increment site with
no new module and no dependency cycle. `:awake:ecs` deliberately does not depend on it; ECS-level
timing stays in `:awake:scene:runtime`, which already owns phase timing.

The shape follows `LayoutStats`: an `object` with an `enabled` flag, free when off.

---

## 5. Phases

### Phase 0 — object naming (**re-estimated: 1-2 days, not 0.5**)

The original 0.5-day figure assumed the debug-utils entry points existed. They do not, and item 1
below was already done.

1. ~~Wire validation layers and a debug messenger.~~ **Already wired.** The real defect was
   `createInstance` enabling every host-installed layer; fixed in `6901f79f6`.
2. **Vulkan object names and pass labels.** Blocked on new JNI bindings:
   `vkSetDebugUtilsObjectNameEXT`, `vkCmdBeginDebugUtilsLabelEXT`, `vkCmdEndDebugUtilsLabelEXT`,
   each needing an `expect`, three `actual`s (android/desktop/ios), and a JNI C++ implementation.
   That is the 1-2 days.
3. **WebGPU labels.** `label: String = ""` exists on all 21 wgpu4k descriptor types, so this is
   ~1 hour of mechanical edits across 72 sites and needs no binding work.

**Open design question, unresolved.** Engine-owned objects (swapchain depth, UI pipelines, the
per-draw uniform pool) must be named by the engine — a game has no handle to them. But objects a
game creates via `GpuDevice.createMesh` / `createMaterial` / `createRenderTarget` can only be
usefully named *by the game*: an engine-derived `mesh-vertex-buffer` repeated 400 times carries no
information, where `tree_oak_lod0` carries all of it. `PipelineKey.Content(name)` is the existing
precedent for the game-authored half. None of those three creation APIs currently accepts a name,
so **the game-authored half is a contract change, not a backend change**, and it is the half that
actually makes a capture readable. Settle this before writing either half.

This phase goes first because **every external tool in section 6 is near-useless without it.**
RenderDoc, Android GPU Inspector, the Xcode Metal debugger, and WebGPU Inspector all key off these
names.

### Phase 1 — counters (~1-2 days)

```kotlin
object FrameDiagnostics {
    var enabled = false

    // 3D
    var drawCalls = 0
    var instances = 0
    var triangles = 0
    var frustumCulled = 0
    var occlusionCulled = 0
    var pipelineBinds = 0
    var descriptorBinds = 0

    // 2D
    var drawRuns = 0
    var batchBreaks = 0
    var quads = 0
    var clipSplits = 0
    var glyphCacheMisses = 0

    // memory
    var bufferBytes = 0L
    var textureBytes = 0L

    fun reset() { /* ... */ }
}
```

Increment sites: `RenderSystem3D` (culling — fold in the existing `lastOccludedCount`),
`RendererDraw3D`, `DrawRunCoalescer` (2D runs and breaks), and buffer/texture creation in both
backends.

`batchBreaks` is the highest-value 2D number. It is the difference between a UI that draws in 4
calls and one that draws in 400, and nothing reports it today.

### Phase 2 — GPU time (**re-estimated: 3-5 days, not 2-3**)

This is the phase that changes decisions.

- **Vulkan:** `VkQueryPool(VK_QUERY_TYPE_TIMESTAMP)`, `vkCmdWriteTimestamp` at pass boundaries,
  read back deferred by 2 frames so the CPU never stalls. Scale results by
  `VkPhysicalDeviceLimits.timestampPeriod`, already exposed in bindings
  (`VkPhysicalDeviceLimits.kt:113`). **`vkCreateQueryPool`, `vkCmdWriteTimestamp`,
  `vkCmdResetQueryPool` and `vkGetQueryPoolResults` are not bound** — the re-verification in
  section 3 found only `timestampPeriod` present. Four more JNI entry points, on top of Phase 0's
  three, is where the extra 1-2 days goes. Doing both phases' bindings in one pass is cheaper than
  doing them separately.
- **WebGPU:** the `timestamp-query` feature, shipped non-experimental since Chrome 121. Two
  caveats to design around: results are **quantized to 100µs** unless
  `chrome://flags/#enable-webgpu-developer-features` is enabled, and the GPU counter can reset,
  producing negative deltas — clamp them.

Without this, `simRenderMs` measures submit time, not GPU work. That is exactly the number that
sends people optimizing the wrong half of the frame.

### Phase 3 — the three faces (~3-4 days)

**Overlay.** Extend the existing F2 path into a real HUD: phase bars, a 120-frame frame-time graph
with a 16.6ms reference line, the Phase 1 counters, and a CPU-vs-GPU verdict. Drawn with Awake's
own UI, so it works on every target including wasm. Reuse `FrameStats` percentiles rather than the
duplicate deque.

**CLI.** Promote `WebSocketDebugTransport` out of `samples/server` into an engine module, then:

```bash
awake diagnose --frames 300 --json
```

Launches headless (or attaches to a running app), drives N frames, and dumps counters,
percentiles, and GPU milliseconds as JSON. Deterministic, no screenshot timing, CI-gateable, and
drivable by an agent. The transport is already written and already render-thread-correct — this is
mostly plumbing plus a subcommand.

**Trace export.** Emit Chrome JSON Trace Event Format (`B`/`E` slices, `C` counter tracks) and open
it in `ui.perfetto.dev`. Roughly 80 lines, no dependency, pure `commonMain`, works on wasm.
Perfetto treats the JSON format as a legacy best-effort path but renders slices, counters, and
flows correctly. Gotcha: Perfetto asserts on a missing trailing `]`, so always close the array.

### Phase 4 — correctness views (~2-3 days)

`Renderer` already has `wireframe` and `debugMode` booleans. Promote `debugMode` to an enum:
**overdraw heatmap, normals, UV checker, mip level, LOD tint, batch color, 2D clip rects, 2D dirty
rects.** Overdraw and batch-color find real 2D/UI bugs fastest.

Extend `WorldDebugSettings` with `showPhysicsBodies` (Jolt has no debug draw at all) and
`showNavGrid` — navgrid path smoothing landed in `40c2a4f` with no way to see its output.

---

## 6. External tools: use, do not rebuild

| Target | Tool | Notes |
| :--- | :--- | :--- |
| Desktop Vulkan | **RenderDoc** | Frame capture, pipeline state, mesh view. Needs Phase 0 labels to be readable |
| Desktop JVM CPU/alloc | **async-profiler** | Already used here — see the `Pool.kt` comment |
| Android Vulkan | **Android GPU Inspector** | System trace plus frame profiler |
| iOS / MoltenVK | **Xcode Metal debugger** | MoltenVK maps debug-utils labels to Metal labels |
| wasm WebGPU | **WebGPU Inspector** (browser extension) | Closest thing to RenderDoc for the web: live object list, frame capture, CPU-vs-GPU-bound verdict, live shader edit. Chrome and Firefox |
| wasm WebGPU, deep | **RenderDoc + Dawn hooks** | Chrome 144+, D3D12 backend, Windows only — narrow but real |
| wasm CPU | **Chrome DevTools + Perfetto** | Pairs with the Phase 3 trace export |

**Skip Tracy.** C++ client only, FFI-bindings-or-nothing, version-locked custom wire protocol, no
wasm story. Perfetto JSON delivers most of the value for a fraction of the cost on this target
matrix.

**Skip building capture/replay.** RenderDoc and WebGPU Inspector already cover it.

---

## 7. Suggested order

Revised after the 2026-08-30 re-verification. The old order opened with Phase 0 on the belief it
was the cheapest thing here; it is not.

1. **Settle Phase 0's engine-vs-game naming question** (section 5). It decides whether the work is
   a backend edit or a contract change, and everything else in Phase 0 depends on the answer.
2. **Phase 1 counters, plus WebGPU labels.** The only work in this plan needing no new bindings.
   Phase 1's 2D half is now cheaper than stated — `DrawMeshUploader` already computes the run
   counts and throws them away.
3. **One Vulkan JNI pass covering Phase 0 and Phase 2 together** — seven entry points in a single
   change rather than three then four.
4. **Phase 3 CLI**, which turns the counters into the agent and CI loop.
5. Overlay, trace export, debug views.

---

## 8. Open question: where the CLI lands

`awake diagnose` should not become the eleventh scattered entry point. Its placement depends on the
tooling-consolidation decision tracked separately — see
`docs/audits/2026-08-30-tooling-consolidation-plan.md`.

## References

- [What's New in WebGPU (Chrome 121)](https://developer.chrome.com/blog/new-in-webgpu-121)
- [Intent to Ship: WebGPU timestamp queries](https://groups.google.com/a/chromium.org/g/blink-dev/c/dtYJ0MQYMlU/m/_ixc9555BgAJ)
- [Perfetto: visualizing external trace formats](https://perfetto.dev/docs/getting-started/other-formats)
- [Perfetto trailing-bracket issue](https://github.com/google/perfetto/issues/926)
- [WebGPU Inspector](https://github.com/brendan-duncan/webgpu_inspector)
- [Profiling WebGPU with RenderDoc](https://toji.dev/webgpu-profiling/renderdoc.html)
- [Tracy Profiler releases](https://github.com/wolfpld/tracy/releases)
