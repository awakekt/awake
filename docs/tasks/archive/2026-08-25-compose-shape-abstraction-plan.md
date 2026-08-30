# 2026-08-25: a real `Shape` abstraction for `background`/`border`/`clip`

## Context

Found while fixing a real border-rendering bug (committed `f617e5fbe`): Awake's compose engine has
no `Shape` type. `Modifier.background(color, cornerRadius: Dp)` and `Modifier.border(width, color,
cornerRadius: Dp)` each take a bare `Dp` instead of sharing one definition of "what shape is this."
`Modifier.clip()` only clips to the node's rectangular bounds (`clipToBounds`) — a rounded-corner
clip (an image inside a rounded card) isn't expressible at all. `15-compose-parity.md` now records
this as a `Gap`; `02-modifier.md` records the same finding with the border-bug citation.

The shared abstraction has landed after the border fix. ButtonGroup remains the first parity
consumer to migrate; that recipe work is separate from the generic capability.

## What already exists to build on

- `DrawCommand.FilledPath` / `DrawCommand.StrokedPath` — draw primitives that take a `DrawPath`.
- `DrawCommand.ClipPathPush` / `ClipPop` — arbitrary-path clipping already exists at the primitive
  level; only `Modifier.clip()` fails to expose it (it only offers rectangular `clipToBounds`).
- `PathCommand` (`MoveTo`/`LineTo`/`QuadTo`/`CubicTo`) + `DrawPath` — general path construction.
- A working rounded-rect-via-path builder, built inline in `BorderNode` (`Background.kt`) for the
  border fix — `MoveTo`/`LineTo`/arc-per-corner. This is the thing to extract, not redo.
- `DrawCommand.RoundedQuad` — the existing GPU fast path for the common flat/rounded-rect case,
  which must not regress. A `Shape`-based redesign that routes every background through path
  tessellation would trade a documented perf win (`RoundedQuad`'s own doc comment, citing
  kool-engine's `RectBackground`/`RoundRectBackground` split) for API cleanliness. Don't do that.

## Target shape — mirror Compose's, not a reinvention

Real Compose:
```kotlin
interface Shape {
    fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline
}
sealed class Outline {
    class Rectangle(val rect: Rect) : Outline()
    class Rounded(val roundRect: RoundRect) : Outline()
    class Generic(val path: Path) : Outline()
}
object RectangleShape : Shape
class RoundedCornerShape(topStart: CornerSize, topEnd: CornerSize, bottomEnd: CornerSize, bottomStart: CornerSize) : Shape
object CircleShape : Shape
```

Proposed Awake equivalent, in `awake:compose:ui`'s `graphics` package (mirroring
`androidx.compose.ui.graphics.Shape`, not `foundation` — `background`/`border`/`clip` are
`foundation`-level callers of a `ui`-level type, same layering Compose uses):

```kotlin
interface Shape {
    fun outline(size: Size, density: Float): ShapeOutline
}

sealed interface ShapeOutline {
    data class Rectangle(val rect: Rectangle) : ShapeOutline
    data class Rounded(val rect: Rectangle, val cornerRadiusPx: Float) : ShapeOutline
    data class Generic(val path: DrawPath) : ShapeOutline
}

object RectangleShape : Shape { ... }              // Outline.Rectangle — the existing Quad fast path
class RoundedCornerShape(                           // uniform values use the existing RoundedQuad path;
    topStart: Dp, topEnd: Dp,                       // independent corners use the established path path
    bottomEnd: Dp, bottomStart: Dp,                 // required by ButtonGroup's joined outer corners
) : Shape
object CircleShape : Shape                          // Outline.Rounded with radius = min(w,h)/2
// A Shape built from an arbitrary DrawPath (Outline.Generic) covers anything else,
// e.g. a future non-rect card shape — not needed by any caller yet, don't build it speculatively.
```

