# 14 — Density, resize, layout direction

## Units: where `Dp` and `Sp` live

`Dp` and `Sp` are the real types declared in `:awake:core:math2d`
(`io.github.awakelab.awake.core.math2d.Dp`/`Sp`) — a math/geometry primitive module, not a
UI-framework one, so non-UI consumers (render-pass code, `DrawShape.kt`, `DrawStroke.kt`) use them
without depending on the compose engine at all. `:awake:compose:ui`'s `unit` package
(`io.github.awakelab.awake.compose.ui.unit`) re-exports them as `typealias Dp = GraphicsDp`
(an import-aliased re-export, see `Units.kt`) so `:awake:compose:*` code can write `Dp` the same
way upstream Compose does, with zero new type and zero conversion — there is exactly one `Dp` in
the tree.

This corrects this doc's earlier sketch, which predated the `ui-core`/`ui-headless` retirement and
assumed the pre-migration module layout (`:awake:ui:graphics`'s `ui.api` package, since deleted
along with the rest of `ui-core`). The real landing spot (`core:math2d`, aliased through
`compose:ui:unit`) is the equivalent decision, made during that migration rather than as a
dedicated pass — the reasoning (`api` bundled unrelated things, 242 call sites, avoid touching
them twice before the `io.github.awakelab.*` rename) still holds and is documented in `Units.kt`'s
own comment.

**Still deferred**: `Density`, `IntSize`/`IntOffset` are not yet unified alongside `Dp`/`Sp` in one
package the way upstream Compose's `androidx.compose.ui.unit` groups them. That consolidation, and
the `io.github.awakelab.*` rename itself, remain open.

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
