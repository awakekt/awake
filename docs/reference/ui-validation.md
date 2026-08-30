# UI Validation

<!-- ui-tooling-map -->
> **Four docs cover UI tooling, with different jobs.** Land in the one that matches your question:
>
> | Question | Read |
> |---|---|
> | *Is anything wrong?* | `scripts/awake verify` — every gate, one run |
> | *Which tool answers my question, and may I re-record this baseline?* | [`awake-ui-verification`](../../.agents/skills/awake-ui-verification/SKILL.md) — judgment |
> | *What proof does this kind of UI change require?* | [`docs/reference/ui-validation.md`](ui-validation.md) — policy |
> | *What commands do I run, in what order?* | [`docs/reference/ui-parity-tool.md`](ui-parity-tool.md) — procedure |
> | *What is this script, and can it fail a build?* | [`tools/README.md`](../../tools/README.md) — catalogue |
<!-- /ui-tooling-map -->


This document is the canonical source for Awake's shared UI verification rule.


## Fixture metadata must not come from reflection

`AwakeUiPreviewMetadata` for a fixture must be supplied as data (see `ShowcasePage`), not read
off an `@AwakeUiPreview` annotation. iOS and wasmJs have no reflection, so annotation-driven
fixtures returned a 1x1 dummy and their tests early-returned -- reporting green on three of
four targets while asserting nothing. Use `renderUiPreviews(entry, metadata)`;
`renderAnnotatedUiPreviews` remains only for JVM-only fixtures that have no other option.

## Goal

Stop relying on eyeballing for shared UI regressions.

Awake's reusable UI is only considered done when it has machine-checkable proof for:

- visual rendering
- semantic structure
- text fit and truncation
- content bounds and clipping
- state coverage
- animation coverage when the component animates

## Non-Negotiable Rule

Any change to shared UI in:

- `:awake:compose:ui`
- `:awake:compose:foundation`
- `:awake:ui:shadcn`
- `:awake:compose:ui-testing` when changing the test/rendering harness itself
- shared showcase or docs previews that demonstrate those modules

must ship with automated validation.

Manual browser review is useful, but it is not enough on its own.

## Verification Tiers

The proof requirements above are cumulative, but they are not all required after every edit. Use
the cheapest tier that can answer the current question, then complete the higher tiers before a
commit or parity claim:

| Stage | Required checks | Avoid unless relevant |
|---|---|---|
| **Iteration** | Owning-module compile, focused component tests, CPU preview for visual changes | Full catalog, browser recapture, GPU capture, unrelated audits |
| **Commit** | Complete standalone state matrix, fresh focused preview and diff inspection, `git diff --check` | Full GPU matrix for recipe-only changes |
| **Merge/parity claim** | Fresh upstream reference, standalone recipe, real showcase route, semantic/geometry/style reports, behavior/motion evidence, required audits | None of the required evidence |
| **GPU-specific** | Real Vulkan/WebGPU/offscreen capture when backend paint, shader, blending, text sampling, frame pacing, or AA changed | GPU capture for pure semantics, state, padding, or layout changes |

Skipping a tier during iteration is allowed only when its evidence is not yet relevant; it is not a
way to waive the final proof. CPU raster previews answer layout and recipe questions quickly but
cannot establish GPU fidelity. Always regenerate artifacts before reviewing them so stale images
cannot be used as evidence.

## Keep UI Tests Easy to Change

Tests must describe the behavior they prove, not repeat renderer setup. This prevents a small UI
change from becoming a large maintenance task.

- Use `composeFrame(...)` for a normal one-frame retained Compose component test.
- Use `composeTestSession(...)` for interaction or animation across frames.
- Use the named gestures (`hover`, `click`, `doubleClick`, `longPress`, `rightClick`, `drag`)
  instead of repeating normal pointer down/up frames.
- Use exact `UiInputState` only for a wheel, keyboard, or another input the named gesture cannot
  represent.
- Do not create a new test file if an existing matrix or component fixture owns the behavior.
  Add a row or case there.
- Use raw `UiContext` only when the lifecycle itself is what the test proves: Core/runtime,
  layout/cache, renderer/backend, or pixel-fidelity mechanics.
- A test using `beginFrame`, `endFrame`, or `finishFrame` directly in the retained Compose
  engine or `ui-shadcn` must declare `@UiLowLevelTest("why this lifecycle is under test")`
  on the class (or a low-level helper file).
  `verifyUiTestLifecycle` runs from `check` and rejects unmarked manual lifecycle code.

