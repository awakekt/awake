---
name: awake-ui-verification
description: How to prove a UI change is correct in Awake - which tool answers which question, when a baseline may be re-recorded, and why a passing golden is not evidence of fidelity. Read before re-recording any snapshot/golden, before citing a parity number, and before claiming a visual change works. Trigger keywords - snapshot, golden, baseline, re-record, AWAKE_RECORD_SNAPSHOTS, parity, fidelity, pixel diff, mismatch, signature, drift, visual regression, shadcn reference, verify UI.
---

# Verifying UI in Awake

Policy for *what* must be proven lives in `docs/reference/ui-validation.md`. This skill is the
*how*: choosing the right tool, and the judgment calls that tooling cannot make for you.

For plain-English meanings of UI test terms, read `docs/reference/ui-testing-dictionary.md`.

## Keep tests out of debt hell

The test must make its behavior obvious. Do not repeat context, font, theme, frame setup, or a
normal pointer press/release sequence in every fixture.

- One normal frame: `renderUiComponent(...)`.
- Several frames: `uiTestSession(...)`.
- Normal pointer actions: `hover`, `click`, `doubleClick`, `longPress`, `rightClick`, `drag`.
- Exact wheel/keyboard/custom input: `frame(UiInputState, ...)`.
- Raw `UiContext`: only when that low-level lifecycle is the thing being tested. Mark the test
  class or low-level helper file `@UiLowLevelTest("reason")`; `verifyUiTestLifecycle` rejects
  unmarked manual frames.

Before adding a test file, find the existing matrix or component fixture that owns the behavior.
Add a case there unless this is a distinct regression invariant.

## The one distinction everything depends on

**"Did this change?" and "Is this right?" are different questions, answered by different
tools. Never let one stand in for the other.**

| Question | Tool | What a pass means |
|---|---|---|
| Did this change? | Snapshot goldens (`snapshots/ui/*.png`), signature maps | Output matches what Awake produced *before*. Says nothing about correctness. |
| Is this right, layout? | `ShadcnGeometryParityTest` vs the reference app's own `getBoundingClientRect` | Size/position match shadcn to sub-pixel, exactly, no rasterizer dependency. |
| Is this right, everything else? | Nothing yet | Colour, border, shadow and all behavior (click/keyboard/focus/hover) have no oracle. `ShadcnReferenceComparisonTest` still runs but is demoted -- see below. |
| Is this value right? | `ShadcnReferenceTokenExpandedTest` vs generated `ShadcnReferenceTokens.kt` | A token equals the pinned reference exactly. |
| Does the logic hold? | Unit tests, throwaway probes reading real `UiBounds`/pixels | The measured number is what you claim. |

This repo shipped the confusion twice. The retired `shadcn-parity.md` described itself as
machine-readable ground truth while sourcing from `shadcn-compose`, a third-party port. The
reference PNGs it cited came from the same port — one rendered the text "Vega", a
shadcn-compose preset name that appears nowhere in real shadcn. Everything "verified against
shadcn" was Awake compared to a lookalike.

**Check provenance before trusting any reference.** If you cannot name where an artifact came
from and how to regenerate it, it is not ground truth.

## Re-recording a baseline

`-DAWAKE_RECORD_SNAPSHOTS=true` overwrites goldens. It is the single easiest way to convert a
real regression into a permanently green test.

Required sequence, no exceptions:

1. Run without recording. Let it fail.
2. **Open the diff PNG** (`build/ui-previews/<id>_diff.png`) and look at it.
3. Explain the drift in terms of the change you made. A uniform content shift means padding or
   size moved. Corner-only pixels mean radius moved. Ghost-doubled text means glyphs moved. If
   you cannot explain it, you have found a bug, not a baseline to refresh.
4. Only then record, and say why in the commit message or a dated comment next to the constants.

When several visual changes land together (this is normal in a multi-agent pass), do **one**
re-record at the end. Recording per-change bakes each intermediate state into the goldens and
destroys the ability to attribute a later regression.

### Step 3 means a predictive rule, not a plausible story

"I changed blending, so the blended scenes moved" is a story. It fits any outcome, which is why
it cannot catch a fix that is also breaking something. Instead state a rule that predicts the
moved set **before** you look, then check it against every scene:

