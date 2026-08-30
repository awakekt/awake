# Structural spacing ownership

Space belongs to the narrowest layout owner that can state its meaning. This preserves a
component's reusable geometry, avoids duplicate insets, and makes semantic parity captures
testable.

| Space means | Owner | Compose-shaped expression | Tailwind source signal |
|---|---|---|---|
| Internal boundary, visual inset, or tap target | The component | `Modifier.padding(...)` | `p-*`, `px-*`, `py-*` on the component |
| Same gap between every adjacent child | The common parent | `Arrangement.spacedBy(...)` | `gap-*` on a flex/grid container |
| One distinct, conditional, or weighted separation | The local layout relation | `Spacer(...)`, including `Modifier.weight()` when required | A one-off layout rule, not a uniform `gap-*` |

## Rules

1. Give a standalone component its own internal padding. Its callers should not need to reproduce
   the component's clickable or visual boundary.
2. Give a `Row` or `Column` an arrangement when all sibling separations are equal. Do not create
   the same interval with repeated child padding or several `Spacer`s.
3. Use `Spacer` only when the interval is intentionally exceptional: it is conditional, weighted,
   or differs from the parent’s uniform sibling gap.
4. Translate Tailwind scale values through `Tw.Spacing`: for example, `gap-2` becomes
   `Arrangement.spacedBy(Tw.Spacing.s2)` and a component’s `px-3 py-2` becomes its content
   padding. Arbitrary upstream values remain named recipe constants with the source class cited.

## Verification

A parity fixture must tag the sibling nodes and capture their bounds. The report then verifies the
parent-owned gap separately from each component’s content padding. A control without a content
slot (such as Checkbox or Switch) does not declare a padding oracle merely to inflate coverage.
