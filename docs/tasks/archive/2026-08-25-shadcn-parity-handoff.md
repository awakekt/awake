# Shadcn parity handoff — 2026-08-25

Status: active handoff

This is the starting document for the next owner of Awake's shadcn parity work. The execution
plan remains [`2026-08-25-shadcn-parity-plan-v2.md`](2026-08-25-shadcn-parity-plan-v2.md); this
document records the delivered capabilities, the evidence that exists, and the shortest safe path
back into the parity loop.

## Goal and authority

Match the pinned official shadcn/ui reference, not a third-party Compose port and not an Awake
golden. The reference pin is `shadcn-6261bd8-new-york-v4-radix-1.4.2` at
`6261bd89f72d794aea491482cc2acfd8dc3d63e2`.

Parity has separate geometry, spacing, relationship, declared-style, paint, behavior, and motion
dimensions. Do not summarize them as one percentage. The policy and procedure are:

- [`docs/reference/ui-validation.md`](../reference/ui-validation.md) — evidence policy.
- [`docs/reference/ui-parity-tool.md`](../reference/ui-parity-tool.md) — commands and artifacts.
- [`skills/awake-ui-verification/SKILL.md`](../../skills/awake-ui-verification/SKILL.md) — tool
  selection and baseline judgment.

## Commits delivered in this handoff

| Commit | Delivered work |
|---|---|
| `a0d864aa1` | Compose node-local `drawShadow`, `dropShadow`, solid/four-corner linear-gradient brushes, and Tabs shadow use. This is intentionally not `graphicsLayer`. |
| `86ee7cefe` | Explicit retained `recomposeScope`, `mutableStateOf` reader invalidation, retained `ComposeHost` composition, scope counters, and Stage 2 documentation. |
| `0efaa0429` | Textured primary pipeline uniform selection fix. |
| `ffa11f58b` | Button, input, checkbox, radio, progress, select, icon, tab, parity-preview, rasterizer, reference-artifact, shader-asset, and skill/documentation updates. Its commit body describes each area. |

This handoff adds the current parity-plan, core-issue, skill, recipe, and preview-fixture updates
on top of `ffa11f58b`; inspect `git status` before committing or continuing.

## What is now available

### Evidence and debugging tools

- `:awake:compose:ui-testing` provides semantic queries, deterministic semantic JSON, normal
  multi-frame input sessions, and `rasterizeDebugOverlay(...)` for component bounds. The overlay
  diagnoses; semantic geometry assertions are the oracle.
- The headless CPU rasterizer gives deterministic preview/debug images. It is not proof of GPU
  paint fidelity; use real offscreen Vulkan texture capture for backend rendering questions.
- Local official-reference captures now include Button icon/disabled/size states and Select-open
  states in `docs/reference/shadcn-previews-local/`.
- `scripts/awake ui reference`, `preview`, `validate`, and `report` run the registered parity
  loop. Generated artifacts must correspond to the same component, state, content, and theme.

### Component and renderer work

- Shadcn recipes now have extracted style helpers for Button, Checkbox, Input, Progress, Radio
  Group, and Select; use them as the recipe's source of truth instead of reintroducing local
  style forks.
- Button icon cases, Select open-menu cases, and layout Tabs have updated reference cases and
  preview coverage.
- Studio's checked-in Vulkan SPIR-V and WebGPU shader assets were regenerated with the shader-pack
  changes. Keep generated assets in sync via the shader-pack drift test; do not hand-edit them.
- Text-field and draw-path regression coverage was added with the related fixes.

### Compose runtime boundary

`recomposeScope` is explicit opt-in, not compiler-plugin Compose skipping. A scoped subtree must
receive every changing external dependency as an input; state reads only invalidate explicit
scopes. Do not wrap an arbitrary Studio region merely to make counters move. See
[`2026-08-25-compose-stage-2-invalidation-plan.md`](2026-08-25-compose-stage-2-invalidation-plan.md).

## Last verified evidence

