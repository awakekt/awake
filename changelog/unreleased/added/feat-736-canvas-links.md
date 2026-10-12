- **Clickable words in canvas text.** A `Text` element's `text` can mark a `[[key|shown words]]`
  span: "shown words" draws in `CanvasStyle.linkColor` (a default blue when unset), and a click on
  them reports `key` once through `CanvasElement.consumeLink()` -- the clickable keywords a
  consumer pack's original client shows in NPC dialogs. `[[` is reserved, and plain text with no
  span draws unchanged.