Read the [UI testing glossary](glossary/ui-testing.md) when a term is unfamiliar. Prefer clear,
specific names such as `popupDismissesOnOutsideClick`; avoid incident-only names such as
`PopupBugProbe`. A permanent probe must be renamed for the invariant it protects.

## Required Proof By UI Type

### Static Shared Widgets

Every shared widget change must include:

1. a preview or snapshot that renders the widget
2. semantic validation
3. text-fit validation
4. content-fit and clipping validation
5. a stable regression signature when the output is intentionally locked

Required states:

- default
- hovered, pressed, or focused when supported
- disabled when supported
- selected/checked/expanded when supported
- long-text or constrained-width case when text is visible
- light/dark or theme variants when the widget is theme-sensitive

### Shared Layout Or Container APIs

Every layout/container change must include:

1. a preview or snapshot of the composed layout
2. overlap validation for sibling controls that must not collide
3. content-fit validation for clipped or padded regions
4. a stable layout signature or snapshot signature

### Animated Components

Animated shared UI must not be validated only at one rest frame.

Required proof:

1. at least three sampled visual states:
   - rest
   - in-flight
   - settled
2. the same semantic/content-fit checks at each sampled frame
3. explicit allowance for intentional truncation or overlap only when documented in the test

If a component shimmers, expands, fades, slides, or eases between values, the preview/test
must capture more than the final state.

## Canonical Test Surfaces

Use these first:

- `composeFrame(...)` (`:awake:compose:ui-testing`) for every single-frame retained Compose
  component test and snapshot fixture. It owns the frame lifecycle, default input, font/density
  restoration, semantics, and emitted primitives. Do not duplicate frame setup in these tests.
- `composeTestSession(...)` (`:awake:compose:ui-testing`) for multi-frame pointer, keyboard,
  focus, and animation interaction tests. It keeps the retained frame/input state persistent and
  provides the named gestures instead of hand-writing press/release frame sequences; use its
  exact-frame input API only for wheel, keyboard, or other custom input snapshots.
- `AwakeUiPreview` for preview-backed docs and reusable gallery entries
- `validateAwakeUiPreview(...)` for shared preview validation
- `inspectUiFrame(...)` for primitive-level rendering checks
- `inspectSemanticNodes(...)` for semantic integrity
- `inspectSemanticContentFit(...)` for bounds and clipping correctness
- `inspectTextTruncation(...)` for accidental clipping or overflow
- `inspectSemanticOverlaps(...)` for sibling-control collision checks
- snapshot signature tests for locked visual baselines
- layout signature tests for page-level semantic layout baselines
- `ShadcnGeometryParityTest` (`samples/ui-showcase:desktopTest`) for automated layout geometry parity (width, height, content padding, position offsets) against official shadcn reference rects
- `ShadcnStyleParityTest` (`samples/ui-showcase:desktopTest`) for automated computed style color & border parity (borderRadius, borderWidth, computed background/foreground tokens) against official shadcn reference styles
- `ShadcnBehaviorParityTest` (`samples/ui-showcase:desktopTest`) for automated interactive behavior, click activation, state toggling, space-key activation, menu item selection, and dialog dismissal
- `ShadcnParityScreenshotTest` (`samples/ui-showcase`) for pixel-baseline parity against the
  shadcn reference — `./gradlew :samples:ui-showcase:desktopTest --tests
  "*ShadcnParityScreenshotTest*"`. Regenerate goldens with `-DAWAKE_RECORD_SNAPSHOTS=true`
  only after inspecting the recorded diff PNG confirms the drift is an intended change, never
  blind. That test is a regression lock against Awake's *own* prior render, not a fidelity
  check against real shadcn/ui — for that, see "Shadcn Reference Fidelity Harness" below
  (`ShadcnReferenceComparisonTest`, `tools/shadcn/capture_shadcn_local.py`,
  `scripts/awake ui validate`)
- the `RendererHeadlessPixelBaselineTest`-style pattern (`awake:backend:vulkan:desktopTest`)
  for headlessly rendering a real frame and dumping it as a PNG when no live window/browser is
  available — the go-to for "does this actually render right" questions on 3D/backend work
- `UiShowcaseLayoutCostTest` (`samples/ui-showcase:desktopTest`) for frame-cost/perf
  regressions — measures real trial-measure pass counts and wall-clock time, not estimates
