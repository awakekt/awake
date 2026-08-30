---
name: awake-shadcn-to-compose
description: Translate official shadcn component anatomy, variants, and Tailwind source into Awake Compose recipes. Use for a shadcn/ui reference in a web-to-Compose port; do not use it merely because a site uses Base UI or resembles shadcn.
---

# shadcn → Awake Compose

Use official shadcn source as evidence for component anatomy, state contracts, variant values, and
visual policy. Produce typed Awake Compose recipes built on Foundation; do not import React,
Radix/Base UI, Tailwind, portals, or DOM event wiring.

## Identify the intended destination

| Request | Destination |
|---|---|
| Recreate a website's branded control that happens to use shadcn | product recipe/theme; retain the website's identity |
| Port an official shadcn component as a shared Awake design-system capability | shared shadcn recipe, with upstream source and parity evidence |
| Use an already available shared recipe in a page | consume its public API; do not restyle its internals locally |
| Port Base UI/Radix behaviour without shadcn visuals | Foundation behaviour analysis through `awake-web-to-compose` |

Do not turn every shadcn-shaped component into a global recipe. Promote only a generic,
independently useful capability; the ownership rule is in
[`docs/reference/ui-ownership.md`](../../docs/reference/ui-ownership.md).

## Translate the component contract

Read the component's pinned `.tsx` and extract:

- anatomy: root, trigger, label, input/content, overlay, action, and indicator;
- named variants and sizes;
- interaction and dismissal rules: disabled, focus, keyboard, outside press, selected/checked;
- Tailwind values, including line-height and all padding axes.

Route its Tailwind utilities through
[`awake-tailwind-to-compose`](../awake-tailwind-to-compose/SKILL.md). Map mechanics to existing
Compose/Foundation primitives first. If the required mechanics do not exist, record a Foundation
gap instead of embedding a custom state machine in a visual recipe.

## Shared-recipe evidence

When changing a shared official Shadcn recipe, also read `awake-ui-authoring` and
`awake-ui-verification`. Use the pinned upstream source and the repo's reference capture pipeline
for values and parity; never recreate a shadcn component from memory. The current legacy
maintainer skill (`awake-shadcn-recipe-authoring`) applies only while editing its existing `ui:*`
implementation, not as the target API for a Compose-native port.

## Completion check

- The result exposes typed recipe inputs, not Tailwind classes or a React-like props bag.
- Component anatomy and state contract are preserved at the selected fidelity level.
- Shared recipes have an upstream proof and focused parity coverage; product recipes have product
  visual ownership instead.
- Base UI mechanics and shadcn visual policy have not been conflated.
