- **A canvas text field the player types into.** `CanvasElementKind.TextField` is a single-line
  `canvas_element` drawn with `BasicTextField`: typing fills `text`, a system writing `text`
  replaces the field's content with the caret at the end, and a system setting `focused` gives or
  takes the keyboard the way a click would. Enter submits once (`consumeSubmit()`) and frees focus;
  Escape frees it without submitting. `BasicTextField` itself gains `onSubmit`, called on Enter in
  a single-line field, and `FocusRequester` gains parameterless `requestFocus()`/`freeFocus()`,
  queued and applied by `ComposeHost` at the start of its next frame.
