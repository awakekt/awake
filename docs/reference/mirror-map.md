# Mirror Map: Awake Compose Engine vs. Jetpack Compose

`:awake:compose:runtime`, `:awake:compose:ui` and `:awake:compose:foundation` are a retained,
Compose-shaped engine: `Modifier` chains, `Row`/`Column`/`Box`, `remember`, `mutableStateOf`,
single-pass `Constraints` layout. This page is the quick status table for a contributor about to
reach for a Compose idiom. It has three states:

- **Faithful**: same ergonomics and behaviour a Compose developer expects.
- **Diverges**: looks Compose-shaped but behaves differently. The dangerous category, since it
  passes a glance-review and then causes a layout or render bug later.
- **Not implemented**: a real gap.

The authoritative, jar-verified parity matrix is
[`compose-engine/15-compose-parity.md`](compose-engine/15-compose-parity.md), and the per-modifier
inventory is [`compose-engine/17-modifier-parity.md`](compose-engine/17-modifier-parity.md). This
page does not repeat them; each row below links the page that holds the evidence. Rows were
re-checked against the current source under `awake/compose/{runtime,ui,foundation}/src/commonMain`
when this page was rewritten (2026-11-07); the Compose side of each row is the one stated in
those pages, not recalled here.

How-to companions: [`compose-modifier-layout-guidance.md`](compose-modifier-layout-guidance.md)
(modifiers and layout) and [`compose-animation-guidance.md`](compose-animation-guidance.md)
(animation).

## Which upstream a given API mirrors

The tables below cover *infrastructure* (modifiers, layout, state, animation, `graphicsLayer`),
and for those Compose is the right thing to check against. Components are a different question.

| Bucket | Where it lives | Upstream to check against |
|---|---|---|
| Layout, text and draw primitives: `Row`, `Column`, `Box`, `Spacer`, `FlowRow`, `FlexBox`, `LazyColumn`, `Text`, `BasicTextField`, `Canvas`, `Image` | `:awake:compose:foundation` | `compose-foundation` |
| Overlay hosts: `Layer(kind, ...)` | `:awake:compose:ui` | `compose-ui` (`androidx.compose.ui.window`) |
| Controls: button, checkbox, switch, slider, select, combobox, dialog, popover, tabs, and the rest of the `Shadcn*` recipes | `:awake:ui:shadcn` | Radix / Base UI anatomy, via shadcn |
| Material 3 theme and `Scaffold` | `:awake:ui:material3` | Material 3 |

**Foundation contains no controls**, just as Compose Foundation has no `Button`, `Checkbox` or
`Switch`. So "does real Compose have this?" is unanswerable when aimed at a `Shadcn*` control.
For those the matching upstream is Radix, which shadcn is built on; the pinned reference id is
`shadcn-6261bd8-new-york-v4-radix-1.4.2` (see `tools/shadcn/shadcn_parity_manifest.json`).

A control whose styling is reachable only through a growing list of `*Style` parameters is missing
a *part*, not a parameter. In Radix anatomy a combobox is `Trigger`/`Positioner`/`Popup`/`Item`,
each styled where it is rendered.

## Modifier chain

Source: `compose/ui/.../Modifier.kt`, `compose/ui/.../draw/DrawModifiers.kt`,
`compose/foundation/.../layout/{LayoutModifiers,OffsetAndSizing}.kt`, `Scroll.kt`, `Clickable.kt`.
Full inventory: [`17-modifier-parity.md`](compose-engine/17-modifier-parity.md).

