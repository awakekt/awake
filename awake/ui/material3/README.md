### Material 3 (`awake:ui:material3`)

Material 3 components and theme contracts for Awake's retained Compose-shaped UI runtime.

This is separate from `:awake:ui:shadcn`: applications choose one component family for a screen
instead of mixing their visual policy. Generic layout, input, drawing, and modifier behavior remain
in `:awake:compose:foundation`.

The initial surface is `Scaffold`, with a Material color-scheme provider. Its top bar, bottom bar,
and floating action button are measured independently; the content slot receives their measured
insets rather than a guessed bar height.
