# Before/after evidence: glyphs cut off by clips and layers (#1053)

Image-only branch; it shares no history with `main` and is never merged. It holds the proof sheets
for the fix in #1069 (squash `345d71c42`), linked from #1053 and #1069.

- BEFORE: `main` at `2ce8755e5`, just before the fix.
- AFTER: `main` at `1fba41861` (also contains #1070 and #1073, so each side is compared with an
  unclipped reference rendered from its own checkout).
- Case 5 (distance-field edge) also shows the fix alone at `345d71c42`.
- Rendered by the CPU snapshot raster (`rasterizeToPixelMap`). Red: ink present in the unclipped
  reference but missing from the render (lost ink, at least 8/255). Green: the clip or layer rect.
- `lost-ink-counts.tsv` holds every count; whole-frame pixels.

| File | Case |
|---|---|
| `overview.png` | One worst-case row per case, and the full count table |
| `sheet1-textfield.png` | Text field: `Åjump` / `jump`, loaded Roboto, 14 and 96 sp, density 1 and 1.5 |
| `sheet2-layer-sidebar.png` | `graphicsLayer` around `Hgjpqy\|Wolf`, and `ShadcnSidebar` |
| `sheet3-fontscale.png` | Wrapped text at font scale 0.85 and 1.3 |
| `sheet4-sdf-edge.png` | A loaded font's distance-field edge at the glyph quad |
| `sheet5-canvas-text-follower.png` | Game canvas: Text element, and a fading follower |
| `sheet6-canvas-textfield.png` | Game canvas: TextField, still cut (no inner padding; #1035) |
