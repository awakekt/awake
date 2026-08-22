# UI parity tool

`scripts/awake ui` is the single entry point for comparing a registered Awake component with
the pinned shadcn reference. It orchestrates existing capture, preview, semantic-crop, and
report tools; it does not create a second renderer or accept guessed file pairings.

## Button vertical slice

```bash
# 1. Render the pinned shadcn/Radix button fixture in Chromium.
scripts/awake ui reference --component button --state rest --theme both

# 2. Render Awake's matching preview and semantic report.
scripts/awake ui preview --component button --state rest --theme both

# 3. Crop Awake using declared semantic ids and compare it to the source capture.
scripts/awake ui validate --component button --theme both

# 4. Write independent evidence and the comparison-tool performance report.
scripts/awake ui report
scripts/awake ui performance --component button --theme light
```

Outputs:

- `build/reports/ui-component-parity/*_awake.png` — Awake crop;
- `build/reports/ui-component-parity/*_diff.png` — reference-to-Awake heatmap;
- `build/reports/ui-component-parity/*.json` — image comparison metrics;
- `build/reports/ui-parity/report.{json,md}` — manifest-backed parity status; and
- `build/reports/ui-parity/performance.json` — elapsed crop/diff tool time, explicitly not UI
  frame-time performance.

`report` shows geometry, four-sided padding, sibling spacing, border/radius, paint, behavior,
and motion separately. It reports exact expected/actual/delta values for captured facts. An
`unmeasured` field means the preview has insufficient evidence (for example, vertical text
padding cannot be inferred from a line box); it is never a pass. Padding rows identify whether
their values came directly from the resolved style or a bounds-derived fallback. `REVIEW` means the image crop
exists but no human-reviewed threshold has been approved.

For overlays, set `coordinateOrigin` to the shared surface ID: the report compares internal
geometry in that coordinate space and leaves trigger-to-overlay placement as its own declared
relationship. `layoutIntent` reads source display/flex, class, alignment, and min/max facts and
compares captured Awake width/height strategies (`fixed`, `intrinsic`, `fill-parent`). A source
declaration alone is not proof of responsive or dynamic behavior; add multi-probe fixtures for
those claims.

## Add a component safely

1. Add an official reference case and React fixture.
2. Add an Awake preview with stable, unique semantic ids.
3. Add a matching row to `tools/shadcn_parity_manifest.json`: content, state, theme, artifact
   paths, semantic ids, source sizing intent, and only the oracles that exist.
4. Capture, preview, validate, inspect crop/heatmap, then run `report`.
5. Add behavior/semantic tests at the lowest owning module. A pixel threshold never compensates
   for missing focus, keyboard, or layout behavior.

## Read the comparison as evidence, not a score

Use this same sequence for every registered component and state—controls, composite layouts,
and overlays alike:

1. Open the official source crop and Awake crop at the same scale, then the heatmap. Verify the
   crops represent the same content and state before interpreting any difference.
2. Triage report fields in order: artifacts → geometry → padding → spacing/relationships →
   layout intent → style → paint. A later signal must not override earlier layout evidence.
3. Fix the lowest owning layer, run the chain again, and report the exact field and delta that
   changed. Do not use a new golden or a pixel threshold as a substitute for a source rule.

| Report evidence | Meaning | Correct next action |
|---|---|---|
| Parent geometry drifts | The component's outer bounds are wrong. | Inspect recipe constraints, the hosting container, or popup placement. |
| Child geometry drifts but parent passes | The parent allocated bounds the child did not honor. | Inspect `fillMax*`, intrinsic measurement, weights, and child modifiers. |
| Padding drifts | Resolved component insets are wrong. | Change the owning recipe/token; never hide this with crop padding. |
| Spacing / relationship drifts | A gap, separator, alignment offset, or anchor relation is wrong. | Inspect arrangement, spacers, dividers, border-collapse, or overlay anchor/offset. |
| Layout-intent mismatch | Source and Awake sizing/alignment strategies differ. | Translate the source flex/grid/min/max rule; use multi-probe fixtures for dynamic claims. |
| Style drifts after geometry passes | A visual property is wrong. | Inspect radius, border, color, shadow, and owning design-system token. |
| Paint differs while geometry drifts | Pixels cannot identify root cause yet. | Resolve semantic layout first. |
| Paint differs after geometry passes | A raster/visual difference remains. | Use the heatmap: corner/edge regions suggest border/radius; uniform regions color; doubled glyphs typography/position. |
| `unmeasured`, `partial`, or missing artifacts | The tool lacks evidence. | Add semantic fields, source IDs, relationships, or a fixture; do not guess. |
| Behavior/motion unmeasured | A still image did not prove interaction/animation. | Add an interaction trace or multi-frame capture. |

A completion claim must link the inspected crops/heatmap, the changed report deltas, the source
rule, the Awake ownership layer, focused test output, and every remaining `drift`, `review`, or
`unmeasured` result.

## Sizing and pixel-fidelity rules

Translate source classes, not screenshots.

| Source intent | Awake expression | Do not do |
|---|---|---|
| intrinsic/content-hugging control | derive from content and style metrics | hard-code a visual Headless fallback |
| `w-N`, `h-N` | `Modifier.width(N.dp)`, `height(N.dp)` | treat a source scale value as a magic number |
| `w-full`, `h-full` | `fillMaxWidth()`, `fillMaxHeight()` | expose `Dimension.FillMax` in public UI APIs |
| `max-w-*`, `min-w-*` | `widthIn(max = ...)`, `widthIn(min = ...)` | turn a bound into a fixed width |
| `gap-N` | `Arrangement.spacedBy(Tw.Spacing.sN)` | manual spacer arithmetic between children |
| `p-N`, `px-N`, `py-N` | named `UiInsets` / source-scale padding | crop-padding to hide component padding drift |
| measured/slot-derived size | compute in px and return with `.px` | wrap a measured pixel value with `.dp` |

All authored values are `Dp`; convert to pixels only at the layout/render boundary. Crop
`--padding` is comparison framing only—it must never conceal wrong component padding or spacing.
Geometry is the primary dimension oracle; image diffs evaluate paint after geometry and content
are aligned. See `skills/awake-ui-authoring/SKILL.md` for the full unit and ownership rules.
