# Jetpack Compose Guidance: Modifier & Layout
## Complete reference for `:awake:compose:ui` and `:awake:compose:foundation` contributors

This guide is retained for detailed modifier examples. The numbered Compose Engine guides are
the current source of truth for layout and modifier contracts; references below to the former
immediate-mode implementation are historical terminology, not active module dependencies.

> **Companion to** [`mirror-map.md`](mirror-map.md) (faithful/diverges status table) and
> [`compose-animation-guidance.md`](compose-animation-guidance.md) (`animateFloat*`/`Easing`/
> `rememberTransition`/`animatedVisibility` how-to -- a separate doc since animation lives in its
> own `awake:ui:animation` module).
> `mirror-map.md` records *what* diverges. This doc explains *why* Compose works the way it does,
> *how* each Awake modifier maps to it, and *which patterns are safe* to use today.
> Every section is grounded in the actual source files, not memory.

---

## Overview: `UiModifier` data class fields

Every modifier function returns a `copy()` of [`UiModifier`](../../awake/ui/ui-core/src/commonMain/kotlin/com/awakekt/awake/ui/modifier/UiModifier.kt).
The full field set is:

| Field | Type | Set by |
|---|---|---|
| `widthDimension` | `Dimension?` | `width()`, `size()`, `fillMaxWidth()`, `fillMaxSize()` |
| `heightDimension` | `Dimension?` | `height()`, `size()`, `fillMaxHeight()`, `fillMaxSize()` |
| `minWidth` | `Dp?` | `widthIn(min = ...)` |
| `maxWidth` | `Dp?` | `widthIn(max = ...)` |
| `minHeight` | `Dp?` | `heightIn(min = ...)` |
| `maxHeight` | `Dp?` | `heightIn(max = ...)` |
| `layoutWeight` | `LayoutWeight?` | `weight(weight, fill)` |
| `alignment` | `UiAlignment?` | `align(alignment)` |
| `offsetX` / `offsetY` | `Dp` | `offset(x, y)` |
| `insets` | `UiInsets` | `padding(...)` |
| `scrollState` | `UiScrollState?` | `verticalScroll(state)`, `horizontalScroll(state)` |
| `scrollConfig` | `UiScrollConfig` | `verticalScroll(..., config)`, `horizontalScroll(..., config)` |
| `graphicsLayer` | `UiGraphicsLayer?` | `graphicsLayer(effect)`, `alpha(v)`, `scale(sx, sy)` |
| `styleable` | `Style?` | `styleable(style)`, `background(color)`, `border(w, c)`, `shape(r)` |
| `clickAction` | `UiClickable?` | `clickable(enabled, onClick)` |
| `forceHover` | `Boolean?` | `forceHover()` |
| `forceActive` | `Boolean?` | `forceActive()` |
| `forceFocus` | `Boolean?` | `forceFocus()` |
| `testTag` | `String?` | `testTag(tag)` |

---

## Layout Modifiers ([`LayoutModifiers.kt`](../../awake/ui/ui-core/src/commonMain/kotlin/com/awakekt/awake/ui/modifier/LayoutModifiers.kt))

### `width(dp)` / `height(dp)` / `size(width, height)`

**Compose equivalent**: `Modifier.width(dp)` / `.height(dp)` / `.size(dp)` — sets a fixed size, propagated top-down as `Constraints(minW = dp, maxW = dp)`.

**Awake**: Sets `widthDimension = Dimension.Fixed(dp)` / `heightDimension = Dimension.Fixed(dp)`. Resolved in `RowScope.claimSlot()` / `ColumnScope.claimSlot()` to a concrete pixel value.

**Status**: ✅ **Faithful** — same semantics.

```kotlin
text("label", modifier = Modifier.width(120.dp).height(40.dp))
surface("card", modifier = Modifier.size(200.dp, 100.dp))
```

---

### `widthIn(min, max)` / `heightIn(min, max)`

**Compose equivalent**: `Modifier.widthIn(min, max)` — clamps resolved width to `[min, max]`.

**Awake**: Sets `minWidth`/`maxWidth` / `minHeight`/`maxHeight` on the modifier. Applied **after** `Dimension` resolution as a clamp. Useful for "wrap content but never narrower than X" or "never wider than Y".

**Status**: ✅ **Faithful** — same semantics.

```kotlin
// Button that wraps its label but is at least 80dp wide
button("OK", modifier = Modifier.widthIn(min = 80.dp))

// Card that shrinks to content but caps at 300dp
surface("card", modifier = Modifier.widthIn(max = 300.dp))
```

**Common use case** — shadcn's button uses `heightIn(min = ...)` to set the minimum tap target without fixing the height for multi-line content.

