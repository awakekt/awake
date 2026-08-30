---
name: awake-ui-verification
description: How to prove a UI change is correct in Awake - which tool answers which question, when a baseline may be re-recorded, and why a passing golden is not evidence of fidelity. Read before re-recording any snapshot/golden, before citing a parity number, and before claiming a visual change works. Trigger keywords - snapshot, golden, baseline, re-record, AWAKE_RECORD_SNAPSHOTS, parity, fidelity, pixel diff, mismatch, signature, drift, visual regression, shadcn reference, verify UI.
---

# Verifying UI in Awake

<!-- ui-tooling-map -->
> **Four docs cover UI tooling, with different jobs.** Land in the one that matches your question:
>
> | Question | Read |
> |---|---|
> | *Is anything wrong?* | `scripts/awake verify` — every gate, one run |
> | *Which tool answers my question, and may I re-record this baseline?* | [`skills/awake-ui-verification`](SKILL.md) — judgment |
> | *What proof does this kind of UI change require?* | [`docs/reference/ui-validation.md`](../../docs/reference/ui-validation.md) — policy |
> | *What commands do I run, in what order?* | [`docs/reference/ui-parity-tool.md`](../../docs/reference/ui-parity-tool.md) — procedure |
> | *What is this script, and can it fail a build?* | [`tools/README.md`](../../tools/README.md) — catalogue |
<!-- /ui-tooling-map -->


Policy for *what* must be proven lives in `docs/reference/ui-validation.md`. This skill is the
*how*: choosing the right tool, and the judgment calls that tooling cannot make for you.

## Verification Tiers

Preserve accuracy without repeating expensive work during iteration:

- **Iteration:** compile the owning module, run focused component tests, and generate a CPU preview
  when pixels are involved.
- **Commit:** run the component's full state matrix, inspect fresh preview/diff artifacts, and run
  `git diff --check`.
- **Merge/parity claim:** run the standalone and showcase routes, manifest/report audits, and the
  required reference comparison.
- **GPU-only:** run offscreen Vulkan/WebGPU capture only for backend paint, shader, blending,
  text-sampling, frame-pacing, or anti-aliasing changes.

The tier is a cost boundary, not an evidence exemption. A component cannot be called complete until
the merge tier and all required state oracles have passed. The canonical command sequence is in
[`docs/reference/ui-parity-tool.md`](../../docs/reference/ui-parity-tool.md).

For shadcn work, follow [`awake-shadcn-parity-workflow`](../awake-shadcn-parity-workflow/SKILL.md):
prove the pinned reference, standalone recipe, and real catalog shell separately before claiming
parity.

## When to create a core-issue entry

Use [`docs/reference/ui-parity-core-issues.md`](../../docs/reference/ui-parity-core-issues.md)
only after evidence proves a remaining mismatch cannot be fixed in the component recipe, token
mapping, fixture, or comparison tooling. A normal wrong color, padding, icon, behavior, or
missing token is active parity work, not an issue-tracker entry. Each entry must identify the
missing engine capability and a future acceptance measurement.

For plain-English meanings of UI test terms, read `docs/reference/glossary/ui-testing.md`.

## Keep tests out of debt hell

The test must make its behavior obvious. Do not repeat context, font, theme, frame setup, or a
normal pointer press/release sequence in every fixture.

- Retained Compose UI, one normal frame: `composeFrame(...)`.
- Retained Compose UI, several frames: `composeTestSession(...)`.
- Retained Compose UI (`:awake:compose:*`), one normal frame: `composeFrame(...)` plus
  `onNode(...)` / `onNodeWithTag(...)` assertions from `:awake:compose:ui-testing`.
- Retained Compose UI, several frames: `composeTestSession(...)`; render the first frame before
  calling `click(tag)`, because input intentionally targets the preceding placed frame.
- Normal pointer actions: `hover`, `click`, `doubleClick`, `longPress`, `rightClick`, `drag`.
- Exact wheel/keyboard/custom input: `frame(UiInputState, ...)`.
- Raw `UiContext`: only when that low-level lifecycle is the thing being tested. Mark the test
  class or low-level helper file `@UiLowLevelTest("reason")`; `verifyUiTestLifecycle` rejects
  unmarked manual frames.

Before adding a test file, find the existing matrix or component fixture that owns the behavior.
Add a case there unless this is a distinct regression invariant.

### Retained Compose testing utilities

`awake:compose:ui-testing` is the Compose engine's test surface. It mirrors the useful shape of
`androidx.compose.ui.test` without pretending a retained frame is a live Android composition:

