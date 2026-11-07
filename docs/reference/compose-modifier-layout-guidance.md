# Jetpack Compose Guidance: Modifier & Layout
## Pointer for `:awake:compose:ui` and `:awake:compose:foundation` contributors

This page used to document the retired immediate-mode `UiModifier` data class and its `row()` /
`column()` / `box()` DSL. Both are gone. The layout and modifier contracts now live in the numbered
Compose Engine guides, which are the source of truth; this page is a short routing table plus the
mistakes a Compose-literate reader still makes here.

| You need | Read |
|---|---|
| `Constraints`, `Measurable`, `MeasurePolicy`, `Row`/`Column` weights, intrinsics | [`compose-engine/01-layout.md`](compose-engine/01-layout.md) |
| The `Modifier` chain, `ModifierNodeElement`, node interfaces, ordering | [`compose-engine/02-modifier.md`](compose-engine/02-modifier.md) |
| Which Compose modifiers exist, are deferred, or are N/A, with reasons | [`compose-engine/17-modifier-parity.md`](compose-engine/17-modifier-parity.md) |
| `graphicsLayer`, offscreen layers, blur, shadows | [`compose-engine/10-graphics-layer.md`](compose-engine/10-graphics-layer.md) |
| Pointer, drag, scroll, focus | [`compose-engine/12-gestures.md`](compose-engine/12-gestures.md), [`06-focus-text-input.md`](compose-engine/06-focus-text-input.md) |
| `Style`, `Modifier.styleable`, theme | [`compose-engine/04-styling-theme.md`](compose-engine/04-styling-theme.md) |
| Lazy lists | [`compose-engine/08-lazy-lists.md`](compose-engine/08-lazy-lists.md) |
| Faithful / Diverges status of a given API | [`mirror-map.md`](mirror-map.md), [`compose-engine/15-compose-parity.md`](compose-engine/15-compose-parity.md) |
| Animation | [`compose-animation-guidance.md`](compose-animation-guidance.md) |

## Where the code is

| Area | Source |
|---|---|
| `Modifier`, `ModifierNodeElement`, `Modifier.Node` | [`Modifier.kt`](../../awake/compose/ui/src/commonMain/kotlin/com/awakekt/awake/compose/ui/Modifier.kt) |
| Node interfaces (`LayoutModifierNode`, `DrawModifierNode`, `PointerInputNode`, ...) | [`ModifierNodes.kt`](../../awake/compose/ui/src/commonMain/kotlin/com/awakekt/awake/compose/ui/node/ModifierNodes.kt) |
| Draw modifiers: `clip`, `scale`, `alpha`, `zIndex`, `graphicsLayer`, `drawBehind` | [`DrawModifiers.kt`](../../awake/compose/ui/src/commonMain/kotlin/com/awakekt/awake/compose/ui/draw/DrawModifiers.kt) |
| `padding`, `width`, `height`, `size`, `fillMax*`, `wrapContent*` | [`LayoutModifiers.kt`](../../awake/compose/foundation/src/commonMain/kotlin/com/awakekt/awake/compose/foundation/layout/LayoutModifiers.kt) |
| `offset`, `absoluteOffset`, `*In`, `required*`, `aspectRatio`, `defaultMinSize` | [`OffsetAndSizing.kt`](../../awake/compose/foundation/src/commonMain/kotlin/com/awakekt/awake/compose/foundation/layout/OffsetAndSizing.kt) |
| `Row`, `Column`, `Box` | [`Layouts.kt`](../../awake/compose/foundation/src/commonMain/kotlin/com/awakekt/awake/compose/foundation/layout/Layouts.kt) |
| `RowScope`/`ColumnScope` (`weight`, `align`, `alignBy`), `BoxScope` | [`Row.kt`](../../awake/compose/foundation/src/commonMain/kotlin/com/awakekt/awake/compose/foundation/layout/Row.kt), [`Column.kt`](../../awake/compose/foundation/src/commonMain/kotlin/com/awakekt/awake/compose/foundation/layout/Column.kt), [`BoxScope.kt`](../../awake/compose/foundation/src/commonMain/kotlin/com/awakekt/awake/compose/foundation/layout/BoxScope.kt) |
| `Arrangement` | [`Arrangement.kt`](../../awake/compose/foundation/src/commonMain/kotlin/com/awakekt/awake/compose/foundation/layout/Arrangement.kt) |
| `FlowRow`/`FlowColumn`, `FlexBox` | [`Flow.kt`](../../awake/compose/foundation/src/commonMain/kotlin/com/awakekt/awake/compose/foundation/layout/Flow.kt), [`FlexBox.kt`](../../awake/compose/foundation/src/commonMain/kotlin/com/awakekt/awake/compose/foundation/layout/FlexBox.kt) |
| `verticalScroll`, `horizontalScroll`, `ScrollState` | [`Scroll.kt`](../../awake/compose/foundation/src/commonMain/kotlin/com/awakekt/awake/compose/foundation/Scroll.kt), [`ScrollState.kt`](../../awake/compose/foundation/src/commonMain/kotlin/com/awakekt/awake/compose/foundation/ScrollState.kt) |
| `clickable`, `combinedClickable` | [`Clickable.kt`](../../awake/compose/foundation/src/commonMain/kotlin/com/awakekt/awake/compose/foundation/Clickable.kt) |
| `background`, `border` | [`Background.kt`](../../awake/compose/foundation/src/commonMain/kotlin/com/awakekt/awake/compose/foundation/Background.kt) |
| `testTag`, `semantics` | [`SemanticsProperties.kt`](../../awake/compose/ui/src/commonMain/kotlin/com/awakekt/awake/compose/ui/semantics/SemanticsProperties.kt) |