> A scene moved **iff** it contains at least one `FilledPath`. — 16/16, zero exceptions.

The rule must be a biconditional over the full scene list. A scene that moved without satisfying
it, or satisfies it without moving, is an unexplained pixel: stop and investigate.

This is not ceremony. Unifying the rasterizer's per-primitive composites onto one blend
(2026-08-21) moved six signatures for the right reason *and* introduced a double-blend seam:
two triangles sharing an edge both claim a sample sitting exactly on it, which the old overwrite
hid. Re-recording on the strength of "translucent things changed, looks right" would have baked
that seam into the goldens as correct. Writing the rule forced a per-scene audit, and the seam
turned up as a scene that moved more than the rule allowed.

Corollaries:

- **A moved golden is a question, not an answer.** Never let re-recording be the fix.
- **Test the mechanism, not the symptom.** "The alert looks right" passes happily while the same
  change wrecks every vector path. Target the actual rule that changed.
- **Measure, don't infer.** Print the pixel. `r=255 g=0 b=0 a=25` is a fact; "the alpha looks
  lost" is a hunch, and hunches send you to patch code that was never broken.
- **Follow the value to where it dies.** The alert's `0.1` alpha was correct in the style *and*
  in the emitted primitive. The bug was a *second* primitive underneath. Dump the whole primitive
  stream before concluding a value was dropped.
- Keep "tests I did not touch still pass" separate from "baselines I re-recorded now pass". Only
  the first is evidence.

Two independent mechanisms exist and both must be updated:

- **PNG goldens** — refreshed by the record flag.
- **Signature constant maps** (`UiSnapshotSignatureTest`, `UiShowcaseLayoutSignatureTest`) —
  hand-edited hex, never touched by the record flag. The failure message prints the replacement
  matrix.

Beware the failure message's shape: `assertSnapshotSignatures` throws on the *first* mismatch,
so it names one scene while many have drifted. `UiShowcaseLayoutSignatureTest` does the
opposite — it always prints the complete matrix, most of which is unchanged. Diff the printed
values against the recorded ones rather than trusting the headline.

## The pinned capture is the authority — not the PNG, and not what you remember shadcn doing

Every reference case ships a JSON next to its PNG holding the browser's own computed styles:

```bash
python3 -c "import json;print(json.load(open('docs/reference/shadcn-previews-local/alert-variants_light.json'))['nodes'])"
```

It gives exact `backgroundColor`, `borderColor`, `borderWidth`, `borderRadius`, `color`,
`fontSize`, `lineHeight` and four-sided padding per node. Read it before concluding anything from
a crop. `tools/shadcn-reference-app/src/ui/<component>.tsx` is the vendored source at the same
pin and is the second half of the evidence — the class list explains *why* the numbers are what
they are.

Worked example, 2026-08-21. Awake's alert painted a `muted` background for the default variant
and `destructive/10` behind a full-strength border for destructive. The capture said otherwise:

| | default | destructive |
|---|---|---|
| `backgroundColor` | `oklch(1 0 0)` (white) | `rgba(0, 0, 0, 0)` — **transparent** |
| `borderColor` | gray, 1px | destructive at **0.5** alpha |
| `color` | black | destructive red |

and `alert.tsx` explained it: `default: "bg-background text-foreground"`,
`destructive: "border-destructive/50 text-destructive"` — which sets no background at all. All
three colors were wrong, and so was a source comment in this repo asserting `AlertDescription` is
`text-muted-foreground`; the pinned file has no color class there, so it inherits the root and a
destructive alert's description is red.

Two rules follow:

- **Never translate from memory of shadcn.** Conventions changed between versions; `bg-*/10`
  tinted alerts are an older one. Only the pin counts, and the pin is what the captures came from.
- **A component can look plausible and still be wrong on every axis.** The tinted alert read as a
  perfectly reasonable alert. Only the numbers exposed it.

## Parity is four dimensions, not one number

Read `docs/reference/ui-validation.md`'s "Component coverage matrix" before citing any parity
percentage. Layout, style, behavior and motion are independent, and as of 2026-08-15 only
layout has an oracle -- 7 of 23 components, 0 of 23 for the other three. There is no single
"parity%" to quote; asking for one and getting a pixel mismatch number back is how a font
mismatch and a mis-framed reference both got mistaken for component bugs earlier that session.

