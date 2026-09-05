---
name: awake-shadcn-recipe-consuming
description: >
  Consume Awake's in-repository shadcn design system from a sample, game, or application.
  Use whenever a task adds or changes a screen that calls Shadcn* components, installs a
  Shadcn theme, or asks how an Awake app should style UI. Do not use for the external
  io.github.ronjunevaldoz:shadcn-compose Maven library, or for maintaining ui-designsystem
  recipes themselves.
---

# Consuming Awake Shadcn Recipes

Use this skill at call sites: games, samples, tools, and feature UI that consume Awake's
in-repository `:awake:ui:shadcn` module on the Compose UI runtime. For implementation inside the design system,
use `awake-shadcn-recipe-authoring` instead; for choosing the ownership layer of a new behavior, use
`awake-ui-authoring`.

When a component is being compared with official shadcn, also use
[`awake-shadcn-parity-workflow`](../awake-shadcn-parity-workflow/SKILL.md).

---

## 1. Component Discovery by Keywords

Every public component in `:awake:ui:shadcn` includes explicit KDoc `Keywords:` to make component discovery instant via IDE code completion or agent symbol search:

| Intent / Use Case | Search Keywords | Component / Recipe |
|---|---|---|
| Action buttons, CTAs, icon buttons | `button`, `cta`, `action` | `ShadcnButton` |
| Form text entry, search fields | `input`, `textfield` | `ShadcnInput` |
| Grouped content, setting panels | `card`, `panel`, `surface` | `ShadcnCard` (`ShadcnCardHeader`, `ShadcnCardContent`, `ShadcnCardFooter`) |
| Structured data grids, list rows | `table`, `data grid` | `ShadcnTable` (`header`, `body`, `row`, `head`, `cell`, `caption`) |
| List item row with avatar & actions | `item`, `media object`, `row` | `ShadcnItem` |
| Validated input field wrapper | `form`, `field`, `error` | `ShadcnForm`, `ShadcnFormField` |
| `Cmd+K` action & search palette | `command`, `cmd+k`, `palette` | `ShadcnCommand`, `ShadcnCommandDialog` |
| Page numbers & pagination bar | `pagination`, `page numbers` | `ShadcnPagination` |
| Month grid view & date selection | `calendar`, `date picker` | `ShadcnCalendar`, `ShadcnDatePicker` |
| Horizontal slide decks | `carousel`, `slider` | `ShadcnCarousel` |
| Bar, line, area, & donut charts | `chart`, `bar chart`, `line chart` | `ShadcnBarChart`, `ShadcnLineChart`, `ShadcnPieChart` |

---

## 2. PascalCase Naming Convention

All public visual recipes are **PascalCase Compose functions** (`ShadcnButton`, `ShadcnCard`, `ShadcnTable`).
Never use legacy lowercase functions (`shadcnButton`).

```kotlin
// Correct: PascalCase Component Name
ShadcnButton(
    label = "Save Changes",
    variant = ShadcnButtonVariant.Default,
    onClick = { handleSave() }
)
```

---

## 3. Container DSL Scopes & Layout Composition

### A. `ShadcnCard` (Slot & Block DSL)
Supports both block composition and optional slot parameters (`header`, `footer`, `content`):

```kotlin
// Option 1: Slot API (Concise)
ShadcnCard(
    modifier = Modifier.width(360.dp),
    header = { ShadcnText("Security Overview", variant = ShadcnTextVariant.H3) },
    footer = { ShadcnButton("Save Changes") }
) {
    ShadcnText("Manage 2FA and active tokens.")
}

// Option 2: Block DSL API
ShadcnCard(modifier = Modifier.width(360.dp)) {
    ShadcnCardHeader {
        ShadcnText("Security Overview", variant = ShadcnTextVariant.H3)
    }
    ShadcnCardContent {
        ShadcnText("Manage 2FA and active tokens.")
    }
    ShadcnCardFooter {
        ShadcnButton("Save Changes")
    }
}
```

### B. `ShadcnTable` (Concise Table DSL)
Provides clean scope extension methods (`header`, `body`, `row`, `head`, `cell`, `caption`) to eliminate repetitive `Shadcn*` prefixes:

```kotlin
ShadcnTable {
    header {
        row {
            head("Invoice")
            head("Status")
            head("Amount", align = ShadcnTableCellAlign.End)
        }
    }
    body {
        row {
            cell("INV-001")
            cell("Paid")
            cell("$250.00", align = ShadcnTableCellAlign.End)
        }
    }
    caption("A list of recent invoices.")
}
```

---

## 4. Theme Provider at App Root

Install `provideShadcnTheme` at the application or showcase root:

```kotlin
provideShadcnTheme(theme = ShadcnThemeValues(ShadcnTheme)) {
    ShadcnButton("Save")
}
```

---

## Checklist Before Finishing

- All components called use **PascalCase** (`ShadcnButton`, `ShadcnCard`, `ShadcnTable`).
- Tables use concise DSL methods (`header`, `body`, `row`, `head`, `cell`).
- Cards use `ShadcnCardScope` or `header`/`footer` slot parameters.
- Built and tested with `./gradlew :awake:ui:shadcn:desktopTest` and `./gradlew :samples:ui-showcase:desktopTest`.
