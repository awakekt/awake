# 16 — What visibly changes when a screen moves

Stage 3 moves ~180 call sites from `ui-core` to this engine. Five things will look different on
arrival. All five are `ui-core` defaults this engine reverted to Compose's, so the shift is expected
and the same on every screen — but a reviewer who does not know the list will read each one as a new
bug.

Every row was found by the [cross-engine differ](../../../awake/ui/testing/src/commonTest/kotlin/io/github/awakelab/awake/testing/crossengine/CrossEngineDiffTest.kt),
which is where the executable version lives.

| # | `ui-core` | This engine | What you will see |
|---|---|---|---|
| 1 | `row`/`column` default to `Arrangement.spacedBy(8.dp)` | zero gap, Compose's default | children close up; pass the arrangement explicitly to keep the gap |
| 2 | text falls back to 16 px | `LocalTextStyle` carries 14 sp, Compose's body default | labels shrink ~12% |
| 3 | `surface` always clips content to its own shape | `background` paints, `clip` clips — separately | overflow that was hidden becomes visible |
| 4 | an uncentered line places its pen at `slot.y - blockMetrics.topPx` | glyphs hang off their own metrics | text sits a few px lower, and stops jumping between strings with and without descenders |
| 5 | `surfaceShapeDefaults` gives a surface content padding | nothing is inset | content moves 8 px up and left |

Rows 1 and 5 are the same mistake in two places, and `mirror-map.md` already carries row 1 against
real Compose.

**Row 4 is a fix, not just a default.** `ui-core`'s *centred* branch already avoids it — it centres
the cap box rather than the ink box, precisely so `"Tag"` and `"Tan"` do not sit at different
heights in the same badge. The uncentered branch never got the same treatment.

## Not on this list

State-dependent styling, because `ui-core` cannot express it for comparison: `hasResolvedVisuals()`
resolves a style before claiming a slot, so it assumes not-hovered and rechecks later. A button's
hover and press appearance therefore has no `ui-core` reference to diff against, and
`StyledSurfaceDiffTest` compares this engine against itself for those.

That is also why the differ stops here rather than growing to the whole Checkout Form — see the
README's Stage 1 gate.