## Reading a pixel parity number (demoted, colour/border/shadow only)

`ShadcnReferenceComparisonTest` writes `build/reports/shadcn-parity-metrics.json`. Since
`ShadcnGeometryParityTest` landed, this test no longer decides layout questions -- padding,
width, spacing, advance. It answers "does this still look like the right colour/radius/border",
nothing more, and its mismatch% will never reach 0 even for a pixel-perfect layout (different
rasterizer, different font hinting). Each entry carries `awakeSize`, `referenceSize` and
`comparedSize`.

**`comparedSize` gates whether `mismatchPct` means anything.** The two images are framed
differently, so the harness compares their aligned intersection. When that intersection is a
sliver — a slider comparison collapsing to 300x12, a dialog comparing 320x150 of a 1280x800
capture — the percentage measures framing, not fidelity. The manifest-backed report marks
these `poor` and they must be read as **unmeasured**, not as failures.

Demonstrated: the glyph-advance fix produced a large, plainly visible improvement in text
quality and moved these numbers by fractions of a percent, one of them upward. A harness
pointed at misaligned inputs cannot see a real fix. Do not use mismatch% to decide whether a
change helped until its row reads `good`.

## Proving a visual change actually renders

Reasoning from source about spacing, centering or smoothness is unreliable. Build the real
thing and read real output:

- **Numbers and ordinary component frames** — use
  `renderUiComponent(...)` from `awake:ui:testing`. It owns the frame lifecycle, density/font
  restoration, input snapshot, semantics, and emitted primitives. Install a design-system scope
  through its `rootProvider`; do not hand-roll `UiContext.beginFrame`, font/theme pushes, and
  `finishFrame` in a component or snapshot fixture.

  ```kotlin
  val frame = renderUiComponent(
      width = 240f,
      height = 80f,
      rootProvider = { content -> shadcnTheme { content() } },
  ) {
      shadcnButton("save", "Save")
  }
  assertEquals(36f, frame.bounds("save").height)
  ```

  `UiTestSession` is the multi-frame equivalent for pointer/key interaction. Its official
  gestures are `hover`, `click`, `doubleClick`, `longPress`, `rightClick`, and `drag`; use an
  exact `UiInputState` frame only for wheel, keyboard, or other input it cannot express. Use raw
  `UiContext` only for a renderer/backend probe that the testing helper cannot express.
- **Pixels** — rasterize and write a PNG you open and look at:

```kotlin
val ui = UiContext()
ui.beginFrame(w.toFloat(), h.toFloat(), testSnapshot(), deltaSeconds = 1f / 60f)
ui.createAbsolute(x = 0f, y = 0f).yourWidget(...)
val pixels = ui.endFrame().rasterize(w, h, background = Color.Black)
// convert to BufferedImage, ImageIO.write to a path you then inspect
```

- **Through a real backend** — `UiAnimationFrameCapture` / `RendererHeadlessPixelBaselineTest`
  when the question involves the GPU path rather than the CPU rasterizer.

Delete the probe once it has answered the question; keep it only if it locks something worth
locking. Both defects found this session were caught this way and were invisible to every
existing test: a centroid fan overfilling concave glyphs, and a CPU-rasterizer bbox truncation
that dropped each triangle's last pixel row. The snapshot suite stayed green through both,
because the baselines were recorded from the same broken code.

**A test suite that renders through the defect it is meant to catch will never catch it.** When
a rendering primitive changes, verify with your eyes at least once before trusting any golden.

## What none of this covers

State it rather than implying coverage:

- Only the default theme (`Vega`, `Neutral`) and light mode have reference captures. Seven
  presets and all of dark mode are unverified against shadcn.
- Behavior — keyboard navigation, focus management, dismiss layers — has no parity coverage.
  These are still-image comparisons.
- Real-GPU output is only spot-checked; most suites run the CPU rasterizer in `ui-testing`,
  which is a separate implementation from the Vulkan/WebGPU pipelines.

### Where the CPU rasterizer deliberately matches the backends, and where it cannot

