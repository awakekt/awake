# 2026-08-23: the glyph atlas belongs in `core:graphics2d`

Status: **planned, not started.** Do it between component batches, not during one.

## The argument, in one fact

`DrawCommand.Glyph` carries atlas coordinates — `u0, v0, u1, v1` — and lives in
`awake/core/graphics2d`. The code that *produces* those coordinates lives two modules above it, in
`:awake:core:text`.

So the primitive that references an atlas is in core, and the atlas is not. Everything that renders
text has to reach up past the layer it draws into.

## What moves where

The module splits cleanly along a line Compose itself draws.

| Type | Goes to | Why |
|---|---|---|
| `UiFont`, `PackedUiFont`, `BitmapFont`, `MsdfFont`, `GlyphAtlasSource`, `RobotoRegularUiFontData` | **`core:graphics2d`** | Rasterisation: pixels, cells, UVs. Compose has no counterpart — it delegates to platform text — so this is Awake's own and belongs beside `Glyph` |
| `TextStyle`, `FontWeight` | **`:compose:ui`** | `androidx.compose.ui.text.TextStyle` and `...text.font.FontWeight` are compose-ui's, so the parity rule places them |
| `UiAnchor`, `pixelPerfectTextScale` | follow their callers | Small helpers; decide per call site rather than by category |

`:awake:core:text` then has nothing left and goes, the way `:awake:ui:graphics` will once `ImageVector`
took its only real contents.

## Why it is cheap

The font classes import almost nothing outside their own package — one helper,
`pixelPerfectTextScale`, and the `awake.ui.font` package itself. There is no dependency knot to
untangle, unlike `ImageVector`, which was blocked for a long time by a cycle that turned out to be a
dead `ui:text` → `ui:graphics` edge.

## Why it is not free

`:compose:ui` currently takes `api(project(":awake:core:text"))`. After the split it takes the two
style types directly and drops the module. Every consumer of `awake.ui.font.*` — `ui-core`,
`ui-headless`, the font-atlas generator, and the tests — changes import.

**Do it between component batches.** The last module move of this shape landed a circular task graph
that no per-module compile could see, and cost a full investigation to trace back to colliding
artifact ids. The lesson is in that commit: run `./gradlew build --dry-run` on the whole graph before
committing a build-file change, and do not start one mid-component.

## What it unblocks

Nothing urgent, which is why this is recorded rather than done. It is a layering correction, and the
one concrete gain is that `:compose:ui` stops depending on a `ui:*` module for text at all — leaving
`:awake:ui` holding only the design system and the generators, which is the end state Stage 3 is
aiming at anyway.