---

### `fillMaxWidth()` / `fillMaxHeight()` / `fillMaxSize()`

**Compose equivalent**: `Modifier.fillMaxWidth(fraction)` — internally `FillNode` does `constraints.maxWidth * fraction`. If `maxWidth == Constraints.Infinity` (unbounded parent), the modifier **has no effect** and the child wraps its own content.

**Awake (current — diverges)**: Sets `Dimension.FillMax`. Resolved in `RowScope.claimSlot()`:

```kotlin
val availableWidth = this.width ?: (context.frameBoundsInternal().width - cursorX)
//                                  ^^^ BUG: falls back to full frame width when parent is unbounded
```

**Status**: ⚠️ **Diverges** — `FillMax` in an unbounded (wrap-content) parent expands to the entire frame, not the child's intrinsic size.

**Safe rule today**:
```kotlin
// ✅ Safe — parent has explicit bounds
row(modifier = Modifier.fillMaxWidth()) {
    button("A", modifier = Modifier.weight(1f))     // weight() is safer than fillMaxWidth() for siblings
}

// ✅ Safe — parent is a bounded root column
column {                                             // root column has frame width
    text("hello", modifier = Modifier.fillMaxWidth())
}

// ❌ Unsafe — wrap-content parent → blows out to frame size
row {
    button("OK", modifier = Modifier.fillMaxHeight())   // expands to full screen height!
}
```

