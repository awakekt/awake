# Awake Engine: Agent Guide

Welcome to **Awake Engine**. This repository is the core Kotlin Multiplatform 3D/2D game engine runtime powered by Vulkan, WebGPU, and Compose Multiplatform.

## Three-Layer Architecture Boundary

Awake enforces strict boundaries across three distinct architectural layers:
1. **Layer 1: Awake Core Engine (`awaken`)** (Apache 2.0): Runtime engine libraries (`:awake:scene`, `:awake:physics`, `:awake:render`, `:awake:ui:shadcn`, `:awake:project`, etc.).
2. **Layer 2: Awake Core Editor (`awaken:awake:editor:contract`)** (Apache 2.0): Public, vendor-neutral editor contracts, provider extension points, and project plugin metadata published under `com.awakekt:awake-editor-contract`.
3. **Layer 3: Awake Studio Pro (`awake-pro`)** (Commercial): Desktop authoring application (`:app:studio`), visual inspectors, collaborative workflows, and the secure runtime loader (`StudioPluginPipeline`).

## Skill precedence and technology boundaries

Project-owned `awake-*` skills are authoritative for Awake engine code and the
Awake-owned UI/runtime framework. They take precedence over generic `kmp-*` skills.

- Use `awake-*` for ECS, rendering, physics, engine lifecycle, Awake UI, Awake styling,
  and Awake's Compose-like engine APIs.
- Use `kmp-compose-*` only when the target code has actual Jetpack Compose or Compose
  Multiplatform imports, source sets, or verified Compose dependencies.
- Use `kmp-shadcn-*` only when the target uses verified `Shadcn*` APIs or the real
  shadcn-compose dependency.
- Do not route by naming resemblance alone. An Awake-owned composable or shadcn-like
  component is not automatically Jetpack Compose or shadcn-compose.

When a task spans both systems, route the Awake-owned boundary first and explicitly
identify any generic KMP/Compose follow-up.

## Engine Domain Skills (.agents/skills/)

All engine-specific skills and architectural rules are located in `.agents/skills/`:

### Core Engine & Lifecycle
- [Awake Engine Overview](.agents/skills/awake/SKILL.md): Engine directory layout and conventions
- [Awake Core Editor](.agents/skills/awake-core-editor/SKILL.md): Vendor-neutral editor plugin contracts, manifests, and provider extension points
- [Awake App Composition](.agents/skills/awake-app-composition/SKILL.md): Composing applications and engine subsystems
- [Awake ECS Authoring](.agents/skills/awake-ecs-authoring/SKILL.md): Entity-Component-System design rules
- [Awake Scene Runtime](.agents/skills/awake-ecs-scene-runtime/SKILL.md): SceneAppLifecycleRuntime & frame loops
- [Awake Core Math](.agents/skills/awake-core-math/SKILL.md): Vec3f, Quat, Mat4, Bounds, and geometry
- [Awake State Management](.agents/skills/awake-state-management/SKILL.md): Reactive state and event flows
- [Awake Framework Boundary](.agents/skills/awake-framework-boundary/SKILL.md): Rules separating core engine from game packs
- [Awake Capability Extraction](.agents/skills/awake-capability-extraction/SKILL.md): Cross-repository capability ownership and duplicate prevention
- [Awake Codec and Asset Source](.agents/skills/awake-codec-and-asset-source/SKILL.md): Pure byte codecs and asynchronous asset resolution
- [Awake Copyright & Provenance](.agents/skills/awake-copyright-provenance/SKILL.md): License and attribution rules

### Rendering, Pipelines & Shaders
- [Awake Render Vulkan](.agents/skills/awake-render-vulkan/SKILL.md): Vulkan pipeline, swapchain, and Naga SPIR-V compilation
- [Awake Render WebGPU](.agents/skills/awake-render-webgpu/SKILL.md): WebGPU pipeline and native WGSL execution
- [Awake Render Pipeline](.agents/skills/awake-render-pipeline/SKILL.md): RenderPlan, ShaderSet, and pass orchestration
- [Awake Render Headless Verification](.agents/skills/awake-render-headless-verification/SKILL.md): Measuring what the engine renders, without a window

> **⚠️ Before touching `Renderer.kt`, any `render:contract` type, or any backend renderer file:**
> Read `awake-render-pipeline` §0.5 (Two-Layer Model) and
> `docs/reference/render-hardware-interface.md` § HAL vs Render Graph.
> The most common defect is adding scene vocabulary (`SceneLight`, `DrawCall`, `Lens`,
> shadow or fog types) to `render:contract`. Decision D31 classifies every current type.
> In the target state, `Renderer` accepts only `GpuPassInput` with generic `GpuSubPass`es.
> `verifyBackendLayering` runs against every backend production source file; its import and
> content-vocabulary exemption ledgers are empty. Remaining backend extensions are tracked
> migration work, not permission to add scene vocabulary.


