### Shadcn UI (`awake:ui:shadcn`)

Awake's [shadcn/ui](https://ui.shadcn.com/) themes, tokens, variants, and component recipes for
the retained Compose-shaped UI runtime.

## Ownership

`ui:shadcn` owns shadcn's visual language: named themes, colors, radii, spacing, typography, and
the public `shadcn*` recipes. It is not a generic widget or layout module. Reusable drawing,
layout, input, semantics, and interaction behavior belong in `:awake:compose:foundation`.

`ui:material3` is a separate component family. A screen should choose one visual system rather
than mixing Material and shadcn policy accidentally.

## Structure

```
shadcn/
  components/  public Shadcn component recipes
  styles/      state and variant Style resolvers
  theme/       retained Compose theme provider and tokens
```

## Dependencies

```
awake:ui:shadcn
  └── awake:compose:foundation
  └── awake:tailwind
  └── awake:heroicons
```

## Adding A Component

1. Check the upstream shadcn reference, `shadcn-compose`, and Awake's existing implementation.
2. Keep generic behavior in Foundation and write the shadcn visual recipe here.
3. Add a focused semantic/layout test and a visual parity snapshot.
4. Use the tools in `tools/shadcn/` to update the reference and parity records.