- `onNode(hasTestTag("save") and hasRole(SemanticsRole.Button))` finds one semantic node;
  `assertExists()`, `assert(...)`, and `assertBoundsInRoot(...)` prove semantic and exact geometry
  facts. Use `onAllNodes(...)` only for intentional collection assertions.
- `captureImage(fileOrPath)` is the required pure-JVM visual snapshot testing method. It software-rasterizes
  the `ComposeComponentFrame` directly into a PNG image without needing GPU hardware, Skiko, or an
  emulator. Authored components must provide a visual snapshot test using `captureImage`.
- `captureSemantics()` emits deterministic JSON containing stable tag, role, label, root bounds,
  selected, and disabled state. This is the Awake-side artifact to compare with a browser reference;
  it is not a screenshot surrogate.
- `rasterizeDebugOverlay(...)` uses the built-in headless CPU image capture and draws every semantic
  node's bounds. Open it to diagnose a failed geometry assertion; the direct bounds assertion and
  semantic JSON remain the oracle.
- `rasterizeLayoutOverlay(...)` draws every placed layout node, including untagged containers and
  overlay layers. For shell, scrolling, clipping, overlap, or z-order bugs, emit and inspect this
  wireframe artifact alongside the normal PNG. Its outlines are painted after rasterization, so an
  outline outside a parent proves the child's measured bounds, not necessarily escaped paint;
  compare it with the normal PNG.
- `composeTestSession(...)` owns a retained `ComposeHost`. Its `click(tag)` sends press/release at
  the centre of the tag's bounds and returns the next frame. For keyboard, wheel, or custom input,
  use `frame(FrameInput(...))` explicitly.

The CPU rasterizer is a fast, deterministic diagnostic and snapshot lane. It cannot establish GPU
fidelity; use the existing real offscreen Vulkan texture capture when the question is backend paint,
frame pacing, or pipeline behavior.

### Debugging a swallowed click, hover, or tap

"Nothing happens when I click X" is never solved by reading source. Work through these tiers in
order — each one rules out a whole failure class before you spend time on the next:

1. **Prove or disprove it headlessly first.** Never trust "I clicked it in the running app and
   nothing happened" as a starting fact without also reproducing it in a test — a native window
   has platform input plumbing (GLFW/OS event delivery) as an extra variable a `composeTestSession`
   bypasses entirely. Render a `before` frame, call `session.click(tag)`, render `after`, and
   `ImageChops.difference(before, after).getbbox()` in Python (or an equivalent pixel diff). `None`
   means literally nothing changed anywhere — that's a real finding, not a maybe.
2. **Every production composable that a test might need to target gets a `Modifier.testTag(...)`.**
   This is not a smell — it's the same pattern Jetpack Compose itself uses, a semantics-only marker
   with zero visual or runtime cost. Use hierarchical, dot-separated names scoped to their owner
   (`showcase.sidebar.page.${page.id}`, `showcase.sidebar.header`), so a test can target one node
   without depending on layout order.
3. **If the diff is `None` (nothing rendered differently), suspect hit-testing, then phase.**
   Confirm the click landed at all: `onClick` firing is the cheapest signal (a local `var clicks =
   0` counter in the test beats hunting through production state). If the counter never increments,
   the event never reached the node. If it *does* increment and the pixels still never change, the
   state was read a phase too early — captured at composition instead of read at measure or draw.
   That is the shape behind the hover, focus and typing bugs of 2026-08-29; there is no
   recomposition or invalidation to suspect, since the whole tree composes every frame.
4. **Bisect with a minimal repro before touching production code.** Strip the failing composition
   down to the smallest tree that still reproduces the bug, in a throwaway test, adding one real
   piece back at a time (a real `Sidebar`/`SidebarProvider` shell here, an overlay there, a
   `graphicsLayer` there) until one specific addition flips a passing test to failing. That one addition
   is the cause — confirmed by evidence, not implicated by suspicion. This is drastically faster
   than reasoning about the whole tree.
5. **`rasterizeLayoutOverlay(...)` proves geometry, not hit-testing.** A node's outline can be
   exactly where you expect and still never receive the click, because Awake's compose engine's
   hit-test (`LayoutNode.contains()`) is a **permissive union** of a node's own box and its
   content's box — deliberately loose so an `offset()`'d child's pointer links are still reachable.
   The cost: a node whose content sits far from its own declared origin (a large `offset()`, or a
   `Row` wrapping a spacer, another wide sibling) gets a hit-test envelope spanning the entire gap
   between them, and `PointerInputDispatcher.descendInto`'s sibling loop stops at the **first**
   envelope match — not the first *precise* one — so that phantom region silently blocks every
   sibling underneath it. If a wireframe outline looks right but the click still misses, this
   envelope/gap interaction is the next suspect, not a rendering bug.
