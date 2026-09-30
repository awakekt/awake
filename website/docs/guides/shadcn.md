# shadcn components

<p class="awake-lede">Ready-made buttons, fields, menus, dialogs and layout blocks in the visual language of shadcn/ui, built on AwakeKt Compose. A theme you choose supplies every colour, radius and spacing.</p>

<div class="awake-badges" markdown>
<span class="awake-badge">module: <code>awake:ui:shadcn</code></span>
<span class="awake-badge awake-badge--ok">Vulkan</span>
<span class="awake-badge awake-badge--ok">WebGPU</span>
<span class="awake-badge">Desktop · Android · iOS · Web</span>
</div>

<div class="awake-figures" markdown>
<figure markdown>
![Six shadcn buttons in a row, one per variant, in the light theme](../assets/ui/button-variants-light.png)
<figcaption>Button variants, light theme</figcaption>
</figure>
</div>

Add `com.awakekt.awake.ui:shadcn` (catalog alias `libs.awake.ui.shadcn`) to `commonMain`. It brings
AwakeKt Compose Foundation with it. The components are ordinary [AwakeKt Compose](ui.md) functions,
so everything on that page applies here.

## Wrap your UI in a theme

Every shadcn component reads the theme in scope. Wrap the UI that uses them in
`provideShadcnTheme(values) { … }`.

```kotlin title="Kotlin"
--8<-- "awake/ui/shadcn/src/desktopTest/kotlin/com/awakekt/awake/ui/shadcn/ShadcnDocsSampleTest.kt:themed-button"
```

!!! warning "No theme, no component"
    A shadcn component outside `provideShadcnTheme` throws `IllegalArgumentException`. Its message starts
    "No shadcn theme in scope. Wrap this content in provideShadcnTheme { }". There is no
    fallback theme, so a missing provider fails where it is missing instead of drawing the wrong
    colours.

## Choose a theme

`shadcnThemeValues(…)` builds a complete theme from four choices.

```kotlin title="Kotlin"
--8<-- "awake/ui/shadcn/src/desktopTest/kotlin/com/awakekt/awake/ui/shadcn/ShadcnDocsSampleTest.kt:custom-theme"
```

| Parameter | Type | Default | What it does |
| --- | --- | --- | --- |
| `preset` | `ShadcnStylePreset`: `Vega` · `Nova` · `Maia` · `Lyra` · `Mira` · `Luma` · `Sera` · `Rhea` | `Vega` | Base corner radius and spacing. `Vega` matches shadcn's new-york-v4 metrics. |
| `baseColor` | `ShadcnBaseColor`: `Neutral` · `Stone` · `Zinc` · `Mauve` · `Olive` · `Mist` · `Taupe` | `Neutral` | The grey scale behind every surface, border and text colour. |
| `accent` | `ShadcnAccent`: `Base`, or one of `Amber` · `Blue` · `Cyan` · `Emerald` · `Fuchsia` · `Green` · `Indigo` · `Lime` · `Orange` · `Pink` · `Purple` · `Red` · `Rose` · `Sky` · `Teal` · `Violet` · `Yellow` | `Base` | Replaces the primary colour, its foreground, and the focus ring. `Base` keeps the base colour's own. |
| `dark` | `Boolean` | `true` | Dark or light palette. |

The `ShadcnTheme` object is the default theme: `Vega`, `Neutral`, `Base`, dark. Wrap it as
`ShadcnThemeValues(ShadcnTheme)` to provide it.

## Read theme tokens

Inside `provideShadcnTheme`, `shadcnTheme` returns the theme in scope. Use its tokens instead of
fixed colours, so your own components follow the theme.

```kotlin title="Kotlin"
--8<-- "awake/ui/shadcn/src/desktopTest/kotlin/com/awakekt/awake/ui/shadcn/ShadcnDocsSampleTest.kt:read-tokens"
```

| Token group | Members |
| --- | --- |
| `palette` | `background`, `foreground`, `primary`, `primaryForeground`, `secondary`, `secondaryForeground`, `muted`, `mutedForeground`, `accent`, `accentForeground`, `destructive`, `destructiveForeground`, `border`, `input`, `ring`, `card`, `cardForeground`, `popover`, `popoverForeground`, `sidebar` and its roles, `shadow`, `overlay` |
| `radii` | `xs` (0.4 × base), `sm` (0.6 ×), `md` (0.8 ×), `lg` (the preset's base radius), `xl` (1.4 ×), `full` |
| `metrics` | `panelPadding`, `surfacePadding`, `fieldPaddingX`, `fieldPaddingY`, `badgePaddingX`, `badgePaddingY`, `inputPaddingY`, `bandPaddingX` |
| `config` | The `preset`, `baseColor`, `accent` and `dark` the theme was built from. |

`shadow` is a base tint: apply your own alpha. `overlay` is a complete scrim colour, ready to draw.

## How it works

`provideShadcnTheme` puts the values in a `CompositionLocal` for its content. A nested
`provideShadcnTheme` wins inside its own content, and the outer theme returns after it closes. The
provider also sets the inherited text colour to `palette.foreground`, so plain `Text` inside it reads
on the theme's background.

Components hold the visual language: colours, radii, spacing and variants. Layout, drawing, input
and semantics come from AwakeKt Compose Foundation. If your app needs a different look, build your
own components on Foundation rather than overriding shadcn internals.

The module's components include alerts, avatars, badges, breadcrumbs, buttons and button groups,
calendars, cards, carousels, charts, checkboxes, comboboxes, commands, menus, dialogs, drawers,
fields and inputs, popovers, progress bars, radio groups, resizable panels, selects, sheets, sidebars,
sliders, switches, tables, tabs, toasts, toggles and tooltips. Each is a `Shadcn*` function in
`com.awakekt.awake.ui.shadcn.components`.

## See also

- [Button](../reference/components/button.md) reference.
- [UI with AwakeKt Compose](ui.md) for layout, state, hosting and tests.
- [Game UI](game-ui.md) for a HUD stored in a scene.
