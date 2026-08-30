# Modifier/Layout Compose-parity plan

Companion to [`mirror-map.md`](../reference/mirror-map.md) (the real, evidence-based
Faithful/Diverges catalog every claim below is drawn from) and
[`compose-modifier-layout-guidance.md`](../reference/compose-modifier-layout-guidance.md)
(the how-to). This doc is the plan to close the remaining gaps between them and real
Compose behavior, systematically instead of bug-by-bug, plus a look at where the
underlying model itself is complex enough to be worth simplifying.

## Where this actually stands today (read before planning more work)

`mirror-map.md`'s own tally, verified against real source, not assumed:

- **Modifier**: 9 Faithful, 4 Diverges (scroll-modifier overwrite, `graphicsLayer` block
  shape, `clickable`'s opt-in-per-widget resolution, standalone `alpha()`/`scale()`
  shorthand-vs-lambda shape), 0 Not implemented.
- **Scope/DSL** (`row`/`column`/`box`/`Arrangement`): 4 Faithful, 3 Diverges (the
  two-pass trial-measure model — partially optimized, still diverges in the general
  case; `LazyColumn`/`LazyRow` real but fixed-item-height-shaped), 1 Not implemented
  (`AbsoluteScope`, a deliberate non-analog).
- **Animation**: 2 Faithful, 5 Diverges (call-shape differences, no reusable
  `AnimationSpec` value type, the forced trial-measurement guard, `rememberTransition`'s
  scoped-down single-progress-value shape).
- **GraphicsLayer**: 2 Faithful (alpha real, scale real — both GPU/CPU-verified), 1
  Diverges (marker-hook block shape).

This is **not a cold-start audit** — real bugs in this exact space have already been
found and fixed this cycle: the `weight()`+`FillMax` starvation order-dependency
(`9455bc51`), a `verticalScroll()` default-styling bug with zero Compose analog (found
by behavior, not by API-shape diffing), the `clickable` modifier piloted end-to-end on
`surface()`, real GPU-side `alpha`/`scale` compositing, and three rounds of trial-measure
performance work (`docs/tasks/archive/2026-08-02-trial-measure-*.md`). The plan below
is about making the *remaining* gaps systematic and closing the ones worth closing —
not re-discovering what's already documented.

## Current test coverage — real, but grown bug-by-bug, not systematic

`awake/ui/ui-core/src/commonTest/` has 27 test files (plus 51 in `ui-headless`). Real coverage exists, but it's
organized by *bug/mechanism* (`WeightTrialReuseTest`, `WrapContentScrollLeakProbeTest`,
`TrialMeasureScalingTest`, `FillMaxUnboundedParentTest`), not by *primitive* — there is
no single file (or even a discoverable naming convention) that says "here is every test
covering `Modifier.weight()`" the way `mirror-map.md`'s table lets you find every claim
about `weight()` in one row. A primitive can be well-tested (weight is, extensively) or
have real coverage gaps, and there's no fast way to tell which from the test suite alone
— you have to cross-reference against `mirror-map.md` by hand, which is exactly what
this plan's Phase 1 turns into a real, one-time deliverable instead of a recurring
manual chore.

## Two different parity axes — do not conflate them

This is the single most important framing in this plan, and the thing most likely to
cause duplicated or misdirected work:

| | **Axis A — Compose behavioral parity** | **Axis B — shadcn visual parity** |
|---|---|---|
| Question | Does `Modifier.weight()` behave like *Compose's* `weight()`? | Does `shadcnButton` look like *real shadcn/ui's* button? |
| Layer | `ui-core` / `ui-headless` primitives | `ui-designsystem` recipes, rendered |
| Oracle | Compose's documented semantics | Pinned shadcn/Radix fixtures captured in Chromium |
| Tooling | Plain unit tests (`ui-core/src/commonTest`) | **`scripts/awake ui`** — already built, see [`ui-parity-tool.md`](../reference/ui-parity-tool.md) |
| Status doc | [`mirror-map.md`](../reference/mirror-map.md) | [`ui-fidelity-status.md`](../reference/ui-fidelity-status.md), `tools/shadcn_parity_manifest.json` |
| This plan | **Phases 1–2 build this** | **Phases 3–4 consume this — do not rebuild it** |

