# 2026-08-17: awake:core module split proposal

> **Superseded 2026-08-22 by
> [docs/reference/module-architecture.md](../reference/module-architecture.md)**, which is now
> the source of truth for module decisions.
>
> This document surveyed `awake:core` only and concluded `core:math` was "the only split with
> real mass". Later measurement found a coupling problem it could not have seen:
> `engine:render:contract` carries a `ui-core` `api` edge for two files, so every mesh consumer
> inherits three UI modules -- which is why `asset:gltf` hand-copies four vertex layouts. That
> extraction now ranks ahead of `core:math`.
>
> Kept for the reasoning behind the **withdrawn** splits (`core:input`, `core:time`), which the
> reference carries forward unchanged.


Status: Active architecture guideline. Revised 2026-08-21 against real source — the original
graph and module list had drifted from the code (details in §4). This document defines the
modular roadmap for splitting `awake:core` packages into focused leaf modules.

**Rule for Agents & Contributors**:
Whenever performing refactors, adding new foundation features, or extracting code from `awake:core` (`math`, `input`, `time`), **always follow the proposed dependency graph and module shapes defined in this document**.
When a module described here is built, create its dedicated `README.md` (matching `geometry` and `animation`) and update this document in the same commit.

---

## 1. Modules Already Extracted / Built

The following modules have been successfully extracted from `awake:core`:
- [`:awake:core:geometry`](../../awake/core/geometry/README.md) — portable mesh geometry algorithms (`MeshSimplifier`, Garland-Heckbert quadric error, `NormalizedInt`). Zero project dependencies — self-contained, does not consume `core.math`.
- [`:awake:core:animation`](../../awake/core/animation/README.md) — skeletal animation runtime (`Skeleton`, `Skin`, `AnimationClip`, `AnimationPose`, `AnimationCrossfade`). Depends on `:awake:core`.
- Subsystems partitioned outside `core/`:
  - **Physics**: Implemented as pure Kotlin contract in `:awake:physics:api` and native bridge in `:awake:backend:jolt`.
  - **Assets**: Partitioned under `:awake:asset:gltf`, `:awake:asset:mesh-optimizer`, and `:awake:asset:shaders`.
  - **Rendering**: Partitioned under `:awake:engine:render:contract`, `:awake:engine:render:passes`, and `:awake:backend:*`.
  - **Application/lifecycle**: Partitioned under `:awake:engine:platform` (contract types), `:awake:engine:app` (backend-bound hosts), and `:awake:engine:bootstrap` (authoring DSLs).

---

## 2. What is actually left in `awake:core`

Measured 2026-08-21, `commonMain` only (each package also carries small per-platform
`actual`s where noted):

| Package | Files | Lines | Contents | Platform source sets |
|---|---:|---:|---|---|
| `core.math` | 13 | 2047 | `Vec2/3/4`, `Mat3/4`, `Quaternion`, `Camera`, `Ray`, `Frustum`, `MathUtils` | — |
| `core.input` | 1 | 188 | `Input` | 3 Android bridges |
| `core.utils` | 2 | 21 | `AwakeLogger`, `Resource` | `Resource` ×4 |
| `core.application` | 2 | ~70 | `FrameLoop`, `FixedTimestepLoop` | `FrameLoop` ×3 |
| `core.graphics` | 3 | 68 | `Bitmap`, `BitmapRgba8`, `WindowLifecycle` | `Bitmap` ×4 |
| `core.colors` | 1 | 69 | `Color` | — |
| **Total** | **22** | **2510** | | |

Two facts drive the revised plan below:

- **`math` is 82% of the module.** The other five packages total 416 lines.
- **27 modules depend on `:awake:core`** — effectively the whole repo. Every split multiplies
  `build.gradle.kts` edits across all of them.

---

## 3. Revised plan — one real extraction, not five

### 3.1 `awake:core:math` — the only split with real mass (do this one)