Because that rasterizer backs the preview, snapshot and parity images, anywhere it diverges from
the shipped pipeline it is a wrong *oracle*, not merely a wrong pixel — a whole class of bug that
stays green. Two such divergences were closed on 2026-08-21 and are worth knowing:

- **Compositing.** Every primitive now blends through one `PixelMap.blend` (straight-alpha
  source-over). Plain quads, gradients and triangle meshes previously overwrote the destination
  and parked the source alpha in the alpha channel, so any translucent fill rasterized fully
  saturated.
- **Vector antialiasing.** `FilledPath` tessellates through `tessellateFillAa` and interpolates
  per-vertex alpha, matching `UiRunCoalescer`. It previously used the flat `tessellateFill()`,
  so previews showed ragged curves and uneven stroke width the backends never render.

`StrokedPath` is **intentionally** left un-antialiased, because `UiRunCoalescer` hands it the
flat `tessellateStroke` too. Do not "improve" it here — the preview must neither under- nor
overstate the backends. Fixing it means fixing the backend path first.

When adding a primitive to the rasterizer, check which tessellator the coalescer gives it and
match that, then pin the behaviour in `UiRasterizerBlendTest`.

## Where a test belongs, and when NOT to write one

131 test files across `ui-core`, `ui-headless`, `ui-designsystem` and `ui-showcase`. The count is
not the problem; the duplication is. Tests here get named after the BUG that produced them
(`WrapContentScrollLeakProbeTest`, `ScrollableFillMaxChildMeasureTest`), so nobody can tell where
a new case belongs and the same behaviour ends up covered three times from three angles -- none
of them exhaustive.

### Three tiers

**1. Matrix -- one per subsystem, data-driven, exhaustive.**
`LayoutSizingMatrixTest` is the model: container x parent sizing x child sizing, one shape per
cell, expected values computed by arithmetic. It owns the spec. **A new case is a ROW here, not a
new file.**

**2. Regression -- one per shipped defect.**
Named for the invariant, never the incident. `CenteredTextOpticalAlignmentTest`, yes;
`WrapContentScrollLeakProbeTest`, no. Must fail with its fix removed -- verify that explicitly,
then delete it if it does not.

**3. Snapshot & parity -- pixels only.**
Baselines and shadcn comparison. Kept separate because they need periodic re-recording, so they
cannot double as correctness gates.

### Which module

| concern | module | why |
|---|---|---|
| sizing, scrolling, measurement | `ui-core` | fast, exact, no baselines |
| does a recipe pass the right modifiers | `ui-designsystem` | composition, not layout maths |
| geometry vs shadcn (layout) | `ui-showcase` (`ShadcnGeometryParityTest`) | exact, sub-pixel, no render needed beyond the semantic tree |
| pixels vs shadcn (colour/border/shadow) | `ui-showcase` (`ShadcnReferenceComparisonTest`) | the only thing needing a render |

A layout assertion in `ui-showcase` is a slow duplicate of a `ui-core` case that fails for
unrelated reasons. Push it down.

### Before adding a test file

- [ ] Is this a ROW in an existing matrix? Add it there instead.
- [ ] Does it fail with the fix removed? If not, it is decorative -- delete it.
- [ ] Is the assertion an exact value? Thresholds are how four sidebar tests passed against a
      visibly broken sidebar -- they all asserted "more than 48px" and 0px-through-24px cleared it.
- [ ] Is it in the lowest module that can express it?
- [ ] Does an existing file already own this behaviour? Extend that one.

### Why exhaustive beats hand-picked

Four hand-written sidebar tests found nothing across a full session. One 12-cell matrix found
eight defects in a single run, and the cells that passed told us as much as the ones that failed
(`Fixed/Fixed` correct everywhere narrowed it to distribution). Hand-picked cases test what the
author already suspects; a matrix tests what nobody thought of.

## Commands

```bash
tools/fetch_shadcn_reference.sh                      # pin the reference (run first)
./gradlew :awake:ui:ui-core:desktopTest
./gradlew :awake:ui:headless:desktopTest
./gradlew :samples:ui-showcase:desktopTest
scripts/awake ui report                              # after reference/preview/validate
```

