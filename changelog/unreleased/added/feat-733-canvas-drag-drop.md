- **Canvas elements can be dragged and dropped onto one another.** `draggable` on a `canvas_element`
  picks it up once the pointer has moved a few pixels from the press -- a plain click still fires
  below that -- and shows a dimmed ghost of it and its children following the pointer while `CanvasElement.dragging`
  is true; the source itself stays put. Releasing over a `dropTarget` element, the one drawn on top where they overlap, reports the source
  through that target's `CanvasElement.dropped`; releasing elsewhere reports the release point, in
  canvas units, through the dragged element's own `CanvasElement.droppedOutside`. A drag claims the
  pointer like a click does, so gameplay never sees the presses or moves it used.
