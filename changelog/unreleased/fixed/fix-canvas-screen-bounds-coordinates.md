- **A canvas drawn over part of the window reports its elements' boxes from its own corner.** In a
  host's view, such as an editor's game view, `CanvasElement.screenBounds` was measured from the
  window's corner rather than the canvas's, as its documentation says, so a dragged element's ghost
  drew that far past the pointer, `droppedOutside` reported a point that far off, and a tooltip a
  system placed beside a hovered element landed that far away. Each is now in canvas units from the
  canvas's own top-left, the same as a canvas filling the window, which nothing changes for. A host
  that added its view's corner to compare a pointer with `screenBounds` no longer adds it.