- the throwaway-probe-test idiom: build the real widget/scene through `composeFrame` (or a raw
  low-level frame only when exercising the retained runtime or renderer layer itself) and read its
  actual `Rectangle`/pixels, instead of reasoning about spacing,
  centering, or collapse behavior from source alone. This is the highest-leverage tool in this
  list — used to settle real "is X actually centered/tighter/regressed" questions with numbers
  instead of guesses (see `RowCrossAxisCenterProbeTest`, `UiShowcaseSidebarGapProbeTest`,
  `TypographyPaddingProbeTest` for the pattern). Keep the probe as permanent regression
  coverage when it proves something worth locking in, delete it when it was purely diagnostic
- `UiAnimationFrameCapture` (`awake:backend:vulkan:desktopTest`,
  `io.github.awakelab.awake.vulkan.UiAnimationFrameCapture`) for "does this animation
  actually render right, frame by frame, through the real backend" questions -- the gap none of
  the tools above can close: a throwaway `Rectangle` probe (like the one above) proves the
  *logical* sequence is smooth, but never asks a renderer to draw anything, so it structurally
  cannot see a render-backend artifact (frame-pacing/dirty-rect lag between computed clip
  bounds and what actually gets presented). Builds the same real headless Vulkan renderer
  `RendererHeadlessUiGlyphBaselineTest` uses (`Renderer.renderUiToTexture`, backed by real
  `ui_quad`/`ui_rounded_quad`/`ui_glyph` pipelines against an `OffscreenRenderTarget`, not the
  CPU rasterizer `saveAwakeUiPreview`/`AwakeUiPreviewScene` use), drives a real animated
  `UiContext` scene for N real frames, and writes each frame as a numbered PNG for direct
  visual/pixel inspection. See `ShadcnCollapsibleRealRenderCollapseFrameCaptureTest`
  (`awake:backend:vulkan:desktopTest`) for the pattern -- real `shadcnSidebar`/
  `shadcnCollapsible` driven through a real collapse, 20 real rendered frames dumped and
  inspected for a jump/snap the logical-bounds probes couldn't have caught. Texture-backed
  primitives (render-target composites, e.g. a minimap) aren't supported yet -- add an
  offscreen texture-quad pipeline the same way `ensureOffscreenQuadPipeline` was added if a
  captured animation ever needs one

## Investigating "Extra Space" Reports

A real miss this session: a sidebar spacing complaint was investigated three times (layout
bounds, paint-level rendering, typography line-height) and all three came back clean, because
every investigation was framed as "did this change relative to before" — a regression diff.
The actual cause was two `spacer()` calls that existed unchanged both before and after the
change under investigation, so a before/after diff correctly reported them as identical and
moved on. "Unchanged" is not the same as "correct" — a spacer that was always too generous is
invisible to regression framing no matter how many times you re-measure it.

When investigating a spacing/layout complaint, do both, not just the first:

1. **Regression check** — did this specific change alter the spacing? (before/after `Rectangle`
   diff, the throwaway-probe idiom above.)
2. **Absolute check** — is the spacing *right*, independent of whether it changed? Grep the
   affected file for hardcoded `spacer(...)`/`padding(...)`/fixed `height(...)` literals near
   the reported area — cheap, and catches exactly this class of longstanding-but-still-wrong
   gap. Compare the absolute dp value against the shadcn reference (`third_party/shadcn-ui-ref/`,
   a pinned checkout -- run `tools/shadcn/fetch_shadcn_reference.sh` if it's missing, see
   [docs/reference/shadcn-reference-pipeline.md](shadcn-reference-pipeline.md))
   or the design-system token scale (`Tw`) it should be using instead of a hardcoded literal.

## Default-Behavior Changes In Hot Paths Need A Performance Check, Not Just A Correctness Check

Real incident: a correctness fix (making `surface()` clip content to its own rounded shape,
previously opt-in and unused by any real caller) changed a *default* — from "never runs" to
"runs for virtually every rounded surface" — inside `performDrawUi`'s per-primitive staging
loop, a genuine hot path. It shipped with full correctness proof (pixel-baseline diffs, a
targeted regression test) and zero performance proof. The result: every glyph inside every
rounded surface started paying Sutherland-Hodgman polygon clipping against a 29-point contour,
every frame, regardless of whether it was anywhere near a corner — a measured ~7.7x cost
increase on a real page. This shipped clean (tests green, no visual drift) and surfaced later
only as a vague, hard-to-pin-down "still lagging" report, chased through multiple dead-end
investigations before landing on the actual commit.

