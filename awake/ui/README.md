### Awake UI libraries (`awake:ui`)

`awake:ui` holds UI libraries and tools built on [Awake Compose](../compose/README.md), Awake's
retained, Compose-shaped UI engine.

The split is one rule:

- `:awake:compose:*` is the engine: runtime, layout, drawing, modifiers, text and neutral controls.
- `:awake:ui:*` is what is built on it: design systems, reusable widgets and UI tools.
- Dependencies point from `ui` to `compose`, never the other way.

Which module owns what is decided in
[docs/reference/ui-ownership.md](../../docs/reference/ui-ownership.md), the canonical boundary
document. It is not repeated here.

### Modules

| Module | What it is |
|---|---|
| [`:awake:ui:material3`](material3/README.md) | Material 3 colour schemes, components and screen recipes such as `Scaffold` |
| [`:awake:ui:shadcn`](shadcn/README.md) | [shadcn/ui](https://ui.shadcn.com/) themes, tokens, variants and `Shadcn*` recipes |
| `:awake:ui:builder` | The visual UI layout builder: layout documents, drag-and-drop reflow and Kotlin code generation, drawn with shadcn |
| `:awake:ui:benchmark` | JVM layout benchmarks (kotlinx-benchmark); not published |
| `:awake:ui:font-atlas-generator` | Build tool that generates the embedded font-atlas sources in `:awake:core:text`; not published |

Related modules outside this directory:
- `:awake:tailwind` and `:awake:tailwind-generator`: Tailwind design tokens and their generator.
- `:awake:heroicons`: the Heroicons set.

The immediate-mode UI modules (`ui-core`, `graphics`, `headless`, `testing`, `animation`) were
retired. For UI test helpers, use `:awake:compose:ui-testing`.

### Verification

Current workflows and known limitations are in
[docs/reference/ui-validation.md](../../docs/reference/ui-validation.md).
