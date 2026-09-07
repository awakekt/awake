# Tasks

## Current Objective

Prototype and decision-gate adaptive bulk ECS structural mutation without changing the default
immediate API or reopening the rejected archetype migration. Public API stabilization and UI
parity remain parallel lanes.

- 2026-09-06: The Kotlin package and Maven namespace migration to `com.awakekt` and repository transfer to `awakekt/awake` are complete. All subprojects now publish under group `com.awakekt.awake` with clean package root `com.awakekt.awake.*`.

- 2026-09-06: Cut release `v0.1.0-dev.10` and expanded the project roadmap to 7 GitHub Milestones (covering `v0.1.0-alpha.1` Maven release, `v0.2.0` WebGPU/Cloudflare Pages, `v0.3.0` Jolt physics, `v0.4.0` Studio prefabs, `v0.5.0` spatial audio/terrain, `v0.6.0` networking, and `v1.0.0` stable ecosystem).

- 2026-08-29: Maven Central publication for the public Awake consumer path is drafted in
  [2026-08-29-maven-central-publication-plan](tasks/2026-08-29-maven-central-publication-plan.md).
  The existing workflow publishes Vulkan bindings only; the plan publishes the engine, asset, and
  Vulkan/WebGPU modules required by [awake-template](https://github.com/awakekt/awake-template)
  and validates a clean Maven-only consumer build.

- 2026-08-29: Maven Central publication for the public Awake consumer path is drafted in
  [2026-08-29-maven-central-publication-plan](tasks/2026-08-29-maven-central-publication-plan.md).
  The existing workflow publishes Vulkan bindings only; the plan publishes the engine, asset, and
  Vulkan/WebGPU modules required by [awake-template](https://github.com/awakekt/awake-template)
  and validates a clean Maven-only consumer build.

## Active Phase

- 2026-08-22: Vector precision variants are drafted in
  [math/01-vector-precision-variants-todo](tasks/math/01-vector-precision-variants-todo.md).
  Three independent flat types, no shared supertype -- generics box and a sealed parent kills
  inlining without unifying the operations. Each variant is gated on a real consumer; `Vec3d` has
  one today (`MeshSimplifier`'s 19 `DoubleArray` uses), `Vec3i` has none.
- 2026-08-22: Retiring the global `UiDensity` is drafted in
  [2026-08-22-retire-global-density-plan](tasks/2026-08-22-retire-global-density-plan.md). Two
  `Dp.toPx()` apply to one receiver and one silently reads a process-wide scale; compose has not
  hit it yet. Four blockers, 80 of the 94 affected files die with `ui-core`. Lands before the
  prefix rename.
- 2026-08-22: `PathFillTessellation.kt` cleanup is drafted in
  [2026-08-22-pathfilltessellation-cleanup-plan](tasks/2026-08-22-pathfilltessellation-cleanup-plan.md).
  The `UiPath` split left one 664-line file, and 53% of it is mesh clipping rather than fill
  tessellation. Eleven functions implement three algorithms, triplicated across vertex types; ten
  public helpers have zero external consumers. Follows the 2D vocabulary lane.

- 2026-08-22: **2D draw vocabulary lane — run these three in order.** They are separate tasks because
  they are separate kinds of work (behavioural / mechanical / structural), and they must not share a
  commit. Two of them have a tail that only completes when Stage 3 deletes `ui-core`.

  | # | Task | Kind |
  |---|---|---|
  | 1 | [retire-global-density](tasks/2026-08-22-retire-global-density-plan.md) phases 1–3 | Behavioural, then structural |
  | 2 | [ui-prefix-rename](tasks/2026-08-22-ui-prefix-rename-plan.md) — move, then Phase A | Structural, then mechanical |
  | 3 | [split-uipath](tasks/2026-08-22-split-uipath-plan.md) | Structural |
  | 4 | Decide whether the vertex/mesh types leave `core:graphics2d` | Decision, cheap after 3 |
  | — | *At Stage 3:* rename Phase B (delete typealiases) and density phase 4 (delete `UiDensity`) | Free |

  Density first because it is the only behavioural work here and wants reviewing alone, and because
  it deletes a type the rename would otherwise have to name. Rename before split so the split starts
  from final names and is reviewed once. Split before the module-boundary decision, because it turns
  that decision from an extraction into a one-line move.

- 2026-08-22: Splitting `UiPath.kt` is drafted in
  [2026-08-22-split-uipath-plan](tasks/2026-08-22-split-uipath-plan.md). 1,651 lines, 16 top-level
  types, named after one of them. Eight files along clusters that are already clean, no behaviour
  change, 31 existing tests to prove it. Runs after the prefix rename so the split starts from
  final names.
- 2026-08-22: The `Ui`-prefix rename of the 2D draw vocabulary is planned in
  [2026-08-22-ui-prefix-rename-plan](tasks/2026-08-22-ui-prefix-rename-plan.md). ~2,200 refs across
  ~200 files, but 60% sit in modules Stage 3 deletes -- so it splits into a surviving-graph pass now
  and typealias cleanup later. `UiDensity` is not a rename: two `Dp.toPx()` apply to one receiver,
  and the fix is moving density out of `core:math2d`.
- 2026-08-22: The editor's rebuild on the compose engine is drafted in
  [editor/01-compose-editor-plan-todo](tasks/editor/01-compose-editor-plan-todo.md). Studio's
  panels are not ported -- they are built on the trial-measure engine being replaced. Stage 0 is
  the plugin seam with no UI and no compose dependency; the UI stages are gated on
  `04-styling-theme`, `06-focus-text-input` and `08-lazy-lists`.
- 2026-08-24: Framework ownership, editor extension seams, and plugin installation are drafted in
  [2026-08-24-framework-boundary-and-plugin-ecosystem-plan](tasks/2026-08-24-framework-boundary-and-plugin-ecosystem-plan.md).
  Studio thinning is separately drafted in
  [2026-08-24-studio-thinning-plan](tasks/2026-08-24-studio-thinning-plan.md).
- 2026-08-25: Scene-session simplification is drafted in
  [2026-08-25-scene-session-simplification-plan](tasks/2026-08-25-scene-session-simplification-plan.md).
  It restores one app/Compose root, extracts a session and explicit schedule, and earns any
  physical module split only after package-level dependency boundaries are proven.
- 2026-08-21: Adaptive bulk ECS structural mutation is drafted in
  [2026-08-21-ecs-adaptive-bulk-mutation-plan](tasks/2026-08-21-ecs-adaptive-bulk-mutation-plan.md).
  Phase 0 freezes same-semantics baselines and the command-order contract before production code.
- 2026-08-20: Public/private template delivery is drafted in
  [2026-08-20-template-repository-plan](tasks/2026-08-20-template-repository-plan.md). The
  public game template proves the external Awake consumer path; the private MMO template layers
  product-specific client/server foundations on that validated baseline.
- 2026-08-20: Architecture-governance standardization is drafted in
  [2026-08-20-architecture-governance-standardization-plan](tasks/2026-08-20-architecture-governance-standardization-plan.md).
  It first restores truthful module/task verification, then aligns active docs, agent routing,
  and facade-versus-leaf dependency rules before considering any further core extraction.
- 2026-08-12: UI showcase visual-parity work is tracked in
  [docs/tasks/2026-08-12-ui-showcase-parity-tracker.md](tasks/2026-08-12-ui-showcase-parity-tracker.md).
  It is deliberately separate from the now-public-boundary-complete Headless migration: no
  component is marked visually complete without source, semantic, crop, and final-build proof.
- 2026-08-21: The path to a reproducible, full shadcn compatibility claim is drafted in
  [2026-08-21-shadcn-parity-tool-plan](tasks/2026-08-21-shadcn-parity-tool-plan.md). It retains
  the existing geometry, token, crop, and golden tools; repairs their stale status evidence; and
  adds the missing unified manifest plus behavior, semantics, focus, and motion trace oracles.
- 2026-08-05: API layering and the scene leaf-module split are complete. Use
  [docs/reference/api-layering.md](reference/api-layering.md) as the stable rule; the
  [completed plan](tasks/archive/2026-08-05-api-layering-plan.md) remains as history.

## Open Questions

- Where is the measured crossover between incremental family maintenance and one dirty-family
  rebuild across batch size, density, and family arity?
- After the internal prototype passes, should the opt-in batch entrypoint be public in v1 or remain
  an advanced API until a real consumer adopts it?
- Should entity destruction join the first batch contract or remain immediate until component
  add/remove semantics are proven?
- Should Awake v1 use one universal `Style`, or separate style types immediately for
  text/button/panel families?
- Which properties stay in `UiModifier`, and which must move into the new `Style` layer?
- Should the first UI DSL slice target inspector panels only, or should it also cover
  HUD/menu composition in the same pass?
- Do we want `animate { }` support in the first style pass, or only after the static style
  property model settles?

## Fix Lanes

- Dev: Adaptive bulk ECS structural-mutation prototype and decision benchmarks
- Dev: UI DSL and style audit
- Beta: None yet
- Stable: Core split, ECS/scene API layering, scene module split, sparse/tag family storage

## Task Log

- [2026-08-21-ecs-adaptive-bulk-mutation-plan](tasks/2026-08-21-ecs-adaptive-bulk-mutation-plan.md)
- [2026-08-24-framework-boundary-and-plugin-ecosystem-plan](tasks/2026-08-24-framework-boundary-and-plugin-ecosystem-plan.md)
- [2026-08-24-studio-thinning-plan](tasks/2026-08-24-studio-thinning-plan.md)
- [2026-08-25-scene-session-simplification-plan](tasks/2026-08-25-scene-session-simplification-plan.md)
- [2026-08-20-template-repository-plan](tasks/2026-08-20-template-repository-plan.md)
- [2026-08-20-architecture-governance-standardization-plan](tasks/2026-08-20-architecture-governance-standardization-plan.md)
- [2026-08-12-ui-showcase-parity-tracker](tasks/2026-08-12-ui-showcase-parity-tracker.md)
- [2026-08-21-shadcn-parity-tool-plan](tasks/2026-08-21-shadcn-parity-tool-plan.md)
- [2026-08-20-vulkan-webgpu-uncommonized-audit](audits/2026-08-20-vulkan-webgpu-uncommonized-audit.md)

## Archive Index

- [2026-07-09-decouple-world](tasks/archive/2026-07-09-decouple-world.md) -- completed World facade split
- [2026-08-05-api-layering-plan](tasks/archive/2026-08-05-api-layering-plan.md) -- classification and scene split complete
- [2026-08-18-ecs-hybrid-archetype-sparse-set](tasks/archive/2026-08-18-ecs-hybrid-archetype-sparse-set.md) -- archetype migration rejected
- [2026-08-21-ecs-storage-decoupling](tasks/archive/2026-08-21-ecs-storage-decoupling.md) -- implemented and verified
- [2026-08-21-ecs-family-tag-columns](tasks/archive/2026-08-21-ecs-family-tag-columns.md) -- implemented and benchmarked
- [2026-08-21-ecs-cached-type-id-churn](tasks/archive/2026-08-21-ecs-cached-type-id-churn.md) -- accepted optimization; micro follow-up rejected
- [2026-07-10-scene-runtime](tasks/archive/2026-07-10-scene-runtime.md) -- core split executed
- [2026-07-14-ui-dsl-audit](tasks/archive/2026-07-14-ui-dsl-audit.md) -- superseded by the 2026-08-11 headless boundary migration doc
- [2026-07-14-ui-module-split](tasks/archive/2026-07-14-ui-module-split.md) -- superseded by the 2026-08-11 headless boundary migration doc
- [2026-07-17-ui-api-simplification](tasks/archive/2026-07-17-ui-api-simplification.md) -- all implementation items done
- [2026-07-22-core-graphics-split](tasks/archive/2026-07-22-core-graphics-split.md) -- standing policy decided (no shared graphics module yet)
- [2026-07-24-uislot-narrowing](tasks/archive/2026-07-24-uislot-narrowing.md) -- superseded plan, UiSlot/Rectangle merged
- [2026-08-02-trial-measure-cross-frame-cache](tasks/archive/2026-08-02-trial-measure-cross-frame-cache.md) -- shipped (`cacheKey` param on row/column)
- [2026-08-02-trial-measure-double-execution](tasks/archive/2026-08-02-trial-measure-double-execution.md) -- shipped (`requiresMeasuredDistribution()`, commit `55dd0681`)
- [2026-08-05-scene-module-split-proposal](tasks/archive/2026-08-05-scene-module-split-proposal.md) -- complete, all five leaf modules exist
- [2026-08-10-glyph-scale-regression](tasks/archive/2026-08-10-glyph-scale-regression.md) -- resolved bug investigation
- [2026-08-11-ui-shadcn-headless-boundary-migration](tasks/archive/2026-08-11-ui-shadcn-headless-boundary-migration.md) -- superseded by the 2026-08-15 visual-policy decision
- [2026-08-14-ui-implementation-plan](tasks/archive/2026-08-14-ui-implementation-plan.md) -- superseded by 2026-08-14-ui-roadmap.md