The rule: any change that flips a *default* (opt-in → always-on, or the reverse) inside code
that runs **per-primitive or per-frame** — draw-call staging, clip resolution, animation
stepping, layout measurement — needs a before/after performance measurement using this
project's own frame-cost tooling (`UiShowcaseLayoutCostTest`, `RendererHeadlessFrameTimingTest`,
or a direct microbenchmark of the changed function against a realistic input size) *before* it
ships, not after a lag report forces someone to go find it. Pixel-baseline tests catch "does
this look the same" — they cannot catch "does this now cost 8x as much to look the same."
`docs/reference/agent-catalog.md`'s render-backend/perf-owning agents should treat this as a
required step alongside pixel-diff review for any change matching that shape, the same way
`ShadcnParityScreenshotTest` is already a required step for anything touching rendered output.

A hardcoded spacing literal sitting next to a token-driven layout (`Arrangement.spacedBy(...)`)
is itself a signal worth flagging even before measuring anything — it's the kind of thing that
accumulates unnoticed specifically because it never shows up in a diff.

## Static Audit Limits

The optional `kmp-shadcn-compose` audit scripts are useful preflight tools, but they are not
Awake UI proof. They scan source names, text, and files; they do not execute a retained frame,
drive input, inspect semantic bounds, or compare rendered pixels against the pinned reference.

| Tool | Useful signal | Cannot prove |
|---|---|---|
| `shadcn_parity.py` | likely component/page/PNG coverage by filename | component behavior, actual visual fidelity, or whether a PNG is the right reference |
| `audit_ui_render_quality.py` | presence of shader and source-pattern hints such as `fwidth`, `smoothstep`, focus, and animation | that the active pipeline uses the code, that AA is visually smooth, or that a font is readable at runtime |
| `audit_compose_perf.py` | likely raw colors, collection parameters, and unkeyed list calls | Compose stability, recomposition cost, or whether a color is an intentional design-system value |
| `theme_contrast_audit.py` | WCAG math for supplied colors or canonical shadcn token pairs | Awake's actual resolved theme unless its runtime token values are supplied |
| `ui_visual_diff.py` | image-level mismatch metrics and a heatmap | semantic correctness, interaction behavior, or whether the reference/actual crops are aligned correctly |

The renderer-backed Compose tests remain the authority:
`composeFrame`/`composeTestSession`, `captureSemantics`, `assertBoundsInRoot`, and
`captureImage`. For GPU questions, use the real offscreen Vulkan capture rather than the CPU
rasterizer or a static shader scan.

The latest static preflight against Awake reported these follow-ups, not completed parity:

- Component inventory: `34/40` design-system components found; `InputGroup` and `RangeSlider`
  were reported missing and require source verification before implementation claims.
- Visual evidence: `28/40` tracked components had a matching PNG filename; missing assets are
  coverage gaps, not proof that the components render incorrectly.
- Render quality: SDF AA patterns were found, but font distance-field range remained a warning;
  this requires a real glyph capture and high-DPI comparison.
- Behavior and motion: static presence checks passed, but they do not replace interaction tests
  or multi-frame animation captures.
- Performance: four hardcoded-color suggestions were reported; each needs design-system
  ownership review before changing source.

## Allowed Exceptions

Exceptions must be explicit in the test, not implicit in the UI:

- intentional truncation must be allowlisted by semantic id
- intentional overlap must be checked in a targeted overlap rule
- intentionally empty semantics are allowed only for non-interactive pure-decoration previews

If an exception is real, encode it in the validation config so the next regression stays loud.

## Required Commands

For shared UI work, run the smallest relevant verification task before considering the work
done:

```bash
./gradlew :awake:compose:ui-testing:commonTest
./gradlew :awake:compose:ui-testing:desktopTest
./gradlew :samples:ui-showcase:desktopTest
```

Add module-specific tasks when the change lives outside those surfaces.

## Placement Rule

Put long-lived policy here in `docs/reference/ui-validation.md`.

Keep only a short reminder in:

- `AGENTS.md`
- `.claude/AGENTS.md`
- repo-local UI quality skills

The rule should stay canonical in docs, not get copied into every agent entrypoint.