See `tools/README.md` for the generators and the full parity chain.

### CLI shortcut

Use `scripts/awake ui` (or add `scripts/` to `PATH` and use `awake ui`) when iterating on a
registered component fixture. It is a dispatcher over the same source-of-truth manifests, not a
new renderer:

```bash
awake ui reference --component checkbox --state rest --theme light
awake ui preview --component checkbox --state rest --theme light --debug-layout
awake ui validate --component checkbox --theme light
awake ui report
awake ui performance --component checkbox --theme light
```

The command rejects states and visual configuration that lack a paired official reference and
Awake preview. Do not interpret a generated Awake-to-Awake golden as parity, and do not use any
record flag before reviewing the official crop heatmap.

The report calls out per-node geometry, four-sided padding, sibling spacing, and border/radius
facts when their semantic evidence exists. It is correct for a property to be `unmeasured`:
never infer vertical padding from a text line box or infer border width from a low-resolution
pixel diff. Add the missing semantic/style capture first.

### Universal comparison triage

Apply the same ordering to every component, state, and overlay: inspect the source crop, Awake
crop, and heatmap; then reason from report artifacts → geometry → padding → spacing/relationships
→ layout intent → style → paint. The image tells you where paint differs, while semantic and
computed-style facts tell you what contract differs.

- Parent bounds drift: inspect the recipe/container constraint or overlay placement.
- Child bounds drift under a correct parent: inspect bound propagation, intrinsic sizing,
  `fillMax*`, weight, and child modifiers. If that evidence indicates a generic measurement or
  allocation defect, escalate it to `ui-core` rather than compensating in the recipe; preserve
  the delta and add density-1-and-2 core layout coverage before retesting parity.
- Padding/spacing/relationship drift: inspect the recipe's insets, arrangement, separators,
  border-collapse, or anchor offset. Never hide it with comparison framing.
- Layout-intent mismatch: translate the source flex/grid/min/max rule and use multi-probe
  fixtures before claiming adaptive behavior.
- Style drift after geometry passes: inspect owning tokens, border, radius, color, shadow, or
  renderer path. A heatmap alone cannot settle these.
- `unmeasured`, `partial`, missing artifact, behavior, or motion: coverage is incomplete; add
  the relevant semantic/source/interaction evidence rather than guessing.

Before claiming a correction, state the report's before/after expected–actual–delta values, the
source rule, the lowest Awake ownership layer changed, inspected crop/heatmap paths, focused
tests, and remaining incomplete evidence. This is universal policy, not a component-specific
checklist.

### Observed-bug handoff is mandatory

Report every observed `drift`, `REVIEW`, behavioral failure, or blocking `unmeasured` field even
when the task stops before a fix. Embed or attach the source crop, Awake crop, and heatmap using
their absolute artifact paths, then summarize the component state/viewport, semantic IDs,
expected → actual (`delta`), likely lowest owner, remaining uncertainty, and the next command.
A mismatch percentage without those images and facts is not a bug report. A resolved issue needs
the same evidence with a concise before/after summary.

For nested components and overlays, source fixtures must put `data-parity-id` on both the
container and every measured child; portal fixtures also require a real trigger/anchor ID. Awake
uses the same IDs in its semantic tree. Declare their horizontal/vertical gaps or trigger-to-
surface offsets in `tools/shadcn_parity_manifest.json`. The manifest is an
explicit correspondence contract, not a request to auto-match elements by label or position.
Use `skills/awake/commands/verify-ui-parity.md` for the complete registration workflow.

## Component-level cropping

When a showcase page contains several widgets, do not manually crop before/after screenshots.
The shadcn reference side is already component-cropped by Playwright through
`tools/capture_shadcn_local.py`. For the Awake side, use
`tools/compare_component_crops.py`: it resolves a semantic node ID from the generated preview
JSON, applies the preview raster scale and optional logical padding, writes the crop and a
heatmap, and records JSON metrics. Use `tools/shadcn_parity_manifest.json` for new coverage.
The legacy crop manifest remains a compatibility input while existing cases migrate. Cases
without a threshold are reported as `REVIEW`; this tool does not update baselines. Review the
crop and diff before adding a threshold or enabling `--fail-on-mismatch`.
