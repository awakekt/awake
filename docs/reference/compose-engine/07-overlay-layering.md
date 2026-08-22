# 07 — Overlay layering

**This page gates Stage 1's tree shape.** Build Row/Column/Box first and bolt overlays on after, and
the tree gets reworked. Design it in, from the start.

## What `ui-core` does today

Overlays are emit-order plus a clip stack: `emitOverlay` appends to a second primitive list painted
after everything else, `registerOverlayOcclusion(bounds, isModal)` tells hit-testing to stop, and
`AbstractUiScope.emitsToOverlay` threads a boolean down every scope.

Three problems that are structural, not bugs:

1. **One overlay level.** A tooltip over a dropdown inside a dialog has no way to express its
   ordering — everything lands in the same second list, ordered by emission.
2. **Occlusion is registered, not derived.** A widget must remember to call
   `registerOverlayOcclusion`, and hit-testing is correct only if it did.
3. **Overlay content still measures inline**, so a popup's size participates in its host's
   `WrapContent` trial unless something suppresses it — which is exactly why
   `withMeasuredSubtreeIsolated` exists.

## Layers as tree nodes

A layer is a node that is measured against the *viewport*, not its parent's constraints, and placed
in its own pass.

```kotlin
enum class LayerKind { Content, Popup, Dialog, Tooltip, Toast }

context(_: Composer)
fun Layer(
    kind: LayerKind,
    anchor: LayoutCoordinates? = null,   // null = viewport-anchored (dialog, toast)
    modal: Boolean = false,
    content: () -> Unit,
)
```

- **Measure.** Layers are collected during reconcile and measured after the content tree, each
  against `Constraints(0, viewportWidth, 0, viewportHeight)`. A layer's size never reaches its host's
  measurement, so `withMeasuredSubtreeIsolated` has nothing to do.
- **Place.** Anchored layers place relative to `anchor`'s resolved bounds — which, unlike today, are
  already known, because placement happens after measurement.
- **Paint.** By `LayerKind` ordinal, then by open order within a kind. Ordering is data, not the
  sequence in which someone happened to call `emitOverlay`.
- **Hit-test.** Top-down through layers before content. Occlusion is *derived* from a layer's placed
  bounds — nothing to remember to register. `modal = true` stops the walk instead of forwarding.

## Consequences to design for

- **Scroll viewports** are not layers. They clip and offset their children, and their children's
  claims must not reach an ancestor's size — which a measure policy gets for free, since a child's
  size is whatever it reports, not where a cursor ended up.
- **`emitsToOverlay` disappears** from every scope. A node either is inside a `Layer` subtree or it
  is not, and the tree already knows.
- **Focus interacts with modality.** A modal layer captures focus traversal; see
  `06-focus-text-input.md`.
- **Nested anchoring** — tooltip on a dropdown item inside a dialog — must work, and is the test
  case that proves the design. It is the case today's model cannot express.