6. **Instrument the engine directly when reading it isn't enough.** Add a temporary `println` in
   the exact dispatcher/measure-policy method in question (e.g. `PointerInputDispatcher.hitTest`),
   run the failing test, and read `<system-out>` from the JUnit XML at
   `build/test-results/desktopTest/TEST-<FqcnClassName>.xml` — Gradle does not print `println`
   output to the console by default. This is the fastest way to see real `absoluteX`/`width`/
   `zIndex` values instead of re-deriving them from source. **Revert every debug print before
   committing** — confirm with `git diff` on the instrumented file showing no changes.
7. **Fix the root cause, not the symptom, and prove the fix at the same three levels the bug was
   found: the minimal repro, the actual production composable, and the full end-to-end app tree.**
   Keep whichever throwaway test exercises the exact regression as a permanent test, and delete the
   rest of the bisection scaffolding.

### Text-input cursor standard

For every public text-input recipe, test cursor behavior at the recipe boundary as well as the
`BasicTextField` foundation tests: clicking/focusing routes typing to that field, insertion occurs
at the saved caret index, Backspace/Delete and Arrow/Home/End preserve valid bounds, and a disabled
field neither gains focus nor mutates. Cursor blink pixels are not a stable screenshot oracle;
assert `TextFieldState.cursor`, text, and focus across deterministic frames instead. Selection and
IME require their own explicit cases when those capabilities are enabled.

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
a crop. `tools/shadcn/reference-app/src/ui/<component>.tsx` is the vendored source at the same
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

### Scoped parity scores: when 100% is a truthful claim

Never average browser-versus-Awake pixels into a universal parity percentage. Report a named,
full-coverage scope instead:

- **Structural parity** covers geometry, padding, spacing, and declared relationships. It may say
  `100%` only when every required deterministic oracle exists and passes.
- **Surface parity** covers border width, radius, and other source values for which Awake exports
  a normalized counterpart. It can independently say `100%` under the same coverage rule.
- **Raster diagnostics** retain image mismatch and heatmaps. They remain diagnostic because glyph
  rasterization, shadow kernels, anti-aliasing, and color management differ by renderer.
- **Typography, shadows, and colors** remain `unmeasured` until each has a normalized oracle.
  They are neither failures nor hidden passes, and must not dilute a structural score.

`generate_ui_parity_report.py` emits `scorePct` plus `coveragePct` for each scope. A score of
100 with less than 100 coverage is incomplete, not parity. This makes “Button icons: 100%
structural parity; typography/shadow/color unmeasured” a precise claim rather than a disguised
pixel-diff conclusion.

## Required parity loop -- capture before declaring a component done

This is the standard workflow for every registered shadcn parity component. It is not an
optional debugging technique and it does not wait for a reviewer to report a visual bug.

1. **Capture the current evidence before editing.** Run `awake ui reference`, `awake ui preview`,
   `awake ui validate`, and `awake ui report` for each registered state/theme in scope. Open the
   source crop, Awake crop, and heatmap. The comparison is invalid if the artifacts are missing,
   stale, or represent different content, theme, or state.
2. **Classify the first failing fact.** Work in this order: artifact correspondence, geometry,
   padding, spacing/relationships, layout intent, computed style, paint, then behavior/motion.
   A paint heatmap cannot diagnose a geometry failure. `partial`, `unmeasured`, or `REVIEW` is
   incomplete evidence, never a pass.
3. **Read the pinned upstream source.** The matching
   `tools/shadcn/reference-app/src/ui/<component>.tsx` class list and its captured browser JSON
   define the rule to translate. Do not infer it from a screenshot or an older shadcn convention.
4. **Fix the lowest reusable owner.** A generic measurement, modifier, drawing, input, or
   semantics defect belongs in the relevant Compose/Foundation/Headless layer; a token or
   recipe-only rule belongs in `ui-shadcn`. Do not compensate for an engine defect with
   component-local offsets, overlays, crop padding, or a new threshold.
5. **Add a regression at that owner.** Prove the invariant with an exact layout/draw/input test
   that fails without the fix. For interaction, use `composeTestSession` and exercise the whole
   semantic hit target, not only the text or icon inside it.
