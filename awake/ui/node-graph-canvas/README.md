# `awake:ui:node-graph-canvas`

The editing surface for [`awake:node-graph`](../../node-graph/README.md) documents, built on Awake
Compose: nodes at their positions, curved wires between ports, pan, zoom and selection. It is
shared by every graph editor (blueprints, state trees, shader graphs). What a node means belongs
to the caller.

## Usage

```kotlin
var graph by remember { mutableStateOf(loadedGraph) }
var selection by remember { mutableStateOf(emptySet<String>()) }
val viewport = rememberNodeGraphViewport()

NodeGraphCanvas(
    graph = graph,
    registry = registry,
    viewport = viewport,
    selection = selection,
    onIntent = { intent ->
        graph = intent.applyTo(graph) // or route through an undo stack
        if (intent is NodeGraphIntent.Select) selection = intent.nodeIds
    },
    modifier = Modifier.fillMaxSize(),
    nodeContent = { node, spec -> /* settings or a preview under the ports */ },
)
```

## Behaviour

| Gesture | Result |
|---|---|
| Drag from an output port to an input | `Connect`; replaces the wire in a single-edge input |
| Drag a connected input's wire away | `Disconnect`, or reconnects where it is dropped |
| Click a node; Shift or Ctrl/Cmd click | `Select`; toggles |
| Drag a node | `MoveNodes` for the selection, every step |
| Drag empty canvas | pans the viewport |
| Shift or Ctrl/Cmd drag on empty canvas | box select; Shift adds |
| Wheel | zooms around the pointer |
| Secondary click | `ContextMenu` at a canvas point |

- **Controlled.** The canvas never edits the graph or the selection. It emits `NodeGraphIntent`s,
  and the caller applies them, owning undo and validation. `applyTo` covers callers without a
  command stack. The viewport is the exception: the canvas pans and zooms it directly, the way a
  scrollable moves its `ScrollState`.
- **Zoom is density.** Node content is composed under a scaled `LocalDensity`, so it really lays
  out larger. Text is re-shaped rather than stretched, and a control inside a node is hit where it
  is drawn. Below `titleMinZoom` and `labelMinZoom` the text is left out, and layout does not move.
- **A node's own controls keep their events.** The canvas handles only pointer events nothing
  inside a node consumed.
- **Ports come from geometry.** They sit on fixed-height rows under the header, so wires are
  placed without waiting for the body slot to measure.

## Performance

`NodeGraphCanvasFrameProbe` ratchets bytes per frame for a 200-node graph at rest. Nearly all of
it is the engine's per-node work (text measured and glyph primitives emitted every frame). The
canvas's own share is small: idle wires redraw retained meshes, off-screen nodes are not composed,
and zoomed-out labels are not composed.

## Not here yet

- Multi-touch pinch zoom. `Modifier.transformable` pans with one finger, which would take over
  node dragging.
- Keyboard shortcuts such as delete. They belong to the host's shortcut system, which already
  has the selection.
- Minimap, groups, comments, auto-layout and copy/paste.
