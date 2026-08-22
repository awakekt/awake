# 14 — Density, resize, layout direction

## Units: where `Dp` and `Sp` live

`Dp` and `Sp` are reused from `:awake:ui:graphics`, in package
`io.github.ronjunevaldoz.awake.ui.api`. Compose puts the equivalents in a dedicated `unit`
package (`androidx.compose.ui.unit.Dp`), and this engine should end up the same way —
`Dp`, `Sp`, `Density`, and eventually `IntSize`/`IntOffset` together in one unit package rather
than mixed into a general `api` bucket.

**Deferred, deliberately.** `api` currently also holds `Rectangle`, `UiIcon`, `UiEasing` and
`PopupContracts`, and **242 files import `Dp`/`Sp` from it**. Moving the package rewrites every one
of those imports, and the same imports are already scheduled to change in the planned
`io.github.awakelab.*` namespace rename. Doing it twice is churn for no gain, so it rides along
with that pass.

Done so far: `Sp` was split out of `Dp.kt` into its own file — same package, so zero import
changes.

**When the namespace pass happens**, the target shape is:

```
io.github.awakelab.ui.unit     Dp, Sp, Density, IntSize, IntOffset
io.github.awakelab.ui.geometry Rectangle
```

Until then `:awake:compose:*` imports `Dp` from `ui.api` and does not define its own — one `Dp`
type in the tree, not two.

## Density

`UiDensity.scale` and `UiDensity.fontScale` are global mutable state today. On a retained tree they
belong on the composition, reachable through `MeasureScope.density` and `Dp.roundToPx()`.

A density change invalidates every measured size. Simplest correct answer: mark the whole tree dirty
and re-measure. Density changes are rare (window moved to another display, user changed font scale),
so a full re-measure is fine and a partial one is a trap.

## Resize

Viewport resize re-measures from the root with new `Constraints.fixed(width, height)`. The tree and
all node state survive — which is the point, and is what `ui-core` cannot do because it has no tree
to keep.

## Layout direction / RTL — decide, do not drift

Compose distinguishes `place` from `placeRelative`, and `Alignment.Start` from `Alignment.Left`. That
distinction is only worth its cost if RTL is actually supported.

`ui-core` has no RTL support today. Two honest options:

1. **Explicit non-goal.** Use absolute `placeAt` and `Alignment.Left`/`Right` naming. Smaller
   surface, and no half-built RTL that looks supported and is not.
2. **Support it from the start.** `LayoutDirection` as a `CompositionLocal`, `placeRelative`
   mirroring, `Start`/`End` naming throughout. Cheap now, expensive to retrofit — every measure
   policy and alignment call site has to change.

**Recommendation: option 2, naming only.** Use `Start`/`End` and `placeRelative` from day one, with
`LayoutDirection.Ltr` as the only value that exists. The naming is free at this stage and is the
part that is expensive to retrofit; the mirroring logic can land whenever RTL is actually wanted.

Record the decision here once made, so it is not re-argued per measure policy.