**After Phase 1 fix**: `FillMax` in unbounded parent → `0f` → child wraps its own content (matching Compose's `Constraints.Infinity` no-op behavior).

---

### `weight(weight, fill)`

**Compose equivalent**: `RowScope.Modifier.weight(1f)` — signals proportional main-axis sizing. Compose's two-pass algorithm:
1. Measure all non-weighted children first.
2. Remaining space = container − sum(non-weighted widths).
3. Each weighted child gets `(remaining × weight / totalWeight)`.

**Awake**: Sets `LayoutWeight(weight, fill)`. Resolved by `resolveWeightedMainAxis()` in [`Arrangement.kt`](../../awake/ui/ui-core/src/commonMain/kotlin/com/awakekt/awake/ui/layouts/Arrangement.kt). A `fillsMainAxis` flag (set in `RowScope.claimSlot()`) prevents non-weighted `FillMax` siblings from starving weighted ones.

**Status**: ✅ **Faithful** (fixed in commit `9455bc51`).

```kotlin
// ✅ Two children split evenly
row(modifier = Modifier.fillMaxWidth()) {
    button("Cancel", modifier = Modifier.weight(1f))
    button("OK",     modifier = Modifier.weight(1f))
}

// ✅ Icon fixed, label takes the rest
row(modifier = Modifier.fillMaxWidth()) {
    icon(modifier = Modifier.size(24.dp))
    text("label", modifier = Modifier.weight(1f))
}

// ❌ Avoid: fillMaxWidth() sibling next to weight() in same row
row(modifier = Modifier.fillMaxWidth()) {
    text("label", modifier = Modifier.fillMaxWidth())  // starves the weighted sibling
    button("go",  modifier = Modifier.weight(1f))
}
```

---

### `align(alignment)`

**Compose equivalent**: `RowScope.align(Alignment.CenterVertically)` or `ColumnScope.align(Alignment.CenterHorizontally)` — per-child cross-axis override.

**Awake**: Sets `UiModifier.alignment: UiAlignment`. `UiAlignment` is a 2D enum (`TopStart`, `Center`, `BottomEnd`, etc.) plus nested `Vertical` and `Horizontal` enums for row/column cross-axis use. The container applies it during slot placement via `claimModifiedSlot()`.

**Status**: ✅ **Faithful** — same per-child override semantics.

```kotlin
row {
    icon(modifier = Modifier.align(UiAlignment.CenterStart))
    text("label")    // uses row's default verticalAlignment
}
column {
    button("centered", modifier = Modifier.align(UiAlignment.Center))
}
```

**Cross-Axis Stretch in Compose**: Jetpack Compose does *not* have an `Alignment.Stretch` option. Instead of a stretch alignment value, Compose uses sizing modifiers on child elements:
- Use `Modifier.fillMaxWidth()` to stretch an item across the width of a parent column.
- Use `Modifier.fillMaxHeight()` to stretch an item along the height of a parent row.
- Use `Modifier.fillMaxSize()` to stretch an item to fill both dimensions.
- Use `Modifier.weight(1f)` inside a `Row` or `Column` to stretch an item to fill remaining proportional space.

---

### `offset(x, y)`

**Compose equivalent**: `Modifier.offset(x, y)` — post-layout pixel translation, does not affect sibling layout.

**Awake**: Sets `offsetX`/`offsetY` on `UiModifier`. Applied during slot placement.

**Status**: ✅ **Faithful**.

```kotlin
text("badge", modifier = Modifier.offset(x = 4.dp, y = (-2).dp))
```

---

### `padding(...)` overloads

**Compose equivalent**: `Modifier.padding(all)` / `.padding(horizontal, vertical)` / `.padding(start, top, end, bottom)`.

**Awake**: Sets `insets: UiInsets`. Four overload shapes match Compose exactly.

**Status**: ✅ **Faithful**.

```kotlin
surface("card", modifier = Modifier.padding(16.dp))
surface("card", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
surface("card", modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 8.dp))
surface("card", modifier = Modifier.paddingTop(4.dp))     // convenience: top only
surface("card", modifier = Modifier.paddingBottom(4.dp))  // convenience: bottom only
```

**Note**: `Modifier.padding()` in Awake adds insets **inside** the widget boundary (like Compose) — it reduces the content area, not the outer slot size.

---

### `withSizeFallback(fallbackWidth, fallbackHeight)` *(internal)*

**Compose equivalent**: None — this is an Awake-internal utility.

Sets `widthDimension`/`heightDimension` **only** when not already set by the caller. Used by widget implementations to express their natural default size while letting an authored modifier override it.

**Status**: Internal — do not use in recipes or app code. Widgets in `ui-headless` use it; callers use the named overloads above.

---

## Graphics Layer Modifiers ([`GraphicsLayer.kt`](../../awake/ui/ui-core/src/commonMain/kotlin/com/awakekt/awake/ui/modifier/GraphicsLayer.kt))

### `graphicsLayer(effect: UiGraphicsEffect)`

**Compose equivalent**: `Modifier.graphicsLayer { ... }` — a configuration lambda that sets `alpha`, `scaleX`, `scaleY`, `rotationZ`, `clip`, `shape`, `shadowElevation`, etc. on a `RenderNode` layer.

**Awake**: `graphicsLayer(effect)` attaches a `UiGraphicsEffect` to `UiModifier.graphicsLayer: UiGraphicsLayer`. Multiple effects compose by appending to the list (`UiGraphicsLayer.then(other)`). Two real effects exist today:

| Effect | Set by | Applied where |
|---|---|---|
| `UiAlphaEffect(alpha)` | `Modifier.alpha(v)` | CPU-side, at `UiContext` primitive emission — multiplies every emitted primitive's color alpha channel |
| `UiScaleEffect(sx, sy, pivotX, pivotY)` | `Modifier.scale(sx, sy, ...)` | GPU-side, via `UiPrimitiveTransform` in the vertex shader |
| `UiShimmerEffect` | `Modifier.shimmer` property | Marker only — consuming widget draws the sweep |

**Status**: ⚠️ **Diverges (partially)** — alpha and scale are real. Rotation, shadow elevation, clip transform, and blend modes are not implemented.

```kotlin
// ✅ Alpha — fades the widget and all its children
button("ghost", modifier = Modifier.alpha(0.4f))

// ✅ Scale — uniform scale around the widget's pivot
button("hover", modifier = Modifier.scale(1.05f))

// ✅ Scale with explicit pivot (e.g. center of a 48×48 button)
button("press", modifier = Modifier.scale(0.95f, pivotX = 24f, pivotY = 24f))

// ❌ Not implemented — rotation
button("rotate", modifier = Modifier.graphicsLayer(/* rotationZ = 45f */))  // no-op
```

**Known gap**: The active clip region does **not** transform with scaled content — clip/scissor rects stay axis-aligned. Deferred.

---

### `alpha(value)` *(shorthand)*

Shorthand for `graphicsLayer(UiAlphaEffect(value.coerceIn(0f, 1f)))`.

**Compose equivalent**: `Modifier.alpha(value)` — identical ergonomics, different implementation (Compose: RenderNode layer alpha; Awake: CPU multiply at emission time). Behavior is visually equivalent for a uniformly faded subtree; not equivalent for overlapping semi-transparent children within one faded group.

```kotlin
text("disabled", modifier = Modifier.alpha(0.38f))
```

---

### `scale(scaleX, scaleY, pivotX, pivotY)` *(shorthand)*

Shorthand for `graphicsLayer(UiScaleEffect(...))`.

**Compose equivalent**: `Modifier.scale(scaleX, scaleY)` — applied via `graphicsLayer { scaleX = ...; scaleY = ... }`.

```kotlin
icon(modifier = Modifier.scale(1.2f))                         // uniform scale
icon(modifier = Modifier.scale(scaleX = 1.5f, scaleY = 1.0f)) // non-uniform
```

---

## Style Modifiers ([`StyleModifiers.kt`](../../awake/ui/ui-core/src/commonMain/kotlin/com/awakekt/awake/ui/modifier/StyleModifiers.kt))

These are **visual** conveniences that set a `Style` on `UiModifier.styleable`. They do not affect layout geometry.

### `background(color)`

**Compose equivalent**: `Modifier.background(color)`.

```kotlin
surface("highlight", modifier = Modifier.background(Color.Blue))
```

### `border(width, color)`

**Compose equivalent**: `Modifier.border(width, color, shape)`.

**Awake**: Sets border width and optional color via `Style`. Shape is set separately via `Modifier.shape(r)` or the widget's own style.

```kotlin
surface("outlined", modifier = Modifier.border(1.dp, Color.Gray))
```

### `shape(radius)`

**Compose equivalent**: Part of `Modifier.clip(RoundedCornerShape(radius))` + `Modifier.background(color, shape)`.

**Awake**: Sets `Style { shape(radius) }` on the modifier's `styleable`. The consuming widget's surface automatically activates `clip(effectiveShape, slot)` when the resolved shape radius > 0.

```kotlin
surface("rounded", modifier = Modifier.shape(8.dp))
```

### `styleable(style)` *(advanced)*

Merges a full `Style` block into the modifier. Used when multiple style rules need to be set in one call without individual convenience functions.

```kotlin
surface("custom", modifier = Modifier.styleable(Style {
    background(Color.DarkGray)
    borderWidth(1.dp)
    shape(4.dp)
}))
```

### `shimmer` *(marker property)*

Sets `UiShimmerEffect` on the graphics layer. The consuming widget (e.g. a skeleton placeholder) reads `modifier.shimmer` and draws its own sweep animation.

```kotlin
// Skeleton loading placeholder
surface("skeleton", modifier = Modifier.shimmer.width(120.dp).height(16.dp))
```

---

## Scroll Modifiers ([`ScrollModifiers.kt`](../../awake/ui/ui-core/src/commonMain/kotlin/com/awakekt/awake/ui/modifier/ScrollModifiers.kt))

### `verticalScroll(state, config)` / `horizontalScroll(state, config)`

**Compose equivalent**: `Modifier.verticalScroll(rememberScrollState())` / `.horizontalScroll(...)`.

**Awake**: Sets `scrollState: UiScrollState` and `scrollConfig: UiScrollConfig`. Activates the `scrollPanel()` branch in `smartColumn()`, which replaces the default `resolveMeasuredColumn()` layout with a scroll-offset-shifted variant.

**Status**: ⚠️ **Diverges** — two key differences:

1. **Chaining is broken**: `Modifier.verticalScroll(v).horizontalScroll(h)` silently overwrites — only the last call's `scrollState`/`scrollConfig` is kept (both functions write the same two fields). Compose's two modifiers are independently composable via nested containers.

2. **Default styling divergence (fixed)**: Previously, a bare `column(modifier = Modifier.verticalScroll(state))` rendered as a themed card (border + card background) because `scrollPanel()` unconditionally applied `defaults = currentTheme.components.surface`. Fixed: the default is now a neutral `Style { shape(UiShape.md); borderWidth(UiShape.none) }` matching a bare `column()`'s invisible default.

```kotlin
// ✅ Vertical scroll
val scrollState = rememberScrollState("my-list")
column(modifier = Modifier.verticalScroll(scrollState)) {
    repeat(50) { text("item $it") }
}

// ❌ Don't chain both — horizontalScroll overwrites verticalScroll
column(modifier = Modifier.verticalScroll(v).horizontalScroll(h))  // only h survives
```

---

## Click / Interaction Modifiers ([`ClickableModifiers.kt`](../../awake/ui/ui-core/src/commonMain/kotlin/com/awakekt/awake/ui/modifier/ClickableModifiers.kt))

### `clickable(enabled, onClick)`

**Compose equivalent**: `Modifier.clickable(enabled, onClick)` — works on any composable automatically, because Compose's pointer-input pipeline is wired to every layout node by the framework.

**Awake**: Sets `clickAction: UiClickable(enabled, onClick)`. **This does not automatically make every widget clickable.** The widget must explicitly call `resolveClickable(id, slot, modifier)` after claiming its slot.

**Status**: ⚠️ **Diverges (opt-in only)** — works on `surface()` today; `button`/`checkbox`/etc. manage their own click detection separately via `interact()` and are not migrated.

**Click semantics (faithful)**:
- Press and release must both occur inside the widget bounds.
- Keyboard activation: Space bar fires `onClick` when the widget is focused.
- Pointer click also claims keyboard focus automatically.

```kotlin
// ✅ Works on surface() — it calls resolveClickable() internally
surface("tile", modifier = Modifier.clickable { doSomething() }) {
    text("Click me")
}

// ❌ Does NOT work on text() — text() doesn't call resolveClickable()
text("click", modifier = Modifier.clickable { doSomething() })  // silently ignored
```

---

## State / Testing Modifiers ([`StateModifiers.kt`](../../awake/ui/ui-core/src/commonMain/kotlin/com/awakekt/awake/ui/modifier/StateModifiers.kt))

### `forceHover(value)` / `forceActive(value)` / `forceFocus(value)`

**Compose equivalent**: None — these are Awake-original testing/previewing utilities.

Force a specific interaction state regardless of actual input, so tests and previews can render hover, pressed, or focused visuals without simulating real pointer/keyboard events.

**Status**: ✅ No Compose analog — Awake-original.

```kotlin
// Force hovered state for screenshot testing
button("hovered", modifier = Modifier.forceHover())

// Force pressed state for a visual design preview
button("pressed", modifier = Modifier.forceActive())

// Force keyboard-focused state
input("focused", modifier = Modifier.forceFocus())
```

### `testTag(tag)`

**Compose equivalent**: `Modifier.testTag(tag)` — attaches a string ID to a node for test queries (`onNodeWithTag(...)`).

**Awake**: Sets `UiModifier.testTag`. Read by the test harness via `UiSemantics` / `UiTestHelper.findByTag()`.

**Status**: ✅ **Faithful** — same purpose and shape.

```kotlin
button("submit", modifier = Modifier.testTag("submit-button"))

// In tests:
// ui.findByTag("submit-button").assertExists()
```

---

## Layout DSL — `row()` / `column()` / `box()` / `Arrangement`

Source: [`Row.kt`](../../awake/ui/ui-core/src/commonMain/kotlin/com/awakekt/awake/ui/layouts/Row.kt),
[`Column.kt`](../../awake/ui/ui-core/src/commonMain/kotlin/com/awakekt/awake/ui/layouts/Column.kt),
[`Box.kt`](../../awake/ui/ui-core/src/commonMain/kotlin/com/awakekt/awake/ui/layouts/Box.kt),
[`Arrangement.kt`](../../awake/ui/ui-core/src/commonMain/kotlin/com/awakekt/awake/ui/layouts/Arrangement.kt).
Companion to [`mirror-map.md`](mirror-map.md)'s Scope/DSL section (status table); this section is
the how-to.

### Real defaults — and the one that surprised even this doc

| Param | Awake default | Compose's real default | Match? |
|---|---|---|---|
| `row()`'s `verticalAlignment` | `UiAlignment.Vertical.Top` | `Alignment.Top` | ✅ matches |
| `column()`'s `horizontalAlignment` | `UiAlignment.Horizontal.Start` | `Alignment.Start` | ✅ matches |
| `row()`'s `horizontalArrangement` | `defaultArrangement()` = **`Arrangement.spacedBy(8f.dp)`** | `Arrangement.Start` (packed, zero gap) | ❌ does not match |
| `column()`'s `verticalArrangement` | `defaultArrangement()` = **`Arrangement.spacedBy(8f.dp)`** | `Arrangement.Top` (packed, zero gap) | ❌ does not match |
| `box()` with no size modifier | `Dimension.FillMax` × `Dimension.FillMax` (fills parent) | shrink-wraps to children's measured size | ❌ does not match |

Verified against `defaultArrangement()`'s real body (`Arrangement.kt:130`):

```kotlin
fun defaultArrangement(): Arrangement = Arrangement.spacedBy(8f.dp)
```

Every `row()`/`column()` overload defaults to this. A Compose developer writing `row { text("a");
text("b") }` expects the two packed together with no gap (Compose's real default). In Awake, that
call renders with an **8dp gap** neither author asked for -- a real layout surprise with no
compile-time signal. If you want Compose's actual default (packed, no gap), say so explicitly:

```kotlin
// ✅ Explicit -- matches what a Compose Row/Column with no arrangement arg actually does
row(horizontalArrangement = Arrangement.Start) { icon(); text("label") }
column(verticalArrangement = Arrangement.Top) { text("title"); text("subtitle") }

// ⚠️ Implicit -- compiles, renders, but silently adds an 8dp gap a Compose port would not have
row { icon(); text("label") }
```

`box()`'s default size is the same shape of surprise on a different axis: `UiPrimitiveScope.box()`
claims its slot via `modifier.withSizeFallback(Dimension.FillMax, Dimension.FillMax)` (`Box.kt`) --
a bare `box { }` with no `.width()`/`.height()` **fills its parent** on both axes, where Compose's
`Box` with no size modifier shrink-wraps to its children instead:

```kotlin
// Compose mental model: this Box is exactly as big as the text inside it
// Awake reality: this box fills its entire parent unless you constrain it
box { text("small label") }

// ✅ If you actually want shrink-to-content sizing, say so with WrapContent (containers that
// support it -- see the css-modifier skill's `w-auto` row for which widgets can)
box(modifier = Modifier.width(Dimension.WrapContent).height(Dimension.WrapContent)) {
    text("small label")
}
```

### `Arrangement` — the real value set

```kotlin
sealed interface Arrangement {
    data class SpacedBy(val space: Dp) : Arrangement
    data object Start : Arrangement
    data object Center : Arrangement
    data object End : Arrangement
    data object SpaceBetween : Arrangement
    data object SpaceEvenly : Arrangement
    data object SpaceAround : Arrangement
}
```

All six named values plus `spacedBy(dp)` are real and match Compose's own free-space-division
formulas (`Arrangement.plan()`, verified line by line): `Center` splits leftover space evenly
before/after, `SpaceBetween` divides it strictly between children (none at the edges),
`SpaceEvenly` divides it into `childCount + 1` equal gaps including both edges, `SpaceAround`
gives each child a half-gap at its own edges and a full gap between (so edge gaps are half the
between-gap). `SpaceEvenly` in particular is present and correct in current source --
double-check any older note claiming otherwise against `Arrangement.kt` directly before trusting
it.

```kotlin
row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
    icon(); text("centered label"); icon()
}
row(horizontalArrangement = Arrangement.spacedBy(12f.dp)) { icon(); text("label") }
```

### The trial-measure model's real consequence for callers: content can run more than once per frame

This is the thing a Compose developer will get wrong without reading this section. Compose's
`Row`/`Column` measure each child's composable body **exactly once** per composition via
`Constraints` negotiation -- a composable's body never re-executes just to "check its size" first.
Awake's `row()`/`column()` do not work that way: any container with a `WrapContent` axis, an
`Arrangement` value that needs measured distribution (`Center`/`End`/`SpaceBetween`/`SpaceEvenly`/
`SpaceAround` -- see `Arrangement.requiresMeasuredDistribution()`), or an as-yet-unproven
`weight()`-using child pays a **throwaway trial pass** that re-executes `content` before the real
placement pass runs it again. See `mirror-map.md`'s Scope/DSL section (the "Compose's
`Constraints`-based measure/layout pass" row) for the full mechanism, caching layers, and fix
history -- this section only covers what it means for code you write *inside* a `row {}`/`column
{}` body.

**The rule: never put an unguarded side effect directly in `row {}`/`column {}`/`box {}` content.**
A `println`, an analytics call, a callback invocation, or incrementing a plain captured `var` will
fire more than once per real frame whenever a trial pass runs -- silently, with no error, no
warning, and no visual symptom until someone notices a counter is off or an analytics event fired
twice.

```kotlin
// ❌ Unsafe -- this callback can fire 2-3x in one real frame if this row's parent
// triggers a WrapContent/weight trial, since `content` re-executes for each trial
row(modifier = Modifier.width(Dimension.WrapContent)) {
    onVisible()   // a plain, unguarded side effect
    text("hello")
}

// ✅ Safe -- state hooks already carry the same isMeasuringInternal() guard animateFloat* has
// (see mirror-map.md's State hooks section and compose-animation-guidance.md's guard section)
row(modifier = Modifier.width(Dimension.WrapContent)) {
    var seen by rememberBooleanState("$id.seen")
    if (!seen) { seen = true; onVisible() }   // the write is dropped during any trial pass
    text("hello")
}

// ✅ Also safe -- an explicit guard around a one-off effect that doesn't go through a state hook
row(modifier = Modifier.width(Dimension.WrapContent)) {
    if (!context.isMeasuringInternal()) onVisible()
    text("hello")
}
```

**When content is most likely to run exactly once (the fast path)**: both axes `Fixed`/`FillMax`
(no `WrapContent`), `Arrangement.Start` or `Arrangement.spacedBy(...)` (not `Center`/`End`/
`SpaceBetween`/`SpaceEvenly`/`SpaceAround`), no `weight()`-tagged child, and either this is not the
node's first frame (the "remembered `hasWeightedChild`" answer from last frame short-circuits the
detection trial) or you opted into the explicit `id`/`cacheKey` cross-frame cache. None of these
are a caller-visible compile-time guarantee -- treat "might run more than once" as the safe default
assumption for any `row {}`/`column {}` body, and reach for a trial-safe primitive
(`rememberStateValue`-family hooks, `animateFloat*`) instead of hand-rolled state whenever the
content needs to remember or effect anything. Widgets built from `row`/`column`/`box`/`surface`
already suppress a composite child's own re-recording during this window
(`withMeasuredRecordingSuppressed`) -- that protects an ancestor's layout math, not your own side
effects, which is exactly the gap this section covers.

**Opt-in `id`/`cacheKey` cross-frame cache**: `row()`/`column()` (and their per-scope wrappers)
accept optional `id`/`cacheKey` parameters that skip the weighted-child detection trial entirely
on a cache hit, once a caller supplies a stable `id` and a `cacheKey` that changes only when the
content's `.weight()`-usage *structure* could change. This is a distinct, unrelated identity pair
from a stateful widget's own `id` -- see `docs/reference/ui-ownership.md`'s "Identity Params: `id`
vs `testTag` vs `cacheKey`" table for the full three-way distinction before reaching for it; it
does not reduce how many times `content` runs for `WrapContent` sizing, only for the weighted-child
check.

---

## Layout Types — `Dimension` and `UiAlignment`

### `Dimension` ([`Dimension.kt`](../../awake/ui/ui-core/src/commonMain/kotlin/com/awakekt/awake/ui/api/layout/Dimension.kt))

| Value | Meaning | Compose analog |
|---|---|---|
| `Dimension.Fixed(dp)` | Fixed pixel size | `Constraints(min = dp, max = dp)` |
| `Dimension.FillMax` | Fill available axis | `fillMaxWidth()` / `fillMaxHeight()` |
| `Dimension.WrapContent` | Size from content | `wrapContentWidth()` / `wrapContentHeight()` |

### `UiAlignment` ([`LayoutValues.kt`](../../awake/ui/ui-core/src/commonMain/kotlin/com/awakekt/awake/ui/api/layout/LayoutValues.kt))

| Awake | Used in | Compose analog |
|---|---|---|
| `UiAlignment.Vertical.Top` | `row(verticalAlignment = ...)` | `Alignment.Top` |
| `UiAlignment.Vertical.Center` | `row(verticalAlignment = ...)` | `Alignment.CenterVertically` |
| `UiAlignment.Vertical.Bottom` | `row(verticalAlignment = ...)` | `Alignment.Bottom` |
| `UiAlignment.Horizontal.Start` | `column(horizontalAlignment = ...)` | `Alignment.Start` |
| `UiAlignment.Horizontal.Center` | `column(horizontalAlignment = ...)` | `Alignment.CenterHorizontally` |
| `UiAlignment.Horizontal.End` | `column(horizontalAlignment = ...)` | `Alignment.End` |
| `UiAlignment.TopStart` / `.Center` / `.BottomEnd` etc. | `box(contentAlignment = ...)`, `Modifier.align(...)` | `Alignment.TopStart` etc. |

---

## Missing / Not-Yet-Implemented Modifiers

These exist in Jetpack Compose but have **no Awake equivalent** today:

| Compose modifier | Status | Notes |
|---|---|---|
| `Modifier.rotate(degrees)` | ❌ Not implemented | No rotation in `UiGraphicsEffect` yet |
| `Modifier.zIndex(value)` | ❌ Not implemented | Draw order is implicit (emission order) |
| `Modifier.clip(shape)` standalone | ⚠️ Partial | `surface(clipContent = true)` achieves the same; no standalone `Modifier.clip()` |
| `Modifier.shadow(elevation, shape)` | ❌ Not implemented | No shadow primitive |
| `Modifier.pointerInput { }` | ❌ Not implemented | Only `clickable()` for pointer/keyboard interaction |
| `Modifier.indication` | ❌ Not implemented | Ripple/press effects are baked into widget implementations |
| `Modifier.semantics { }` | ⚠️ Partial | `UiSemanticRole` and `testTag` exist; full semantics tree does not |
| `Modifier.focusable()` | ⚠️ Partial | Focus is implicit for `button`/`input`/`clickable` surfaces |
| `Modifier.onGloballyPositioned { }` | ❌ Not implemented | No post-layout callback |
| `Modifier.onSizeChanged { }` | ❌ Not implemented | No size-change listener |
| `Modifier.drawBehind { }` / `drawWithContent { }` | ❌ Not implemented | Use `canvas { }` escape hatch instead |

---

## Quick Reference — All Modifiers at a Glance

| Category | Modifier | Status |
|---|---|---|
| **Size** | `width(dp)`, `height(dp)`, `size(w, h)` | ✅ Faithful |
| **Size clamping** | `widthIn(min, max)`, `heightIn(min, max)` | ✅ Faithful |
| **Fill** | `fillMaxWidth()`, `fillMaxHeight()`, `fillMaxSize()` | ⚠️ Diverges (unbounded parent) |
| **Weight** | `weight(weight, fill)` | ✅ Faithful (fixed `9455bc51`) |
| **Alignment** | `align(UiAlignment)` | ✅ Faithful |
| **Offset** | `offset(x, y)` | ✅ Faithful |
| **Padding** | `padding(...)`, `paddingTop(...)`, `paddingBottom(...)` | ✅ Faithful |
| **Alpha** | `alpha(value)` | ✅ Faithful (CPU-side premultiply) |
| **Scale** | `scale(sx, sy, pivotX, pivotY)` | ✅ Faithful (GPU vertex shader) |
| **Graphics layer** | `graphicsLayer(effect)` | ⚠️ Partial (no rotate/shadow/clip-transform) |
| **Shimmer** | `shimmer` property | ✅ Awake-original |
| **Background** | `background(color)` | ✅ Faithful |
| **Border** | `border(width, color)` | ✅ Faithful |
| **Shape** | `shape(radius)` | ✅ Faithful |
| **Style block** | `styleable(style)` | ✅ Awake-original |
| **Scroll** | `verticalScroll(state)`, `horizontalScroll(state)` | ⚠️ Diverges (chaining broken, separate scroll states) |
| **Click** | `clickable(enabled, onClick)` | ⚠️ Diverges (opt-in per widget, not automatic) |
| **Force state** | `forceHover()`, `forceActive()`, `forceFocus()` | ✅ Awake-original (no Compose equiv) |
| **Test** | `testTag(tag)` | ✅ Faithful |

---

## Links

- [`UiModifier.kt`](../../awake/ui/ui-core/src/commonMain/kotlin/com/awakekt/awake/ui/modifier/UiModifier.kt) — data class definition
- [`LayoutModifiers.kt`](../../awake/ui/ui-core/src/commonMain/kotlin/com/awakekt/awake/ui/modifier/LayoutModifiers.kt) — size/fill/weight/align/offset/padding
- [`GraphicsLayer.kt`](../../awake/ui/ui-core/src/commonMain/kotlin/com/awakekt/awake/ui/modifier/GraphicsLayer.kt) — alpha, scale, shimmer, UiGraphicsEffect
- [`StyleModifiers.kt`](../../awake/ui/ui-core/src/commonMain/kotlin/com/awakekt/awake/ui/modifier/StyleModifiers.kt) — background, border, shape, styleable
- [`ScrollModifiers.kt`](../../awake/ui/ui-core/src/commonMain/kotlin/com/awakekt/awake/ui/modifier/ScrollModifiers.kt) — verticalScroll, horizontalScroll
- [`ClickableModifiers.kt`](../../awake/ui/ui-core/src/commonMain/kotlin/com/awakekt/awake/ui/modifier/ClickableModifiers.kt) — clickable, resolveClickable
- [`StateModifiers.kt`](../../awake/ui/ui-core/src/commonMain/kotlin/com/awakekt/awake/ui/modifier/StateModifiers.kt) — forceHover, forceActive, forceFocus, testTag
- [`Row.kt`](../../awake/ui/ui-core/src/commonMain/kotlin/com/awakekt/awake/ui/layouts/Row.kt) / [`Column.kt`](../../awake/ui/ui-core/src/commonMain/kotlin/com/awakekt/awake/ui/layouts/Column.kt) / [`Box.kt`](../../awake/ui/ui-core/src/commonMain/kotlin/com/awakekt/awake/ui/layouts/Box.kt) / [`Arrangement.kt`](../../awake/ui/ui-core/src/commonMain/kotlin/com/awakekt/awake/ui/layouts/Arrangement.kt) — Layout DSL section
- [`Dimension.kt`](../../awake/ui/ui-core/src/commonMain/kotlin/com/awakekt/awake/ui/api/layout/Dimension.kt) — Fixed / FillMax / WrapContent
- [`LayoutValues.kt`](../../awake/ui/ui-core/src/commonMain/kotlin/com/awakekt/awake/ui/api/layout/LayoutValues.kt) — UiAlignment, UiInsets
- [`mirror-map.md`](mirror-map.md) — complete faithful/diverges status table
- [`compose-animation-guidance.md`](compose-animation-guidance.md) — `animateFloat*`, `Easing`, `rememberTransition`, `animatedVisibility` how-to (separate doc, `awake:ui:animation` module)
- [`2026-08-21-compose-layout-modifier-parity-plan.md`](../audits/2026-08-21-compose-layout-modifier-parity-plan.md) — Phase 1–3 implementation roadmap
