# Documentation Sitemap & System Status

> [!TIP]
> **AI Agent Navigation Directives**:
> 1. **Never recursively scan the `docs/` folder.**
> 2. Read this sitemap table to find the exact document you need.
> 3. Only open the specific single file relevant to your current task.

**Last Self-Healed**: `2026-10-06` | **Total Tracked Docs**: `80`

| Category | Document | Status | 1-Line Summary |
| :--- | :--- | :--- | :--- |
| **Architecture** | [`D34: render plugin lifecycle`](architecture/D34-render-plugin-lifecycle.md) | `Stable` | Status: accepted |
| **Architecture** | [`Architecture`](architecture/architecture.md) | `Stable` | Awake is a Kotlin Multiplatform game engine library: a Vulkan-first cross-platform renderer |
| **Architecture** | [`D10 — jni-binding-generator de-risk findings (2026-07-07)`](architecture/decisions/D10-codegen-derisk-findings.md) | `Stable` | Phase 1a of [mvp-plan.md](../mvp-plan.md) called for a week-one de-risk: run |
| **Architecture** | [`D11: JNI Native Implementation Boundary`](architecture/decisions/D11-jni-native-implementation-boundary.md) | `Stable` | `jni-binding-generator` owns the JNI boundary only: exported JNI names, parameter |
| **Architecture** | [`D28: Open-World Subsystems — Awake vs Starter-Kit Boundary`](architecture/decisions/D28-open-world-framework-boundary.md) | `Stable` | The open-world RFC's subsystems are split by the |
| **Architecture** | [`D29: Physics — When Awake Would Write Its Own Engine`](architecture/decisions/D29-physics-own-engine-exit-criteria.md) | `Stable` | Awake builds on Jolt ([D5](../reference/decision-log.md)) and does **not** hold "write our own |
| **Architecture** | [`D30: Math — Which Numeric Primitive Variants Earn a Type`](architecture/decisions/D30-math-numeric-primitive-variants.md) | `Stable` | `:awake:core:math` does **not** carry a variant of every vector/quaternion/matrix type for |
| **Architecture** | [`D31: Net — What the Transport Module Owns, and What Stays in the Game`](architecture/decisions/D31-net-api-extraction.md) | `Stable` | `:awake:net:api` exists and carries exactly two things: the **transport port** and the |
| **Architecture** | [`D32: scene and render domain modules`](architecture/decisions/D32-scene-and-render-domain-modules.md) | `Stable` | Status: accepted |
| **Architecture** | [`D33: render feature plugin boundary`](architecture/decisions/D33-render-feature-plugin-boundary.md) | `Stable` | Status: accepted |
| **Architecture** | [`D34: game UI is scene data; the design system is a style layer`](architecture/decisions/D34-game-ui-is-scene-data.md) | `Stable` | Status: accepted (2026-09-30) |
| **Architecture** | [`D35: the editor boundary between Core and Studio`](architecture/decisions/D35-editor-boundary.md) | `Stable` | Status: accepted (2026-10-03) |
| **Architecture** | [`D36: scene-bound editor hooks`](architecture/decisions/D36-scene-bound-editor-hooks.md) | `Stable` | Status: accepted (2026-10-04) |
| **Architecture** | [`D37: component property schema`](architecture/decisions/D37-component-property-schema.md) | `Active` | Status: proposed (2026-10-06) |
| **Architecture** | [`D38: shaders as project data`](architecture/decisions/D38-shaders-as-project-data.md) | `Active` | Status: proposed (2026-10-06) |
| **Reference** | [`Agent Catalog Boundary`](reference/agent-catalog.md) | `Active` | Awake does not keep an agent roster or personas as tracked repository content. The public |
| **Reference** | [`Agent Routing`](reference/agent-routing.md) | `Active` | This public repository routes only technical Awake engine work. The authoritative role details |
| **Reference** | [`Agent Starter Pack`](reference/agent-starter-pack.md) | `Active` | Use a source/deployment split when adding agent guidance to another repository. |
| **Reference** | [`AI Collaboration`](reference/ai-collaboration.md) | `Active` | Awake keeps architecture and product tooling independently usable from agent tooling. |
| **Reference** | [`API Layering`](reference/api-layering.md) | `Active` | Awake separates public API into three layers: **core**, **helpers**, and **sugar**. This |
| **Reference** | [`Backend commonisation: where the duplication actually is`](reference/backend-commonisation.md) | `Active` | Vulkan and WebGPU are hand-authored side by side. This is the measurement of how much, where, |
| **Reference** | [`AwakeKt brand system`](reference/brand-system.md) | `Active` | **Status:** Stable. This is the canonical visual-identity record for the AwakeKt and Awake names |
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
| **Reference** | [`AwakeKt docs style guide`](reference/docs-style-guide.md) | `Active` | How pages under `website/docs/` are written. The first page on this template is |
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
| **Reference** | [`Maven coordinates and publication dependency graph`](reference/maven-coordinates.md) | `Active` | This page is the current Maven publication inventory. **It is maintained by hand:** a pull request |
| **Reference** | [`Mirror Map: Awake UI DSL vs. Jetpack Compose`](reference/mirror-map.md) | `Active` | Awake's retained UI API (`Modifier`, `Row`/`Column`/`Box`, state hooks, |
| **Reference** | [`Module Architecture`](reference/module-architecture.md) | `Active` | How Awake's 44 modules are grouped, why, and how to decide where a new one goes. |
| **Reference** | [`Engine Performance Matrix & Scorecard`](reference/performance-matrix.md) | `Active` | High-level dashboard of Awake's performance baselines, micro-benchmark suites, algorithmic complexity guarantees, and co... |
| **Reference** | [`Publishing Awake libraries`](reference/releasing.md) | `Active` | Awake publishes Kotlin Multiplatform modules to Maven Central. The current publication set and |
| **Reference** | [`Render Backend Audit: Vulkan vs. WebGPU`](reference/render-backend-audit.md) | `Active` | Audits `awake/backend/vulkan`'s `Renderer.kt` against `awake/backend/webgpu`'s `Renderer.kt` |
| **Reference** | [`Render extensibility convention`](reference/render-extensibility.md) | `Active` | Awake is a library/framework: a consumer must be able to build their own rendering content |
| **Reference** | [`The Render Hardware Interface (`GpuDevice`)`](reference/render-hardware-interface.md) | `Active` | Awake's rendering boundary: one backend-neutral API that engine code targets, implemented once |
| **Reference** | [`Scenes as data`](reference/scenes-as-data.md) | `Active` | Goal: a scene is a file the app loads, not Kotlin code the app compiles. This is the |
| **Reference** | [`Shadcn Reference Pipeline`](reference/shadcn-reference-pipeline.md) | `Active` | This document is the canonical source for how Awake pins and extracts real shadcn/ui ground |
| **Reference** | [`Source provenance`](reference/source-provenance.md) | `Active` | Awake source is original unless an entry in `source-provenance.json` records an external source. |
| **Reference** | [`Tutorial Coverage`](reference/tutorial-coverage.md) | `Active` | This page tracks the rollout from "docs infrastructure exists" to "every meaningful Awake |
| **Reference** | [`UI fidelity status matrix`](reference/ui-fidelity-status.md) | `Active` | **Generated by `tools/shadcn/generate_ui_status.py` — do not hand-edit.** |
| **Reference** | [`UI Ownership`](reference/ui-ownership.md) | `Active` | This document is the canonical source for Awake's current reusable UI boundaries. |
| **Reference** | [`UI parity core-issue tracker`](reference/ui-parity-core-issues.md) | `Active` | This tracker records only a proven parity limitation that cannot be corrected in the component, |
| **Reference** | [`UI parity tool`](reference/ui-parity-tool.md) | `Active` | <!-- ui-tooling-map --> |
| **Reference** | [`UI Validation`](reference/ui-validation.md) | `Active` | <!-- ui-tooling-map --> |
| **Reference** | [`Vulkan Binding Annotations`](reference/vulkan-annotations.md) | `Active` | The Vulkan Kotlin API intentionally uses readable Kotlin types such as `Long`, `Array<T>`, |
| **General** | [`About Awake`](about.md) | `Active` | Awake is built for developers who prioritize code-first visibility, precision, |
| **General** | [`Changelog Archive (v0.1.0-dev.1 to v0.1.0-dev.7)`](archive/CHANGELOG-v0.1-archive.md) | `Archived` | This archive documents historical pre-releases of Awake Engine. |
| **General** | [`Documentation Site on docs.awakekt.com`](deployment/mkdocs-site.md) | `Active` | The Awake documentation site is built with [Material for MkDocs](https://squidfunk.github.io/mkdocs-material/) |
| **General** | [`Samples on Cloudflare Pages`](deployment/samples-cloudflare-pages.md) | `Active` | The repository contains a GitHub Actions deployment for the two browser samples: |
| **General** | [`ECS Benchmark Scorecard`](ecs-benchmark-scorecard.md) | `Active` | Latest rerun: `2026-08-21`, from the working tree based on commit `851663afb`, using the exact |
| **General** | [`📝 KDoc Reference Manual & Rules`](kdoc-guidelines.md) | `Active` | This document outlines the strict guidelines and best practices for writing public-facing KDoc |
| **General** | [`📦 Master Library Documentation & Visualization Blueprint`](kotlin-notebook-guidelines.md) | `Active` | This manifest establishes a strict, unified standard for writing documentation, configuring |
| **General** | [`Awake Engine — MVP Plan`](mvp-plan.md) | `Active` | The live roadmap. Completed work is summarised here in one line per phase; decisions and their |
| **General** | [`Network Plan — Transport, Replication, Smoothing`](plans/network.md) | `Active` | Where networking lives in Awake, which transport and wire format to pick, and the order to |
| **General** | [`Physics Plan — Open World & Character`](plans/physics-open-world.md) | `Active` | What the physics subsystem is missing before Awake can carry an open world with a |
| **General** | [`Awake Engine Release Process & Branching Guidelines`](release-process.md) | `Active` | This document serves as the canonical source of truth for repository branching, versioning, |

---
*Automated documentation sitemap generated by [KMP Agent Skills](https://github.com/ronjunevaldoz/kmp-agent-skills)*