## Definition Of Done

A shared UI change is done only when:

1. the relevant preview/snapshot exists
2. validation is automated and green
3. the docs/gallery artifact can be regenerated from tests
4. any intentional exception is encoded in test config
5. a human can open the generated report and confirm the result without discovering new obvious breakage

## Shadcn Reference Fidelity Harness

`ShadcnParityScreenshotTest` (above) is a regression lock: it diffs Awake's render against
Awake's *own* previously recorded golden. That proves nothing changed by accident, but it
cannot tell you whether Awake actually looks like real shadcn/ui -- and, until this harness
existed, nothing automated ever checked. This closes that gap with a real upstream reference.

**Read this first: there are two oracles now, and they answer different questions.**

| oracle | test | answers | reaches 0 mismatch? |
|---|---|---|---|
| geometry | `ShadcnGeometryParityTest` | is the size/position right, to sub-pixel precision | yes -- badge and button are already within ~1px |
| pixel diff | `ShadcnReferenceComparisonTest` | does it look catastrophically wrong (colour/radius/border/shadow) | no, never -- two different rasterizers cannot produce identical anti-aliased pixels, structurally |

Geometry is the primary oracle for layout questions (padding, width, spacing, alignment) as of
2026-08-15. It compares Awake's semantic bounds against the reference app's own
`getBoundingClientRect` numbers -- both sides state their geometry exactly, so there is no
tolerance to tune and no rasterizer/font/anti-aliasing dependency. Three real bugs were found
*in* the pixel instrument before this existed (a mis-framed reference scored as a fidelity
number, an unset reference font, a reference rendering every weight as 400) against one real
bug found *by* it (badge's padding, in one pass, once the instrument itself was fixed) -- that
ratio is why pixel diff is no longer where a layout question gets decided. See
`ShadcnReferenceComparisonTest`'s class doc for the detailed account.

What geometry cannot see: fill colour, border colour, shadow, opacity. No oracle for those
exists yet -- a computed-style comparison (sampling `getComputedStyle` the same way the capture
already samples `getBoundingClientRect`) is the natural next piece, not yet built. Until it
exists, pixel diff is the only signal for that dimension and should be trusted for it,
distrusted for layout.

### Component coverage matrix

"Parity" is not one number. The generated manifest-backed coverage matrix at
`build/reports/ui-parity/report.md` is authoritative: it records structural and surface scores
with their coverage plus every deliberately unmeasured raster, typography, shadow, color,
behavior, and motion dimension. This static table is historical context only; do not edit it to
claim coverage the generated evidence does not show.

**Corrected 2026-08-17.** The previous version of this section claimed 100% on layout,
style, AND behavior, with a per-component detail table underneath that contradicted it --
every row in that detail table read "no oracle" for style and behavior. Neither table was
right. Verified against the actual test files in
`samples/ui-showcase/src/desktopTest/kotlin/.../ui/`:

| dimension | oracle file | exists? | real coverage |
|---|---|---|---|
| **layout** (size, position, spacing) | `ShadcnGeometryParityTest` | yes | see file -- component-by-component sub-pixel table, largely current |
| **style** (fill/border colour, radius, shadow) | `ShadcnStyleParityTest` | yes | **23 of 23** components, one assertion function per component, real `getComputedStyle` oracle via `tools/shadcn/capture_shadcn_local.py` -- but **light theme, rest state only**: no dark-theme case and no hover/pressed/focus variant anywhere in the file, matching the capture tool's own documented limitation (`capture_shadcn_local.py`: "focus/disabled/hover were uncapturable") |
| **behavior** (click, keyboard, focus ring, hover, disabled) | `ShadcnBehaviorParityTest` | yes | **5 of 23** components only: button (click + space-key), switch, checkbox, dropdown-menu, dialog. No focus-ring or hover assertions anywhere in the file |
| **motion** (transition, easing) | none, and captures actively disable animation | no | 0% |

**Coverage, counted against every distinct component with a parity preview** (23, deduplicating
`@AwakeUiPreview` ids in `ShadcnParityScreenshotTest` by name, e.g. `awake-toggle-matrix-light`
and `awake-toggle-button-variants-light` are the same component sampled two ways): alert,
avatar, badge, breadcrumb, button, checkbox, collapsible, dialog, dropdown-menu, kbd, popover,
progress, radio-group, select, skeleton, slider, spinner, switch, tabs, textarea, textfield
(input), toggle-button, tooltip.

| dimension | components covered | % of 23 |
|---|---|---|
| layout | see `ShadcnGeometryParityTest`'s own per-component notes | not re-derived here -- re-count from the file, don't trust a cached percentage |
| style | 23, light theme + rest state only (no dark theme, no hover/pressed/focus variant) | **100% of rest-state light coverage**, 0% of interactive/dark |
| behavior | 5 (button, switch, checkbox, dropdown-menu, dialog) | **~22%** |
| motion | 0 | **0%** |

**Real gap, not just doc drift:** 18 of 23 components have zero click/keyboard/dismiss
assertion. Closing it means writing `ShadcnBehaviorParityTest` cases for the rest of the
catalog, not fixing a stale number. The doc lesson: the two tables above disagreed with
each other for at least a week before this correction, and neither was checked against
the actual test files in the interim -- verify against `grep -c "fun .*MatchesShadcn"` on
these files before trusting a percentage in here again.

| component | layout | style | behavior | motion | notes |
|---|---|---|---|---|---|
| badge | sub-px (+0.02..+0.30px), light+dark | rest, light | no oracle | no oracle | verified across light and dark themes |
| button | sub-px (+0.14..+1.78px), light+dark | rest, light | click+key oracle | no oracle | verified across light and dark themes |
| checkbox | sub-px (<=1.0px) | rest, light | click oracle | no oracle | |
| switch | sub-px (<=1.0px) | rest, light | click oracle | no oracle | |
| textfield (input) | sub-px (<=1.0px) | rest, light | no oracle | no oracle | |
| tabs | sub-px (<=2.5px) | rest, light | no oracle | no oracle | track's allowance is wider -- accumulates both triggers' text-advance rounding |
| select | sub-px (<=1.0px) | rest, light | no oracle | no oracle | |
| radio-group | sub-px (indicators <=0.00px, text <=6.5px) | rest, light | no oracle | no oracle | tagged `data-parity-id`, indicators sub-px exact |
| progress | sub-px (<=1.0px) | rest, light | no oracle | no oracle | tagged `data-parity-id` |
| dialog | sub-px (width/X/button <=0.83px, height <=6.0px) | rest, light | dismiss oracle | no oracle | tagged `data-parity-id`, text wrapping line-height allowance |
| tooltip | sub-px (<=1.5px) | rest, light | no oracle | no oracle | tagged `data-parity-id` |
| slider | sub-px (width/X <=0.00px, height <=14.0px) | rest, light | no oracle | no oracle | tagged `data-parity-id`, Radix 6px track vs Awake 20dp knob height allowance |
| alert | sub-px (width <=0.00px, height <=25.0px) | rest, light | no oracle | no oracle | tagged `data-parity-id`, padding allowance |
| avatar | sub-px (<=1.0px) | rest, light | no oracle | no oracle | tagged `data-parity-id` |
| breadcrumb | sub-px (<=76.0px) | rest, light | no oracle | no oracle | tagged `data-parity-id`, inline trail advance allowance |
| collapsible | sub-px (<=2.0px) | rest, light | no oracle | no oracle | tagged `data-parity-id` |
| kbd | sub-px (<=5.0px) | rest, light | no oracle | no oracle | tagged `data-parity-id`, Roboto font advance allowance |
| skeleton | sub-px (<=1.0px) | rest, light | no oracle | no oracle | tagged `data-parity-id` |
| spinner | sub-px (<=1.0px) | rest, light | no oracle | no oracle | tagged `data-parity-id` |
| textarea | sub-px (<=1.0px) | rest, light | no oracle | no oracle | tagged `data-parity-id` |
| toggle-button | sub-px (<=1.0px) | rest, light | no oracle | no oracle | tagged `data-parity-id` |
| dropdown-menu | sub-px (<=110.0px) | rest, light | select oracle | no oracle | tagged `data-parity-id`, item text-width allowance |
| popover | sub-px (<=10.0px) | rest, light | no oracle | no oracle | tagged `data-parity-id` |

Note: `docs/reference/shadcn-previews-local/card-login_light.png` and its Awake pair
(`card-local-light` in `tools/shadcn/shadcn_parity_pairs.json`) are compared for pixel diff but "card"
has no standalone `@AwakeUiPreview` entry of its own in `ShadcnParityScreenshotTest` under this
naming scheme, so it is not counted as one of the 23 -- flagged here rather than silently
dropped; reconcile when card gets tagged for a geometry oracle.

The older 23-component claims below predate the manifest-backed report and may be stale. Run
`python3 .agents/skills/awake-ui-verification/scripts/generate_ui_parity_report.py` after every capture;
its report is the source of truth for current coverage and missing-oracle work.

Adding a component's layout row: tag the reference app's JSX with `data-parity-id` matching
Awake's semantic ids (see `tools/shadcn/reference-app/src/cases.tsx`'s badge/button/checkbox/
switch/input/tabs/select cases for the pattern), re-capture with
`python3 tools/shadcn/capture_shadcn_local.py --only <case> --theme light`, add one
`assertGeometry(...)` call in `ShadcnGeometryParityTest` naming an `allowancePx` you can
justify, then update this table. Style, behavior and motion columns cannot be filled in yet --
no oracle exists for them.

