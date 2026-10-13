- **Load a TrueType or OpenType font at run time.** `UiFonts.load(bytes, name, chars, cellSize)` reads
  a `.ttf` (`glyf`, composite glyphs included) or `.otf` (CFF Type 2 charstrings with their
  subroutines) and bakes `chars`, by default `UiFonts.DEFAULT_CHARS`, the built-in atlas's own set, into
  a distance-field atlas laid out like the built-in Roboto's, so the glyph shader draws it unchanged.
  It is pure Kotlin: desktop, Android, iOS and the browser bake the same bytes from the same file, and
  no display is needed. A char the font lacks draws as `?`. Truncated, corrupt or unsupported files
  (collections, WOFF, CID-keyed CFF, CFF2) throw an `IllegalArgumentException` that names the table.