| Compose | Awake | Status | Detail |
|---|---|---|---|
| `Modifier` chain (`then`, `foldIn`, `foldOut`, `any`, `all`) | `Modifier` interface with the same operations | Faithful | Elements are `ModifierNodeElement`s reconciled against retained `Modifier.Node`s by chain position |
| `ModifierNodeElement` / `Modifier.Node` | Same split; `onAttach`/`onDetach` | Faithful (subset) | A `Modifier.Node` cannot be put in a chain directly; only elements can |
| `width`/`height`/`size`/`widthIn`/`heightIn`/`sizeIn`/`required*`/`defaultMinSize`/`aspectRatio` | Same names | Faithful | `Dp.Unspecified` defaults, as in Compose |
| `fillMaxWidth/Height/Size(fraction)` | Same, fraction in `(0, 1]` | Faithful | Real `Constraints` propagation: on an unbounded axis it has no effect |
| `wrapContent*`, `offset`, `absoluteOffset`, `padding` | Same names and overloads | Faithful | `offset` mirrors in RTL; `absoluteOffset` does not |
| `weight(w, fill)`, `align`, `alignBy` | `RowScope`/`ColumnScope` members; `BoxScope.align`, `matchParentSize` | Faithful | Unweighted children measure first, weighted children split the slack, each measured once |
| `background`, `border`, `clip(shape)`, `clipToBounds` | Same, shape-aware | Faithful | `background` paints, `clip` clips, separately. See `15-compose-parity.md` "Shape" rows |
| `border` per side | `Modifier.border(..., sides: BorderSides)` | Not in Compose | Awake addition for joined controls |
| `alpha`, `scale` | `ui.draw`, per-primitive multiply / CPU affine | Diverges | Not an isolated layer: two overlapping children under one `alpha` double-darken where they overlap. `scale` composes when nested |
| `graphicsLayer { }` | `graphicsLayer(alpha, scaleX, ..., rotationDegrees, blendMode, renderEffect, shadowElevation, shape)` and a `GraphicsLayerScope` block overload | Diverges (subset) | Real offscreen composition on Vulkan and WebGPU; some blend modes and generic-shadow gradients are missing. See [`10-graphics-layer.md`](compose-engine/10-graphics-layer.md) |
| `Modifier.rotate`, `shadow`, `indication`, `pointerInput`, `onGloballyPositioned` | none as such | Not implemented | Rotation and elevation go through `graphicsLayer(rotationDegrees = ..., shadowElevation = ...)`, and `dropShadow` exists; `pointerInput` is a coroutine API, an explicit non-goal. Reasons in `17-modifier-parity.md` |
| `zIndex` | `Modifier.zIndex(Float)` | Faithful | Same-parent paint order and hit-test priority |
| `verticalScroll(state)`, `horizontalScroll(state)` | Same names, `ScrollState`; `enabled` flag | Faithful (subset) | Content measures on an unbounded main axis and the node clips. No `reverseScrolling` or `flingBehavior`. One `ScrollElement` per call, parameterised by `Orientation` |
| `clickable(interactionSource, onClick)` | Same | Faithful | Consumes on the `Main` pass, so an ancestor scroller gets first refusal |
| `combinedClickable` | `combinedClickable(onClick, onLongClick)` | Faithful (subset) | No double-click parameter |
| `testTag`, `semantics`, `clearAndSetSemantics` | Same | Faithful | `testTag` is a `SemanticsProperties.TestTag` entry. See [`13-semantics.md`](compose-engine/13-semantics.md) |
| `Modifier.styleable` | `:awake:compose:foundation` `style` package | Faithful | Resolved when the chain is assembled, not by a node that layout must understand. See [`04-styling-theme.md`](compose-engine/04-styling-theme.md) |
| `Brush.horizontalGradient` / `verticalGradient` | Same factories | Diverges (subset) | Evenly spaced, full-bounds colours only: no custom stops, tile modes or gradient shadows. `linearGradient(topLeft, ...)` and `horizontal`/`vertical` two-colour factories cover the corner case |

## Layout composables

Source: `compose/foundation/.../layout/`. Contract: [`01-layout.md`](compose-engine/01-layout.md).

| Compose | Awake | Status | Detail |
|---|---|---|---|
| `Row(horizontalArrangement, verticalAlignment)` | Same signature and defaults (`Arrangement.Start`, `Alignment.Top`) | Faithful | Zero gap by default. The former immediate-mode engine defaulted to an 8 dp gap; see [`16-migration-deltas.md`](compose-engine/16-migration-deltas.md) |
| `Column(verticalArrangement, horizontalAlignment)` | Same (`Arrangement.Top`, `Alignment.Start`) | Faithful | |
| `Box(contentAlignment)` | Same (`Alignment.TopStart`); extra overload with split horizontal/vertical alignment | Faithful | Shrink-wraps to its children. A child's `align` wins over the box's |
| `Arrangement` | `Start`, `End`, `Top`, `Bottom`, `Center`, `CenterHorizontally`, `SpaceBetween`, `spacedBy` | Diverges (subset) | `SpaceEvenly` and `SpaceAround` are **not** on `Arrangement`; they exist only as `FlexJustifyContent` values for `FlexBox`. Horizontal variants have their own names (`SpaceBetweenHorizontal`, `spacedByHorizontal`) |
| `FlowRow` / `FlowColumn` | Same, with `maxItemsInEach*` and `maxLines` | Faithful (subset) | No overflow-indicator slot (deprecated upstream, needs multi-content subcomposition) |
| `FlexBox`, `Modifier.flex { }` | `FlexBox(config)` | Faithful (subset) | Mirrors AndroidX's experimental configuration shape; plans from intrinsics and measures each child once |
| `LazyColumn { items(...) }` | `LazyColumn(itemCount, modifier, state, itemContent)` on `SubcomposeLayout` | Diverges | Items measure for real, per item. No `items { }` DSL, no `key` parameter, **no `LazyRow`**. See [`08-lazy-lists.md`](compose-engine/08-lazy-lists.md) |
| `BoxWithConstraints`, `SubcomposeLayout` | Same | Faithful | |
| `Layout`, `MeasurePolicy`, `Constraints`, intrinsics | Same | Faithful (subset) | `Constraints` packs fixed 16-bit fields (65534 px cap) and saturates; `Rulers` is deferred. See `15-compose-parity.md` "Layout" |
| `Popup` / `Dialog` | `Layer(kind, ...)` into a node's `Slot.Layers` | Diverges | A layer belongs to the node that declared it, which is what an anchored popup anchors to |
| Layout invalidation | None: the whole tree composes, measures and places every frame | Diverges | O(tree) per frame. See `15-compose-parity.md` "Layout invalidation" |

