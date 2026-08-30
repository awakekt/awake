# Shadcn parity plan v2

Status: active

This is the execution plan for visual and behavioral parity with the pinned official shadcn/ui
reference. It supersedes the execution portions of
`docs/audits/2026-08-20-ui-shadcn-parity-report-and-plan.md`, which remains historical evidence.

## Contract

Parity is not one percentage. Each required case reports independently on layout, style, paint,
behavior, and motion. A passing Awake golden proves regression stability only; it does not prove
fidelity. Geometry comes from the browser reference's captured DOM bounds. Paint remains a
review signal until matching content and a human-reviewed residual explanation exist.

The pinned reference is `shadcn-6261bd8-new-york-v4-radix-1.4.2` at
`6261bd89f72d794aea491482cc2acfd8dc3d63e2`. Do not substitute a third-party Compose port,
remembered shadcn conventions, or an Awake baseline.

## Live baseline — 2026-08-25 (full preview matrix)

Running the full `ShadcnComposeParityPreviewTest` and then `scripts/awake ui report` produced 29
manifest cases. The repaired pilot rows (`button-group.*`, `card.login`, `popover.states`, and
`alert.variants`) now have passing structural geometry; the remaining deterministic geometry
drifts are documented in [the core-issue tracker](../reference/ui-parity-core-issues.md).

| Dimension | Current result |
|---|---|
| Geometry | 18 pass, 11 drift |
| Spacing | passing where required; no remaining measured spacing drift |
| Relationships | passing where required; no remaining measured relationship drift |
| Padding | passing or explicitly unmeasured where no semantic slot exists |
| Style | passing for declared style fields; partial where token export is not normalized |
| Paint | review-only diagnostic; no normalized cross-renderer raster oracle yet |
| Behavior / motion | unmeasured; normalized traces and multi-frame reference capture remain future milestones |

The immediate pilot blockers are:

| Case | Measured geometry drift | Required outcome |
|---|---:|---|
| `button.variants.{light,dark}.rest` | 3.641px | Blocked on `UI-CORE-003`; weighted `font-medium` faces now resolve correctly, but integer intrinsic placement accumulates fractional reference widths. |
| `tabs.states.{light,dark}.rest` | 1.953px | Blocked on `UI-CORE-003`; weighted faces now resolve correctly, but the track and trigger bounds are quantized to integer pixels. |

## Non-negotiable rules

1. Do not re-record an Awake snapshot or raise a geometry allowance to make a drift pass.
2. Every visual change starts by reading the pinned reference JSON and source fixture, then ends
   with the component crop, heatmap, and report slice.
3. `unmeasured`, `partial`, `review`, and `missing-artifact` are evidence gaps, never passes.
4. Correct layout before investigating paint. A pixel mismatch cannot identify a root cause while
   semantic geometry is wrong.
5. Keep unsupported surface explicit in the manifest with a reason; never omit it silently.

## Milestone 1 — close the pilot geometry drifts

Fix the four listed cases in order of measured impact. Put the primary assertion in the existing
Compose preview/parity test using `onNodeWithTag(...)` and exact bounds/relationship assertions;
use the semantic JSON and debug overlay only to diagnose the failed assertion.

For each case:

1. Capture/inspect the official reference JSON and JSX fixture.
2. Add or tighten the exact semantic geometry assertion.
3. Fix the lowest owning Compose recipe/layout primitive.
4. Run `awake ui preview`, `awake ui validate`, and `awake ui report` for that component.
5. Inspect the Awake crop and heatmap. Record the residual paint pattern and its cause.

Exit: the four geometry rows are `pass`, no new measured row regresses, and every residual paint
result has a written explanation. The team explicitly decides which residual patterns are
renderer noise before adding a paint threshold.

## Milestone 2 — make static evidence complete for the pilot

Extend the preview semantics/export only where it enables a direct comparison of captured facts:
four-sided padding, gaps, radius, border width/color, foreground/background color, opacity, and
shadow/focus-ring data. Do not infer missing values from a screenshot.

Exit: every pilot row has pass/fail evidence for required geometry, padding, spacing,
relationships, and declared style fields in light and dark. Paint remains secondary evidence.

## Milestone 3 — expand static coverage by category

Only after Milestones 1–2, add cases in small category batches: controls, field/input controls,
overlay/navigation, layout/surface, then status/typography. Each case needs a pinned reference
fixture, Awake preview, stable semantic IDs, manifest row, both themes when applicable, and a
report slice.

Exit: each supported component/state pair is executable or explicitly unsupported. No category
is reported as parity-complete merely because a recipe exists.

## Milestone 4 — behavior and overlays

Add normalized interaction traces and assertions for click/outside-dismiss, Escape, Tab and
shift-Tab, Enter/Space, arrows, focus trap/restore, anchoring, collision, layer order, and nested
overlays. Use `composeTestSession(...)` for normal interactions; use explicit frame input only
where the trace requires it.

Exit: every interactive manifest row names its supported browser-contract traces and each trace
has an executable Awake assertion.

## Milestone 5 — motion and backend confidence

Capture rest, in-flight, settled, and interrupted states from the pinned reference. Compare
declared duration/easing where the source exposes them. Use real offscreen Vulkan capture for
backend-paint questions; the CPU rasterizer is not a GPU-fidelity oracle.

Exit: animated/overlay components have declared motion evidence and backend-specific gaps are
explicit rather than implied by CPU snapshots.

## Operating commands

```bash
scripts/awake ui reference --component <component> --state <state> --theme both
scripts/awake ui preview --component <component> --state <state> --theme both
scripts/awake ui validate --component <component> --theme both
scripts/awake ui report
scripts/awake verify --only shadcn-reference
```

Run the focused desktop parity test after each recipe change. Run the wider showcase suite before
claiming a milestone. The report and inspected artifacts—not a cached percentage in this document—
are the current status.
