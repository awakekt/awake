# 02 — Modifier

Supersedes the modifier half of `docs/reference/compose-modifier-layout-guidance.md`.

## Chain, not data class

`ui-core`'s `UiModifier` is a flat data class of 20 nullable fields. Consequences visible today:
`claimModifiedSlot()` has to hand-order width → min/max → align → insets → offset in one function,
and `smartColumn` has to *inspect* the modifier to pick between three container strategies.

A chain makes each concern a node that wraps measurement, and ordering follows the chain.

```kotlin
interface Modifier {
    infix fun then(other: Modifier): Modifier =
        if (other === Modifier) this else CombinedModifier(this, other)

    interface Element : Modifier
    companion object : Modifier
}

interface LayoutModifierNode : Modifier.Element {
    fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult
}

interface DrawModifierNode : Modifier.Element {
    fun DrawScope.draw(drawContent: () -> Unit)
}

interface PointerInputNode : Modifier.Element {
    fun onPointerEvent(event: PointerEvent, bounds: IntRect): Boolean
}

interface ParentDataModifierNode : Modifier.Element {
    fun modifyParentData(current: Any?): Any?      // how weight()/align() reach the parent
}
```

Stateful nodes use a per-pass `ModifierNodeElement` and a retained `Modifier.Node`. The reconciler
matches an element class at the same chain position, calls `update` on the retained node, and calls
`onAttach()` / `onDetach()` exactly once at its ownership boundary. A `Modifier.Node` is not a
`Modifier.Element`, so it cannot be inserted in a chain directly; every retained implementation
must enter through its element.

```kotlin
private class PaddingElement(private val insets: UiInsets) : ModifierNodeElement<PaddingNode>() {
    override fun create() = PaddingNode()
    override fun update(node: PaddingNode) { node.insets = insets }
}

private class PaddingNode : Modifier.Node(), LayoutModifierNode {
    lateinit var insets: UiInsets
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val h = insets.horizontal.roundToPx()
        val v = insets.vertical.roundToPx()
        val p = measurable.measure(constraints.offset(-h, -v))
        return layout(p.width + h, p.height + v) {
            p.placeAt(insets.start.roundToPx(), insets.top.roundToPx())
        }
    }
}

fun Modifier.padding(all: Dp): Modifier = this then PaddingElement(UiInsets.all(all))
```

## Order is meaningful, and we say so

`Modifier.padding(8.dp).background(Red)` and `Modifier.background(Red).padding(8.dp)` differ, and
nothing warns. That is inherent to a chain — removing it would mean losing the model.

What we can do that Compose's compiler cannot: the reconciler owns the chain and can walk it.
`ModifierChainDiagnostics.enabled`, off by default, flags the known-wrong orderings —
`clickable` after `padding` shrinks the hit target; `background` after `padding` leaves an unpainted
ring. Same opt-in shape as `UiLayoutDiagnostics.allowUnplannedWeight` and
`UiWeightCacheConsistencyCheck.enabled`. Refinement row 2.

## Scopes: receiver for the scope, context parameter for the composer

`Modifier.weight()` must mean something only inside a `Row`/`Column`. That is a scope question, and
the answer is Compose's: the scope is a **receiver on the content lambda**, and all layout scopes
share one `@DslMarker`.

```kotlin
@DslMarker
annotation class LayoutScopeMarker

@LayoutScopeMarker
interface RowScope { fun Modifier.weight(value: Float, fill: Boolean = true): Modifier }

context(_: Composer)
fun Row(modifier: Modifier = Modifier, content: RowScope.() -> Unit)
```

**One marker for every layout scope, not one per scope.** `@DslMarker` makes receivers mutually
exclusive only when they *share* the annotation; a marker per scope restricts nothing. `ui-core`
already gets this right by putting `@AwakeUiDsl` on the common `UiPrimitiveScope` base.

**The scope is not a second context parameter**, and this is the load-bearing detail: `@DslMarker`
restricts implicit *receivers*. Two context parameters of different scope types would both stay
resolvable, so an outer `ColumnScope` would leak into a nested `Row` — exactly what the marker
exists to prevent.

