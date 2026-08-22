# 12 — Gestures

Click alone is not enough for Stage 1: `ResizablePanelGroup`, `Slider` and `RangeSlider` all need
drag, and `ResizablePanelDragConservationTest` already guards real behaviour.

## Three passes, not one

The obvious design — one walk, deepest node first, `return true` to consume — cannot express a
parent stealing a gesture from a child. A scroll container has to take over a drag that started on
a button inside it, and by the time the button has seen the event and consumed it, the container's
turn has passed.

Compose solves this with passes, and this is a case where its shape is load-bearing rather than
incidental (`11-refinements.md` rule 1):

| Pass | Direction | For |
|---|---|---|
| `Initial` | root → leaf | An ancestor claims first. A scroll container takes the drag here. |
| `Main` | leaf → root | Normal handling. A button consumes its own click here. |
| `Final` | root → leaf | Notification after the fact — "someone else took it, reset." |

A node sees every pass and decides per pass. Consumption is a flag on the event, not a return
value, so a node can inspect what an earlier pass already took.

```kotlin
enum class PointerEventPass { Initial, Main, Final }

interface PointerInputNode : Modifier.Element {
    fun onPointerEvent(event: PointerEvent, pass: PointerEventPass)
}
```

Adding passes later would change every implementation's signature, which is the retrofit
`07-overlay-layering.md` already cost us once.

## The event

```kotlin
enum class PointerEventType { Press, Release, Move, Enter, Exit, Wheel }

class PointerEvent(
    val type: PointerEventType,
    /** Node-local, like DrawScope's coordinates -- a node never needs to know where it sits. */
    val x: Int,
    val y: Int,
    val scrollDelta: Float = 0f,
) {
    var isConsumed: Boolean private set
    fun consume()
}
```

Node-local coordinates for the same reason draw uses them: a handler that had to subtract its own
origin would break the moment it moved.

## Hit order

Reverse of paint order, because whatever is drawn last is on top and must be offered the event
first:

1. Layers, highest `LayerKind` first, then reverse declaration order within a kind
2. Children in reverse declaration order
3. The node itself

A layer with `modal = true` stops the walk rather than forwarding — that is what makes a dialog
block the content behind it.

## Capture

A node that consumes a `Press` holds the pointer until `Release`, so a drag that leaves its bounds
keeps arriving. This replaces `tryClaimActive`/`releaseActiveIfMatches`'s string-id tracking with
node identity — no ids, no collisions.

Capture also ends if the holding node leaves the tree. A reconcile that removes the node mid-drag
would otherwise strand the pointer, and every subsequent event would be delivered to something that
is no longer laid out.

## Not built

- **Multi-touch.** One pointer. Recorded rather than half-built; a second pointer changes capture
  from a field to a map and every handler's assumptions with it.
- **Long-press.** Needs a frame clock to synthesize, which arrives with `05-animation.md`.
- **Enter/Exit.** Needs last frame's hover set to diff against, which belongs to the frame loop.

## Watch

- **`isActive` today is a string id**, and `hitTest` reads emission order. Both become tree walks.
- **Drag conservation.** `ResizablePanelDragConservationTest` exists because a drag that loses or
  gains pixels across a divider is the visible failure. Port it before porting the widget.
- **Wheel vs scroll.** `onOverScrollable`/`onScrollConsumed` currently feed `UiInputOwnership`; that
  has to be derived from which node consumed the wheel event instead.
