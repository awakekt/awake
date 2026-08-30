# Open items

What is actually outstanding, in one place. `docs/tasks/` holds fifteen plan documents, each with
its own `Status:` line, and reading all fifteen to find out what is left is the problem this file
solves.

**Rule for this file:** a row here restates a status that lives in the plan itself. When a plan's
status changes, change it there first — this index follows, it does not lead. An item with no plan
document gets a row in "Loose items" below rather than a new plan nobody asked for.

Last reconciled against the tree: **2026-08-28**.

## Plans

| Plan | Status |
|---|---|
| [vendor-the-reference-app-components](2026-08-23-vendor-the-reference-app-components.md) | **done** — generator + re-vendor landed; see what it exposed |
| [ui-tooling-formalization](2026-08-23-ui-tooling-formalization-plan.md) | **done** — five gates, all generators gated or documented as ungateable |
| [shadcn-palette-from-table](2026-08-23-shadcn-palette-from-table-plan.md) | **done** — all five steps plus the three formalisation gaps |
| [shadcn-parity-handoff](2026-08-25-shadcn-parity-handoff.md) | **active handoff** — current commits, evidence, resume procedure, and immediate queue for the parity owner |
| [stage-3](2026-08-23-stage-3-plan.md) | **planned, not started** — shadcn straight onto `:compose:foundation`; three layers become two. **Nothing blocked**: both prerequisites closed 2026-08-23 |
| [font-belongs-in-core-graphics](2026-08-23-font-belongs-in-core-graphics.md) | **planned** — `DrawCommand.Glyph` carries atlas UVs from `core:graphics2d` while the atlas lives two modules up |
| [shadcn-compose-adoption](2026-08-23-shadcn-compose-adoption.md) | analysis — feeds Stage 3 step 3; four patterns to take, two not to |
| [compose-node-local-shadow](2026-08-25-compose-node-local-shadow-plan.md) | **implemented** — node-local `drawShadow`, Compose-shaped `dropShadow`, and the linear-gradient brush subset; intentionally narrower than `graphicsLayer` |
| [compose-stage-2-invalidation](2026-08-25-compose-stage-2-invalidation-plan.md) | **in progress** — explicit retained skip scopes and UI-local state-read invalidation landed; select and measure one Studio consumer |
| [does-a-headless-tier-earn-itself-back](2026-08-23-does-a-headless-tier-earn-itself-back.md) | **open question** — answered by evidence gathered during Stage 3, not before |
| [rhi-gpudevice](2026-08-23-rhi-gpudevice-plan.md) | **in progress** — phases 0-4 done, 4b closed by the content split; phase 5 and the import-ledger phase remain |
| [what-nothing-checks](2026-08-24-what-nothing-checks-plan.md) | **in progress** — CI gates done; open: triage the 767-finding audit before wiring it, and the unenforced per-frame allocation rule |
| [heightfield-physics-shape](2026-08-24-heightfield-physics-shape-plan.md) | **in progress** — common collision contract and verified jolt-jni mapping; iOS/wasmJs capability gaps are explicit; terrain rendering out of scope |
| [terrain-rendering](2026-08-24-terrain-rendering-plan.md) | **in progress** — shared asset and single-grid Studio sample landed; local surface queries, then tiled patches reusing existing MeshBounds/LodGroup before crack-safe terrain LOD and optional collision lifecycle |
| [animation-player](2026-08-24-animation-player-plan.md) | **in progress** — player, glTF adapter, scene bridge, and CesiumMan wiring landed; Studio visual/performance verification remains |
| [render-plan-and-app-seam](2026-08-24-render-plan-and-app-seam-plan.md) | **done** — RenderPlan, backend capabilities, shared UI shader list; `:awake:engine:app` deleted, and the per-target entry point turned out to be irreducible |
| [ui-render-pipeline-contract](2026-08-28-ui-render-pipeline-contract-plan.md) | **implemented** — common font sampling and pipeline descriptors, shared UI preparation, and descriptor-driven Vulkan/WebGPU UI pipelines; resource manager and frame-loop work remain separate |
| [backend-content-split](2026-08-23-backend-content-split-plan.md) | **done** — every phase; exemption list 14 → 0. But empty means "no content in a declared name": RendererDraw3D still builds a shadow box and the check cannot see it |
| [ui-prefix-rename](2026-08-22-ui-prefix-rename-plan.md) | planned, not started — blast radius measured; **gated on `:awake:compose` finishing** (`UiDensity` collides with compose's `Density`) |
| [split-uipath](2026-08-22-split-uipath-plan.md) | planned, not started |
| [retire-global-density](2026-08-22-retire-global-density-plan.md) | planned, not started |
| [pathfilltessellation-cleanup](2026-08-22-pathfilltessellation-cleanup-plan.md) | planned, not started |
| [ecs-adaptive-bulk-mutation](2026-08-21-ecs-adaptive-bulk-mutation-plan.md) | narrowed — no implementation authorized by the document |
| [architecture-governance-standardization](2026-08-20-architecture-governance-standardization-plan.md) | draft — no rename authorized |
| [template-repository](2026-08-20-template-repository-plan.md) | draft |
| [modifier-layout-compose-parity](2026-08-21-modifier-layout-compose-parity-plan.md) | no status line |
| [render-3d-bugfixes-and-shader-module-split](2026-08-21-render-3d-bugfixes-and-shader-module-split-plan.md) | no status line |
| [shadcn-parity-tool](2026-08-21-shadcn-parity-tool-plan.md) | no status line |
| [ui-tooling-simplification](2026-08-16-ui-tooling-simplification.md) | superseded in practice by the formalization plan; retirement criteria still live there |
| [navgrid-navigation](2026-08-30-navgrid-navigation-plan.md) | **draft, not started** — heightmap-derived walkability grid + A* in `commonMain`, replacing the `recast4j` backend that left with `samples:scene3d-playground`. Gate is met: terrain clipmap draw is complete |
| [behavior-tree-state-machine](2026-08-30-behavior-tree-state-machine-plan.md) | **draft** — research + sequencing; one hybrid runtime instead of separate BT and FSM frameworks, LLM confined to authoring time and an off-frame director. Phase 0 (make `ChaseAiSystem` query-driven) is the only part not gated behind terrain/streaming |

Three plans carry no `Status:` line at all, so nothing states whether they are live. That is its
own small item — see below.

## Loose items

Open work with no plan document. Each names its evidence so it can be picked up cold.

### ~~`awake:ui:testing` -- the name is fine; one edge is not~~ **Closed 2026-08-24**

The wrong edge is gone: `backend:vulkan` no longer reaches into a `ui:` module. `PixelMap`,
`PixelProbe`, `comparePixels`/`PixelDiffResult`, `FrameSpans` and `TimingBaseline` now live in
`awake:engine:render:testing` under `awake.render.testing`; everything UI-specific
(`AwakeUiSnapshot*`, `AwakeUiPreview*`, `FigmaModeMatrix`) stayed put, and `ui:testing` `api()`s
the new module so its own validators still see them.

Two conclusions this item reached are worth keeping, because both were re-derived the hard way:

- **`core:testing` was the wrong destination**, and the eventual one proves it. That module would
  have had to depend *up* into `ui-core`/`render:contract`/`compose`, inverting the layering. The
  primitives went to a *render* module instead, which is what they actually serve.
- **A partial move is worse than none.** The move landed in two halves -- `PixelMap`/`PixelProbe`
  first, with `backend:vulkan`'s dependency switched at the same time, while `comparePixels` (the
  function that module's own build file names as its reason for the dependency) stayed behind.
  That took the entire `backend:vulkan` desktopTest source set out of compilation, shadow gate
  included, until `compileTestKotlinDesktop` caught it (`2784793f3`). Move the symbol and its
  consumers in one commit, or neither.

### Three shadcn parity failures, found by fixing the reference

Discovered when the re-vendor corrected components that had drifted from upstream. **None may be
re-baselined** — the reference is now the correct one. Full framing in
[vendor-the-reference-app-components](2026-08-23-vendor-the-reference-app-components.md).

| Failure | Evidence | Shape of the fix |
|---|---|---|
| `kbd` corner radius | reference **6**, Awake **4** | a radius constant in `ui-shadcn` |
| `toggle` horizontal padding | upstream moved `px-3` → `px-2`; Awake still 12px, which also pushes every following toggle 24px right | a padding constant |
| `dropdown` trigger semantics | Awake emits no `parity-dropdown.trigger` node at all; the reference has always had one | a missing semantic node, not a token — real authoring work |

The first two are constants. The third is the only one that is not.

### Skills over the size recommendation

`awake-ui-authoring` was split into `references/` (10.4k → 2.1k tokens). Two remain, flagged by the
`skill-spec` gate as warnings rather than failures:

- `awake-shadcn-recipe-authoring` — ~7,830 tokens
- `awake-ui-verification` — ~5,700 tokens

Same treatment: keep the decision rules and the checklist at activation, move detail into
`references/`. Verify the split by extracting every heading from the previous revision and
confirming each survives, rather than asserting it.

### `:compose` follow-ons

- **Semantics adapter for the parity tooling.** Every UI tool depends on one JSON contract — a flat
  list of `{id, bounds}` beside a preview PNG. Compose's `SemanticsNode` tree differs in identity
  (`testTag`, not `id`), bounds type (four `Int`s, not a `Rectangle`) and shape (tree, not flat).
  One adapter; already the `09-testing-harness.md` deliverable. See "Does any of this need
  replacing for `awake:compose`?" in [`tools/README.md`](../../tools/README.md).
- **`generate_ui_status.py` probes hard-code module paths** (`awake/ui/ui-core/src`,
  `awake/ui/headless/src`, `awake/ui/shadcn/src`). It reports on the module layout, so it
  follows the modules. A path list, not a redesign.
- **Icons need `:compose:ui` to depend on `:awake:ui:graphics`** — one build-file line the plan
  already called for. The path primitives, `emit`, and `UiPath` are all present; only
  `UiImageVector.fitTo` is out of reach. A `DrawScope.drawPath` convenience is worth adding on top
  but is not a prerequisite.
- ~~**detekt baselines are stale**~~ — measured and fixed 2026-08-23: 34 of 525 entries named files
  that no longer existed, and 32 of those were `awake/ui/graphics` alone, whose baseline was **80%
  dead** after `UiPath`/`UiGradient` moved to `core:graphics2d`. Pruned, and
  `tools/verify_detekt_baselines.py` now gates it.
- **`ComposeFrameProbe`'s allocation ratchet is breached and the reading is no longer stable.**
  Five runs gave 35537, 35537, under-ceiling, 35177, 35177 against a 35000 B ceiling. The probe's
  own note says readings "are exact now" and to "keep the ratchet above the band"; neither holds.
  It is not the cursor work: a paired run with those edits stashed produced the same 35537 to the
  byte, and nothing in that change allocates. Raising the ceiling would be re-baselining a gate to
  make it green, so it stays failing until someone profiles what moved.
- **Retiring `ui-core` is a screen migration, not a type migration.** Measured rather than assumed:
  the render backends needed exactly one type from it, `UiCursor`, now `core:input`'s
  `PointerCursor`. `UiFont` was never `ui-core`'s -- it is `ui:text`, a keeper. What is left is
  `scene:runtime` (8 imports) and `engine:bootstrap` (17), and both construct a `UiContext` and
  drive a frame, so they move when there is a compose host to move them to. Everything else is
  `ui-headless` (100 files), the legacy designsystem recipes (17) and the showcase pages (~50).
- **The compose recipes style fewer interaction states than `ui-core` did.** `fieldStyle` branches
  on `focused` and `disabled` only, and the slider branches on none, so the slider/input/textarea
  state matrices were deleted rather than moved: forcing hover and press through the new
  `interactionSource` seam produced three identical PNGs per component, which reads as coverage and
  checks nothing. They come back when the fields gain a hover state and a real focus ring and the
  slider gains hover/press on its thumb. The toggle and switch matrices did move, because those two
  do style hover.
- **`Tw.Text` carries half a Tailwind text step.** A step is a pair -- `text-sm` is
  `font-size: 14px; line-height: 20px` -- and the generated `Tw.Text` emits only the font size, so
  every consumer either restates the line height or silently inherits the font's own metric.
  `ShadcnTextVariant` states it for the nine shadcn variants; `:awake:tailwind-generator` should
  emit the pair so nothing else has to. Found when a dropdown item measured 29px against shadcn's 32.
- **The shipped glyph atlas is ASCII-printable only.** `RobotoRegularUiFontData` covers
  `#$%&'()*+,-./0-9:;<=>?@A-Z[\]^_`a-z{|}~` and nothing else, so an ellipsis, curly quote, em dash
  or accented character renders as the missing-glyph `?` with no error. Found when `"Select…"`
  rendered as `Select?` in a preview. Any UI string outside ASCII is affected, which makes this a
  localisation blocker as much as a typography one.
- **`UiFonts.default()` renders below the atlas it ships.** The weighted Roboto family is generated
  at `baseCellSize = 16` (atlas 656x300) and `PackedUiFont` defaults to that, but `UiFonts.default()`
  and `trueSans()` both pass `cellSize = 12`, overriding it downward. So `text-sm` (14) and
  `text-base` (16) are scaled up from a 12px rendering of a 16px atlas, which is why ported
  components read soft in the preview renders. Not changed here: the default moves text metrics
  repo-wide and every snapshot baseline with them, so it is a visual decision with its own review.
  [[MsdfFont]] is the other half of the same question -- a distance field scales without this
  trade at all.
- **`MsdfFont` is built but not wired.** `UiFonts.msdf()` has no callers, so the distance-field
  text path is also uncovered by any test. Text renders through the embedded bitmap font, which is
  the standing decision until quality demands otherwise. Recorded because unused code is
  indistinguishable from dead code without this line — it was deleted once on that reading.
- ~~**`:awake:core:text` belongs in `:compose:ui`**~~ — superseded by
  [font-belongs-in-core-graphics](2026-08-23-font-belongs-in-core-graphics.md), which splits it
  correctly: the *atlas* goes down to `core:graphics2d` beside the `Glyph` that carries its UVs,
  and only `TextStyle`/`FontWeight` go up to `:compose:ui`. Original note, for the record:
  `androidx.compose.ui.text.TextStyle` and `...text.font.FontWeight` are compose-ui's. 11 files
  (9 font, 1 scope, 1 theme), consumers are `ui-core`, `ui-headless` and `:compose:ui`, and its
  `commonMain` depends only on `core:math2d`/`core:color` — so no cycle, unlike before. Deliberately
  *not* urgent: unlike the `ImageVector` move, which unblocked icons, this unblocks nothing —
  `TextStyle` and `FontWeight` are reachable today through `:compose:ui`'s `api(":awake:core:text")`.
  Its own mechanical commit, whenever.
- **The Tailwind generator emits no border-width scale.** `Tw` has `Spacing`/`Radius`/`Text`, so
  `border-0`/`border`/`border-2`/`border-4`/`border-8` have no named home -- which is what all 26
  `1f.dp` literals in `ui-shadcn/styles/` are. Measured 2026-08-23: 73 of 99 raw literals
  there *do* have an exact `Tw` step already, so the rest is discipline, not a missing scale.
- **Batch 2 modifier leftovers**, each blocked on something real rather than deferred by choice:
  `rotate` (needs a vertex layout change plus four shaders), `graphicsLayer` (needs `drawUi` to
  accept a `RenderTarget`), `zIndex` (deferred).

### Housekeeping

- **Three plans have no `Status:` line** (`modifier-layout-compose-parity`,
  `render-3d-bugfixes-and-shader-module-split`, `shadcn-parity-tool`), so nothing says whether they
  are live, superseded or done. Adding one to each is cheap and makes this index honest.
- **`AGENTS.md`/`GEMINI.md` drift from `CLAUDE.md`.** Pre-existing; `verify_agent_skills_sync.py`
  checks the mandatory-skill lists match but not the rest of the content.
- **`docs/audits/2026-08-17-ui-refactor-vs-recreate-audit.md`** — package 6 pending.
- **`ronjunevaldoz/kmp-agent-skills#6`** — docs-hygiene consumer-project gap, open.
- **Figma design tokens** — deferred deletion of `design-tokens.json` and the Figma test utilities
  from `ui-shadcn`, waiting on the module rename.

## Not open

Recorded so they are not re-investigated:

- **`RotatingCubeDemo` "blinking"** — exhaustively investigated, never reproduced. Needs a specific
  trigger before anyone spends time on it again.
- **Spotless** — not a gate here. Only detekt runs in CI and hooks; roughly 26 modules already fail
  spotless, so a spotless failure is almost certainly not caused by your change.