Axis B already has a real, working, source-backed toolchain (`scripts/awake ui
reference|preview|validate|report|performance`, 7 registered cases across button,
button-group, card, dropdown-menu, popover, landed in `0dcd4ef65`). Nothing in this plan
should reinvent it. Design-system's `*FidelityTest.kt` files are part of that same Axis B
family.

**The two axes are causally linked, which is why this plan spans both.** The parity
tool's own triage table says an Axis B symptom usually has an Axis A root cause —
"child geometry drifts but parent passes → inspect `fillMax*`, intrinsic measurement,
weights, and child modifiers." That is precisely the primitive set Phase 1 covers. So the
existing parity tool is already an empirical detector for the bugs Phase 1 is trying to
prevent in the abstract, and Phase 3 should exploit that rather than invent a new
verification surface.

## Phase 1 — systematic Modifier/Layout test matrix

Goal: one test suite structured so "does `Modifier.X` behave like Compose's `Modifier.X`"
is answerable by running one file, not grepping 26 files for coincidental coverage.

1. Walk `mirror-map.md`'s `UiModifier` and `Scope/DSL` tables row by row (13 + 9 rows).
   For each **Faithful** row, confirm a real test exists that would fail if the
   behavior regressed — if the row's own "Deviation detail" cites a specific fix commit
   (e.g. `9455bc51` for weight/fillMax), find that fix's test and confirm it's still
   the one asserting the behavior, not a since-deleted or renamed test.

   **Verify each row's claim against source, don't just test against it.** `mirror-map.md`
   states every row is "backed by a direct read of the current source" — but at least one
   row is already stale (see the scroll-modifier correction in Phase 4 item 1 below, found
   during this plan's own review). A row that misdescribes current behavior will produce a
   test pinning the wrong thing, which is worse than no test. When source and the doc
   disagree, fix the doc row as part of Phase 1 rather than leaving the next reader to
   rediscover it.
2. For each row with **no** confirmed regression test, write one — named and grouped so
   a future contributor can find "the weight tests" or "the fillMax tests" as a set, not
   scattered by the bug that originally motivated each one. Prefer extending an existing
   well-scoped file over adding a 27th top-level file where a natural home exists.
3. Produce the actual matrix as a table in this doc (or a generated companion file) once
   done: primitive | Compose semantic it claims | test file(s) | confirmed / gap-filled.
   This is the concrete "full unit test" deliverable the plan's goal names — a real,
   checkable list, not a vague "more tests" gesture.

**Do not test the Diverges rows as if they were bugs.** Every Diverges row in
`mirror-map.md` is either a documented, deliberate scope decision (e.g. `AbsoluteScope`
has no Compose analog on purpose) or a real gap already tracked with its own reasoning
(e.g. `clickable`'s opt-in resolution, chosen because Awake's click detection is
id-based, not automatic-per-composable the way Compose's pointer-input pipeline is).
Writing a test that asserts Compose-identical behavior for a row the doc says
*intentionally* diverges would just be pinning the wrong answer. Where a Diverges row
is a live latent bug candidate (not a deliberate decision), that's Phase 4's job, not
Phase 1's.

## Phase 2 — simplify, don't just cover

`mirror-map.md` names the real complexity concentration by itself: the **two-pass
trial-measure model** (`row()`/`column()` re-executing their own content lambda to learn
sizes before really measuring) is the single largest source of divergence from Compose's
single-pass `Constraints` propagation, and it's had three separate optimization passes
already (`55dd0681`'s arrangement short-circuit, the trial-`UiContext` reuse fix, the
`id`/`cacheKey` weight-cache) without changing the underlying two-pass shape. Each pass
measured a real, smaller win than hoped (the doc says so plainly — "confirms the
original doc's own honest caveat... was optimistic").

This phase is explicitly **investigation, not a promise to rewrite the layout engine**:
1. Read the three archived trial-measure task docs
   (`docs/tasks/archive/2026-08-02-trial-measure-*.md`) to understand exactly what's
   been tried and why each fix landed a smaller win than the model change would.
2. Assess whether the *next* real lever is a fourth incremental optimization, or whether
   the two-pass model itself has reached the point where a structural change (a real
   lazy-children-gathering phase, replacing "trial-measure then re-measure" with
   something closer to Compose's single-pass negotiation) is warranted. This is a large,
   real architecture decision — do not start it without sizing the blast radius first
   (every `row()`/`column()` call site with a `WrapContent` axis or weighted child is a
   consumer of the current model).
3. Smaller, safer simplification candidates to size independently of the trial-measure
   question: the animation primitives' lack of a reusable `AnimationSpec` value type
   (each `animateFloatTween` call site repeats its own duration/easing literals — a real
   maintainability gap `mirror-map.md` calls out directly), and whether `clickable`'s
   opt-in-per-widget resolution pattern (now piloted on `surface()`) should extend to
   `button`/`checkbox` (deliberately left unmigrated so far, each hand-rolling its own
   `interact()` call) for one consistent click-resolution path instead of two.

Report findings before committing to any structural rewrite — this phase's deliverable
is a sized, evidence-based recommendation, not code.

## Phase 3 — verify real consumers using the parity tool that already exists

Not a new test-writing exercise, and **not a new tool**. Run the existing Axis B
toolchain and read its geometry/padding/spacing findings as evidence about Axis A
primitive correctness:

1. Run the full registered set — `scripts/awake ui reference|preview|validate|report`
   over all 7 manifest cases (button, button-group ×2, card, dropdown-menu, popover) —
   and capture the current baseline before any Phase 4 changes. `button-group` is the
   highest-value case here: it's the exact `weight()`+`FillMax`-sibling shape that
   produced the starvation bug (`9455bc51`) *and* the vertical-fill bug fixed in
   `d8d27031f` this cycle.
2. Triage every `drift` in the tool's own documented order (artifacts → geometry →
   padding → spacing → layout intent → style → paint). For each, classify the root cause
   layer: recipe-local (Axis B, fix in `ui-designsystem`), or primitive (Axis A, fix in
   `ui-core`/`ui-headless` — and then it belongs in Phase 4 and probably wants a Phase 1
   matrix row it currently lacks).
3. That classification is this phase's real deliverable: an evidence-backed list of which
   visible shadcn drifts are actually `Modifier`/layout defects wearing a design-system
   costume. It converts Phase 4 from a doc-derived guess-list into a list ordered by real
   observed consumer impact.
4. Anything the tool reports as `unmeasured` for a geometry/padding field is a gap in the
   *evidence*, not a pass — per the tool's own rules. Where a Phase 1 primitive claim has
   no consumer-level evidence either way, that's worth knowing explicitly rather than
   assuming the unit test generalizes.

`samples/studio` and `samples/ui-showcase` remain the integration surfaces for anything
the manifest doesn't cover (this session's id-collision bugs were found exactly that way
— by rendering real screens, not by unit-testing primitives in isolation).

## Phase 4 — parity testing and fixing (the live Diverges rows)

Only after Phases 1–3 give a real, current picture: pick off the Diverges rows that are
genuine gaps (not deliberate scope decisions) one at a time, same discipline as every
other item landed this cycle — investigate the real current shape first (line numbers
and specifics drift fast in this codebase), size the real blast radius, land a scoped
fix, verify, document the outcome in `mirror-map.md` (move the row from Diverges to
Faithful, or record why it's staying Diverges on purpose).

Real candidates already named in `mirror-map.md`, roughly ordered by how contained they
look (smallest/clearest first, not a commitment — re-verify before starting each):

1. `Modifier.verticalScroll()`/`.horizontalScroll()` — **`mirror-map.md`'s row for this is
   itself wrong; corrected here during this plan's review (2026-08-21), verify before
   acting.** The doc claims the second call overwriting the first means "no bidirectional
   scroll." Verified against source: the *overwrite* is real (`ScrollModifiers.kt`'s two
   functions are byte-for-byte identical — same `copy(scrollState, scrollConfig)` body, no
   axis field anywhere), but the *conclusion* is not. `scrollPanel` (`ScrollContainers.kt`)
   paints and scrolls both axes independently off measured content overflow
   (`verticalNeeded`/`horizontalNeeded` → `paintScrollThumb(axis = Height)` and
   `(axis = Width)`), and `UiScrollState` is genuinely 2D (`initialOffsetX` +
   `initialOffsetY`). So bidirectional scrolling likely works fine; which of the two
   identically-bodied modifier functions you called has no effect on it.

   The real divergence is an API-honesty one, not a broken-behavior one: Compose's
   `verticalScroll`/`horizontalScroll` are genuinely axis-scoped and independently
   composable, while Awake's pair are decorative aliases over one content-driven 2D
   container — the names imply per-axis control that does not exist. Also relevant, and
   not mentioned in the doc's row at all: only `column()` reads `scrollState`; `row()` and
   `box()` now *throw* on a scroll modifier (`UnsupportedScrollModifierTest` pins this
   deliberately). Decide the real fix shape only after confirming the above first-hand —
   candidates range from "collapse to one honestly-named `scrollable()`" to "make the two
   genuinely axis-scoped," and they are materially different amounts of work.
2. `clickable`'s extension from `surface()` to `button`/`checkbox` — real design
   decision (does the codebase want one click-resolution path or is the split
   intentional long-term?), not purely mechanical; needs the decision made explicitly,
   not assumed.
3. `graphicsLayer` rotation + clip-transform-under-scale — named as "the remaining
   load-bearing gap" in the GraphicsLayer section; real GPU work in both backends, the
   largest single item on this list.
4. The two-pass trial-measure model itself — deferred to Phase 2's own investigation,
   not started here without that sizing.

## Tailwind translation — full review, and the clear path (2026-08-21)

**Is `Tw` enforced? No.** `ui/designsystem/styles/` contains **86 raw `Nf.dp` literals and
zero `Tw.Spacing` references**, despite `Tw` being the designated design-system spacing
scale after B12 (which deleted `ShadcnSpacing` and `UiSpacing` precisely so `Tw` would be
the single named scale). `Tw` is currently used in only three non-test designsystem files.
The parity tool's sizing table mandates `Tw` only for `gap-N`; for `p-N` it says
"named `UiInsets` / source-scale padding", which nothing enforces.

**But "convert all 86" is the wrong instinct.** Broken down by what they actually are:

| Consumer | Count | Verdict |
|---|---|---|
| `border(...)` | 25 (24 of them `1f.dp`) | **Not spacing.** Tailwind's `border` default is 1px; it is not on the spacing scale. Converting these to `Tw.Spacing` would be wrong. |
| `contentPadding(...)` | 12 sites / 22 values | **The real translation candidates** — these are direct `p-*` translations. |
| `shape(...)` | 3 | Radii — belong to `values.shapes.*`, a different scale. |
| size-enum entries | 5 | Component heights, legitimately fixed. |

**Every `contentPadding` value except two lands exactly on Tailwind's scale**, which is
strong evidence they *were* translated from real classes and then written as bare numbers:
`8.0→s2`, `6.0→s1_5`, `12.0→s3`, `4.0→s1`, `16.0→s4`, `24.0→s6`, `10.0→s2_5`. The
exceptions are `0.0` (just zero, fine) and exactly one genuine off-scale value —
`ShadcnNavigationStyles.kt:37`'s `contentPadding(3f.dp)`. Tailwind has no 3px step
(`0.5`=2px, `1`=4px), so that one is either deliberate or a mistranslation, and it is worth
a look on its own.

### Clear path, in order

1. **`contentPadding` → `Tw.Spacing.sN`** (12 sites). Mechanical, all but one map exactly,
   and it makes each value's shadcn origin self-documenting instead of a number to
   re-derive. Do it per-site with the source class in view, not as a blind sed.
2. **Investigate `ShadcnNavigationStyles.kt:37`'s `3f.dp`** — the single off-scale padding.
   Either it maps to a real class nobody recorded, or it is a translation error.
3. **Name the border width once** (24 identical `1f.dp` borders). Tailwind's `border` is
   1px; a single `ShadcnBorderWidth` constant removes 24 repeats and, more importantly,
   gives the border-box fold (below) one place to reference.
4. **Audit the other 9 bordered styles for the border-box under-inset** — `alert`, `table`,
   `toast`, `kbd`, `tab`, `surface`, `popover`, `sheet`, `drawer` all pair a 1px border with
   `contentPadding`, the exact shape that made `shadcnDropdownMenu` 1px-per-side too small
   on both axes (fixed in `b92245195`). **Each needs a registered parity case first** —
   there is currently no captured reference for any of them, and fixing without one would
   pin an unverified number. Registering the case is the work; the fix is one line after.

Step 4 is the highest-value item on this list: it is a known-shape defect with a proven fix
and nine live suspects, gated only on capturing references.

## The thesis, stated plainly

**Fix the `ui-core` `Modifier`/layout primitives, and a class of `ui-headless` /
`ui-designsystem` bugs stops being fixable-one-at-a-time and starts being fixed at the
source.** This is not speculative — it's the pattern this codebase has already lived
through repeatedly:

- The `weight()`+`FillMax` starvation bug (`9455bc51`) surfaced as *three separate*
  design-system symptoms (`shadcnField*` controls, a checkout-form grid row,
  `shadcnToggleGroup`) that each got a local `weight(1f)` workaround. One primitive fix
  retired the whole class; `mirror-map.md` notes the three workarounds are now optional
  cleanup rather than load-bearing.
- The vertical button-group fill bug (`d8d27031f`, this cycle) was fixed in
  `RowScope`/`ColumnScope`'s `FillMax` resolution — a `ui-core` change — not in the
  button-group recipe.
- `awake-ui-authoring`'s own "Escalate layout-engine defects; do not hide them in a
  recipe" rule and the parity tool's "fix the lowest owning layer" rule both encode this
  same lesson independently.

So: yes, core-first — but *evidence-first within that*. Phase 3 exists specifically so
Phase 4 fixes primitives that real consumers are demonstrably suffering from, in that
order, rather than whichever Diverges row reads worst on paper.

## Sequencing

Phase 1 before Phase 3 (need the matrix before classifying consumer drift against it).
Phase 2 runs independently — investigation, not blocked on anything — but its
trial-measure finding should land before Phase 4 attempts item 4. Phase 4's items 1–3
don't depend on Phase 2; they need Phase 1 to confirm they're real gaps and Phase 3 to
order them by observed consumer impact.

Cheapest useful first step if a smaller start is wanted: run Phase 3's step 1 (the
existing 7 parity cases) *before* Phase 1. It's a few commands against tooling that
already exists, and it produces a real drift list that would tell us whether Phase 1's
matrix should prioritize sizing, spacing, or something else entirely — turning the whole
plan's ordering from an assumption into an observation.

Not starting execution yet — this is the plan. Confirm scope/order before launching.

## Measured drift baseline, and one attempted fix that failed (2026-08-21)

Ran the 8 registered parity cases rather than continuing to reason about ordering. Result:

| case | geometry | padding | max Δpx |
|---|---|---|---|
| `dropdown-menu.states.light.open` | pass | pass | 0.0 |
| `button-group.basic.light.rest` | pass | pass | 0.922 |
| `button-group.vertical.light.rest` | pass | pass | 1.0 |
| `button.variants.light.rest` | drift | pass | 1.781 |
| `button.variants.dark.rest` | drift | pass | 1.781 |
| `card.login.light.rest` | drift | **drift** | 16.0 |
| `popover.states.light.open` | drift | pass | 24.0 |
| `alert.variants.light.rest` | drift | pass | 24.0 |

Two of these numbers are not component bugs. `alert` and `popover` both report x/y = 24.0
because their showcase fixture renders at (24, 24) while `button-group` renders at the origin —
the harness inset is not normalized against the reference frame. Fixing that first would
de-noise the table; until then their real deltas are hidden behind framing.

The alert's genuine delta was height −3.281px, which decomposes exactly: 3.281 / 2 description
lines = 1.64px per line. Cause is real and systemic — a Tailwind `text-*` utility sets font size
AND a paired line-height (`text-sm` = 14/20), while `Tw.Text.*` carries only the size, so text
falls back to font metrics at roughly 1.31x (18.36px for `sm`).

**The obvious fix does not work.** Defaulting `shadcnTextStyle` to `Tw.Leading.forTextSize(size)`
regressed the table: `card.login` 16.0 → 21.0, alert height −3.28 → +4.0, nothing improved.
Reverted. The pair is only Tailwind's *default*; shadcn overrides it per component, and
`AlertTitle` is `leading-none` (14/14), so a blanket 20px is 6px too tall there. Line height has
to be translated per recipe from that component's own class string.

Blocked on evidence: the alert's remaining height cannot be decomposed from the capture, because
`alert-variants_light.json` records only the two root nodes. Add `nodeIds` for the title and
description rows to the manifest case first — then the title-vs-description contribution is
measurable instead of inferred.

Also worth knowing before touching the scale: `Tw` is generated by `:awake:tailwind-generator`
and carries a do-not-hand-edit banner. A new scale means editing the generator.

Still open and unrelated to the above: `ShadcnGeometryParityTest.dropdownMenuGeometryMatchesShadcn`
fails on a missing `parity-dropdown.trigger` semantic node — the fixture emits `parity-dropdown`,
`.surface` and `.item.N` but never `.trigger`. Pre-existing.

## Open task: give the 75 inline style literals a home

`ui/designsystem/styles/` holds 90 raw `Nf.dp`/`Nf.sp` literals, 3 named constants and 3 `Tw.*`
references across 14 files (measured 2026-08-21). Fifteen of the 90 are correct — they sit inside
`ShadcnButtonSize`/`ShadcnCardSize`, carrying the spec as named fields, which is why the button's
missing `py-2` was findable at all. The remaining 75 are inline in `Style { }` blocks with no name
and no traceable origin.

Worst first: `ShadcnSidebarStyles` (13), `ShadcnNavigationStyles` (11), `ShadcnStatusStyles` (10),
`ShadcnSelectionStyles` (7), then six files at 6 and below.

The taxonomy to convert against is in `awake-shadcn-recipe-authoring` under "Which home a number belongs
in". The conversion is not mechanical and should not be delegated as such: each value has to be
traced to its Tailwind class or the pinned capture, and the point of the pass is that wrong ones
surface while being named rather than getting encoded under a nicer name. Six size bugs already
found this way (field height 40 vs `h-9`=36, InputOTP 36x40 vs 36x36, Sidebar 240 vs 256, Tabs 32
vs 36, drawer handle 48x6 vs 8x100).

Expect baseline churn wherever a literal turns out to be wrong; verify with the predictive-rule
discipline in `awake-ui-verification` rather than re-recording on the strength of "looks right".

## Foundation grouping: what landed, and what blocks the rest (2026-08-21)

`ui-core/foundation/` now holds the interaction layer: `interact()` (moved out of
`ui-headless/internal/layout`, where it was both misfiled and mislabelled) plus the value
semantics that were missing -- `toggled(Boolean)`, `toggled(UiToggleableState)`, `selected()`.
`checkbox`/`switch`/`toggle` had each written their own copy of the toggle transition and had
already drifted; they now share one.

Correction to an earlier count in this doc's discussion: `hoverable`/`focusable` were never
missing. `interact()` already reports hover and drives focus. Only the value semantics were.

**Blocked: moving the text primitives out of `ui-headless`.** They are Foundation and they sit in
the module slated to retire into `ui-designsystem`, which would land `BasicText` in the design
system -- wrong, since every layer needs it and `ui-core` cannot depend upward to reach it. Only
three of the five are Foundation at all: `BasicText`/`Text`/`TextMetrics` have zero headless
dependencies, while `textField`/`textarea` paint their own surface and resolve an interactive
surface, which makes them styled controls closer to Material's `TextField` than Foundation's
decoration-less `BasicTextField`. Those two stay with the controls.

The move was attempted and reverted. `BasicText` calls `shimmerBand`, which lives in
`:awake:ui:animation`, and `animation` already depends on `ui-core` -- so ui-core cannot reach
it without a cycle. `Text.kt` depends on `BasicText` (`renderTextBlock`, `layoutBitmapText`), so
the three cannot be separated, and `shimmer` is threaded through `modifier.shimmer` rather than
being a single call site.

Unblocking it is its own task: `shimmerBand`'s own doc comment already calls it "widget-agnostic",
so the fix is to move its phase/band math down to `ui-core`/`ui:graphics`, or invert it so the
caller passes a phase and `BasicText` stops knowing about shimmer at all. The second is the
better shape -- a text primitive carrying a `shimmer` flag is skeleton policy leaking into
Foundation -- but it touches `modifier.shimmer` and every caller.