- `tools/shadcn/capture_shadcn_local.py` -- renders real shadcn/ui components straight from
  `ui.shadcn.com`'s own docs pages (Playwright, headless Chromium, fixed 1280x800 viewport,
  devicePixelRatio 1, forced light theme, animations/transitions/caret disabled via injected
  CSS) and saves them to `docs/reference/shadcn-previews/`. Verified byte-identical across
  repeated runs. Re-capture with:
  ```bash
  python3 -c "import playwright" || (pip3 install playwright pillow && python3 -m playwright install chromium)
  python3 tools/shadcn/capture_shadcn_local.py            # all components
  python3 tools/shadcn/capture_shadcn_local.py --only button,card   # a subset, while iterating
  ```
  Each `capture_*` function names the exact `data-slot` selector(s) it depends on -- if
  `ui.shadcn.com`'s own markup drifts enough to break one, that's where to look first.
- `tools/shadcn/compare_parity.py` -- given an Awake preview PNG and a shadcn reference PNG, trims
  each image's own uniform-color border independently, compares the two at their intersection
  size (top-left anchored, since the two are never pixel-identical in layout), and reports
  mismatch %, max channel delta, mean delta, plus a red/blue diff heatmap PNG.
  `python3 tools/shadcn/compare_parity.py --all` runs every pair listed in
  `tools/shadcn/shadcn_parity_pairs.json` (the single source of truth for which Awake preview id pairs
  with which reference filename).
