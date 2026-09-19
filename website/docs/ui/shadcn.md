# Shadcn UI

The Shadcn module provides themes, tokens, variants, and component recipes for Awake’s retained
Compose-shaped UI runtime.

Add [`awake:ui:shadcn`](../getting-started.md#ui) to use the Shadcn component family. Its published
API depends on Compose Foundation, so a separate foundation alias is not normally needed when
Shadcn is your only UI layer.

## Usage model

Shadcn recipes own visual language: colors, radii, spacing, typography, and component variants.
Reusable layout, drawing, input, and semantics remain in the neutral Compose/Foundation runtime.

The public component surface includes buttons, fields, menus, overlays, navigation components,
status components, and layout blocks. See the [Button guide](../guides/components/button.md) for a
small example.