## Rules that bite a Compose-literate reader

**Modifier order is meaningful, as in Compose.** Draw and pointer links honour chain position:
`padding(8.dp).background(c)` paints inside the padding, `background(c).padding(8.dp)` paints
behind it, and `padding(8.dp).clickable { }` shrinks the hit target. Both orders are correct;
choose by what you mean.

```kotlin
Row(
    Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),   // the default is Arrangement.Start, zero gap
    verticalAlignment = Alignment.CenterVertically,
) {
    Text("label", Modifier.weight(1f))                     // weight is a RowScope member
    Box(Modifier.size(24.dp).background(color))
}
```

**There is no implicit gap.** `Row` and `Column` default to `Arrangement.Start` / `Arrangement.Top`,
packed with zero spacing, as in Compose. The retired engine defaulted to 8 dp; code migrated from
it needs `Arrangement.spacedBy(...)` stated explicitly
([`16-migration-deltas.md`](compose-engine/16-migration-deltas.md)).

**`Box` shrink-wraps.** It sizes to its children, with the box's own minimum applied to itself and
not to its children. Use `Modifier.matchParentSize()` for a child that should take the box's size
without deciding it, and `Modifier.align(...)` inside a `BoxScope` to override `contentAlignment`
per child.

**`Arrangement` is narrower than Compose's.** It has `Start`, `End`, `Top`, `Bottom`, `Center`,
`CenterHorizontally`, `SpaceBetween` and `spacedBy`, with separate `*Horizontal` names where a
vertical value shares a name. `SpaceEvenly` and `SpaceAround` are `FlexJustifyContent` values for
`FlexBox`, not `Arrangement` members.

**Scroll content measures on an unbounded main axis.** `verticalScroll(state)` loosens the
constraint, reports the viewport's size and clips; a `fillMaxHeight()` child inside it has no
bounded height to fill. `ScrollState` is one axis per object; take a second state for
`horizontalScroll`.

**`alpha` and `scale` are not layers.** They apply per primitive, so two overlapping children under
one `alpha` double-darken where they overlap. Use `graphicsLayer(alpha = ...)` to isolate a subtree.

**Not present:** `Modifier.rotate` (use `graphicsLayer(rotationDegrees = ...)`), `LazyRow`,
`pointerInput` (a coroutine API; implement `PointerInputNode`), `onGloballyPositioned` (use
`onPlaced` / `onSizeChanged`). Full list with reasons in `17-modifier-parity.md`.

**Side effects in content.** Content runs once per frame and each child is measured once, so a
side effect there fires once per frame, like any frame loop. There is no trial pass to guard
against. Keep state in `remember`, not in a captured `var`.

## Adding a modifier

Match Compose's real name, parameter order and defaults, and check the jar rather than memory
(the provenance rule in [`compose-engine/README.md`](compose-engine/README.md)). Implement it as a
`ModifierNodeElement` with `create`/`update`, hold per-frame state in `remember` at the call site,
not on the node instance (the chain is rebuilt every pass), and record the verdict in
`17-modifier-parity.md`. An Awake-only modifier is labelled as such in `mirror-map.md`.