- `ShadcnReferenceComparisonTest` (`samples/ui-showcase`) -- the same aligned-crop comparison,
  reimplemented in Kotlin against `java.awt.image`/`ImageIO` so it runs in the normal Gradle
  suite with no Python/Playwright dependency at test time:
  `./gradlew :samples:ui-showcase:desktopTest --tests "*ShadcnReferenceComparisonTest*"`.
  Renders its own Awake-side previews (doesn't depend on `ShadcnParityScreenshotTest` having
  run first), writes `build/reports/shadcn-parity-metrics.json` plus one diff heatmap per pair
  under `build/reports/shadcn-parity/`, and prints a summary table. Absolute mismatch against
  the real upstream reference is expected and stays untargeted -- pixel-perfect parity with
  shadcn/ui isn't the goal -- but *drift* is gated, see below.
- `.agents/skills/awake-ui-verification/scripts/compare_component_crops.py` for component-level parity when a page preview contains
  several widgets. The reference side is cropped automatically by
  `tools/shadcn/capture_shadcn_local.py` using the reference app's `#case`/portal selector; the Awake
  side is cropped by semantic node ID from the generated preview JSON. This is the canonical
  way to compare a component inside a larger showcase without manually cropping screenshots.

Caveat carried over from the retired parity scorecard: shadcn/ui has no single canonical
look (style presets, base colors, density all vary) -- these captures are *a* real reference,
not *the* one true pixel target. The previous `docs/reference/shadcn-previews/*.png` set
(hand-captured 2026-07-19) was never actually from `ui.shadcn.com` despite being described that
way -- it was captured from `shadcn-compose`, a third-party Kotlin reimplementation (evidence:
the old `select_closed_light.png` showed the literal text "Vega", `shadcn-compose`'s own style
preset name, not a real shadcn select demo's option text). This harness fixes that by going
straight to the upstream demo pages.

### Shadcn Parity Regression Gate

This is the pixel side's regression mechanics, unchanged by the geometry oracle above --
it still catches "got worse," it is just no longer where "is this right" gets decided for
layout. Skip to the component coverage matrix above for which oracle owns which question.

`ShadcnReferenceComparisonTest` used to be deliberately non-failing -- its own header said it
"only asserts the harness itself ran and produced a report a human can open." That gap was not
theoretical: the checkbox component regressed to a 8dp corner radius on a 16dp box (a visible
circle, not a checkbox), `checkbox-local-light` is one of the 12 pairs the harness already
compared every run, and the test still passed because nothing ever read the number it wrote.

The fix is a committed baseline, not a fidelity target. `tools/shadcn/shadcn_parity_baseline.json`
records each pair's mismatch% as of the last time a maintainer looked at its diff PNG and
accepted it. The test now fails if a pair's current mismatch% exceeds its recorded value by
more than `tolerancePct` (1.0 percentage point). Getting *worse* than shadcn/ui fails the
build; staying exactly as far from shadcn/ui as before does not -- absolute distance from the
reference is untouched policy, only regression is gated.

**Tolerance, from evidence, not a guess.** Four back-to-back runs of the comparison on
unchanged code produced byte-identical `shadcn-parity-metrics.json` output (0.00 percentage
points of drift) -- this harness has no measured run-to-run noise on one machine/JVM, because
it renders through Awake's own deterministic CPU rasterizer, not a live browser. 1.0pp is
headroom above that measured-zero floor for cross-machine/JDK/font-rendering variance across
CI runners, not a response to observed flakiness. A real regression dwarfs it: temporarily
forcing the checked checkbox's fill to bright red (`AwakeCheckboxStatesLightPreview`'s
`style = Style { background(Color(1f, 0f, 0f)) }`, a color-only override applied entirely from
the test file, no `commonMain` edit) moved `checkbox-local-light` from 26.32% to 30.77% -- a
4.45pp jump, over 4x the tolerance, and the gate failed with exactly that message. Reverting
the override returned the number to exactly 26.32% and the gate passed again. (A `shape(8f.dp)`
corner-radius override -- the closer analog to the shipped circle-checkbox incident -- was
tried first and produced *zero* measured change, not because of a harness blind spot but
because 8dp already *is* the default preset's `theme.radii.md` -- `ShadcnRadiusScale.fromBase`
computes `md = baseRadius - 2`, and the default `Vega` preset's `baseRadius` is 10dp, so the
"perturbation" was a no-op that happened to match production exactly. Switched to a color
override, which changes pixels the radius token doesn't touch.)

**Same record convention as everywhere else** (`ShadcnParityScreenshotTest`,
`UiShowcasePreviewDocsTest`): `-DAWAKE_RECORD_SNAPSHOTS=true` overwrites
`tools/shadcn/shadcn_parity_baseline.json` with the current run's numbers instead of gating. Follow
`.agents/skills/awake-ui-verification/SKILL.md`'s re-recording discipline -- run without recording
first, read `build/reports/shadcn-parity/<name>_diff.png`, explain the drift, only then record.

**Not every pair is gated.** A pair whose aligned crop doesn't cover most of its own content
measures framing, not fidelity (see `build/reports/ui-parity/report.md` after `awake ui report`:
`good` >=75% of the Awake render's own area was compared, `partial` >=35%, `poor` below that).
`tools/shadcn/shadcn_parity_baseline.json`'s `excluded` map holds pairs deliberately left out, each
with a stated reason -- currently just `slider-local-light`, whose reference PNG
(`docs/reference/shadcn-previews-local/slider-states_light.png`) is itself only 6px tall before
any Awake-side cropping runs (the capture never included the slider thumb -- a
`tools/shadcn/capture_shadcn_local.py`/reference-app issue, not an Awake or alignment problem). It
still renders and appears in the report, just isn't compared against a baseline. `tabs-local-
light` and `card-local-light` were previously `partial`/borderline; `tabs-local-light` stayed
un-gated-by-exclusion but its full-canvas ratio (0.744) undercounts a mostly-fine crop (its
own trimmed-content ratio is ~95%, the shortfall is deliberate preview margin, not
misalignment) and is gated anyway. `card-local-light` and the tooltip pair were fixed instead
of excluded: the card preview had drifted to compare content the reference never showed (an
extra password field, a footer-slot divider absent from the real login-card demo), and the
tooltip pair was comparing a button trigger against the reference's tooltip-bubble capture --
a wrong-content pairing, not an alignment problem. Both are `good` crops now.
