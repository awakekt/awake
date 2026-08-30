# UI parity core-issue tracker

This tracker records only a proven parity limitation that cannot be corrected in the component,
recipe, fixture, token mapping, or comparison tooling today. It is the backlog for closing the
remaining quality scopes after structural parity reaches 100%.

Do **not** add a normal mismatch here. Incorrect padding, colors, size, icon choice, interaction,
fixture mapping, or a missing exported token is a fixable parity bug and must be fixed in the
current component pass. If the cause is obvious and a recipe-level correction exists, fix it
immediately; do not create a tracker entry first.

## Admission rule

An entry needs all of the following:

1. Reference, Awake, and diff captures with the exact pinned case and theme.
2. Structural/layout evidence showing whether the issue is independent of component geometry.
3. The attempted recipe-level correction and why it cannot express the source result.
4. The missing engine/core capability and its likely owning module.
5. A deterministic acceptance measurement for the future fix.

## Open issues

### UI-CORE-002 — weighted text faces for intrinsic layout

- Scope: typography
- Affected cases: `button.variants.light.rest`, `button.variants.dark.rest`, `tabs.states.light.rest`, `tabs.states.dark.rest`
- Evidence: the pinned reference source applies `font-medium` to Button and Tabs labels. Awake now
  ships generated Roboto Thin/Light/Regular/Medium/SemiBold/Bold/Black faces, and the full preview
  matrix confirms weighted glyphs select distinct atlas slices. Remaining Button/Tabs geometry
  deltas are sub-pixel layout rounding, tracked separately below.
- Recipe-level investigation: verified the source fixtures, Tailwind spacing, button variants,
  tab padding, and intrinsic row geometry. Changing component padding or report tolerances would
  falsify the source contract and does not explain the shared text-width drift.
- Missing capability: **resolved** in `:awake:core:text` by `WeightedUiFont`, which combines the
  generated face atlases while retaining one renderer texture, and by passing `TextStyle.weight`
  through measurement, caret positioning, wrapping, and glyph painting.
- Acceptance: `WeightedUiFontTest` proves exact and intermediate-weight resolution; the Compose
  `TextPaintingTest` proves Medium samples a different atlas slice. Remaining geometry drift is
  not evidence that weight selection is missing.
- Status: implemented and verified 2026-08-25

### UI-CORE-003 — fractional intrinsic layout coordinates

- Scope: typography / geometry
- Affected cases: `button.variants.{light,dark}.rest`, `button-group.basic.light.rest`,
  `button.sizes.{light,dark}.rest`, `button.disabled.{light,dark}.rest`,
  `badge.variants.{light,dark}.rest`, `tabs.states.{light,dark}.rest`
- Evidence: `build/reports/ui-parity/report.json`; weighted faces are selected, but layout bounds
  are integer pixels while the browser reference reports fractional widths (for example Button
  default 77.156px vs Awake 78px and Tabs track 155.953px vs Awake 154px).
- Recipe-level investigation: source padding, face selection, and glyph UV selection are correct;
  changing component padding would make the individual width less faithful and would not remove
  cumulative sibling-position drift.
- Missing capability: a Compose-faithful sub-pixel intrinsic measurement/placement path, or an
  explicit deterministic quantization policy that preserves fractional totals across siblings.
  The owning module is the retained `:awake:compose:ui` layout engine.
- Acceptance: retain fractional measured bounds through the row/box placement path and bring the
  listed cases within the existing geometry tolerance without per-component allowances.
- Status: open

`UI-CORE-001` (node-local shadow drawing) was resolved on 2026-08-25 by
`DrawScope.drawShadow(...)` and Compose-shaped `Modifier.dropShadow(shape, shadow)`. The focused
frame tests cover node placement, scale, alpha, chain order, rounded shapes, and the linear-gradient
brush subset; Tabs now uses the pinned Tailwind `shadow-sm` values. The implementation plan retains
the acceptance evidence in [the shadow plan](../tasks/2026-08-25-compose-node-local-shadow-plan.md).

## Entry template

```md
### UI-CORE-### — concise capability name

- Scope: anti-aliasing | color | typography | shadow | interaction | motion
- Affected cases: `component.state.theme`
- Evidence: reference / Awake / diff paths and report measurement
- Recipe-level investigation: what was tried; why a component cannot fix it
- Missing capability: concrete engine feature and owner module
- Acceptance: exact capture/oracle that closes the issue
- Status: open | implemented | verified
```
