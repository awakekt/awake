# UI Ownership

This document is the canonical source for Awake's current reusable UI boundaries.

## Current Module Model

| Module | Owns | Does not own |
|---|---|---|
| `:awake:compose:runtime` | Retained composition, invalidation, and composition lifecycle | Widgets, branded recipes, or sample policy |
| `:awake:compose:ui` | Compose-shaped layout, drawing, modifiers, text, and UI runtime contracts | Shadcn variants or sample-specific compositions |
| `:awake:compose:foundation` | Foundation-shaped layout and neutral controls such as text fields, sliders, selection, and scrolling | Branded colors, variants, or upstream-specific recipes |
| `:awake:ui:material3` | Material 3 color schemes, components, and screen recipes such as `Scaffold` | Shadcn tokens, recipes, or product branding |
| `:awake:ui:shadcn` | Shadcn themes, tokens, variants, and `shadcn*` recipes | Core layout/runtime mechanics |
| `:awake:compose:ui-testing` | Frame composition, semantics, rasterization, and UI verification helpers | Production UI behavior |
| `:samples:*` | Showcase pages, debug shells, and sample-specific adapters | Reusable widgets and design-system policy |

The former immediate-mode UI modules were retired. Their history is preserved in
[`2026-08-28-immediate-mode-ui-ownership.md`](../archive/2026-08-28-immediate-mode-ui-ownership.md),
but new code must not use those names or recreate that layering by default.

## Placement Rules

1. Put reusable layout, drawing, modifier, text, and neutral interaction behavior in the
   appropriate `:awake:compose:*` module.
2. Put APIs that AndroidX Compose exposes from `material3` in `:awake:ui:material3`; keep their
   Material color and layout policy there.
3. Put shadcn visual recipes in `:awake:ui:shadcn`, with a `shadcn*` public name when they
   represent the shadcn surface.
4. Keep sample/game overlays, inspector composition, and debug wiring in the owning sample or
   game module.
5. Prefer modifiers and styles for size, spacing, border, shape, and focus appearance. Do not
   add widget parameters that duplicate those capabilities.
6. Keep `Dp` and `Sp` in public UI APIs. Convert to pixels only at the rendering boundary.
7. Use `composeFrame` for a single static proof and `composeTestSession` for interaction or
   animation across frames. Use raw runtime objects only when the runtime mechanism itself is
   under test.

## Verification

UI fidelity claims require both semantic/layout evidence and raster evidence where appearance
matters. The active workflow and known limitations are maintained in
[`ui-validation.md`](ui-validation.md); the archived coverage matrix is historical and is not
the current component inventory.
