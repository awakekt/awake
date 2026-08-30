---
name: awake-ui-layout-guidance
description: >
  Design and review retained Compose layouts in Awake when deciding between fixed and adaptive
  sizing, parent spacing, Spacer, arrangement, and alignment. Use for showcase pages, catalog
  shells, and component composition. It does not override `ui-designsystem` recipe internals or
  their pinned shadcn source metrics.
license: Apache-2.0
metadata:
  author: awake
  last-updated: '2026-08-26'
  keywords: Awake Compose, layout, fixed size, adaptive size, Spacer, alignment, showcase
---

# Awake UI Layout Guidance

Use this skill before changing a catalog page, preview shell, or composed component layout. The
goal is predictable bounds under both the headless preview viewport and the live application host.

## Size ownership

- Use fixed size only for a real design constraint: a control's standard height, a sidebar width,
  an icon/tap target, or a deliberately fixed preview viewport.
- Use adaptive sizing for page content and reusable fields: `fillMaxWidth()`, `fillMaxHeight()`,
  `weight()`, and the parent's constraints. A recipe should not guess its caller's width.
- `heightIn(min = ...)` is a minimum content contract; `height(...)` is a hard control or viewport
  contract. Do not use a hard height to hide overflow.
- In a `Row`, use `weight()` for a pane that consumes remaining width. `fillMaxWidth()` is not a
  substitute for a weighted sibling and can leave retained descendants intrinsically narrow.

## Spacing and alignment

- Use `Arrangement.spacedBy(...)` for equal gaps between all adjacent siblings.
- Use `Spacer(...)` only for an exceptional, conditional, or weighted relationship.
- Keep component padding inside the component when it defines the visual or tap boundary; keep
  section/page gaps in the parent.
- In showcase input and textarea specimens, authored **page-level** card-content padding is capped
  at `8.dp` unless the pinned reference explicitly requires a larger inset. This does not change
  `ui-designsystem` recipe padding; recipes keep their own source-derived metrics. Do not use a
  large page inset to compensate for a missing parent width or spacing contract.
- Alignment positions a child in space already allocated; it does not make a narrow child fill.
  Fix the parent width contract before changing alignment or typography.

## Catalog contract

- Give preview and code panels an explicit specimen viewport and make their surfaces
  `fillMaxWidth()`.
- Notes, code panels, and preview panels should share the content width unless intentionally
  content-hugging.
- The code sample must declare and render the same states, labels, order, and multiline controls
  as the hero.
- Verify the live catalog route separately from an isolated recipe fixture.

## Verification

Render the actual catalog route at a known viewport and inspect the shell surface, preview frame,
card, input, and textarea bounds in that order. Only after parent bounds match should typography
or recipe tokens be investigated. Read `awake-ui-verification` before treating screenshots as
fidelity evidence.
