# Visual Composition

Read this only when the brief requests design adaptation, the reference is incomplete, or the
port needs a new composition around an established product style. It is not a pixel-parity rule.

## Golden ratio: optional proportion, not a layout system

The golden ratio is approximately `1.618`; its useful split is about 62% / 38%. It can offer a
starting proportion for one dominant relationship: a hero's copy and artwork, a detail pane and
supporting rail, or a primary card and secondary information.

Use it only after content, minimum readable widths, touch targets, and the supplied reference are
satisfied. It is a visual hypothesis to review, not a number to force into `weight(1.618f)`.
Avoid applying it to repeated cards, tables, navigation, forms, dense dashboards, or compact
layouts where content and task flow determine the geometry.

For example, when a design-adaptation brief has no specified hero split, begin with a roughly
62/38 bounded `Row`, then test its compact branch as a `Column`. Keep or discard the proportion
based on readability, focal hierarchy, and the product's actual assets.

## Practical hierarchy checks

- Give each screen one primary task or focal region before adding decorative balance.
- Use whitespace and type scale to separate sections before adding borders or shadows.
- Align repeated edges and baselines; intentional asymmetry needs a clear focal reason.
- Preserve scan order in compact layouts, usually primary action before secondary content.
- Let copy length and image aspect ratio change the composition; no ratio is more important than
  avoiding overflow, clipped content, or an unreadable action.

When a screenshot exists, match its observed hierarchy. This guide is for choices the screenshot
does not settle.
