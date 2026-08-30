# UI / shadcn Parity Audit and Remediation Plan

> **Historical audit.** Use [the active parity plan](../tasks/2026-08-25-shadcn-parity-plan-v2.md)
> for execution. This document retains the original findings and evidence, including sections that
> are now stale after the Compose migration and tooling formalization.

Date: 2026-08-20, refreshed 2026-08-24.
Scope: `ui-shadcn` (now built on `awake:compose:*`) and `samples:ui-showcase`, compared with the repository's pinned official shadcn/ui checkout (`6261bd89f72d794aea491482cc2acfd8dc3d63e2`).

## Status as of 2026-08-24 — read this before anything below

Between the original audit and this refresh, two separate things happened:

1. **Full engine migration.** `ui-core`/`ui-headless` (immediate-mode) are deleted;
   `ui-shadcn` is 100% ported (75/75 recipes) onto the retained `awake:compose:*` engine.
2. **The tooling itself was formalized, 2026-08-23**
   (`docs/tasks/2026-08-23-ui-tooling-formalization-plan.md`, status: done). This is a real,
   separate fix to the exact P0 this audit raised, not a casualty of the engine cutover. Correction
   to an earlier draft of this refresh: the two broken generators this audit named
   (`tools/generate_parity_report.py`, `tools/generate_ui_status.py`) were **not deleted** — they
   were renamed/relocated to `skills/awake-ui-verification/scripts/generate_ui_parity_report.py`
   and `generate_ui_status.py`, and are now called through one real entry point:
   `scripts/awake ui {reference,preview,validate,report,performance}` and
   `scripts/awake verify [--only NAME]`. Running `./scripts/awake ui report` today produces a real,
   current `build/reports/ui-parity/report.json` — **this addresses this audit's Phase 0
   ("make reporting truthful") directly.** All 15 tools in `tools/` now carry an explicit
   Gate/Generator/Investigation label in their own header, so "does this scan real paths" and
   "can this fail a build" are no longer inferred, they're stated.

**What the truthful report actually says, verified live 2026-08-25** (`./scripts/awake ui preview`, `validate`, `report`):

- **Compose-native preview pipeline implemented**: `ShadcnComposeParityPreviewTest.kt` in `samples:ui-showcase` renders Compose component layouts to PNG + JSON semantics reports (`awake-*.png`, `awake-*.json`).
- **Semantic alignment**: `testTag` semantics mapped directly to manifest node IDs (`badge.*`, `parity-button-group.*`, `parity-dropdown.item.*`, `parity-radio.*`, `parity-tabs.*`).
- **Validation results**: Component semantic crops compare directly against reference PNGs:
  - `badge-variants-light`: Exact `(291, 22)` dimension match
  - `button-group-basic-light`: Exact `(157, 36)` dimension match, 22.56% paint mismatch, geometry passes (`maxDeltaPx: 0.922`)
  - `button-group-vertical-light`: Exact `(156, 72)` dimension match, 17.51% paint mismatch
  - `dropdown-menu-states-light`: Exact `(160, 138)` dimension match, 11.32% paint mismatch
  - `input-states-light`: Exact `(256, 132)` dimension match, 13.07% paint mismatch
  - `select-closed-light`: Exact `(172, 36)` dimension match, 18.02% paint mismatch
  - `checkbox-states-light`: Exact `(80, 16)` dimension match
  - `progress-states-light`: Exact `(212, 32)` dimension match
  - `radio-group-states-light`: `(102, 72)` vs `(103, 72)` match
  - `tabs-states-light`: `(153, 36)` vs `(156, 36)` match
- **Report Generation**: `./scripts/awake ui report` runs cleanly to produce `build/reports/ui-parity/report.json` with geometry, relationships, and paint metrics.

**This does not move the parity number in this document.** Engine-migration completeness and
shadcn/React parity are orthogonal axes: the old audit already measured Awake's shadcn-fidelity
gaps against ui-core's *output*, not its implementation, so a from-scratch reimplementation on a
new engine starts this audit's coverage table at effectively the same place — every row below is
still open. Do not read "100% ported" (an engine-cutover fact) as "100% parity" (this document's
subject); they answer different questions.

New engine-cutover-era facts that DO change this document's specifics:
- `awake/ui/shadcn/detekt-baseline.xml` now has **15 findings** (was 19) — improved, still
  non-zero, Phase 5's exit criterion is unmet either way.
