# Awake vs libGDX, Kool, Bevy, Godot, Unity, Unreal — architecture comparison

Status: research summary, not a design doc. Sourced from public docs/READMEs for every
non-Awake engine — treat as directionally accurate, not source-verified the way this
session's Awake-side findings are (those cite real `file:line`).

## Matrix

| Engine | Language | Shader authoring | Render pass architecture | App/lifecycle pattern | Threading | Scene/entity model | Vulkan |
|---|---|---|---|---|---|---|---|
| **Awake** | Kotlin (KMP) | Hand-written GLSL+WGSL per backend, per shader (`GameShaderSet.vulkan`/`.webGpu`) | Ordered `RenderFeature` list (planned), 3-4 passes | Constructor-injected Mediator (`GameApplication`/`Game`/`Renderer`) | Single-threaded frame loop (deliberate) | ECS (`awake:ecs`) | Yes |
| **libGDX** | Java | Hand-written GLSL per platform | None formalized — direct GL calls | Static globals (`Gdx.graphics`/`Gdx.input`/...) + `ApplicationListener` | Single-threaded | Scene2D (optional, thin) | **No** |
| **Kool** | Kotlin (KMP) | **KSL** — one Kotlin DSL generates GLSL+WGSL | Passes exist (deferred, SSR) but structure undocumented publicly | Undocumented publicly | Decoupled game-logic/render threads | Scene graph | Yes |
| **Bevy** | Rust | WGSL only (via wgpu) | **Explicit `RenderGraph`**, dependency-based, dual-World Extract stage | Plugin-based `App` builder | Pipelined — render world computed one frame behind main world | **ECS-native** (this *is* the engine) | Yes (via wgpu) |
| **Godot 4** | C++/GDScript/C# | Own shading language, transpiled internally | Backend-specific (`RendererCompositorRD` for Vulkan/D3D12/Metal) | `RenderingServer` — full client/server split, backend-agnostic API | Optional dedicated render thread | Node/scene tree (not ECS) | Yes |
| **Unity** | C# | HLSL (+ visual Shader Graph) | **`RenderGraph` API** (URP, default since 6000.3) — auto pass-merge via texture-usage analysis | `RenderPipeline`/`ScriptableRenderPass`, C# scripted | Engine-managed, not exposed | GameObject/Component (ECS/DOTS optional) | Yes |
| **Unreal** | C++ | HLSL, cross-compiled per platform via RHI | **RDG** (Render Dependency Graph) — setup/compile/execute, auto barriers+aliasing | `RHI` abstracts D3D/Vulkan/Metal; engine-managed app shell | Game thread + render thread + RHI thread | Actor/Component | Yes |

## Reading the matrix

- **Shader authoring:** Awake and libGDX are the only two hand-authoring per-backend source.
  Kool (KSL), Bevy (WGSL-only via wgpu), Godot (own language), Unity (HLSL), Unreal (HLSL)
  all use one source of truth per shader. **This is Awake's clearest, most isolated gap.**
- **Render pass architecture:** Bevy, Unity, Unreal all converged on an explicit dependency
  graph (`RenderGraph`/RDG) with automatic barrier/lifetime management — industry-standard
  since ~2021 per Unreal's own docs. Awake's planned ordered list (this session's
  `RenderFeature` draft) is the right size for today's pass count, not this shape yet.
- **App/lifecycle pattern:** Awake's constructor-injected Mediator is closest to Godot's
  server/client split in spirit (explicit, no hidden statics) and strictly better than
  libGDX's global-singleton `Gdx.*` approach.
- **ECS:** Bevy *is* an ECS with a renderer built on top, closest architectural sibling to
  Awake's own `awake:ecs` + scene runtime. Worth reading Bevy's Extract-stage pattern (copy
  only what the renderer needs from the main `World` into a separate render `World` each
  frame) if Awake's ECS-to-renderer boundary ever needs hardening — not proposed here, just
  noted as the one engine whose scene model rhymes with Awake's.
- **Vulkan:** Only libGDX lacks it. Not a differentiator among the rest.

## libGDX's subsystem split vs Awake's — detailed (added 2026-08-21)

Unlike the rest of this doc, the libGDX side here **is** source-verified (`libgdx/libgdx` on
GitHub, `gdx/src/com/badlogic/gdx/`), because it was used to decide a real Awake change.

**libGDX's shape.** `interface Application` exposes six subsystems as getters —
`getApplicationListener()`, `getGraphics()`, `getAudio()`, `getInput()`, `getFiles()`,
`getNet()` — each an interface with per-backend implementations. A static `Gdx` class mirrors
them as global fields (`Gdx.app`, `Gdx.graphics`, `Gdx.input`, `Gdx.audio`, `Gdx.files`,
`Gdx.net`), populated by whichever backend booted. `interface ApplicationListener` is the
user's game: `create()`, `resize(w, h)`, `render()`, `pause()`, `resume()`, `dispose()`.
Per-frame state lives on `Graphics`: `getWidth()`, `getHeight()`, `getDeltaTime()`,
`getFramesPerSecond()`, and a real runtime knob, `setForegroundFPS(int)`. Configuration is a
constructor argument to the backend (`Lwjgl3ApplicationConfiguration`,
`AndroidApplicationConfiguration`), never a global.

**Mapping onto Awake:**

