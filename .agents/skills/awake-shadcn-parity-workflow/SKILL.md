---
name: awake-shadcn-parity-workflow
description: >
  Verify Awake shadcn components end to end, from the pinned official reference through the
  standalone recipe and the real showcase/catalog shell. Use before claiming a component is done.
license: Apache-2.0
metadata:
  author: awake
  last-updated: '2026-08-28'
  keywords: shadcn parity, catalog, showcase, geometry, behavior, proof image, tools
---

# Awake Shadcn Parity Workflow

Use this workflow for focused component work and for the complete registered matrix. It prevents
an isolated recipe pass from being mistaken for parity in the real catalog host.

## Automated Tooling Quick Reference

Use the verification tier that matches the stage of work. Do not run every expensive audit after
each edit; the final tier still requires the complete evidence chain.

### Iteration tier

Use this after each implementation change:

```bash
./gradlew :awake:ui:shadcn:compileKotlinDesktop
./gradlew :awake:ui:shadcn:desktopTest --tests '*<Component>*'
```

For visual changes, add the focused Compose preview test. CPU raster previews are diagnostic and
fast; they do not replace GPU verification for renderer, shader, blending, or anti-aliasing work.

### Commit tier

Before committing a component change, run its complete standalone state matrix, generate the
focused preview, inspect the crop and heatmap, and run `git diff --check`. Include all relevant
states: default, hover, focus, pressed, disabled, selected, expanded, keyboard, overflow, and
long text where supported.

### Merge tier

The manifest-driven aggregate audit is mandatory before claiming a component set is complete,
claiming shadcn parity, or merging UI changes. A focused `inspect` run is not a substitute because
it can omit another registered component, state, theme, or shared showcase regression.

```bash
scripts/awake ui audit --theme both
```

The audit must complete its reference capture, Compose preview generation, semantic comparison,
and report generation. Review every generated contact sheet and record all `drift`, `review`,
`unmeasured`, or environment-blocked results in the handoff. If the audit cannot complete, the
parity claim is blocked; do not re-record a baseline or waive the failure silently.

Before claiming parity or merging, run the real showcase route and the complete audits below:

```bash
# 1. Audit Live Component & Showcase Parity (Design System vs Showcase vs Visual Baselines)
python3 tools/shadcn/shadcn_parity.py --project .

# 2. 1-Command Full-Stack Scaffolding (Component + Page + Test + Catalog registration)
python3 tools/shadcn/scaffold_shadcn_component.py --name Sheet --category overlays

# 3. Audit 100% Render Quality & Behavioral Fidelity (SDF AA, Shadows, Dismissal, Delays)
python3 tools/shadcn/audit_ui_render_quality.py --project .

# 4. WCAG 2.1 Contrast Ratio Doctor (AA/AAA mathematical compliance across light & dark modes)
python3 tools/shadcn/theme_contrast_audit.py

# 5. Compose Performance & Stability Linter (Unstable parameters, recomposition leaks)
python3 tools/shadcn/audit_compose_perf.py --project .

# 6. Automated Pixel Diff Engine (Perceptual comparison against upstream reference PNGs)
python3 tools/shadcn/ui_visual_diff.py --ref <ref.png> --actual <act.png>
```

Run GPU/offscreen capture only when the change affects backend paint, shaders, blending, text
sampling, frame pacing, or anti-aliasing. Do not use a GPU capture to validate a pure state,
semantics, padding, or layout change that standalone geometry and behavior tests already prove.

---

The aggregate summary is `build/reports/ui-parity/all-components-inspect.json`. This is an evidence
generator and validator, not permission to accept drift automatically; inspect the generated
contact sheets and the report before claiming parity.

## Ownership boundary

- `ui-shadcn` owns the shadcn recipe: source-derived tokens, component padding, shape, paint,
  and interaction behavior.
- Showcase pages own composition: pane width, preview frame, page spacing, specimen content, and
  the code sample shown beside the preview.
- Never change a recipe to compensate for a catalog width, weight, alignment, or padding mistake.
- Never change a showcase page to hide a recipe mismatch that reproduces in an isolated fixture.

## Required sequence

1. **Pin the reference.** Identify the official shadcn source and capture computed bounds, padding,
   typography, colors, radius, and available behavior. Do not use memory or a third-party port as
   ground truth.
2. **Verify the standalone recipe.** Render the design-system component at a known viewport and
   assert semantic bounds and deterministic behavior. If it fails here, fix `ui-shadcn`.
3. **Verify the real catalog route.** Render the actual showcase shell, including its sidebar or
   responsive branch. Inspect bounds in order: shell pane, preview surface, specimen card, control.
   If standalone passes but this fails, fix the showcase layout only.
   For sidebar, scrolling, clipping, overlap, or z-order work, also generate and open the matching
   layout wireframe with `ComposeComponentFrame.rasterizeLayoutOverlay(...)`; do not infer
   containment from the normal screenshot alone.
4. **Match the specimen contract.** The preview and code tab must use the same states, labels,
   order, card structure, padding, and multiline/slot content. Treat a shorthand snippet as a bug
   when it cannot recreate the visible specimen.
5. **Verify behavior.** Test the component's click, keyboard, focus, disabled, open/close, or
   selection behavior at the recipe boundary. Test catalog navigation separately when relevant.
6. **Show proof.** Run `tools/shadcn/ui_visual_diff.py`, produce the real catalog-shell image, and
   for shell, scrolling, clipping, overlap, or z-order work produce the matching layout-debug
   wireframe. Open both images, report the focused tests and any known unmeasured dimensions. A
   passing snapshot alone is not a fidelity claim.

## Stop conditions

- Do not re-record a baseline before opening and explaining the diff.
- Do not claim “fixed” from an isolated fixture when the catalog route has not been rendered.
- Do not claim “parity” when behavior or motion has no oracle; name the covered dimensions instead.
- If the shell and recipe disagree, stop and classify the failure before editing either layer.

## Minimum handoff

Record the reference source, the standalone test, the catalog-shell test, the proof image path, and
remaining gaps. The next component starts only after these artifacts exist.

Read [`awake-ui-verification`](../awake-ui-verification/SKILL.md) for evidence rules and
[`awake-ui-layout-guidance`](../awake-ui-layout-guidance/SKILL.md) for fixed/adaptive sizing,
weight, spacing, and alignment decisions.