The complete `ShadcnComposeParityPreviewTest` matrix was regenerated on 2026-08-25. The report
contains 29 registered cases: 25 pass structural geometry and four remain deterministic drifts.
Card, Popover, Alert, and both ButtonGroup orientations are no longer pilot blockers. Button
variants and Tabs now use real weighted faces; their remaining drift is tracked as `UI-CORE-003`
(fractional intrinsic layout), not by component padding.
The latest report is `build/reports/ui-parity/report.md` and the machine-readable evidence is
`build/reports/ui-parity/report.json`.

The showcase input regression is also covered: `TextFieldPage` and `TextareaPage` now make the
card and its content column fill the available width, preventing an empty textarea from collapsing
to its intrinsic one-glyph width. `ShadcnFieldsTest` and the rendered
`fields-full-width-card.png` preview verify the fix.

These focused suites passed before the handoff commits:

```bash
./gradlew :awake:compose:runtime:desktopTest \
  --tests io.github.awakelab.awake.compose.runtime.RecomposeScopeTest --no-daemon
./gradlew :awake:compose:ui:desktopTest \
  --tests io.github.awakelab.awake.compose.ui.ComposeFrameStatsTest --no-daemon
```

An earlier full Studio probe attempt compiled the Compose/UI path but was blocked by compilation
errors in `:awake:tailwind` and `:awake:scene:authoring`. That result predates the final parity
and shader commits; re-run it before treating it as a current blocker:

```bash
./gradlew :samples:studio:desktopTest \
  --tests io.github.awakelab.awake.studio.ui.StudioFramePerfProbeTest --rerun-tasks --no-daemon
```

The probe prints frame time, composition time, composition passes, explicit scope executions, and
scope skips. It is investigation evidence, not a performance gate.

## Resume procedure

1. Start with the live report, not an old screenshot:

   ```bash
   scripts/awake ui report
   ```

2. Take the first unresolved registered component/state from the report or the pilot rows in the
   v2 plan. Capture both themes before editing:

   ```bash
   scripts/awake ui reference --component <component> --state <state> --theme both
   scripts/awake ui preview --component <component> --state <state> --theme both
   scripts/awake ui validate --component <component> --theme both
   ```

3. Inspect the reference JSON and matching vendored source. Classify the first failing fact in
   this order: correspondence, geometry, padding, spacing/relationships, layout intent, declared
   style, paint, then behavior/motion.
4. Fix the lowest reusable owner. Engine/layout/input defects belong in Compose or Foundation;
   token and recipe rules belong in `ui-designsystem`. Never compensate with a component-local
   offset or an altered crop threshold.
5. Add a focused regression at that owner. For interactions use `composeTestSession(...)` and
   exercise the whole semantic target over separate frames.
6. Recapture, inspect the source/Awake/diff images, then run `scripts/awake ui report`. Do not
   re-record a golden until an explained predictive drift rule accounts for every changed case.

## Immediate queue

1. Resolve `UI-CORE-003` in the retained Compose layout path: preserve fractional intrinsic bounds
   through sibling placement and rerun the registered parity matrix without component allowances.
2. Re-run the Studio probe above. If it builds, identify a profile-proven subtree with explicit
   external inputs before adopting `recomposeScope`; otherwise record the current compiler owner
   and error in the Stage 2 plan.
3. Expand static evidence only for the pilot cases after their geometry is correct. Padding,
   color, radius, border, and shadow facts must come from the browser capture, not a raster guess.
4. Add behavior traces for Select and Dropdown Menu after their static open states are paired and
   reviewed: full trigger hit target, item click, outside dismissal, Escape, keyboard focus, and
   overlay placement.

## Do not regress these rules

- A passing Awake snapshot proves stability, never shadcn fidelity.
- The source capture and browser-computed JSON are the authority; upstream source explains them.
- Whole-row and whole-trigger hit regions need multi-frame behavior tests, not a text-only click.
- Modifier order is semantic; do not hand-reorder chains inside individual recipes to repair a
  framework ordering bug.
- Icon vectors are generated from SVG source through the icon skill's script, never transcribed or
  derived by rotating a different glyph.
- A missing Compose-core feature gets a narrowly scoped plan and acceptance evidence before a
  component-local workaround is introduced.
