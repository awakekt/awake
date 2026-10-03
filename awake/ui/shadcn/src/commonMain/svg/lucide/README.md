# Vendored Lucide SVGs

Unmodified inputs from the Lucide `0.469.0` release at
<https://github.com/lucide-icons/lucide/tree/0.469.0/icons>, with its ISC license in `LICENSE`.
The `com.awakekt.awake.plugin.icon-codegen` plugin turns them into the internal `LucideIcons` at
build time; nothing loads them at runtime.

To add a glyph, copy its SVG from the same pinned release into `icons/` and point a `ShadcnIcons`
entry at the generated `val`. Strokes, caps and joins stay strokes; only `circle` and rounded
`rect` are rewritten as equivalent path commands.
