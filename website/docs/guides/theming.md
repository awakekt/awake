# Theming

Awake UI separates neutral layout behavior from visual tokens. A component family supplies colors,
typography, radii, spacing, and state variants; the same layout and input primitives can then be
used with a different visual system.

## Shadcn themes

Shadcn components read their values from a scoped `ShadcnThemeValues` provider. Install the theme
around the subtree that uses Shadcn recipes with `provideShadcnTheme`, then compose components
inside that scope.

The theme value types and their KDoc are the source of truth for constructing a theme. Keep
application branding in the theme layer rather than overriding individual component internals.

## Choosing a family

- Use Shadcn for the Awake Shadcn visual language and its recipe catalog.
- Use Material 3 when the application follows Material color and component conventions.
- Use headless primitives when the application owns its entire visual system.
