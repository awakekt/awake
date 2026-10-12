- **A canvas element's hover state and its box on screen.** `CanvasElement.isHovered` is true while
  the pointer is over a Button, Image, Panel, Text, Bar or TextField; a non-button's hover is a
  passive observer that never swallows a tap meant for something else at the same point.
  `CanvasElement.screenBounds` is where every kind, Joystick included, was placed last frame in the
  canvas's own units, in canvas coordinates even for a child, and null when it was not drawn. A
  system can place a tooltip at a hovered item's `screenBounds` by setting a root element's
  `offsetX`/`offsetY` from it. `Modifier.hoverable` gains a `blocksHitTest` parameter for this.
