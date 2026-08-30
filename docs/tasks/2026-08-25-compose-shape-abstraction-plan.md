# 2026-08-25: a real `Shape` abstraction for `background`/`border`/`clip`

Status: **superseded — landed independently, more completely than this plan proposed.**

## What actually shipped

A concurrent session built the full `Shape`/`ShapeOutline` system while this plan was being
written, in `awake:compose:ui`'s `graphics` package and `awake:compose:foundation`'s
`Background.kt`:

- `Shape` / `ShapeOutline` (`Rectangle`/`Rounded`/`Generic`), `RectangleShape`, `RoundedCornerShape`
  — matches this plan's proposed shape, including the two-tier fast-path/generic-path resolution
  this plan called out as necessary to protect `RoundedQuad`'s perf win.
- `RoundedCornerShape` supports **per-corner radii** (`topStart`/`topEnd`/`bottomEnd`/`bottomStart`)
  — this plan explicitly scoped that out ("no caller needs it yet"). It shipped anyway and
  `ShadcnButtonGroup.kt` immediately used it (collapsing the shared radius between adjacent
  members).
- `BorderSides` — selective per-edge borders (`top`/`end`/`bottom`/`start`), with real arc-preserving
  path construction (`partialBorderPath`) for a partial rounded border. Not proposed in this plan at
  all; a real need this plan didn't anticipate.
- `Modifier.background(color, cornerRadius: Dp)` / `Modifier.border(width, color, cornerRadius: Dp)`
  — kept as compatibility overloads exactly as this plan recommended, so no existing call site needed
  to migrate.

## What this plan got right vs. wrong

Right: the overall shape (`Shape` interface + sealed `Outline`/`ShapeOutline`, `RectangleShape`/
`RoundedCornerShape`, additive `Dp`-overload compatibility, protecting the `RoundedQuad` fast path).

Wrong: scoped out per-corner radii and partial-side borders as unneeded generality. Both were needed
almost immediately (`ShadcnButtonGroup`'s adjacent-member border collapse needs both) — a reminder
that "no caller needs it yet" is only safe to defer when you can see every near-term caller, not
when other sessions are shipping features in parallel.

## Follow-up found while verifying the landed system

`ShadcnButtonGroup.kt`'s `memberBorderSides()` had a real bug, unrelated to the `Shape` work itself:
every member drew its full trailing edge (`end = true` for horizontal, `bottom = true` for vertical)
regardless of position, so interior members double-bordered the edge shared with their neighbor.
Should be `index == count - 1`. Fixed.

## `Modifier.clip(shape: Shape)`

Confirmed landed too (`awake/compose/ui/.../draw/DrawModifiers.kt`) — the original motivating gap,
a rounded-corner clip, is closed. All three items this plan set out for (`background`, `border`,
`clip` sharing one `Shape`) are done.