**No `LayoutDirection` parameter** — `14-density-resize.md` already decided RTL is naming-only for
now (`LayoutDirection.Ltr` the only value), so threading it through `Shape.outline(...)` today would
be unused generality. Add it when RTL support actually lands, not before.

**Two-tier resolution, to protect the `RoundedQuad` fast path:**
- `ShapeOutline.Rectangle` / `.Rounded` → `BackgroundNode`/`BorderNode` call `drawRect`/
  `drawRoundedRect`/the new `drawStrokedPath` directly, exactly as today. Zero behavior change,
  zero perf regression, for every existing caller (`RectangleShape`/`RoundedCornerShape` cover
  100% of today's call sites).
- `ShapeOutline.Generic` → falls back to `FilledPath`/`StrokedPath`/`ClipPathPush` via the path
  machinery the border fix already proved out. This tier only executes for a shape nothing uses yet.

## API changes

```kotlin
// New primary overloads:
fun Modifier.background(color: Color, shape: Shape = RectangleShape): Modifier
fun Modifier.border(width: Dp, color: Color, shape: Shape = RectangleShape): Modifier
fun Modifier.clip(shape: Shape): Modifier   // NEW — clipToBounds() becomes clip(RectangleShape),
                                             // or stays as a documented rectangle-only fast alias;
                                             // decide during implementation, not here

// Existing Dp-radius overloads: convenience wrappers, not deleted
fun Modifier.background(color: Color, cornerRadius: Dp): Modifier =
    background(color, RoundedCornerShape(cornerRadius))
fun Modifier.border(width: Dp, color: Color, cornerRadius: Dp): Modifier =
    border(width, color, RoundedCornerShape(cornerRadius))
```

Keeping the `Dp` overloads as thin wrappers avoids a breaking migration across the ~6 files that
currently call `.background(color, radius)`/`.border(width, color, radius)` directly — they don't
need to change at all. New code should prefer the `Shape` overload; existing code is not required to
migrate just because the new API exists.

## Work items, in order

1. **Done:** `Shape`/`ShapeOutline`/`RectangleShape`/`RoundedCornerShape`/`CircleShape` in
   `awake:compose:ui`'s `graphics` package, delegating independent-corner paths to existing
   `DrawShape.RoundedCorners` geometry.
2. **Done:** `Modifier.clip(shape: Shape)` emits the existing `ClipPathPush`/`ClipPop` pair for
   rounded and generic outlines; `RectangleShape` preserves the cheap rectangular clip path.
3. **Done:** `background`/`border` gain shape overloads; the old `Dp` overloads are compatibility
   wrappers. Rectangle and uniform rounded cases preserve `Quad`/`RoundedQuad` fast paths.
4. **Done:** `Style.shape(...)` passes one shape to its resolved background and border, so a recipe
   can supply an independent-corner shape without a Core type in its public API.
5. **Next:** migrate ButtonGroup to supply outer/inner member shapes and collapsed shared borders,
   then prove it through its registered reference/Awake/diff parity cases.

## Verification

- Focused Compose tests cover uniform `RoundedQuad` preservation, independent-corner filled and
  stroked paths, `clip(shape)` path commands, rectangle-clip preservation, and the `Style` bridge.
- `compose:ui`, `compose:foundation`, and `ui:designsystem` desktop compilation pass.
- `compileKotlinDesktop` across `compose:ui`, `compose:foundation`, `ui:designsystem`,
  `backend:vulkan`, `backend:webgpu` — the `RoundedQuad`/`StrokedPath` fast paths mean neither
  backend needs new code, same as the border fix; confirm that holds, don't assume it by analogy.
- `15-compose-parity.md` records the completed Shape support as `Built`.

## Explicitly out of scope

- `LayoutDirection`-aware shapes — RTL is still a naming-only non-goal per `14-density-resize.md`.
- Migrating existing `.background(color, cornerRadius)` call sites to the new `Shape` overload —
  the `Dp` overload stays supported, this is purely additive.
