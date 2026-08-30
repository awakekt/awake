---
name: awake-tailwind-to-compose
description: Translate Tailwind utility classes into typed Awake Compose tokens, layouts, styles, and states. Use for Tailwind source in a web-to-Compose port; do not retain class strings or introduce a utility runtime.
---

# Tailwind → Awake Compose

Tailwind is a compact source notation for design values and layout intent. Read its utility
classes as evidence, then emit typed Kotlin tokens, Compose layout, `Modifier`, and reusable
`Style`/recipe policy. Never store class strings, parse them at runtime, or reproduce Tailwind's
cascade/variant engine.

## Translate by concern

| Tailwind source | Awake Compose output |
|---|---|
| spacing, sizing, colours, radii, type | named product/design-system tokens |
| `flex`, `items-*`, `justify-*`, `gap-*` | `Row`/`Column`, alignment, parent arrangement |
| `grid-cols-*` | explicit known row/column structure; Foundation gap for arbitrary grids |
| `sm:` / `md:` / other responsive utilities | product layout mode and alternate structure |
| `hover:` / `focus:` / `disabled:` / `data-*` | `StyleState`/interaction state and a recipe state rule |
| `dark:` | complete theme selection, not duplicated per-property conditions |

Read [`awake-ui-css-modifier`](../awake-ui-css-modifier/SKILL.md) for source semantics that are
easy to mistranslate: parent-owned margins/gaps, CSS border-box insets, fixed height plus vertical
padding, and paired text size/line-height. Its legacy API calls are historical examples only;
the target is the Compose-native surface described by `awake-web-to-compose`.

## Preserve the facts the class string carries

- Keep all declared axes: `h-9 px-4 py-2` has an outer height and two internal padding axes.
- Treat `text-*` as size plus its source line-height, unless the component explicitly overrides it.
- Turn repeated values into named tokens; do not scatter matching `Dp` literals through a page.
- Translate sibling `m-*` spacing to its parent arrangement or padding.
- Keep arbitrary values only when the reference proves they are intentional rather than a missing
  scale token.

Do not let Tailwind's source notation decide ownership. A product's brand values stay in that
product's theme/recipes. A reusable generic behaviour belongs below the design-system layer.

## Variants and state

Tailwind variants encode state selectors, not merely paint values. Identify the state owner first:
interaction mechanics belong in the Compose/Foundation primitive; the visual response belongs in
a typed reusable style/recipe. A responsive variant normally changes a product layout mode rather
than adding an independent breakpoint condition to one component.

## Completion check

- No Tailwind class strings, class merger, CSS file, or Tailwind runtime remains in the result.
- Each repeated source value has one appropriate typed token owner.
- The result preserves source box-model and text-line-height facts at the selected fidelity level.
- Responsive and state utilities are represented by explicit Compose state/layout decisions.