- `samples:ui-showcase` has **12 stub pages** (`showcasePlaceholder(...)`, up from an earlier
  session's 11 — a `pages/blocks/BlockPlaceholders.kt` category was added since): Combobox,
  InputGroup, InputOtp, RangeSlider, Select, ScrollArea, AlertDialog, ContextMenu, Sheet, Drawer,
  Toast, and the Blocks category. Each names its missing dependency in-page rather than faking
  coverage — consistent with this audit's Phase 1 "explicit unsupported list" recommendation,
  informally, not yet formalized into the manifest Phase 1 calls for.
- Several recipes ported this session are known-simplified relative to shadcn/React, found and
  recorded (not yet in a formal manifest): no `shadcnAvatarGroup`; no `shadcnH1`/`Blockquote`/
  `Code`/`SectionTitle`/`TextLines` (typography falls back to `shadcnText(variant=)`); `ShadcnCard`
  has no header slot/variant; no `ShadcnSurfaceVariant`; `Canvas` has no gradient/circular-clip
  primitive; sidebar has no `HeaderButton`/`FooterButton`/`Group`; `shadcnDropdownMenu`/
  `shadcnTabs`/`shadcnRadioGroup`/`shadcnToggle` are still "returns the next value" rather than
  `onXChange` callbacks (a real API-shape gap, not a visual one — affects Phase 3's interaction
  matrix more than Phase 2's static geometry).

The rest of this document is materially unchanged from 2026-08-20 — its findings were never about
`ui-core` vs `compose`, so the cutover doesn't invalidate them. Section headers below now say
`awake/ui/...` generically since the specific `ui-core`/`headless` module paths they used to cite
no longer exist; treat any remaining literal path below as historical, not a live location.

## Executive summary

Awake has a broad, working shadcn-inspired component layer and its desktop UI test suites pass. It does **not** currently have 100% parity verification with official shadcn/ui.

The primary problem is not an absence of tests. It is that the existing evidence has different scopes and strengths:

- token tests can prove a resolved token equals the pinned reference;
- geometry tests can compare selected semantic bounds with a reference DOM rectangle;
- screenshots detect an Awake regression, not correctness;
- the current pixel comparison does not establish full visual fidelity because some captures are misframed and every renderer/font differs;
- behavior and motion have only local tests, not a browser-oracle comparison.

The generated parity dashboards are currently unreliable: both generators still search the deleted `awake/engine/ui/...` module tree. They consequently report `0/46` components, no dark captures, and absent functionality that is present in the active `awake/ui/...` tree. Do not use either generated percentage as a release decision until that is repaired.

## Evidence run

| Check | Result | Meaning |
|---|---|---|
| `tools/fetch_shadcn_reference.sh` | pass | The local reference is the pinned official shadcn/ui source. |
| `tools/extract_shadcn_tokens.py` | pass | Reference token data was extracted from that checkout. |
| `:awake:ui:ui-core:desktopTest` | pass | Core layout/render/runtime regression tests pass. |
| `:awake:ui:headless:desktopTest` | pass | Headless behavior regression tests pass. |
| `:awake:ui:shadcn:desktopTest` | pass | Recipe and design-system regression tests pass. |
| `:samples:ui-showcase:desktopTest` | pass | Showcase parity and preview test suite passes. |
| Design-system naming / duplicate / Headless-backed audits | pass | 21 component files have valid naming, 20 recipe files are Headless-backed, and duplicate recipes were not found. |
| `:awake:ui:shadcn:check` | fail | Detekt reports 19 pre-existing quality violations. This is unrelated to the passing functional UI tests, but prevents a clean full `check`. |

Passing regression tests mean Awake still behaves as its accepted baseline. They do not prove the accepted baseline matches shadcn.

## Coverage assessment

| Dimension | Current evidence | Audit verdict | What prevents 100% |
|---|---|---|---|
| Component inventory | Recipes exist across the design-system module; the inventory generator is broken. | Unknown, not certifiable | Repair the source-path and symbol scanner, then maintain a component-by-component manifest. |
| Spacing and padding | Geometry tests derive reference content bounds from CSS padding for selected nodes. | Partial | No exhaustive component × variant × state × constrained/dynamic-size matrix. |
| Width / height | 26 official-reference geometry fixtures cover selected components. | Partial | Allowances reach 76px for breadcrumb, 25px for alert, 14px for slider, and 10px for textarea/popover. Those are useful regression limits, not strict parity. |
| Dynamic size / text fit | Core matrices and a subset of reference geometry cases cover intrinsic/fill behavior. | Partial | No official matrix across short/long content, min/max constraints, density, and every size variant. |
| Colors | `ShadcnReferenceTokenExpandedTest` compares all seven base colors in light and dark. | Partial | Neutral matches reference. The six non-neutral palettes contain intentional `knownDrifted` locks, meaning the test preserves their current divergence instead of proving them correct. Accent overrides are outside the official-token oracle. |
| Borders / radius | Style parity reads reference CSS and checks selected rounded quads. | Partial | Border width is read but not asserted; color comparison is qualitative (light/dark threshold), not exact RGBA/OKLCH equality. Shadows are absent from recipes. |
| Focus ring | Local focus behavior exists. | Missing parity | No real shadcn ring geometry, color, blur, or state comparison. |
| Dark mode | 26 official local dark PNG captures and matching JSON captures exist. | Partial | The official-pair manifest gates only selected dark cases; no exhaustive dark state/variant/motion matrix. The generated dashboard incorrectly says there are none. |
| Static pixels | Reference screenshot comparisons exist for selected cases. | Regression only | Pixel percentages are not a full-fidelity oracle; font rasterization differs and slider/tooltip captures are excluded for framing defects. |
| Popup / overlay | Dialog, dropdown menu, popover, tooltip, drawer/sheet code and local behavior tests exist. | Partial | No complete keyboard, focus-trap, portal/layering, escape, positioning, collision, and animation parity matrix. |
| Keyboard / focus | Button Space activation and some local focus mechanics are covered. | Missing parity | No Tab traversal, Escape dismissal contract, arrow navigation, Enter activation matrix, roving focus, or official browser behavior oracle. |
| Animation / motion | Progress, spinner, skeleton, sheet, collapsible, and popup mechanics have local tests. | Partial implementation; missing parity | No official sampled rest/in-flight/settled frames, duration/easing comparison, or GPU validation. Toast is explicitly a hard show/hide rather than Sonner-style fade motion. |
| Accessibility / semantics | Local semantic roles and widget tests exist. | Partial | No browser/assistive-tech or official ARIA parity audit. |
| GPU output | CPU rasterizer and some backend checks exist. | Partial | No systematic Vulkan/WebGPU reference comparison. |

## Specific audit findings

### P0 — parity dashboards are false sources of truth — **resolved 2026-08-23**

The original `tools/generate_parity_report.py` and `tools/generate_ui_status.py` used
`awake/engine/ui/...` paths against modules that had already moved to `awake/ui/...`, and the old
symbol matcher failed to discover real recipe declarations. Fixed, not worked around: both scripts
now live under `skills/awake-ui-verification/scripts/`, are wrapped by `scripts/awake ui
report`/`awake verify`, and every generator in `tools/` gained a matching staleness gate or an
explicit written reason it can't have one
(`docs/tasks/2026-08-23-ui-tooling-formalization-plan.md`). `./scripts/awake ui report` today
produces `build/reports/ui-parity/report.json` with per-case, per-dimension real status — verified
live 2026-08-24, see the table above.

Remaining impact, now correctly scoped: the report is truthful but **thin** — 8 cases in the
manifest, most dimensions `missing-artifact` pending an `awake ui preview` run, `behavior`/`motion`
explicitly marked not-yet-implemented rather than silently blank. That's Phase 1's job, not Phase
0's.

### P0 — no complete parity contract

There is no manifest that says, for every supported shadcn component and Awake variant, which official fixture, semantic nodes, styles, behavior sequences, and animation samples are required. Without it, a passing suite can leave an entire variant, popup state, dynamic size, or dark state unmeasured.

### P1 — geometry proof is selected and sometimes intentionally loose

The geometry harness is the strongest existing layout oracle: it compares reference DOM rectangles and derived content widths. However, it covers selected fixtures rather than the full component surface and permits large component-specific variance. It also calculates content height but does not include that delta in the failure condition. Padding is therefore only partially proven.

### P1 — style proof is not exact enough for color, border, or shadow parity

The style harness checks rounded-quad radius on selected primitives, but treats a present border-width field as an assertion without comparing it. Background color checks only decide whether a surface is broadly light or dark. It does not compare foreground color, border color, opacity, shadow, focus ring, or computed state styles exactly.

### P1 — color parity has declared divergence

The seven-base-color test is valuable, but it deliberately locks known drift for Stone, Zinc, Mauve, Olive, Mist, and Taupe. These should be reported as non-parity until the palette becomes a per-token mapping equivalent to the pinned reference. Neutral is the only fully matching base-color palette.

### P1 — dark mode is captured but not fully gated

Official local capture assets include 26 light and 26 dark PNGs plus 46 JSON captures. That is a good starting point, but pair manifests and assertions do not span every component/state/variant. The report must distinguish “dark reference exists” from “dark parity verified.”

### P1 — interaction behavior is locally tested, not browser-parity tested

The behavior suite covers click activation, Space activation, checkbox/switch toggles, dropdown selection, and outside-dialog dismissal. It does not prove the broader Radix/shadcn interaction contract: Tab and shift-Tab order, Escape, Enter, arrows, typeahead, roving focus, focus restoration, nested dismissable layers, and portal behavior.

### P2 — motion is not compared to official shadcn

Awake has animation mechanics and local rest/in-flight/settled tests for some widgets. It has no official timing/easing or frame-by-frame oracle. This is particularly visible for toast, which documents an immediate show/hide lifetime rather than Sonner's animated transition.

### P2 — full `check` remains red

The design-system `check` task fails on 19 Detekt findings. Resolve those independently of parity so a release gate can be trusted as a single command.

## Definition of “100% checked”

The target should be a complete *supported-surface* contract, not a single pixel percentage:

1. Every supported official component has a declared Awake mapping, or an explicit unsupported rationale.
2. Every exposed variant, size, enabled/disabled/selected/open/focused state, and light/dark theme has an official reference fixture.
3. Static components have exact geometry assertions for outer bounds, content bounds, padding, gap, radius, border width, and border color; pixel review remains a secondary regression signal.
4. Dynamic components add short/long text, constrained/fill/intrinsic dimensions, min/max size, density 1x/2x, scrolling/resizing, and layout-direction cases where applicable.
5. Overlay components add anchoring, clipping/collision, portal/layer order, outside click, Escape, focus trap/restore, keyboard navigation, and nested-layer cases.
6. Animated components add rest, in-flight, settled, and interrupted samples, plus declared duration and easing comparisons.
7. All of the above run on the default official theme in light and dark. Non-official Awake presets are tested for internal consistency, never described as official shadcn parity.
8. A clean `check` plus the full official-reference parity suite is required before claiming completion.

## Remediation plan

### Phase 0 — make reporting truthful

1. Move both generators to `awake/ui/...` paths and repair their Kotlin declaration extraction.
2. Regenerate the reports only after the generators locate live source correctly.
3. Add generator unit tests with known recipe fixtures so another module move cannot silently produce `0/46`.
4. Make the report show three distinct numbers: implemented, reference-covered, and fully gated. Never label any of them simply “parity.”

Exit criterion: the generated component inventory lists live recipes, dark-capture counts are correct, known token drift is reported, and a deliberately broken path test fails.

### Phase 1 — establish the parity manifest and reference corpus

1. Create one data manifest per official component mapping Awake recipe, variants/sizes/states, fixture IDs, semantic node IDs, and verification dimensions.
2. Expand the official local reference app/capture list to every supported component and state. Keep a separate explicit unsupported list for components Awake intentionally does not provide.
3. Capture light and dark references from the pinned checkout; recapture slider and tooltip to fix their bounding boxes.
4. Add a manifest completeness test: every supported recipe must have a reference; every reference must have an Awake preview; every preview must have semantic IDs.

Exit criterion: every supported component-state pair is either executable in the manifest or explicitly unsupported with a reason.

### Phase 2 — close deterministic static parity

1. Tighten geometry to a maximum 1px allowance except documented renderer/font-only subnodes. Split text advance from container geometry so typography cannot justify loose container bounds.
2. Assert both content width and content height, individual padding edges, gaps, border radius, and border width exactly from reference JSON.
3. Replace qualitative light/dark color checks with precise computed-color comparisons using a documented color-space tolerance.
4. Add border color, foreground color, opacity, shadow, and focus-ring fields to captured reference styles and compare the corresponding Awake primitives.
5. Fix known 40dp defaults and wire named shadcn sizes from design-system metrics; verify small/default/large/icon controls against the official fixtures.

Exit criterion: each static manifest row passes exact layout/style assertions in light and dark without broad component-level allowances.

### Phase 3 — dynamic layout and overlays

1. Add data-driven matrices for short/long content, wrap/fill/intrinsic dimensions, fixed/min/max bounds, density 1x/2x, scrolling, resize handles, and constrained parents.
2. Add an overlay matrix for popup, select, dropdown, tooltip, popover, dialog, sheet, drawer, and context menu: anchor position, edge collision, clipping, z-order, nested overlays, outside click, and dismissal.
3. Implement and test full keyboard/focus contracts: Tab/shift-Tab, Enter, Space, Escape, arrows, roving focus/typeahead where Radix defines them, focus trap, and focus restoration.

Exit criterion: all dynamic and overlay rows pass their official reference and local behavior assertions.

### Phase 4 — motion, rendering, and accessibility

1. Add official capture/measurement support for rest, in-flight, settled, and interrupted motion states; assert duration/easing with an allowed frame-time error.
2. Bring toast/Sonner behavior to the declared target or explicitly mark it as a non-parity component.
3. Run selected representative parity cases through Vulkan and WebGPU in addition to the CPU rasterizer.
4. Add semantic/ARIA contract tests for role, label, state, and keyboard behavior; document any platform limitations.

Exit criterion: every animated or accessible manifest row includes its required states and backend/semantic evidence.

### Phase 5 — release gate and maintenance

1. Resolve the 19 Detekt findings so the full design-system `check` task passes.
2. Add the reference refresh, manifest completeness, official geometry/style/behavior/motion parity, and dashboard-consistency checks to CI.
3. Require a pinned-upstream bump workflow: refresh source, tokens, captures, manifest, tests, reviewed diffs, and report in one change.

Exit criterion: CI has a single green parity gate, and release documentation can accurately state which official component surface is 100% verified.

## Recommended order

Do Phase 0 before any visual tuning. Otherwise, teams will optimize against reports that deny existing coverage and hide known drift. Then do Phase 1 and Phase 2 together for one component family at a time: button/input/selection controls first, overlays second, navigation and complex layout third, motion last.

## Fast execution plan for Phase 1 + 2 (added 2026-08-24)

Phase 0 being done changes what "fast" means here: the pipeline is real and running
(`./scripts/awake ui reference`/`preview`/`validate`/`report`), the manifest schema is proven (see
the `button-group.basic.light.rest` case above — component/state/theme/`nodeIds`/`relationships`,
pure JSON), and the reference React app already has case scaffolding for 24 components. What's
missing is volume: **8 manifest cases against 75 ported recipes.** That's a data-entry and
per-component-wiring problem, not a design problem — the same shape as this session's
51-showcase-page conversion and the 75-recipe port, both of which finished by fanning out one
agent per component family rather than one long serial pass.

**Pilot run, completed 2026-08-24/25 — real, not hypothetical.** The flow was run by hand against
two live components rather than a synthetic `badge` walkthrough, and the flow correctly produced
two DIFFERENT verdicts, which is the actual proof it works (a flow that always says "needs fixing"
or always says "fine" isn't discriminating anything):

- **`popover` — real bug found and fixed.** `./scripts/awake ui validate --component popover`
  reported 65.76% mismatch with the Awake crop 40px taller than the reference. Reading the actual
  PNGs (not trusting the number) showed why: wrong placeholder copy ("Popover content" vs the
  reference's "Place content for the popover here.") and a missing `width = 260.dp` override on
  `shadcnPopover(...)` (defaulted to shadcn's `w-72`/288dp instead of the reference case's
  `w-[260px]`). Fixed both in `ShadcnComposeParityPreviewTest.kt`. Also found the crop itself was
  wrong at the *manifest* level — `nodeIds` unioned trigger+content for the pixel crop, but the
  reference PNG only ever captures content — added a `paintNodeIds` field, distinct from `nodeIds`,
  so geometry/relationship checks keep both nodes while the pixel crop uses only what the reference
  PNG actually shows. Final: dimensions now match exactly `(260, 54)` = `(260, 54)`, mismatch
  65.76% → 15.29% (real residual color/AA difference, not a wiring bug anymore).
- **`dropdown-menu` — already correct, nothing to fix.** 9.40% mismatch, dimensions match exactly.
  Read the actual PNG: Awake's render is visually right (My Account / Edit / Duplicate / Delete,
  correct spacing, correct red destructive-item color, correct border). The diff heatmap has the
  same signature as `button-group`'s residual (text anti-aliasing + a slightly different border
  color) — real renderer-noise floor, not a defect. **This is the flow correctly reporting "no
  action needed" instead of manufacturing a fix for a number that looked alarming in isolation.**

**A real, load-bearing bug surfaced along the way, not planned for**: the pipeline had TWO
independent, hand-maintained manifest files (`tools/shadcn/shadcn_parity_manifest.json` and
`skills/awake-ui-verification/scripts/ui_component_parity_cases.json`) and TWO independent
pixel-diff implementations (`compare_parity.py` and `compare_component_crops.py`), each with its
own copy of the same crop-alignment bug (anchoring two independently-trimmed/rounded crops at
`(0, 0)` instead of searching for the best-aligning offset, which read a real 1-2px anti-aliasing
difference as a large fake mismatch). Consolidated to one manifest, ported the alignment-search fix
to both diff scripts (they have genuinely different tolerance rules so aren't fully mergeable),
added a coverage gate (`tools/shadcn/test_manifest_coverage.py`) that already caught a third gap
(`slider` has no case in either file and never did). All landed in commit `f0a29e862`.

**Revised acceptance read, given what actually happened rather than what was guessed in advance:**
the pilot's real value wasn't proving the happy path — it was surfacing that the *tooling itself*
had unfixed bugs the numbers were hiding behind. Before fanning out to more components, the next
pilot round should specifically re-run `button-group` (residual 21.57%/16.62%, already investigated
down to real per-row causes: a flat-vs-anti-aliased border stroke and a ~9-row content-rendering
difference — worth a decision on whether that's "acceptable renderer noise" or a real border-style
gap before moving on) and `card`/`button.variants` (never individually eyeballed yet, unlike
popover/dropdown-menu/button-group) to build a real "what does acceptable residual mismatch look
like" reference set, since there is currently no written definition of that threshold anywhere in
this document — Phase 2's exit criterion says "without broad component-level allowances" but never
states what number those allowances currently are, or should be.

**Only after that reference set exists**, proceed to automate: turn the by-hand step sequence
above into the fan-out below, unchanged in shape, scaled in count.

**Unit of work, per component:** add its manifest case(s) (one per required state × theme —
`rest`/`hover`/`active`/`focus`/`disabled` × `light`/`dark`, only the states that component
actually has), confirm or add the matching `awakePreview` id (wired to the component's existing
`hero`/`variants` composable in `samples:ui-showcase`'s pages — most already exist from this
session's port, so this is often "point the manifest at what's already there," not new UI code),
confirm the reference-app has a matching case in `tools/shadcn/reference-app/src/cases.tsx` (24
already scaffolded, gaps only where a ported recipe has no case yet), then run the pipeline for
that component only (`awake ui reference --only X`, `awake ui preview --only X`,
`awake ui validate --only X`) and read its own `report.json` slice before moving to the next.

**Fan-out shape** (mirrors what worked this session): group the 75 recipes into the same
categories `port_progress.py` already uses implicitly by directory (`buttons/fields/inputs`,
`popups/overlays/navigation`, `sidebar/table/surface/layout`, `status/typography/toast`,
`motion` last per the existing "Recommended order" above). One agent per category, each agent:

1. Reads 1-2 already-in-the-manifest cases (`button-group.*`) as the ground-truth shape — same
   "read the reference example first" rule that kept the showcase-page port consistent.
2. Adds manifest cases + reference-app cases for every recipe in its category.
3. Runs the pipeline for just that category and reports its own `report.json` slice — geometry/
   style still show `missing-artifact` honestly until Phase 2's actual assertion-tightening lands,
   but `paint` (pixel mismatch %) becomes real immediately, which is enough to catch the loudest
   drift fast without waiting for the full phase to finish.
4. Flags, not fakes, anything it can't wire (a recipe with no stable `awakePreview` hook yet, a
   reference-app case that doesn't exist upstream) — same "explicit unsupported, not silent gap"
   rule Phase 1's own exit criterion already states.

**What this does not shortcut:** Phase 2's actual assertion work (exact geometry/style/color
comparison, tightening from "missing-artifact" to a real pass/fail) is still real engineering per
dimension, not data entry — the fan-out gets every component *measured*, it doesn't make the
measurement itself free. Behavior (Phase 3) and motion (Phase 4) still have no implementation to
fan out against yet (`report.json` says so verbatim: "not implemented") — that's still greenfield
work, not a volume problem, and doesn't parallelize the same way.
