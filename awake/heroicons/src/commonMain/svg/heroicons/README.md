# Vendored Heroicons SVGs

Codegen inputs, not runtime resources. `:awake:heroicons:generateImageVectors` reads `manifest.json`
and every `.svg` here and writes `HeroIcons.kt` into `build/generated/imagevector`. Nothing loads
these at runtime, which is why they live in `src/commonMain/svg/` rather than
`src/commonMain/resources/` -- resources are packaged into every consumer's jar, framework and wasm
bundle.

| | |
|---|---|
| Upstream | <https://github.com/tailwindlabs/heroicons> |
| Version | `v2.2.0` (2024-11-18) |
| Tarball SHA-256 | `42bd31001127631a20270e7bd87ac13647bfcd628dd533e2ff31497068b4f7af` |
| License | MIT, retained verbatim in `LICENSE` |

Files are copied unmodified from the release's `optimized/` tree. Adding a glyph means copying its
SVG out of **that same release** -- see `.agents/skills/awake-ui-icons/SKILL.md`. Taking one from
`master` instead leaves the vendored set spanning two upstream versions with nothing recording it,
which is the exact problem pinning a version was meant to end.
