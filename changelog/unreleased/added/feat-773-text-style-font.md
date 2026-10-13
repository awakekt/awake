- **`TextStyle` carries the font `Text` and `BasicTextField` measure and draw with.** A null `font`
  inherits `LocalFont`, same as before. `Text`, `BasicTextField`'s glyphs, its caret, its selection
  and its masked-password path all measure and paint through the resolved `style.font ?: LocalFont.current`,
  so two text nodes in one frame can each use a different font. `UiFonts.bitmap()`, `trueSans()` and
  `msdf()` are now memoized per cell size, matching `default()`, so a `TextStyle` built fresh every
  frame does not re-upload its atlas every frame.