- **Scope**: Pure linear algebra and geometric primitives. Zero dependencies.
- **Types**: `Vec2`/`Vec3`/`Vec4`, `Mat3`/`Mat4`, `Quaternion`, `Camera`, `Ray`, `Frustum`, `MathUtils`.
- **Current Home**: `io.github.awakelab.awake.core.math` inside `awake:core`.
- **Target**: `:awake:core:math` subproject.
- **Why this one is worth it**: it is the only package a consumer genuinely wants *without* the
  rest. A leaf module needing `Vec3` currently also drags in `GameLoop`, `Input`, `Bitmap`, and
  the Android input bridges. `:awake:physics:api`, `:awake:engine:render:contract`, and the UI
  modules are the real beneficiaries.
- **Read [`skills/awake-core-math/SKILL.md`](../../skills/awake-core-math/SKILL.md) first** — the
  mutating-vs-allocating naming contract (`normalize()` mutates, `normalized()` allocates) must
  survive the move unchanged.

### 3.2 The rump `awake:core` keeps the rest — do not split further

`input` (188) + `utils` (21) + `application` (70) + `graphics` (68) + `colors` (69) = 416 lines.
Splitting these into four modules averages ~105 lines each while forcing dependency edits across
27 consumers. The original plan's `core:input` and `core:time` are **withdrawn** on that basis:
the module boundary would cost more than the coupling it removes.

libGDX is direct precedent: a far larger engine keeps all six of its subsystems (`Graphics`,
`Input`, `Audio`, `Files`, `Net`, `Application`) in one core artifact and splits by *backend*
instead — the axis Awake already splits on. See
[the engine comparison](../audits/2026-08-19-engine-comparison-libgdx-kool.md).

Revisit only on a real trigger, not on principle:
- `core.input` grows a second backend (gamepad, XR controllers) with its own platform bridges.
- `core.application`'s loop gains scheduling policy beyond `FixedTimestepLoop`.

### 3.3 `core.graphics.WindowLifecycle` — a real boundary question, tracked separately

`WindowLifecycle` (renamed from `WindowApplication` on 2026-08-21) sits in `core.graphics` but
its only consumers are `:awake:engine:platform` (`GraphicsEngine.kt`, and `VulkanView.kt`, whose
Android package is `core.graphics` precisely to match it). It is a windowing/lifecycle contract,
not a core primitive — it belongs with `AppLifecycle`/`AppSpec` in `:awake:engine:platform`.

Moving it is small (one file, two consumers) but it is a **platform-layer** decision, not a
core-split one. Scoped out of this document deliberately; do it as part of any
`:awake:engine:platform` cleanup instead.

### 3.4 Withdrawn from the original proposal

- **`core:diagnostics`** — the proposed types (`Profiler`, `MemoryTracker`, `AssertionEngine`)
  **do not exist anywhere in the repo**. Creating a module for them would be new feature work
  mislabeled as an extraction. Real profiling already lives in `awake:ui:testing`'s
  `FrameSpans`/`TimingBaseline` harness and `awake:ecs:benchmark`; extend those instead.
- **`core:audio`** — no audio code exists. Not a split; revisit when the audio milestone starts.

---

### 3.5 Naming cleanup applied 2026-08-21

Audited every type left in `awake:core`; most names were accurate and kept unchanged
(`FixedTimestepLoop`, `Input`, `Color`, `Bitmap`, `BitmapRgba8`, `AwakeLogger`, `Resource`).
Changed:

- **`GameLoop` → `FrameLoop`** (plus `Desktop`/`Android`/`IOS` actuals). Stale `Game` prefix left
  over from the 2026-08-20 `Game`→`App` sweep; the type is a per-frame ticker with nothing
  game-specific in it. Real consumers: `VulkanDesktopHost` and Android's `VulkanView`. WebGPU
  does not use it — `WebGpuCanvasHost` drives frames via `window.requestAnimationFrame`.
