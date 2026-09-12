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
  last-updated: '2026-09-13'
  keywords: Awake Compose, layout, Row, Column, FlowRow, FlowColumn, FlexBox, fixed size, adaptive size, Spacer, alignment, showcase
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

## Choosing the layout primitive

Choose the least powerful primitive that expresses the layout. Extra flexibility is not free: it
increases the number of sizing, wrapping, and alignment states that must be reasoned about and
verified.

| Need | Use | Do not use |
|---|---|---|
| One non-wrapping horizontal sequence | `Row` | `FlexBox` configured as a row, unless flex growth/shrink/order is required |
| One non-wrapping vertical sequence | `Column` | `FlexBox` configured as a column |
| Horizontal children that wrap into additional rows | `FlowRow` | A `Row` with manual line breaks, or a `FlexBox` used only for ordinary wrapping |
| Vertical children that wrap into additional columns | `FlowColumn` | A `Column` with manual column bookkeeping |
| CSS-like flex behavior: grow/shrink, reverse direction, per-item order, line distribution, or cross-line alignment | `FlexBox` | Nested `Row`/`Column` trees that simulate flex behavior with magic widths or spacers |

### Row and Column

- Use `Row` and `Column` for the normal one-dimensional case when all children belong on one line
  or one column.
- Put equal sibling gaps on `horizontalArrangement` or `verticalArrangement` with
  `Arrangement.spacedBy(...)`.
- Use scoped `weight()` only when a child consumes the remaining main-axis space. A weighted child
  is not a substitute for a fixed gap, and `fillMaxWidth()`/`fillMaxHeight()` does not distribute
  remaining space among siblings.
- Use the cross-axis alignment parameter for the common alignment and the child-scoped
  `align(...)` modifier only for an intentional exception.

### FlowRow and FlowColumn

- Use `FlowRow` for repeated content whose number or width can exceed the available width:
  chips, tags, compact buttons, filters, and responsive card specimens. Use `FlowColumn` for the
  corresponding height-to-columns case.
- Give the flow container a bounded axis when wrapping is expected. Under an unbounded main axis,
  children stay on one line/column unless `maxItemsInEachRow` or `maxItemsInEachColumn` supplies an
  explicit cap.
- Use `maxItemsInEachRow`/`maxItemsInEachColumn` for a deliberate item-count contract and
  `maxLines` when later lines must be clipped. Do not hide overflow with a hard parent height.
- Use the flow arrangement for equal gaps within each line and between lines. Use child alignment
  only when one item differs from the line’s common cross-axis alignment.

### FlexBox

- Use `FlexBox` only when the layout contract needs flex-specific behavior: `grow`, `shrink`, a
  non-auto `basis`, `order`, reverse direction, `Wrap`/`WrapReverse`, `justifyContent`,
  `alignItems`, or `alignContent`.
- Configure container behavior through `FlexBoxConfig`; configure direct-child behavior through
  `Modifier.flex { ... }`. Do not put `Modifier.flex` on a grandchild and expect the FlexBox to
  consume it.
- `rowGap` is the cross-axis gap and `columnGap` is the main-axis gap for the configured flex
  direction. Prefer `gap(value)` when both should be equal.
- Flex growth and shrink are line-local. If the UI only needs one remaining-width pane, a `Row`
  with `weight()` is clearer and easier to verify.
- Do not use `FlexBox` to paper over an unknown parent width. Fix the parent constraints first,
  then choose the primitive that matches the actual relationship.

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
