---
name: verify-ui-parity
description: Register and verify an Awake component against the pinned shadcn source fixture.
---

# Verify source-to-Awake UI parity

Use this command for an existing or new `shadcn*` component when the question is whether the
implementation matches official source layout, not whether an old Awake snapshot stayed stable.

## Required workflow

1. Add stable `data-parity-id` attributes to every source container, interactive child, and
   overlay surface that needs a measurement. A portal fixture MUST render a real trigger/anchor
   and give it an ID too. Do not map nodes by text or DOM order.
2. Give the matching Awake preview the same semantic IDs. Declare trigger-to-surface offsets as
   `vertical-offset` or `horizontal-offset` relationships in the manifest. Surfaces must export resolved padding,
   border width, and radius; add semantic capture rather than estimating from pixels.
3. Register paths, node IDs, and parent/child or sibling relationships in
   `tools/shadcn_parity_manifest.json`. A relationship names either a horizontal or vertical gap.
4. Run `awake ui reference`, `preview`, `validate`, then `report`. Read the JSON report before
   changing a style.
5. For a sizing claim (`intrinsic`, fill-parent, bounded, responsive, or weighted), add source
   and Awake probes that vary content, parent bounds, and viewport. One static capture cannot
   prove a dynamic sizing rule.

## Evidence meanings

- `pass` means the named fact is within the declared tolerance.
- `drift` is an actionable expected/actual/delta mismatch; report the property, never only a
  screenshot percentage.
- `unmeasured` means the fixture or semantic capture is insufficient and is incomplete coverage. Add the missing capture;
  do not infer padding, border, alignment, or sizing behavior from a heatmap.
- Pixel mismatch is paint evidence only. Geometry, spacing, and constraints come from DOM and
  Awake semantic facts.

## Diagnose every component the same way

This is a component-agnostic triage loop. Do not invent a special interpretation for buttons,
cards, menus, or any other recipe.

1. Open the source crop and the Awake crop at the same scale. Confirm they contain the same
   state, content, and component boundary. Then open the heatmap. The heatmap localizes paint
   differences; it does not measure layout.
2. Read the manifest report in this order: `artifacts`, `geometry`, `padding`, `spacing` and
   `relationships`, `layoutIntent`, `style`, then `paint`. Do not start from `mismatchPct`.
3. Locate the first drifted node or relationship, compare its `expected`, `actual`, and `delta`,
   and fix the *lowest layer that owns that contract*. Re-run the complete chain and explain the
   changed delta in the handoff.

| Evidence | What it proves | Typical owner / next check |
|---|---|---|
| parent geometry drift | the component's outer size or position differs | recipe modifier/constraint, container measurement, or overlay placement |
| child geometry drift while parent passes | the child did not receive or honor its allocated bounds | parent layout propagation, `fillMax*`, intrinsic sizing, weight, or child modifier |
| padding drift | resolved content insets differ | recipe style/token; never compensate by changing crop padding |
| spacing or declared relationship drift | sibling gap, separator thickness, alignment offset, or portal placement differs | arrangement, spacer/separator, border-collapse rule, or popup anchor/offset |
| `layoutIntent` mismatch | source and Awake have different sizing/alignment strategies | translate the source flex/grid/min/max rule; add multi-probe fixtures before claiming responsive behavior |
| style drift with geometry pass | radius, border, color, shadow, or token differs | design-system recipe/token; inspect the crops and heatmap after layout is correct |
| paint `REVIEW` / mismatch with geometry drift | image difference may be caused by layout | fix the semantic layout drift first; do not tune fonts, colors, or pixels yet |
| paint mismatch with geometry pass | a visual/rasterization difference remains | inspect heatmap pattern: edges/corners → radius or border; uniform fill → color; doubled glyphs → font metrics/position; then verify through the owning renderer path |
| `unmeasured`, missing artifact, or `partial` | no conclusion is justified | add the semantic field, source ID, relationship, or fixture needed for that fact |
| behavior or motion `unmeasured` | still images did not test it | add interaction traces or multi-frame reference capture; never call visual parity behavioral parity |

When parent geometry passes while a child bound, fill, or cross-axis allocation drifts, escalate
to `ui-core` `Modifier`/layout. Do not conceal a generic measurement defect with copied dimensions,
trial-pass branching, negative offsets, or separator hacks in a recipe. Preserve the report delta,
add a density-1-and-2 layout test, and rerun the full parity chain after the core fix.

**Fix direction is evidence-driven:** a static screenshot may reveal *where* a defect is, but
the report and source-computed facts decide *what* is wrong. Never re-record a golden or approve
a pixel threshold to silence a semantic drift.

## Observed-bug report (required before and after a fix)

When validation exposes a `drift`, `REVIEW`, behavioral failure, or incomplete evidence that
blocks a parity claim, produce a report immediately; do not wait until the component is fixed.
The handoff must show the source crop, Awake crop, and heatmap (embedded with absolute artifact
paths when the client supports images) and include:

- component, state, theme, viewport, and source/Awake semantic IDs;
- the decisive report fact(s), written as expected → actual (`delta`), rather than only a pixel
  mismatch percentage;
- a one-paragraph diagnosis naming the likely lowest Awake owner (`ui-core`, `ui-headless`,
  `ui-designsystem`, overlay host, or renderer) and why;
- remaining `drift`, `review`, and `unmeasured` facts; and
- the next focused test and parity command.

For a fixed bug, show the same three images and a compact before/after summary. If an artifact
cannot be generated, say so explicitly, include its expected absolute path, and mark the visual
evidence incomplete; never substitute prose for the captures.

## Completion evidence

Before declaring a component corrected, provide all of the following:

- the reference crop, Awake crop, and heatmap you inspected;
- the report field(s) that changed, with before/after expected–actual–delta values;
- the source rule and Awake ownership layer that explain the fix;
- explicit remaining `drift`, `review`, and `unmeasured` fields; and
- focused tests plus the report command used.

See `docs/reference/ui-parity-tool.md` for commands and manifest fields, and
`skills/awake-ui-verification/SKILL.md` for the verification policy.
