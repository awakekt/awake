- **A canvas TextField can be a password field.** `canvas_element` and `CanvasElement` gain
  `mask: String = ""`; a TextField with a one-character mask draws it once for each typed character
  while `text` keeps what was typed, so the caret, selection and clicks follow the masked string,
  copy and cut yield nothing, and accessibility services and the platform keyboard see a password.
  Clearing `mask` at runtime shows the password. A longer mask, or one on another kind, is reported.
