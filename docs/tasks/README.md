# Open items

What is actually outstanding, in one place. `docs/tasks/` holds plan documents, each with its own
`Status:` line, and reading all of them to find out what is left is the problem this file solves.

**Rule for this file:** a row here restates a status that lives in the plan itself. When a plan's
status changes, change it there first — this index follows, it does not lead. An item with no plan
document gets a row in "Loose items" below rather than a new plan nobody asked for.

Last reconciled against the tree: **2026-08-28**.

## Plans

| Plan | Status |
|---|---|
| [render-architecture-finalization](2026-09-09-render-architecture-finalization-plan.md) | **draft** — audited at `36fe7c2f0`; phases assigned to Astra 6 (architecture/integration) and Gemini 3.8 Flash (bounded follow-ups). Start with A0 flicker recovery; runtime work not started. |
| [terrain-surface-layers](2026-09-27-terrain-surface-layers-plan.md) | **proposed** — unbounded layer palette with a fixed-cost top-4 control map; Core gains a terrain surface seam and scene binding (amends D28); three open decisions block implementation |
| [node-graph](2026-09-27-node-graph-plan.md) | **active** — `:awake:node-graph` model and node registry plus a `:awake:ui:node-graph-canvas` canvas (nothing in `:awake:compose`) for Studio (coders + non-coders, LLM later); consumers in order: blueprints, AI state trees, shader graphs. Model, registry and canvas are done |
| [blueprint-runtime](2026-09-27-blueprint-runtime-plan.md) | **active** — event-graph game logic as data, run by one ECS system and reloaded live from Studio; seven design decisions made (execution + data wires, compiled program + per-entity slots, latent actions, live reload, plain-class nodes, trace, Logic/Presentation effect); prerequisites and the `:awake:blueprint` runtime are done, `:awake:scene:blueprint` is #123 |
| [shader-graph](2026-09-27-shader-graph-plan.md) | **proposed** — graph → ASL → WGSL with terrain and mesh targets; pack shaders are targets, pack functions and ASL builtins are nodes; waits on node-graph phases 1–2, shader hot reload and a per-material shader render plan |

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
re-baselined** — the reference is now the correct one.

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
  the Gradle `awakeVerify` task now gates it.
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
- ~~**`:awake:core:text` belongs in `:compose:ui`**~~ — superseded by a correct split: the *atlas*
  goes down to `core:graphics2d` beside the `Glyph` that carries its UVs, and only
  `TextStyle`/`FontWeight` go up to `:compose:ui`. Original note, for the record:
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

- **`AGENTS.md`/`GEMINI.md` drift from `CLAUDE.md`.** Pre-existing; the skills repository owns
  bundle synchronization, while Awake verifies only its pinned consumer lock.
- **`ronjunevaldoz/kmp-agent-skills#6`** — docs-hygiene consumer-project gap, open.
- **Figma design tokens** — deferred deletion of `design-tokens.json` and the Figma test utilities
  from `ui-shadcn`, waiting on the module rename.

## Not open

Recorded so they are not re-investigated:

- **`RotatingCubeDemo` "blinking"** — exhaustively investigated, never reproduced. Needs a specific
  trigger before anyone spends time on it again.
- **The 2026-08-17 UI refactor audit** — closed 2026-09-01 without finishing package 6. It plans
  sweeps over `ui-core`/`ui-headless` widgets that `awake:compose` deleted, and its central item
  (replacing callbacks with a return-value idiom) is the opposite of what retained-mode Compose
  wants. Contrary to the current design, not merely stale.
- **Spotless** — not a gate here. Only detekt runs in CI and hooks; roughly 26 modules already fail
  spotless, so a spotless failure is almost certainly not caused by your change.
