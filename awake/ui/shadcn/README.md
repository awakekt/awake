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
```

## Density

`LocalShadcnDensity` (`theme/ShadcnDensity.kt`) sets how much room the components take for its
content: `ShadcnDensity.Default`, shadcn's own sizes and what applies when nothing provides one, or
`ShadcnDensity.Compact`, for a dense UI such as an editor.

```kotlin
CompositionLocalProvider(LocalShadcnDensity provides ShadcnDensity.Compact) {
    ShadcnButton("Apply")   // 32dp, as `size = Sm`; `Default` is 36dp
}
```

Under `Compact` every component's default size drops a step. The two densities, side by side:

| Component | `Default` | `Compact` |
| --- | --- | --- |
| Button, button group member | 36dp (`Default`) | 32dp (`Sm`) |
| Input, input group, combobox field, select trigger | 36dp, `px-3` | 28dp, `px-2` |
| Textarea | min 64dp | min 48dp |
| Select, combobox, dropdown and context menu rows | 32dp | 28dp |
| Checkbox, radio ring | 16dp | 14dp |
| Switch | 32 x 18dp | 28 x 16dp |
| Toggle, toggle group item | 40dp, 36dp | 32dp |
| Tabs, every variant | 36dp | 28dp (`Sm`) |
| Table head, cell | 40dp, `p-2` | 32dp, `px-2 py-1.5` |
| `ShadcnItem` row, command palette | `px-4 py-3` | `px-3 py-2` |
| Label, field label, field description | `text-sm` | `text-xs` |
| Field, field group, field set gaps | 12, 28, 24dp | 8, 20, 16dp |
| Popover and hover card padding | `p-4` | `p-3` |

A size passed at the call site (`size = ShadcnButtonSizeVariant.Lg`) always wins, so the `size`
parameter of `ShadcnButton`, `ShadcnToggleGroup` and `ShadcnTabs` is nullable and `null` means "follow
the density". Colours, radii and the type scale never change with density. Recipes read it through
`shadcnDensity` and keep their `Compact` numbers beside their `Default` ones, in the same file.

## Adding A Component

1. Check the upstream shadcn reference, `shadcn-compose`, and Awake's existing implementation.
2. Keep generic behavior in Foundation and write the shadcn visual recipe here.
3. Add a focused semantic/layout test and a visual parity snapshot.
   A component with a size or spacing of its own also reads `shadcnDensity`, and gets a case in
   `ShadcnDensityTest`.
4. Use the tools in `tools/shadcn/` to update the reference and parity records.
