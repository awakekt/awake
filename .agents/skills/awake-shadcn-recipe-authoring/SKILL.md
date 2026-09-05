---
name: awake-shadcn-recipe-authoring
description: >
  Maintainer-facing how-to for BUILDING/EXTENDING shadcn-flavored components inside Awake's
  ui-designsystem itself -- not for an app/sample that just calls an existing shadcn* component.
  Style.then's per-state-rule merge semantics, PascalCase component naming, KDoc keyword annotations,
  slot-based container scopes (ShadcnCardScope, ShadcnTableScope), and parity testing.
license: Apache-2.0
metadata:
  author: awake
  last-updated: '2026-10-15'
  keywords: Awake, shadcn, tailwind, Tw.Spacing, h-9, parity, ui-designsystem, Style, ShadcnCard, ShadcnTable, HeroIcons, ShadcnIcons
---

# Authoring Awake Shadcn Recipes

## When to Use This Skill

For whoever is working ON `:awake:ui:shadcn` itself -- adding a new `Shadcn*` component, composing
one out of existing ones (a card that's also a collapsible, a button that's also a trigger),
adding a `hovered`/`active` style override, or reaching for `animateFloatTween` for
something that needs to visibly finish (collapse, dismiss, fade out). NOT for a sample/app that
only *consumes* an already-built `Shadcn*` component -- that caller needs `awake-shadcn-recipe-consuming`.

**Trigger keywords:** ShadcnCard, ShadcnTable, ShadcnCalendar, ShadcnCommand, ShadcnItem, ShadcnForm, ShadcnCarousel, ShadcnPagination, ShadcnChart, Style.then, hover
bleed, button hover looks wrong, animatedHeight, animateFloatTween, HeroIcons, ShadcnIcons, icon registry, new shadcn component, extend ui-designsystem.

---

## Mandatory Rule 1: PascalCase Compose Components & KDoc Keywords

All public visual recipes MUST be **PascalCase Compose functions** (`ShadcnButton`, `ShadcnCard`, `ShadcnTable`, `ShadcnCalendar`).

Every public component file MUST include comprehensive KDocs with:
1. **Tailwind Reference**: Official `.tsx` class string.
2. **Use cases**: Concrete scenarios (e.g. settings rows, data grids, search dialogs).
3. **Example Usage**: Code `@sample` snippet.
4. **Parameter documentation**: `@param` tags.
5. **Keywords**: Search terms for IDE/agent component discovery (e.g. `Keywords: table, data grid, rows`).

```kotlin
/**
 * `ShadcnButton`: Primary action button component.
 *
 * **Tailwind Reference**: `inline-flex items-center justify-center rounded-md text-sm font-medium transition-colors`.
 *
 * Use cases:
 * - Form submit triggers, primary/secondary actions, icon-only buttons.
 *
 * **Example Usage**:
 * ```kotlin
 * ShadcnButton("Submit", variant = ShadcnButtonVariant.Default) { handleSubmit() }
 * ```
 *
 * @param label Button text label.
 * @param modifier Custom layout modifier.
 * @param variant Visual style variant (`Default`, `Destructive`, `Outline`, `Secondary`, `Ghost`, `Link`).
 * @param size Button dimensions (`Default`, `Sm`, `Lg`, `Icon`).
 * @param enabled Whether the button is interactive.
 * @param onClick Click action handler.
 *
 * Keywords: button, action, cta, primary button, outline button, ghost button.
 */
```

---

## Mandatory Rule 2: Container Scopes & Dual Slot/Block API

Container components should support both **concise slot parameters** (`header`, `footer`, `caption`) and **explicit DSL receiver scopes** (`ShadcnCardScope`, `ShadcnTableScope`):

```kotlin
@ShadcnTableDsl
class ShadcnTableScope internal constructor() {
    context(_: Composer)
    fun header(...) = ShadcnTableHeader(...)

    context(_: Composer)
    fun body(...) = ShadcnTableBody(...)

    context(_: Composer)
    fun caption(...) = ShadcnTableCaption(...)
}
```

---

## Mandatory Rule 3: Proof of Official Reference for Every Style Recipe

Every visual style in `awake:ui:shadcn` (colors, borders, paddings, corner radii, alignments) **MUST HAVE PROOF** originating directly from official React `shadcn/ui`. Agents and developers are strictly forbidden from hand-guessing or inventing arbitrary padding/radius values.

### How Proof Is Captured & Verified:

1. **Official Browser Capture (`tools/shadcn/capture_shadcn_local.py`)**:
   Playwright renders official React `shadcn/ui` components and dumps computed DOM style metrics (`getBoundingClientRect` & `getComputedStyle`) to:
   `docs/reference/shadcn-previews-local/<component>_<theme>.json`

2. **Automated CI Parity Gating (`ShadcnStyleParityTest.kt` & `ShadcnGeometryParityTest.kt`)**:
   Every component recipe must be registered in the automated Parity Test Suite:
   - `ShadcnGeometryParityTest`: Verifies width, height, and padding against DOM bounds.
   - `ShadcnStyleParityTest`: Verifies actual drawn `RoundedQuad` corner radius and fill/border colors against computed CSS metrics.

---

## Recipe File Split and Tailwind Values

Keep a component's retained composition and its visual translation separate: `ShadcnButton.kt`
owns public API, slots, state wiring, and headless composition; `ShadcnButtonStyle.kt` owns the
theme-to-`Style` resolver, palette choices, border widths, and source-derived spacing constants.

When an upstream Tailwind class supplies a spacing-scale value, use the generated `Tw` member
(`gap-2` -> `Tw.Spacing.s2`, `px-3` -> `Tw.Spacing.s3`) rather than spelling the equivalent `8.dp` or `12.dp`.

---

## Related Skills

- `awake-shadcn-recipe-consuming` -- consumer-facing how-to for using existing `Shadcn*` components in apps, games, and showcases.
- `awake-shadcn-parity-workflow` -- automated DOM style capture and screenshot parity verification.
