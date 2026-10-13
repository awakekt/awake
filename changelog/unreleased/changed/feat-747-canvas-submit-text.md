- **A canvas `TextField`'s Enter behaves one frame earlier.** `CanvasElement.consumeSubmit()` now
  returns the submitted line (`String?`, `null` when nothing was submitted) instead of a `Boolean`,
  taken from the field's live state at submit time so a character typed the same frame Enter is
  pressed is included, not just what `text` held as of the frame's last composition. An Enter whose
  down event focused the field -- a system reading that same Enter to open a chat window, say --
  no longer also submits the field it just opened; only a later Enter does.