Verified against Kotlin 2.4.10 (`LayoutScopeMarkerTest` plus an out-of-band negative compile):

- The two mechanisms coexist — a `Composer` context parameter resolves through nested receiver
  lambdas, two scopes deep.
- Cross-scope leakage is rejected: *"'fun Modifier.alignStart(): Modifier' cannot be called in this
  context with an implicit receiver."*

The rejection is a compile error, so only the first half can be a test. That is the half that can
break silently, which is the half worth guarding.

## Port map from `UiModifier`

| `UiModifier` field | Becomes | Note |
|---|---|---|
| `widthDimension`, `heightDimension` | `SizeNode` (`Modifier.width/height/size/fillMaxWidth/…`) | `Dimension.WrapContent` disappears — a loosened `Constraints` *is* wrap |
| `minWidth`/`maxWidth`/`minHeight`/`maxHeight` | `SizeNode` (`widthIn`/`heightIn`) | Applied as constraint clamps, not post-hoc |
| `layoutWeight` | `ParentDataModifierNode` | Read by Row/Column policies via `parentData` |
| `alignment` | `AlignNode` → parent data | |
| `offsetX`, `offsetY` | `OffsetNode` | Placement-only; does not affect measurement |
| `insets` | `PaddingNode` | |
| `testTag` | `SemanticsNode` | See `13-semantics.md` |
| `forceHover`/`forceActive`/`forceFocus` | Test-only overrides on the interaction node | Exist because hover is unknowable at build time in immediate mode; a retained tree reads last frame's placed bounds, so these shrink to a test affordance |
| `scrollState`, `scrollConfig` | `ScrollNode` | See `08-lazy-lists.md` |
| `graphicsLayer` | `GraphicsLayerNode` | See `10-graphics-layer.md` |
| `clickAction` | `PointerInputNode` (`Modifier.clickable`) | |
| `styleable` | **Not ported.** `Style` stays in `ui-shadcn` | See `04-styling-theme.md` |
| — | `Modifier.onPlaced { bounds -> }` | New. Replaces reading a container's returned `Rectangle`; 46 call sites need it |

## `Shape` unifies `background`/`border`/`clip`

`compose:ui` provides `Shape`/`ShapeOutline`, `RectangleShape`, `RoundedCornerShape`, and
`CircleShape`; `background(color, shape)`, `border(width, color, shape)`, and `clip(shape)` consume
the same outline. Existing `cornerRadius: Dp` overloads remain compatibility wrappers around a uniform
`RoundedCornerShape`. A per-corner `RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)` falls
back to the established path pipeline, while rectangle and uniform rounded shapes retain the cheaper
quad paths. `clipToBounds()` remains the explicit rectangular fast alias.

## What consumers stop writing

No `id: String` on a modifier — node identity is positional. No `cacheKey`. No `withSizeFallback`.
No container inspecting a modifier to choose a strategy.

## Retained node state

The chain is rebuilt on every pass, so a `Modifier.Element` instance lives exactly one frame.
`ModifierNodeElement` splits that per-pass element from a retained node and reconciles them with
`create`/`update`.

The consequence is not theoretical. `clickable` originally recorded the press on the node instance,
and a click spanning two frames -- which every real click does -- never fired, because the instance
that saw the release was not the one that saw the press. Every test passed, because none of them
reconciled between the two.

Anything that has to outlive the pass belongs in a `Modifier.Node`, or in one of these existing
owners:

| Where | For |
|---|---|
| The `LayoutNode` | Anything derived from the tree -- focus, `remember` slots |
| A caller-owned object held with `remember` | `InteractionSource`, and anything a styling layer reads |
| The dispatcher | Gesture bookkeeping, exposed on the event as `isCaptureHolder` / `isInBounds` |

The retained split is proven by `ModifierNodeLifecycleTest`: updates preserve identity, replacement
detaches before attaching, and removing a subtree detaches every retained modifier node. The
migration inventory and completion criteria live in
`docs/tasks/2026-08-27-compose-modifier-node-lifecycle-plan.md`.