### Physics & Asset Pipelines
- [Awake Physics Jolt](.agents/skills/awake-physics-jolt/SKILL.md): Jolt physics bodies, collision shapes, and steps
- [Awake Terrain Authoring](.agents/skills/awake-terrain-authoring/SKILL.md): Raw Heightmap and surface sampling
- [Awake FBX Asset Cooking](.agents/skills/awake-fbx-asset-cooking/SKILL.md): FBX to GLB conversion rules

### UI, Shadcn & Styling
- [Awake UI Authoring](.agents/skills/awake-ui-authoring/SKILL.md): Compose UI component authoring
- [Awake UI Performance](.agents/skills/awake-ui-performance/SKILL.md): Frame pacing & draw call reduction
- [Awake Compose Authoring](.agents/skills/awake-compose-authoring/SKILL.md): Design system tokens and spacing
- [Awake Pro Core Scoring](.agents/skills/awake-pro-core-scoring/SKILL.md): Evaluates and scores proposed Awake Engine features, editor panels, runtime modules, and asset tools to decide whether they belong in Awake Core (Free & Open-Source) or Awake Pro (Commercial Studio Tier). Produces structured insights, industry runtime architecture benchmarks, and a scoring matrix report.
- [Awake UI Design Audit](.agents/skills/awake-ui-design-audit/SKILL.md): Automated UI design audit rubric
- [Awake UI Layout Guidance](.agents/skills/awake-ui-layout-guidance/SKILL.md): Responsive and adaptive layouts
- [Awake UI Verification](.agents/skills/awake-ui-verification/SKILL.md): Visual regression and component crops
- [Awake UI Icons](.agents/skills/awake-ui-icons/SKILL.md): Heroicons vector compiling
- [Awake UI CSS Modifier](.agents/skills/awake-ui-css-modifier/SKILL.md): CSS modifier semantics
- [Awake Shadcn Parity Workflow](.agents/skills/awake-shadcn-parity-workflow/SKILL.md): Web Shadcn parity workflow
- [Awake Shadcn to Compose](.agents/skills/awake-shadcn-to-compose/SKILL.md): Porting Shadcn primitives
- [Awake Shadcn Recipe Authoring](.agents/skills/awake-shadcn-recipe-authoring/SKILL.md): Custom Shadcn recipe authoring
- [Awake Shadcn Recipe Consuming](.agents/skills/awake-shadcn-recipe-consuming/SKILL.md): Consuming recipes in UI
- [Awake Tailwind to Compose](.agents/skills/awake-tailwind-to-compose/SKILL.md): Tailwind classes to Compose modifiers
- [Awake Web to Compose](.agents/skills/awake-web-to-compose/SKILL.md): Web layout patterns to Compose

### Multiplatform Capability Design
- [KMP Platform Capability Design](.agents/skills/kmp-platform-capability-design/SKILL.md): Weakest-platform-first API and adapter design
- [KMP Conformance Contract Testing](.agents/skills/kmp-conformance-contract-testing/SKILL.md): Shared contract tests and reference implementations

### Release Process & Repository Flow
- [Awake Release Process & Branching Guidelines](docs/release-process.md): Branching flow, SemVer channels, CHANGELOG rules, and `./scripts/release.py` usage
- [Awake Milestone Workflow](.agents/skills/awake-milestone-workflow/SKILL.md): Repository hygiene, GitHub milestone tracking, and commit squashing rules

**Pull Request & Release Invariants**:
1. Every PR must be created with `--milestone "<milestone>"` (e.g. `gh pr create --milestone "v0.1.0-beta.1"`). Never create a PR without an assigned milestone.
2. Every `feat:` and `fix:` PR must add a bullet entry to `CHANGELOG.md` under `## [Unreleased]`. CI enforces this.
3. When all issues/PRs for an active milestone are merged, cut the release via `./scripts/release.py cut --channel <channel>`, push the tag, and close the milestone.

Use `feat/*`, `fix/*`, `refactor/*`, or `docs/*` for topic branches. For dependent layers,
create a linear stacked PR chain with each PR targeting the branch immediately below it; review
and merge from the bottom upward. Follow `docs/release-process.md` before creating or retargeting
stacked PRs.
