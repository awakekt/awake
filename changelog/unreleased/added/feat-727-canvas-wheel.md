- **Canvas elements can take the mouse wheel.** `canvas_element` gains `wheel: Boolean = false`; a
  `canvas_element` with `wheel` set takes the mouse wheel while the pointer is over it or its
  children -- the innermost such element wins -- so the frame reports the wheel consumed and
  `GameplayInput.scrollDeltaY` reads zero instead of zooming the camera. `CanvasElement.consumeWheel()`
  reads what it collected since the last call, then zero.
