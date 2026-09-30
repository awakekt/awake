# Vendored Lucide SVGs

These eight SVGs are unmodified inputs from the Lucide `0.469.0` release at
<https://github.com/lucide-icons/lucide/tree/0.469.0/icons>. The release's ISC license is
retained in `LICENSE`. `:awake:heroicons:generateLucideImageVectors` converts them to
`LucideIcons.kt` at build time; they are not packaged as runtime resources.

The source strokes, caps, joins, and paths are preserved. The generator converts only the
`circle` and rounded `rect` primitives to equivalent path commands; it does not expand strokes.
Keep additions on the same pinned release and update the manifest when upgrading it.
