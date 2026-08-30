# Compose Primitives for a Web Port

Read this when the port uses layout/paint modifiers, component style, or animation. This is a
guide to the Compose-native runtime currently in `:awake:compose:*`; it intentionally does not
describe the retiring `:awake:ui:*` APIs.

## Modifier: instance layout, paint, and interaction

`Modifier` is an ordered chain attached to a specific composable instance. Use it for that
instance's constraints, placement, drawing, clipping, input, focus, scroll, and semantics.

- Use Foundation layout modifiers such as `padding`, `width`, `height`, `size`, `widthIn`,
  `heightIn`, `fillMaxWidth`, `fillMaxHeight`, `offset`, and `aspectRatio` for layout.
- Use `weight` only in the relevant `RowScope` or `ColumnScope`. Translate CSS margin/gap into
  the parent arrangement or parent padding, not a child modifier.
- Use Foundation `background`/`border` and UI draw modifiers such as `alpha`, `scale`,
  `drawBehind`, and `clipToBounds` for paint effects.
- Use Foundation `clickable`, `hoverable`, `focusable`, `selectable`, `toggleable`, scroll, and
  drag modifiers for the interaction contract; use semantics/test tags deliberately.

The modifier order is observable: it determines whether padding is inside or outside a painted
background and whether a clip applies before a draw effect. Do not mechanically reorder a CSS
declaration list into a chain. Verify the reference box model at each styled surface.

## Style: reusable component visual policy

`Style` is a typed, composable visual bundle for a reusable component recipe. It resolves from a
`StyleState`, and `Modifier.styleable(state, styles...)` applies it as ordinary modifiers. Later
styles win for the properties they provide.

Use a `Style` for recurring component visual policy: background, border, corner radius, internal
content padding, external padding, component width/height, text colour, alpha, and state variants
such as hover, pressed, focused, disabled, selected, and checked.

```kotlin
val PrimaryButtonStyle = Style {
    background(theme.primary)
    cornerRadius(theme.buttonRadius)
    contentPadding(horizontal = theme.space4, vertical = theme.space2)
    hovered(Style { alpha(0.92f) })
    disabled(Style { alpha(0.5f) })
}
```

Keep a recipe's style at the product/design-system boundary. Do not use `Style` as a page-local
CSS substitute: structural layout remains `Row`/`Column`/`Box` plus `Modifier`, and an
application should use named component recipes instead of restyling ordinary controls ad hoc.

## Animation: current supported surface

Use `animateFloat(target, durationSeconds)` for a finite, linear, state-driven scalar transition.
It retargets from the current value and starts at the target on first appearance. Use the returned
value in a supported modifier or layout value—for example alpha, scale, a progress fraction, or
an explicitly measured dimension.

Use `rememberLoopingPhase(durationSeconds)` only for an intentionally continuous phase such as a
spinner, shimmer sweep, or indeterminate progress effect. Its identity is the composable's
remembered position, not a manually supplied string id.

The current Compose-native Foundation does **not** yet expose arbitrary easing/tween specs,
springs, animated visibility/layout transitions, or a general transition coordinator. If the web
reference depends on one, record it as a Foundation capability gap or use a deliberately simpler
motion approved by the selected fidelity target. Do not import or copy the retiring
`ui:animation` API to fill that gap.

Respect reduced-motion preferences when the host exposes them, and ensure that motion never
contains the only indication of state change.
