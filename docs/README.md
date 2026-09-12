# Documentation Sitemap & System Status

> [!TIP]
> **AI Agent Navigation Directives**:
> 1. **Never recursively scan the `docs/` folder.**
> 2. Read this sitemap table to find the exact document you need.
> 3. Only open the specific single file relevant to your current task.

**Last Self-Healed**: `2026-09-09` | **Total Tracked Docs**: `201`

| Category | Document | Status | 1-Line Summary |
| :--- | :--- | :--- | :--- |
| **Strategy & Roadmap** | [`MMORPG Engine Roadmap`](mmorpg-roadmap.md) | `In Progress` | Companion to [mvp-plan.md](./mvp-plan.md). That document covers the near-term "spinning |
| **Architecture** | [`Architecture`](architecture/architecture.md) | `Stable` | Awake is a Kotlin Multiplatform game engine library: a Vulkan-first cross-platform renderer |
| **Architecture** | [`D10 — jni-binding-generator de-risk findings (2026-07-07)`](architecture/decisions/D10-codegen-derisk-findings.md) | `Stable` | Phase 1a of [mvp-plan.md](../mvp-plan.md) called for a week-one de-risk: run |
| **Architecture** | [`D11: JNI Native Implementation Boundary`](architecture/decisions/D11-jni-native-implementation-boundary.md) | `Stable` | `jni-binding-generator` owns the JNI boundary only: exported JNI names, parameter |
| **Architecture** | [`D28: Open-World Subsystems — Awake vs Starter-Kit Boundary`](architecture/decisions/D28-open-world-framework-boundary.md) | `Stable` | The open-world RFC's subsystems are split by the |
| **Architecture** | [`D29: Physics — When Awake Would Write Its Own Engine`](architecture/decisions/D29-physics-own-engine-exit-criteria.md) | `Stable` | Awake builds on Jolt ([D5](../reference/decision-log.md)) and does **not** hold "write our own |
| **Architecture** | [`D30: Math — Which Numeric Primitive Variants Earn a Type`](architecture/decisions/D30-math-numeric-primitive-variants.md) | `Stable` | `:awake:core:math` does **not** carry a variant of every vector/quaternion/matrix type for |
| **Architecture** | [`D31: Net — What the Transport Module Owns, and What Stays in the Game`](architecture/decisions/D31-net-api-extraction.md) | `Stable` | `:awake:net:api` exists and carries exactly two things: the **transport port** and the |
| **Architecture** | [`D32 — The editor is a library, not Studio's UI (2026-08-31)`](architecture/decisions/D32-editor-is-a-library.md) | `Stable` | **Status: Accepted.** `awake:editor` and its adapters are a published library whose consumers are |
| **Audits** | [`Shadcn visual parity audit — 2026-08-15`](audits/2026-08-15-shadcn-visual-parity-audit.md) | `Active` | Live audit of `samples:ui-showcase` (wasmJs, WebGPU, dpr=2) against official shadcn/ui |
| **Audits** | [`UI refactor plan — 2026-08-17`](audits/2026-08-17-ui-refactor-vs-recreate-audit.md) | `Active` | **Verdict: refactor in place. Do not recreate.** Recreate only 3 small units |
| **Audits** | [`Application layer — full shape survey and options`](audits/2026-08-19-application-layer-shape-options.md) | `Active` | Status: draft, not implemented. Scope widened from |
| **Audits** | [`ASL — Kotlin DSL generating WGSL, feeding the existing naga pipeline`](audits/2026-08-19-asl-single-source-shader-sketch.md) | `Active` | Implementation plan: [2026-08-23-asl-procedural-shader-plan.md](../tasks/2026-08-23-asl-procedural-shader-plan.md) |
| **Audits** | [`Awake vs libGDX, Kool, Bevy, Godot, Unity, Unreal — architecture comparison`](audits/2026-08-19-engine-comparison-libgdx-kool.md) | `Active` | Status: research summary, not a design doc. Sourced from public docs/READMEs for every |
| **Audits** | [`"Game" naming — generalize for non-game consumers (video/animation apps)`](audits/2026-08-19-game-naming-generalization-plan.md) | `Active` | Status: completed. AppLifecycle, AppSpecDsl, AppLifecycleRuntime naming generalized across bootstrap and runtime. |
| **Audits** | [`RenderFeature Strategy refactor — plan draft`](audits/2026-08-19-render-feature-strategy-plan.md) | `Active` | Status: **implemented** (commits e26c7ca/466e1b3/d5e738f0 on `dev/improvement`). Kept as the |
| **Audits** | [`Vulkan god-class de-bloat + Vulkan/WebGPU shared-logic extraction — plan`](audits/2026-08-19-vulkan-godclass-and-shared-render-logic-plan.md) | `Active` | Status: landed. Part A step 2 (offscreen extraction): `8d5abeccd` — `Renderer.kt` now 407 |
| **Audits** | [`Vulkan/WebGPU common backend — phased plan`](audits/2026-08-19-vulkan-webgpu-common-backend-plan.md) | `Active` | Status: landed, all phases. Phase 0 (`CommandRecorder`/`MaterialBinding`/`PipelineHandle`): |
| **Audits** | [`DSL convenience sugar — mesh generation + camera/light one-liners`](audits/2026-08-20-dsl-convenience-sugar-plan.md) | `Active` | Status: landed (`6ed450ac5`). Scoped from comparing Awake's real DSL (`game { }`/`GameDsl`, |
| **Audits** | [`Entity scope config — folding `EntityModifier` into `entity { }`'s block`](audits/2026-08-20-entity-scope-config-plan.md) | `Active` | Status: landed (`2b014ff25`). Follows the [Modifier -> EntityModifier rename](2026-08-20-dsl-convenience-sugar-plan.md) |
| **Audits** | [`Game and Scene management — clean shape`](audits/2026-08-20-game-and-scene-management-plan.md) | `Active` | Status: landed. SceneManager created in awake:scene:runtime, integrated into SceneAppLifecycleRuntime, and consumed by S... |
| **Audits** | [`UI / shadcn Parity Audit and Remediation Plan`](audits/2026-08-20-ui-shadcn-parity-report-and-plan.md) | `Active` | Date: 2026-08-20, refreshed 2026-08-24. |
| **Audits** | [`Vulkan & WebGPU Architecture Audit: God Class Decoupling, Duplication Elimination, Utilization & Commonization`](audits/2026-08-20-vulkan-webgpu-godclass-and-optimization-audit.md) | `Active` | Date: 2026-08-20 |
| **Audits** | [`Vulkan & WebGPU KMP Commonization Audit`](audits/2026-08-20-vulkan-webgpu-uncommonized-audit.md) | `Active` | Date: 2026-08-20 |
| **Audits** | [`Jetpack Compose vs Awake UI: Layout & Modifier Parity Plan`](audits/2026-08-21-compose-layout-modifier-parity-plan.md) | `Active` | Awake UI is an immediate-mode UI framework designed for high-performance 60–120 FPS games and tooling. While it mimics J... |
| **Audits** | [`2026-08-23: shadcn tokens — keep what exists, take one idea from the proposal`](audits/2026-08-23-shadcn-tokens-existing-vs-proposed.md) | `Active` | **Question asked:** is the existing shadcn skill set and parity tooling stale enough to recreate, or |
| **Audits** | [`REUSE baseline — 2026-08-25`](audits/2026-08-25-reuse-baseline.md) | `Active` | `reuse 6.2.0 lint` establishes this repository's starting point for making REUSE a required CI |
| **Audits** | [`Architecture & Quality Audit Report (2026-08-28)`](audits/2026-08-28-architecture-audit.md) | `Active` | **Date:** 2026-08-28 |
| **Audits** | [`Detekt real findings, 2026-08-30`](audits/2026-08-30-detekt-real-findings.md) | `Active` | Detekt was failing 18 modules. The findings were two unrelated kinds sharing one red build: three |
| **Audits** | [`Engine Diagnostics: Inventory, Gaps, and Plan (2026-08-30)`](audits/2026-08-30-engine-diagnostics-plan.md) | `Active` | **Date:** 2026-08-30 |
| **Audits** | [`Scene editor — production-readiness audit`](audits/2026-08-30-scene-editor-production-readiness-audit.md) | `Active` | Audited 2026-08-30 against `awake:editor`, `awake:editor:scene`, their only host |
| **Audits** | [`Tooling Consolidation, Duplicate Audit, and 2D/3D Decoupling (2026-08-30)`](audits/2026-08-30-tooling-consolidation-plan.md) | `Active` | **Date:** 2026-08-30 |
| **Audits** | [`Button group: intrinsic cross-axis sizing + per-corner radii`](audits/button-group-intrinsics-handoff.md) | `Active` | Handoff for a fresh session. Reference implementation is the user's own |
| **Active Task** | [`Implementation Plan: HAL vs Render Graph — Decoupling `render:contract``](tasks/2026-09-09-hal-vs-render-graph-phase-1-plan.md) | `Active` | `render:contract` is Awake Engine's **Hardware Abstraction Layer (HAL)**. Over time, it accumulated |
| **Active Task** | [`Awake Editor Plan — rebuild, do not port`](tasks/editor/01-compose-editor-plan-todo.md) | `Active` | Drafted 2026-08-22. Revised 2026-08-25. Status: Stages 0 and 1 foundations complete; |
| **Active Task** | [``Vec3f` / `Vec3d` / `Vec3i` — precision variants`](tasks/math/01-vector-precision-variants-todo.md) | `Active` | Drafted 2026-08-22. Status: todo. Gated on a real consumer per variant; see Triggers. |
| **Reference** | [`Agent Catalog`](reference/agent-catalog.md) | `Active` | This document is the canonical source for Awake's repo-local agent roster, naming convention, |
| **Reference** | [`Agent Routing`](reference/agent-routing.md) | `Active` | This page shows how Awake routes real work between repo-local agents across the **Engine Framework Suite** and the **Gam... |
| **Reference** | [`Agent Starter Pack`](reference/agent-starter-pack.md) | `Active` | This page turns Awake's cross-agent setup into a reusable downstream starter pack. |
| **Reference** | [`AI Collaboration`](reference/ai-collaboration.md) | `Active` | This document is the canonical source for how Awake organizes project guidance for agents. |
| **Reference** | [`API Layering`](reference/api-layering.md) | `Active` | Awake separates public API into three layers: **core**, **helpers**, and **sugar**. This |
| **Reference** | [`Backend commonisation: where the duplication actually is`](reference/backend-commonisation.md) | `Active` | Vulkan and WebGPU are hand-authored side by side. This is the measurement of how much, where, |
| **Reference** | [`Jetpack Compose Guidance: Animation`](reference/compose-animation-guidance.md) | `Active` | A sibling doc rather than a section of `compose-modifier-layout-guidance.md` because animation |
| **Reference** | [`01 — Layout`](reference/compose-engine/01-layout.md) | `Active` | Supersedes the layout half of `docs/reference/compose-modifier-layout-guidance.md`. |
| **Reference** | [`02 — Modifier`](reference/compose-engine/02-modifier.md) | `Active` | Supersedes the modifier half of `docs/reference/compose-modifier-layout-guidance.md`. |
| **Reference** | [`03 — CompositionLocal`](reference/compose-engine/03-composition-locals.md) | `Active` | **Stage 1 blocker.** Two of these reach *measurement*, so text cannot be measured without them. |
| **Reference** | [`04 — Styling and theme`](reference/compose-engine/04-styling-theme.md) | `Active` | **Decision: `Style` and `Modifier.styleable` do not exist in `:awake:compose:ui`.** They belong in |
| **Reference** | [`05 — Animation`](reference/compose-engine/05-animation.md) | `Active` | Frame-clock driven, no coroutines. A node subscribes on attach and unsubscribes on detach. |
| **Reference** | [`06 — Focus and text input`](reference/compose-engine/06-focus-text-input.md) | `Active` | Stage 1 needs the focus *source* (a node can be focusable, and hit-testing can move focus). Caret, |
| **Reference** | [`07 — Overlay layering`](reference/compose-engine/07-overlay-layering.md) | `Active` | **This page gates Stage 1's tree shape.** Build Row/Column/Box first and bolt overlays on after, and |
| **Reference** | [`08 — Lazy lists`](reference/compose-engine/08-lazy-lists.md) | `Active` | Stage 3. Recorded now because the current design exists for a reason that this engine removes. |
| **Reference** | [`09 — Testing harness`](reference/compose-engine/09-testing-harness.md) | `Active` | **Stage 1 deliverable.** Without it, nothing in Stage 1 can be visually verified. |
| **Reference** | [`10 — graphicsLayer`](reference/compose-engine/10-graphics-layer.md) | `Active` | This page exists to stop the engine over-promising. "graphicsLayer is real now" will be true of the |
| **Reference** | [`11 — Refinement register`](reference/compose-engine/11-refinements.md) | `Active` | Every deliberate divergence from Compose, with the evidence that justifies it. |
| **Reference** | [`12 — Gestures`](reference/compose-engine/12-gestures.md) | `Active` | Click alone is not enough for Stage 1: `ResizablePanelGroup`, `Slider` and `RangeSlider` all need |
| **Reference** | [`13 — Semantics`](reference/compose-engine/13-semantics.md) | `Active` | `UiFrameOutput.semantics` keeps its shape, so `ui-testing` and the parity tooling keep working. What |
| **Reference** | [`14 — Density, resize, layout direction`](reference/compose-engine/14-density-resize.md) | `Active` | `Dp` and `Sp` are the real types declared in `:awake:core:math2d` |
| **Reference** | [`15 — Parity matrix against Compose`](reference/compose-engine/15-compose-parity.md) | `Active` | What this engine mimics, what it leaves out, and where it genuinely improves. **Every "Compose does" |
| **Reference** | [`16 — What visibly changes when a screen moves`](reference/compose-engine/16-migration-deltas.md) | `Active` | Stage 3 moves ~180 call sites from `ui-core` to this engine. Five things will look different on |
| **Reference** | [`17 — Modifier parity`](reference/compose-engine/17-modifier-parity.md) | `Active` | **Goal: every stable Compose `Modifier` extension, or a recorded reason it does not apply here.** |
| **Reference** | [`Jetpack Compose Guidance: Modifier & Layout`](reference/compose-modifier-layout-guidance.md) | `Active` | This guide is retained for detailed modifier examples. The numbered Compose Engine guides are |
| **Reference** | [`Decision Log`](reference/decision-log.md) | `Active` | Historical "why we chose X" rationale for Awake's engine architecture, extracted from |
| **Reference** | [`Developer Docs`](reference/developer-docs.md) | `Active` | Awake now has two documentation lanes, because one format is not enough for an engine: |
| **Reference** | [`Ecs Scene Simplification`](reference/ecs-scene-simplification.md) | `Active` | Instead of using a generic mutableListOf<Any>() which creates extra garbage collection allocations, your EntityModifier ... |
| **Reference** | [`Engineering Change Summaries`](reference/engineering-change-summaries.md) | `Active` | Use this format when handing off non-trivial Awake changes: renderer fixes, ECS/runtime |
| **Reference** | [`Framework and Game Boundary`](reference/framework-game-boundary.md) | `Active` | Awake is a reusable engine/framework. A future MMORPG is a separate consumer repository. |
| **Reference** | [`Application & Game DSL`](reference/game-dsl.md) | `Active` | This page is the quick guide for Awake's root application and game shell DSL. |
| **Reference** | [`Game Structure`](reference/game-structure.md) | `Active` | This document is the canonical source for how Awake organizes state and folders when |
| **Reference** | [`2D rendering glossary`](reference/glossary/2d.md) | `Active` | UI rendering terms — how a widget becomes pixels. See [3d.md](3d.md) for the scene pass and |
| **Reference** | [`3D rendering glossary`](reference/glossary/3d.md) | `Active` | Scene rendering terms. See [2d.md](2d.md) for the UI pass and |
| **Reference** | [`Physics glossary`](reference/glossary/physics.md) | `Active` | Terms used by Awake's portable collision API and its Jolt backend. These describe collision, |
| **Reference** | [`UI Testing Dictionary`](reference/glossary/ui-testing.md) | `Active` | Plain-English meanings for terms used in Awake UI code and tests. This page is for people who |
| **Reference** | [`KMP Architecture Stance`](reference/kmp-architecture-stance.md) | `Active` | Awake is a Kotlin Multiplatform project that deliberately skips most of what people mean by |
| **Reference** | [`Library API Boundaries`](reference/library-api-boundaries.md) | `Active` | This document is the canonical rule for how Awake splits public library surface from DSL |
| **Reference** | [`Maven coordinates`](reference/maven-coordinates.md) | `Active` | The frozen publication surface: what an external consumer resolves, under what coordinates, and |
| **Reference** | [`Mirror Map: Awake UI DSL vs. Jetpack Compose`](reference/mirror-map.md) | `Active` | Awake's retained Compose-shaped UI API (`Modifier`, `Row`/`Column`/`Box`, state hooks, |
| **Reference** | [`Module Architecture`](reference/module-architecture.md) | `Active` | How Awake's 44 modules are grouped, why, and how to decide where a new one goes. |
| **Reference** | [`Engine Performance Matrix & Scorecard`](reference/performance-matrix.md) | `Active` | High-level dashboard of Awake's performance baselines, micro-benchmark suites, algorithmic complexity guarantees, and co... |
| **Reference** | [`Releasing`](reference/releasing.md) | `Active` | Awake has no released version yet. Everything lands under `## [Unreleased]` in |
| **Reference** | [`Render Backend Audit: Vulkan vs. WebGPU`](reference/render-backend-audit.md) | `Active` | Audits `awake/backend/vulkan`'s `Renderer.kt` against `awake/backend/webgpu`'s `Renderer.kt` |
| **Reference** | [`Render extensibility convention`](reference/render-extensibility.md) | `Active` | Awake is a library/framework: a consumer must be able to build their own rendering content |
| **Reference** | [`The Render Hardware Interface (`GpuDevice`)`](reference/render-hardware-interface.md) | `Active` | Awake's rendering boundary: one backend-neutral API that engine code targets, implemented once |
| **Reference** | [`Scenes as data`](reference/scenes-as-data.md) | `Active` | Goal: a scene is a file the app loads, not Kotlin code the app compiles. This is the |
| **Reference** | [`Shadcn Reference Pipeline`](reference/shadcn-reference-pipeline.md) | `Active` | This document is the canonical source for how Awake pins and extracts real shadcn/ui ground |
| **Reference** | [`Source provenance`](reference/source-provenance.md) | `Active` | Awake source is original unless an entry in `source-provenance.json` records an external source. |
| **Reference** | [`Tutorial Coverage`](reference/tutorial-coverage.md) | `Active` | This page tracks the rollout from "docs infrastructure exists" to "every meaningful Awake |
| **Reference** | [`UI fidelity status matrix`](reference/ui-fidelity-status.md) | `Active` | **Generated by `.agents/skills/awake-ui-verification/scripts/generate_ui_status.py` — do not hand-edit.** |
| **Reference** | [`UI Ownership`](reference/ui-ownership.md) | `Active` | This document is the canonical source for Awake's current reusable UI boundaries. |
| **Reference** | [`UI parity core-issue tracker`](reference/ui-parity-core-issues.md) | `Active` | This tracker records only a proven parity limitation that cannot be corrected in the component, |
| **Reference** | [`UI parity tool`](reference/ui-parity-tool.md) | `Active` | <!-- ui-tooling-map --> |
| **Reference** | [`UI Validation`](reference/ui-validation.md) | `Active` | <!-- ui-tooling-map --> |
| **Reference** | [`Vulkan Binding Annotations`](reference/vulkan-annotations.md) | `Active` | The Vulkan Kotlin API intentionally uses readable Kotlin types such as `Long`, `Array<T>`, |
| **Tasks (Archive)** | [`2026-07-09: Decouple World`](tasks/archive/2026-07-09-decouple-world.md) | `Archived` | Status: completed. The implemented ownership was later refined by the focused storage registries |
| **Tasks (Archive)** | [`Scene Runtime`](tasks/archive/2026-07-10-scene-runtime.md) | `Archived` | Turn the serialized scene contract into the runtime bootstrap path for the MVP scene. |
| **Tasks (Archive)** | [`2026-07-14: UI DSL and Style Audit`](tasks/archive/2026-07-14-ui-dsl-audit.md) | `Archived` | Fix the bitmap text noise in the sample UI, then define a cleaner path toward reusable |
| **Tasks (Archive)** | [`2026-07-14: UI Module Split and Shape Plan`](tasks/archive/2026-07-14-ui-module-split.md) | `Archived` | Split Awake's UI stack into clearer ownership layers so: |
| **Tasks (Archive)** | [`2026-07-17: UI API Simplification`](tasks/archive/2026-07-17-ui-api-simplification.md) | `Archived` | Reduce public UI API sprawl so Awake's reusable UI stack reads more like a real library and |
| **Tasks (Archive)** | [`UI Showcase Cleanup`](tasks/archive/2026-07-18-ui-showcase-cleanup.md) | `Archived` | Turn `samples:ui-showcase` from a single large catalog file into a clearer sample structure that |
| **Tasks (Archive)** | [`2026 07 22 Core Graphics Split`](tasks/archive/2026-07-22-core-graphics-split.md) | `Archived` | Date: 2026-07-22 |
| **Tasks (Archive)** | [`2026-07-22: UiContext Refactor Plan`](tasks/archive/2026-07-22-ui-context-refactor-plan.md) | `Archived` | Continue the `UiContext` cleanup as a staged architecture refactor instead of a second |
| **Tasks (Archive)** | [`UiSlot narrowing (2026-07-24)`](tasks/archive/2026-07-24-uislot-narrowing.md) | `Archived` | Follow-up to `docs/tasks/2026-07-17-ui-api-simplification.md`'s deferred item: `UiSlot` is |
| **Tasks (Archive)** | [`graphicsLayer rotation/scale tier (2026-08-02)`](tasks/archive/2026-08-02-graphicslayer-rotation-scale.md) | `Archived` | Design/scoping only -- no fix implemented in this task. Read |
| **Tasks (Archive)** | [`Cross-frame trial-measure caching for `row()`/`column()` (2026-08-02)`](tasks/archive/2026-08-02-trial-measure-cross-frame-cache.md) | `Archived` | Design/scoping only -- no fix implemented in this task. Follows on directly from |
| **Tasks (Archive)** | [`Trial-measure double-execution perf bug (2026-08-02)`](tasks/archive/2026-08-02-trial-measure-double-execution.md) | `Archived` | Design/scoping only -- no fix implemented in this task. Read `UiContextMeasureState.kt`, |
| **Tasks (Archive)** | [`Cross-frame text-layout measurement caching (2026-08-03)`](tasks/archive/2026-08-03-text-layout-measure-cache.md) | `Archived` | Design/scoping only -- no fix implemented in this task. Follow-on from the real WebGPU web-lag |
| **Tasks (Archive)** | [`API Layering Plan`](tasks/archive/2026-08-05-api-layering-plan.md) | `Archived` | Date: 2026-08-05 |
| **Tasks (Archive)** | [`Scene Module Split Proposal`](tasks/archive/2026-08-05-scene-module-split-proposal.md) | `Archived` | Date: 2026-08-05 |
| **Tasks (Archive)** | [`Application seam and module naming plan`](tasks/archive/2026-08-09-application-seam-and-module-naming-plan.md) | `Archived` | Date: 2026-08-09 |
| **Tasks (Archive)** | [`Glyphs render ~0.6x their own metrics -- RETRACTED; real ~0.9x residual FIXED`](tasks/archive/2026-08-10-glyph-scale-regression.md) | `Archived` | Status: **the 0.6x report was a measurement artifact; behind it sat a real, smaller defect, |
| **Tasks (Archive)** | [`MTSDF atlas migration`](tasks/archive/2026-08-10-mtsdf-atlas-migration.md) | `Archived` | Status: **implemented**. The generated Roboto faces use committed MTSDF atlases, the default |
| **Tasks (Archive)** | [`Final Plan: UI API / Core / Headless Boundary`](tasks/archive/2026-08-11-ui-designsystem-headless-boundary-migration.md) | `Archived` | `ui-designsystem` compiles only against `ui-headless` and `ui-api`. It has no `ui-core` |
| **Tasks (Archive)** | [`UI Showcase Parity Tracker`](tasks/archive/2026-08-12-ui-showcase-parity-tracker.md) | `Archived` | This is the execution tracker for the post-boundary-migration UI cleanup. It is intentionally |
| **Tasks (Archive)** | [`UI implementation plan — from 2026-08-14`](tasks/archive/2026-08-14-ui-implementation-plan.md) | `Archived` | Ordered so each phase leaves the tree in a state the next one can trust. Phase 0 first because |
| **Tasks (Archive)** | [`UI layout: weight distribution — parked 2026-08-14`](tasks/archive/2026-08-14-ui-layout-weight-parking.md) | `Archived` | **Superseded 2026-08-31.** The `awake:ui` module this bug lived in (`UiScope`, `ColumnScope.claimSlot`, |
| **Tasks (Archive)** | [`UI roadmap — consolidated 2026-08-14`](tasks/archive/2026-08-14-ui-roadmap.md) | `Archived` | Supersedes `archive/2026-08-14-ui-implementation-plan.md` (phases 0–2 are done). Companion: |
| **Tasks (Archive)** | [`UI tooling simplification plan`](tasks/archive/2026-08-16-ui-tooling-simplification.md) | `Archived` | Make Awake UI tooling easy to enter, trustworthy when it reports status, and small enough that |
| **Tasks (Archive)** | [`2026-08-17: awake:core module split proposal`](tasks/archive/2026-08-17-awake-core-module-split-proposal.md) | `Archived` | Status: Active architecture guideline. Revised 2026-08-21 against real source — the original |
| **Tasks (Archive)** | [`2026-08-18: ECS hybrid archetype + sparse-set`](tasks/archive/2026-08-18-ecs-hybrid-archetype-sparse-set.md) | `Archived` | Status: closed. Archetype migration was rejected after matched stable-row, structural-migration, and |
| **Tasks (Archive)** | [`UI capability-scoped receivers — simplified plan`](tasks/archive/2026-08-18-ui-capability-scopes-plan.md) | `Archived` | Companion to the "Capability-scoped receivers" (P1) row in |
| **Tasks (Archive)** | [`Architecture Governance Standardization Plan`](tasks/archive/2026-08-20-architecture-governance-standardization-plan.md) | `Archived` | Date: 2026-08-20 |
| **Tasks (Archive)** | [`Awake Template Repository Plan`](tasks/archive/2026-08-20-template-repository-plan.md) | `Archived` | Date: 2026-08-20 |
| **Tasks (Archive)** | [`2026-08-21: ECS adaptive bulk structural mutation plan`](tasks/archive/2026-08-21-ecs-adaptive-bulk-mutation-plan.md) | `Archived` | Status: **narrowed**. No production implementation exists and none is authorized by this document |
| **Tasks (Archive)** | [`2026-08-21: ECS cached type-ID structural churn`](tasks/archive/2026-08-21-ecs-cached-type-id-churn.md) | `Archived` | Status: accepted. Public ECS shape and sparse/family storage remain unchanged. |
| **Tasks (Archive)** | [`2026-08-21: ECS payload-free family tag columns`](tasks/archive/2026-08-21-ecs-family-tag-columns.md) | `Archived` | Status: implemented and decision-benchmarked on 2026-08-21. |
| **Tasks (Archive)** | [`2026-08-21: ECS storage decoupling`](tasks/archive/2026-08-21-ecs-storage-decoupling.md) | `Archived` | Status: implemented and verified on 2026-08-21. The recommended answers under |
| **Tasks (Archive)** | [`Modifier/Layout Compose-parity plan`](tasks/archive/2026-08-21-modifier-layout-compose-parity-plan.md) | `Archived` | Companion to [`mirror-map.md`](../reference/mirror-map.md) (the real, evidence-based |
| **Tasks (Archive)** | [`3D render path: bug fixes, then shader module split`](tasks/archive/2026-08-21-render-3d-bugfixes-and-shader-module-split-plan.md) | `Archived` | Two correctness bugs in the WebGPU 3D path, then the authored-shader module separation. |
| **Tasks (Archive)** | [`Shadcn 100% parity tool plan`](tasks/archive/2026-08-21-shadcn-parity-tool-plan.md) | `Archived` | Build a versioned, reproducible UI-parity toolchain that can prove whether Awake matches a |
| **Tasks (Archive)** | [`2026-08-22: `PathFillTessellation.kt` — findings and fixes`](tasks/archive/2026-08-22-pathfilltessellation-cleanup-plan.md) | `Archived` | Status: **planned, not started.** Found by reading the largest file the |
| **Tasks (Archive)** | [`2026-08-22: retiring the global `UiDensity``](tasks/archive/2026-08-22-retire-global-density-plan.md) | `Archived` | Status: **planned, not started.** Independent of the |
| **Tasks (Archive)** | [`2026-08-22: splitting `UiPath.kt``](tasks/archive/2026-08-22-split-uipath-plan.md) | `Archived` | Status: **planned, not started.** Independent of the |
| **Tasks (Archive)** | [`2026-08-22: dropping the `Ui` prefix from the 2D draw vocabulary`](tasks/archive/2026-08-22-ui-prefix-rename-plan.md) | `Archived` | Status: **planned, not started.** Blast radius measured 2026-08-22 — every number below is a count, |
| **Tasks (Archive)** | [`ASL procedural shaders: module, DSL shape, generation wiring`](tasks/archive/2026-08-23-asl-procedural-shader-plan.md) | `Archived` | Implementation plan for the vision in |
| **Tasks (Archive)** | [`Split game-authored content out of the GPU backends`](tasks/archive/2026-08-23-backend-content-split-plan.md) | `Archived` | Status: **every phase done. `contentExemptBackendFiles` is empty, 14 files to 0.** Written |
| **Tasks (Archive)** | [`2026-08-23: does a headless tier earn itself back?`](tasks/archive/2026-08-23-does-a-headless-tier-earn-itself-back.md) | `Archived` | Status: **open question, deliberately unanswered.** Revisit after Stage 3 step 3, not before. |
| **Tasks (Archive)** | [`2026-08-23: the glyph atlas belongs in `core:graphics2d``](tasks/archive/2026-08-23-font-belongs-in-core-graphics.md) | `Archived` | Status: **planned, not started.** Do it between component batches, not during one. |
| **Tasks (Archive)** | [``GpuDevice`: finish the render hardware interface (option B)`](tasks/archive/2026-08-23-rhi-gpudevice-plan.md) | `Archived` | Status: **in progress.** Written 2026-08-23. Phases 0, 1, 1b, 2, 3 and 4 are DONE; phase 4b step 1 |
| **Tasks (Archive)** | [`2026-08-23: what to take from `shadcn-compose``](tasks/archive/2026-08-23-shadcn-compose-adoption.md) | `Archived` | Status: **analysis, feeding Stage 3 step 3.** Read before rewriting the 94 recipes. |
| **Tasks (Archive)** | [`2026-08-23: stop deriving shadcn's tokens, look them up`](tasks/archive/2026-08-23-shadcn-palette-from-table-plan.md) | `Archived` | Status: **done 2026-08-23.** All five ordered steps landed, plus the three formalisation gaps. |
| **Tasks (Archive)** | [`2026-08-23: Stage 3 — shadcn straight onto `:compose:foundation``](tasks/archive/2026-08-23-stage-3-plan.md) | `Archived` | Status: **planned, not started.** Replaces the Stage 3 bullet in |
| **Tasks (Archive)** | [`2026-08-23: one entry point, and three kinds of tool`](tasks/archive/2026-08-23-ui-tooling-formalization-plan.md) | `Archived` | Status: **done 2026-08-23.** The per-generator staleness gates, still outstanding when this was |
| **Tasks (Archive)** | [`2026-08-23: the parity reference is hand-copied, and 11 of 26 have drifted`](tasks/archive/2026-08-23-vendor-the-reference-app-components.md) | `Archived` | Status: **fixed 2026-08-23** — generator `7ba0c8b0d`, re-vendor `7d81b794b`. See what it exposed, below. |
| **Tasks (Archive)** | [`Skinned animation player plan`](tasks/archive/2026-08-24-animation-player-plan.md) | `Archived` | **Status:** in progress. Core playback, the glTF adapter, the scene bridge, and the CesiumMan |
| **Tasks (Archive)** | [`Framework Boundary and Plugin Ecosystem Plan`](tasks/archive/2026-08-24-framework-boundary-and-plugin-ecosystem-plan.md) | `Archived` | Date: 2026-08-24 |
| **Tasks (Archive)** | [`Heightfield collision shape plan`](tasks/archive/2026-08-24-heightfield-physics-shape-plan.md) | `Archived` | **Status:** in progress — implementation authorized 2026-08-24. Reviewed against the checked-in |
| **Tasks (Archive)** | [`One plan an app declares, one seam that runs it`](tasks/archive/2026-08-24-render-plan-and-app-seam-plan.md) | `Archived` | Status: **done.** Written 2026-08-24. |
| **Tasks (Archive)** | [`Studio Thinning Plan`](tasks/archive/2026-08-24-studio-thinning-plan.md) | `Archived` | Date: 2026-08-24 |
| **Tasks (Archive)** | [`Terrain rendering plan`](tasks/archive/2026-08-24-terrain-rendering-plan.md) | `Archived` | **Status:** in progress — milestone 0 is complete: `:awake:asset:terrain` provides an immutable |
| **Tasks (Archive)** | [`ui-core / ui-headless retirement — DONE (2026-08-24)`](tasks/archive/2026-08-24-ui-core-headless-retirement-plan.md) | `Archived` | Closed out. Kept for history and as a reference for the next module-boundary cleanup of this |
| **Tasks (Archive)** | [`vulkan-kmp: publishing Awake's Vulkan bindings as the first maintained KMP Vulkan library`](tasks/archive/2026-08-24-vulkan-kmp-bindings-publish-plan.md) | `Archived` | Decision (2026-08-24): **core profile first, vk.xml later; published from this repo.** The |
| **Tasks (Archive)** | [`What nothing checks`](tasks/archive/2026-08-24-what-nothing-checks-plan.md) | `Archived` | Status: **in progress. Step 1 done, 2-4 open.** Written 2026-08-24. |
| **Tasks (Archive)** | [`Compose node-local shadow plan`](tasks/archive/2026-08-25-compose-node-local-shadow-plan.md) | `Archived` | Status: implemented |
| **Tasks (Archive)** | [`2026-08-25: a real `Shape` abstraction for `background`/`border`/`clip``](tasks/archive/2026-08-25-compose-shape-abstraction-plan.md) | `Archived` | Status: **superseded — landed independently, more completely than this plan proposed.** |
| **Tasks (Archive)** | [`Compose Stage 2 — state invalidation and retained skip scopes`](tasks/archive/2026-08-25-compose-stage-2-invalidation-plan.md) | `Archived` | Status: completed — runtime scopes, state invalidation, host retention, focused tests, Studio chrome |
| **Tasks (Archive)** | [`Scene Session Simplification Plan`](tasks/archive/2026-08-25-scene-session-simplification-plan.md) | `Archived` | Date: 2026-08-25 |
| **Tasks (Archive)** | [`Shadcn parity handoff — 2026-08-25`](tasks/archive/2026-08-25-shadcn-parity-handoff.md) | `Archived` | Status: active handoff |
| **Tasks (Archive)** | [`Shadcn parity plan v2`](tasks/archive/2026-08-25-shadcn-parity-plan-v2.md) | `Archived` | Status: active |
| **Tasks (Archive)** | [`Compose destination-colour blend modes`](tasks/archive/2026-08-27-compose-destination-blend-plan.md) | `Archived` | `Screen`, `Overlay`, `Darken`, `Lighten`, and related `graphicsLayer` blend modes must be |
| **Tasks (Archive)** | [`Compose generic-shape shadow plan`](tasks/archive/2026-08-27-compose-generic-shape-shadow-plan.md) | `Archived` | Status: planned |
| **Tasks (Archive)** | [`Compose Modifier node lifecycle plan`](tasks/archive/2026-08-27-compose-modifier-node-lifecycle-plan.md) | `Archived` | Move retained modifier behavior from per-pass `Modifier.Element` instances into Compose-shaped |
| **Tasks (Archive)** | [`Compose next stages plan`](tasks/archive/2026-08-27-compose-next-stages-plan.md) | `Archived` | Status: in progress — Stage A (bridge) and Stage B (retained scope closure) completed. This plan |
| **Tasks (Archive)** | [`JNI Native Implementation Migration Plan`](tasks/archive/2026-08-28-jni-native-implementation-migration-plan.md) | `Archived` | **Status:** Open |
| **Tasks (Archive)** | [`Pluggable Compose Editor Architecture for Awake Engine`](tasks/archive/2026-08-28-pluggable-editor-architecture-plan.md) | `Archived` | **Date:** 2026-08-28 |
| **Tasks (Archive)** | [`Texture Resource Manager Plan`](tasks/archive/2026-08-28-texture-resource-manager-plan.md) | `Archived` | Date: 2026-08-28 |
| **Tasks (Archive)** | [`UI Antialiasing Capability Plan`](tasks/archive/2026-08-28-ui-antialiasing-plan.md) | `Archived` | Date: 2026-08-28 |
| **Tasks (Archive)** | [`UI Render Pipeline Contract Plan`](tasks/archive/2026-08-28-ui-render-pipeline-contract-plan.md) | `Archived` | Date: 2026-08-28 |
| **Tasks (Archive)** | [`Async Cell Streaming Plan`](tasks/archive/2026-08-29-async-cell-streaming-plan.md) | `Archived` | Date: 2026-08-29 |
| **Tasks (Archive)** | [`Content Feature Textures Plan`](tasks/archive/2026-08-29-content-feature-textures-plan.md) | `Archived` | Date: 2026-08-29 |
| **Tasks (Archive)** | [`Kotlin Package Namespace Migration Plan`](tasks/archive/2026-08-29-kotlin-package-namespace-migration-plan.md) | `Archived` | **Date:** 2026-08-29 |
| **Tasks (Archive)** | [`Material Binding Declaration Plan`](tasks/archive/2026-08-29-material-binding-declaration-plan.md) | `Archived` | Date: 2026-08-29 |
| **Tasks (Archive)** | [`Maven Central Publication Plan`](tasks/archive/2026-08-29-maven-central-publication-plan.md) | `Archived` | **Date:** 2026-08-29 |
| **Tasks (Archive)** | [`Terrain Clipmap Draw Plan`](tasks/archive/2026-08-29-terrain-clipmap-draw-plan.md) | `Archived` | Date: 2026-08-29 |
| **Tasks (Archive)** | [`Behavior Tree & State Machine Plan — one hybrid runtime, and where an LLM fits`](tasks/archive/2026-08-30-behavior-tree-state-machine-plan.md) | `Archived` | Date: 2026-08-30 |
| **Tasks (Archive)** | [`Gizmo rotation: scoping audit gap #13`](tasks/archive/2026-08-30-gizmo-rotation-scope.md) | `Archived` | Scope for the scene-editor audit's P1 #13, *"Euler-radian rotation summed per component -- gimbal |
| **Tasks (Archive)** | [`Navigation Plan — a Heightmap-Derived NavGrid, in commonMain`](tasks/archive/2026-08-30-navgrid-navigation-plan.md) | `Archived` | Date: 2026-08-30 |
| **Tasks (Archive)** | [`2026-08-31: generate `ImageVector` icons from vendored SVGs at build time`](tasks/archive/2026-08-31-icon-codegen-plan.md) | `Archived` | Status: **done, 2026-09-01.** Heroicons is pinned at **v2.2.0** and all 78 committed icons |
| **Tasks (Archive)** | [`Studio Infinite Grid with Infinite Axis Lines`](tasks/archive/2026-08-31-studio-infinite-grid-plan.md) | `Archived` | Formalize an unprojected, multi-scale **Infinite Grid** with **Infinite Axis Lines** (X red / Z |
| **General** | [`RFC: Open-World Terrain, Modular Character, and Environment Subsystems`](RFC_OPEN_WORLD_ENGINE_SUBSYSTEMS.md) | `Active` | Awake has established a solid Vulkan-first renderer, entity-component system (ECS), and cross-platform shader architectu... |
| **General** | [`About Awake`](about.md) | `Active` | Unreal and Unity are editor-first: huge surface area, and it wasn't clear where |
| **General** | [`Archived: Immediate-Mode UI Ownership`](archive/2026-08-28-immediate-mode-ui-ownership.md) | `Archived` | This document is retained for history only. The ownership model below was replaced by the |
| **General** | [`Archived: DSL Modules`](archive/2026-08-28-retired-dsl-modules.md) | `Archived` | This document is retained for history only. It describes the deleted immediate-mode DSL module |
| **General** | [`Archived: UI component coverage matrix`](archive/2026-08-28-retired-ui-component-coverage.md) | `Archived` | This document is retained for history only. Its inventory describes deleted `ui-headless` and |
| **General** | [`Archived: UI status report`](archive/2026-08-28-retired-ui-status.md) | `Archived` | This document is retained for history only. It describes the deleted immediate-mode UI module |
| **General** | [`Changelog Archive (v0.1.0-dev.1 to v0.1.0-dev.7)`](archive/CHANGELOG-v0.1-archive.md) | `Archived` | This archive documents historical pre-releases of Awake Engine. |
| **General** | [`Awake Engine — MVP phase log (archive)`](archive/mvp-phase-log.md) | `Archived` | Historical record of how phases 0–8 were executed, kept verbatim. Superseded as a planning |
| **General** | [`ECS Benchmark Scorecard`](ecs-benchmark-scorecard.md) | `Active` | Latest rerun: `2026-08-21`, from the working tree based on commit `851663afb`, using the exact |
| **General** | [`Compose parity handoff — 2026-08-27`](handoffs/2026-08-27-compose-parity-handoff.md) | `Active` | The open objective is **“implement remaining items”** from the Compose parity inventory. It is |
| **General** | [`Compose Stage 2 invalidation handoff — 2026-08-27`](handoffs/2026-08-27-compose-stage-2-invalidation-handoff.md) | `Active` | Status: active, uncommitted worktree handoff. Do not discard or overwrite the listed changes. |
| **General** | [`📝 KDoc Reference Manual & Rules`](kdoc-guidelines.md) | `Active` | This document outlines the strict guidelines and best practices for writing public-facing KDoc |
| **General** | [`📦 Master Library Documentation & Visualization Blueprint`](kotlin-notebook-guidelines.md) | `Active` | This manifest establishes a strict, unified standard for writing documentation, configuring |
| **General** | [`Awake UI Design System - Component Registry`](layout-system/components.md) | `Active` | Shared component registry for Awake UI Design System, mapping components to token slots, default metrics, and layout con... |
| **General** | [`UI Showcase Component Pages - Layout Specification`](layout-system/ui-showcase-components.md) | `Active` | Layout specs and ASCII wireframe diagrams for the UI Showcase component pages (Buttons, Cards, Inputs, Navigation, Overl... |
| **General** | [`ui-showcase`](layout-system/ui-showcase.md) | `Active` | screen: ui-showcase |
| **General** | [`Lesson: AGP 9 / Kotlin 2.4 / Compose MP 1.11 / Gradle 9.6 migration (Awake)`](lessons/2026-07-07-agp9-kotlin24-migration.md) | `Active` | **Date:** 2026-07-07 · Same-day follow-up to the Kotlin 2.1/AGP 8.7 migration — |
| **General** | [`Lesson: Kotlin 1.8 → 2.1 toolchain migration (Awake)`](lessons/2026-07-07-toolchain-migration.md) | `Active` | **Date:** 2026-07-07 · **Skill involved:** `kotlin-multiplatform-migration` |
| **General** | [`Awake Engine — MVP Plan`](mvp-plan.md) | `Active` | The live roadmap. Completed work is summarised here in one line per phase and recorded in full |
| **General** | [`Network Plan — Transport, Replication, Smoothing`](plans/network.md) | `Active` | Where networking lives in Awake, which transport and wire format to pick, and the order to |
| **General** | [`Physics Plan — Open World & Character`](plans/physics-open-world.md) | `Active` | What the physics subsystem is missing before Awake can carry an open world with a |
| **General** | [`Release Notes — vulkan-kmp v0.1.0 (MVP Release)`](release-notes-v0.1.0.md) | `Active` | `vulkan-kmp` is the first Kotlin Multiplatform library providing raw, high-performance Vulkan bindings across **Desktop ... |
| **General** | [`Awake Engine Release Process & Branching Guidelines`](release-process.md) | `Active` | This document serves as the canonical source of truth for repository branching, versioning, |
| **General** | [`Tasks`](tasks.md) | `Active` | Prototype and decision-gate adaptive bulk ECS structural mutation without changing the default |

---
*Automated documentation sitemap generated by [KMP Agent Skills](https://github.com/ronjunevaldoz/kmp-agent-skills)*