6. **Recapture after the fix.** Repeat the full chain, open the new source/Awake/diff images, and
   report the before/after facts. A component is only complete when its registered evidence and
   focused regression tests pass, or every remaining gap is explicitly recorded as unsupported.

### What is automated, and what still needs review

The tool must reject missing paired artifacts and report geometry, layout-intent, style, and
pixel evidence. It should surface fresh crops and heatmaps for every required case, and fail a
declared gate on an unexplained required mismatch. This prevents silent coverage gaps.

Human review is still required for the conclusion a pixel difference is acceptable: rasterizer
differences, font hinting, a deliberately divergent behavior, or a newly discovered unsupported
capability need an explicit source rule and written classification. Automation can show the
evidence; it must not silently relabel a visible difference as parity.

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
  `composeFrame(...)` from `:awake:compose:ui-testing`. It owns the frame lifecycle, density/font
  restoration, input snapshot, semantics, and emitted primitives. Install a design-system scope
  through its `rootProvider`; do not hand-roll `UiContext.beginFrame`, font/theme pushes, and
  `finishFrame` in a component or snapshot fixture.

  ```kotlin
  val frame = composeFrame(
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

- **Retained Compose engine** — use `composeFrame(...)` for one frame or
  `composeTestSession(...)` for input, then assert through semantic interactions. For parity,
  write `captureSemantics().toJson()` with the preview artifact and inspect
  `rasterizeDebugOverlay(...)` when geometry drifts. For shell, scrolling, clipping, overlap, or
  z-order drift, also use `rasterizeLayoutOverlay(...)`. Do not reimplement a tree walker, bounds
  probe, or press/release sequence in an individual recipe test.

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

131 test files across `compose:ui`, `compose:foundation`, `ui:designsystem` and `ui-showcase`. The count is
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
| retained Compose semantic/bounds/input test | `awake:compose:ui-testing` | one canonical test harness over `ComposeHost`; emits parity-ready semantic artifacts |
| sizing, scrolling, measurement | `compose:foundation` | fast, exact, no baselines |
| does a recipe pass the right modifiers | `ui-shadcn` | composition, not layout maths |
| geometry vs shadcn (layout) | `ui-showcase` (`ShadcnGeometryParityTest`) | exact, sub-pixel, no render needed beyond the semantic tree |
| pixels vs shadcn (colour/border/shadow) | `ui-showcase` (`ShadcnReferenceComparisonTest`) | the only thing needing a render |

A layout assertion in `ui-showcase` is a slow duplicate of a `compose:foundation` case that fails for
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
tools/shadcn/fetch_shadcn_reference.sh                      # pin the reference (run first)
./gradlew :awake:compose:foundation:desktopTest
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

Prefer this one-command loop while fixing a registered component:

```bash
awake ui inspect --component checkbox --state rest --theme both
```

It regenerates the official reference and retained-Compose evidence, validates it, writes the
report, and produces `build/reports/ui-parity/<component>-inspect.png` (`Reference | Awake
Compose | Diff`) plus a hash-based provenance record. Geometry drift also produces the semantic
debug overlay automatically. The contact sheet is the required visual-review input, not a
replacement for that review.

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
surface offsets in `tools/shadcn/shadcn_parity_manifest.json`. The manifest is an
explicit correspondence contract, not a request to auto-match elements by label or position.
Use `skills/awake/commands/verify-ui-parity.md` for the complete registration workflow.

## Component-level cropping

When a showcase page contains several widgets, do not manually crop before/after screenshots.
The shadcn reference side is already component-cropped by Playwright through
`tools/shadcn/capture_shadcn_local.py`. For the Awake side, use
`skills/awake-ui-verification/scripts/compare_component_crops.py`: it resolves a semantic node ID from the generated preview
JSON, applies the preview raster scale and optional logical padding, writes the crop and a
heatmap, and records JSON metrics. `tools/shadcn/shadcn_parity_manifest.json` is the single
source of truth for cases; the legacy `ui_component_parity_cases.json` duplicate has been
retired. Cases without a threshold are reported as `REVIEW`; this tool does not update
baselines. Review the
crop and diff before adding a threshold or enabling `--fail-on-mismatch`.


## Automated Verification Gate Commands

Before claiming visual or behavioral verification, execute:

```bash
# 1. Verify Render Quality & Behavioral Dismissal
python3 tools/shadcn/audit_ui_render_quality.py --project .

# 2. Verify Contrast Ratios
python3 tools/shadcn/theme_contrast_audit.py

# 3. Verify Visual Mismatch Delta
python3 tools/shadcn/ui_visual_diff.py --ref <ref.png> --actual <act.png>
```