- **`WindowApplication` → `WindowLifecycle`** (see §3.3). "Application" had come to read as a
  sibling of `AwakeApplication`, which it is not.
- **`FrameLoop.startLoop` → `tick`.** The method runs exactly one frame; the caller owns the
  repeat (`VulkanView` wraps it in `while (running) { }`, and the iOS actual's own doc says
  "single tick per call, caller owns the repeat loop"). The old name stated the opposite.
- **`EngineConfig`/`EngineConfigHolder` deleted**, replaced by a `TARGET_FPS = 60` constant in
  `FrameLoop.kt`. Nothing had written the holder since the OpenGL backend was removed, and each
  `FrameLoop` actual is an `object` that read `config.fps` into a `val` — captured once at
  object init, so a later write would have been silently ignored. It was a knob that could not
  be turned. Give `fps` a real home on `WindowConfig` when something actually needs a second
  value; `EngineConfig.ups` went with it, having never been read at all (and its "update per
  second" comment described a rate while `1.0 / 30.0` is a duration).

- **`WindowLifecycle.input`'s `// TODO: move to AppLifecycle` resolved — by rejecting it.**
  `AppLifecycle` is the game-authored behavior interface; making games *supply* an `Input`
  inverts ownership, since a game consumes input and the engine owns it. The TODO's real
  motivation was an unchecked `(appLifecycle as AwakeAppLifecycle)` downcast in
  `GraphicsEngine`, guarded only by a doc comment claiming it was "guaranteed". Fixed by
  narrowing the parameter type to `AwakeAppLifecycle` through the engine plumbing
  (`GraphicsEngine`, `VulkanEngine`, `WebGpuEngine`, `AwakeApplication` expect + both actuals)
  — every real caller already passed one, so no call site changed and the compiler now enforces
  what the comment asserted.

Still open, deliberately not changed:

- **`core.utils.Time` and `core.utils.Frame` are write-only.** The `FrameLoop` actuals write
  `Time.Delta`/`Time.FpsString` and Android's `VulkanView` writes `Frame.width`/`Frame.height`,
  but nothing in the repo reads any of them — `FrameStats` (`:awake:engine:platform`, unit
  tested, with p50/p95/p99) superseded them. `Time.Fps` is never even written. The `@Suppress`
  comment on `Time` justifying its capitalised property names as "public API with call sites
  across the samples" is stale; there are no call sites. Same class of dead global state as the
  deleted `EngineConfigHolder`. Delete both plus their write sites, unless something outside
  this repo reads them.
- **`ManualTimeController` deleted** (2026-08-21). Zero consumers repo-wide; it was a
  scrub-the-clock helper for stepping an animation by hand. The replacement belongs in the scene
  layer, built when `samples:studio` actually grows a timeline — a real editor timeline needs
  scrubbing, playback rate and keyframes together, which is a different shape than this class
  guessed at. (`PixelProbe` had the same zero-consumer problem and moved to `:awake:ui:testing`
  alongside `PixelBaseline`.)

## 4. Revision log (2026-08-21)

Corrected against real source; the original text is preserved above only where it still holds.

- **Dependency graph removed.** It showed `core:geometry` depending on `core:math`;
  `core:geometry` has zero project dependencies and does not reference `core.math`. It also
  showed a single `awake:engine` node, which became `:awake:engine:platform` /
  `:awake:engine:app` / `:awake:engine:bootstrap` on 2026-08-20.
- **`core:input` / `core:time` withdrawn** — 188 and 93 lines respectively against 27 consumers.
- **`core:diagnostics` withdrawn** — its three named types do not exist.
- **`colors`, `graphics`, `utils` documented** — three of six real packages were absent from the
  original module list entirely.
- **`core.application` naming** — the original targeted a module named `core:time`; the real
  package is `core.application` and contains `EngineConfig` alongside the loop types, so the
  proposed name never matched its contents.