## State and `remember`

Source: `compose/runtime/.../{Remember,State,RememberObserver,CompositionLocal}.kt`,
`compose/foundation/.../ScrollState.kt`, `lazy/LazyListState.kt`.

| Compose | Awake | Status | Detail |
|---|---|---|---|
| `remember { }`, `remember(key) { }` | Slots on the node; zero-, one- and two-key overloads | Diverges (subset) | No vararg form (it allocates per frame). With no compiler groups, an unkeyed call that comes or goes shifts later slots; use `rememberGroup(key) { }` or `key(value) { }` |
| Disposal on leaving composition | Slots die with the node; `RememberObserver.onForgotten()` | Faithful | The old string-keyed store never disposed; this one does |
| `mutableStateOf`, `by` delegates | Same (`State`, `MutableState`) | Faithful | Reads are tracked per `Composition` (`isInvalid`); no snapshot isolation |
| `compositionLocalOf` | Same | Faithful (subset) | No `staticCompositionLocalOf`, since there is no recomposition |
| `rememberScrollState(initial)` | `rememberScrollState(initial: Int)` | Faithful | One axis per state object, as in Compose |
| `rememberLazyListState()` | Same name, list-owned scroll state | Faithful | |
| `LaunchedEffect` / `DisposableEffect` | none | Not implemented, by design | Frame-driven, no coroutines. Node `onAttach`/`onDetach` covers disposal |
| Skipping / restart groups | none | Diverges | Content runs every frame, so a captured `var` mutated in content behaves like any per-frame side effect; keep state in `remember` |

Identity is positional. There is no `id` string, so two call sites cannot collide on one key. The
residual failure mode is the one Compose has: reordering unkeyed siblings moves state. Wrap list
items in `key(value) { }`.

## Animation

Source: `compose/foundation/.../animation/{Animate,TrailingValue,ShimmerModifier}.kt`,
`compose/ui/.../platform/FrameClock.kt`. How-to:
[`compose-animation-guidance.md`](compose-animation-guidance.md).

| Compose | Awake | Status | Detail |
|---|---|---|---|
| `animateFloatAsState(target, animationSpec)` | `animateFloat(target, durationSeconds = 0.2f)`, returns `Float` | Diverges (subset) | Linear tween only, no `AnimationSpec`, no `spring`, no easing. Retargeting restarts from the **current** value, as Compose does |
| `rememberInfiniteTransition` + `infiniteRepeatable` | `rememberLoopingPhase(durationSeconds)`, a `0f..1f` sawtooth | Diverges (subset) | No `RepeatMode.Reverse`, no iteration count; derive a ping-pong from the phase |
| `Easing`, `tween`, `spring`, `Animatable`, `updateTransition`, `AnimatedVisibility` | none | Not implemented | No easing types exist in `awake/compose` today |
| none | `animateTrailing` / `TrailingValue` | Not in Compose | A damage-trail value for health bars: rises at once, holds, then drains |
| none | `Modifier.shimmer(highlight, durationSeconds)` | Not in Compose | A `DrawModifierNode`; phase comes from `rememberLoopingPhase` |
| `withFrameNanos` | `LocalFrameClock` (`deltaSeconds`, `totalSeconds`) | Diverges | No coroutine. The delta is clamped to 0.1 s so a stalled frame is not a fast-forward |

## Tally

The numbered tables in `15-compose-parity.md` carry the current counts; counting rows here would
only drift. The rows in this page that most often cause a wrong assumption are: `alpha`/`scale`
are not isolated layers, `Arrangement` lacks `SpaceEvenly`/`SpaceAround`,
`LazyColumn` has no `items { }` or `LazyRow`, and animation is a linear tween without easing.