| Concern | libGDX | Awake | Verdict |
|---|---|---|---|
| Game callbacks | `ApplicationListener` | `AppLifecycle` (`ready`/`update`/`resize`/`pause`/`resume`/`dispose`) | Near 1:1 — same idea, same six-ish hooks |
| App shell | `Application` | `WindowLifecycle` + `GraphicsEngine` | Same role |
| Input access | `Gdx.input` static | Registered service, reached via `AwakeAppLifecycle.input`, constructor-injected into `GraphicsEngine` | **Awake simpler** — no global service locator |
| Frame timing | `Graphics.getDeltaTime()`/`getFramesPerSecond()` | `FrameStats` — injected object, unit-tested, adds p50/p95/p99 | **Awake ahead** |
| Frame-rate config | `Graphics.setForegroundFPS(int)` | `TARGET_FPS` constant | libGDX has the real knob; Awake deliberately has none until needed |
| App config | `Lwjgl3ApplicationConfiguration` ctor arg | `WindowConfig` inside `AppSpec` | Same pattern — both avoid globals |
| Audio / Net / Files | Three always-present interfaces | none / none / `Resource` | Awake ships less surface, on purpose |

**How Awake is simplified, concretely:**

1. **No service locator.** libGDX's `Gdx.*` statics are convenient but mean one implicit global
   app per process, awkward tests, and no way to run two sessions. Awake reaches the same
   subsystems through constructor injection plus a service map scoped to a single
   `AwakeAppLifecycle`.
2. **Two types where libGDX has two, but split differently.** libGDX separates
   `Application` (shell, queried by the game) from `ApplicationListener` (game, called by the
   shell). Awake's game side never sees the shell at all — `GraphicsEngine` calls
   `AppLifecycle`, and nothing calls back the other way.
3. **Fewer subsystems.** Six interfaces exist in libGDX whether a game uses them or not.
4. **Config is an immutable spec**, built once by the DSL, rather than a mutable per-backend
   configuration object.

**The structural lesson for [the core split](../reference/module-architecture.md).**
libGDX does **not** ship `graphics`/`input`/`audio`/`files`/`net` as separate artifacts — all
six are interfaces inside the single `gdx` core jar, and the split is by *backend*
(`gdx-backend-lwjgl3`, `gdx-backend-android`, `gdx-backend-gwt`). A far larger engine keeps
every subsystem in one core artifact and partitions on the backend axis instead. That is direct
support for that document's §3.2 decision not to break the 559-line rump of `awake:core` into
four subsystem modules — and Awake already splits on the same axis libGDX does
(`awake:backend:vulkan`, `awake:backend:webgpu`).

**What this comparison changed (2026-08-21).** Awake pushed delta and viewport size but *pulled*
input — `SceneAppLifecycleRuntime.update` did a `requireService(Input::class)` lookup every
frame. `AppLifecycle.update` now takes one `AppFrame` (delta, viewport size, and this frame's
`InputSnapshot`), so every per-frame value arrives the same way and future ones cost a field
rather than a signature change across five implementors. The distinction that matters: the
long-lived `Input` **accumulator** stays an injected service — platform bridges write into it
outside the frame, and the scene runtime still writes `textInputFocused` back through it — while
the per-frame **snapshot** is pushed. Subsystem vs. frame value, not push vs. pull everywhere.

**One thing this comparison found in Awake's own code:** `core.utils.Time`
(`Fps`/`Delta`/`FpsString`) and `core.utils.Frame` (`width`/`height`) are Awake's vestigial
version of `Gdx.graphics`'s per-frame state — and they are **write-only**. The `FrameLoop`
actuals write `Time.Delta`/`Time.FpsString`, Android's `VulkanView` writes `Frame.width`/
`height`, and *nothing in the repo reads any of them*; `FrameStats` superseded them. `Time`'s
own `@Suppress` comment claims they are "public API with call sites across the samples", which
is no longer true. Tracked as an open item in the core split doc.

## What we could improve (real, actionable)

1. **Shader duplication** — the one gap every modern comparison engine has already solved
   differently (KSL, WGSL-only, own language, HLSL). A Kotlin shader DSL generating
   SPIR-V/WGSL from one definition would close this. Compiler-shaped work, not a refactor —
   deserves its own scoped design pass with `awake-render-backend-engineer` (and
   `awake-asset-pipeline-engineer` for the shared uniform-layout contract side). Not started
   here.
2. **Render pass scaling ceiling** — today's planned ordered `RenderFeature` list won't scale
   to Bevy/Unity/Unreal's pass count without becoming a real dependency graph. Not needed at
   Awake's current scope (3-4 passes) — noted so it isn't a surprise later, not a call to
   build a render graph preemptively (same YAGNI reasoning as the `RenderFeature`
   sealed-hierarchy rollback earlier this session).

## What's already planned (this session's drafts — no new work implied here)

- **`RenderFeature` Strategy + `RenderFrameContext` port** —
  [2026-08-19-render-feature-strategy-plan.md](2026-08-19-render-feature-strategy-plan.md)
- **`GameShaderSetSpec` open registry** —
  [2026-08-19-application-layer-shape-options.md](2026-08-19-application-layer-shape-options.md)
- **`Game`/`GameApplication`/`GameShaderSet` → `AppBehavior`/`AppRuntime`/`ShaderSet` naming** —
  [2026-08-19-game-naming-generalization-plan.md](2026-08-19-game-naming-generalization-plan.md)
- **`awake-render-pipeline` skill** —
  [skills/awake-render-pipeline/SKILL.md](../../skills/awake-render-pipeline/SKILL.md)

## Caveat

Every non-Awake row is sourced from public docs/READMEs, not source-level review — treat as
directionally reliable, not load-bearing for an implementation decision without checking the
actual source first if any of this becomes real work.
