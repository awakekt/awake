# Woodland rivals

Original sample art generated on 2026-10-07 with the built-in Codex image generation tool.
The generated art is distributed under this repository's Apache-2.0 license.

The hero is a teal-cloaked woodland ranger with a saffron scarf and short sword. The enemy is
an amber-eyed purple thorn beast with pale crystal spines. Both use original pixel-art-inspired
three-quarter designs. Each was generated as four idle poses; the idle loops use only the first
one. These are idle loops, not walk or attack animations. The enemy is mirrored in scene data to
face the hero.

The original transparent source sheet is preserved in `source-sheet.png`. Sprite-gen v2.35.0
(commit `47e985b6320eb000e587632c2cd62a98b1bc5c99`) imported the generated poses through
`slice-sheet`, `unpack-atlas --pngs-dir`, `compose-atlas`, `compose-gif` and `inspect`.
The packing workflow normalized each pose to a 160-pixel main-body height on a transparent
256-by-256 cell, with the feet baseline at pixel 232. Tiny disconnected noise was removed by the
slicer's default component filter. Existing alpha was retained: key distance, fringe reach and
spill processing were disabled. No new artwork was painted by the packing tools.

Four separately generated poses played in turn redraw the hood, scarf and sword every frame, so
the loop flickered rather than breathed. The idle loops are therefore baked from one pose with
sprite-gen's Breathe, its recipe for a standing pose. The first pose of each character was linked
into twelve frames and given `breathe = {"depth": 0.02, "depth_x": 0, "breaths": 1, "lag": 0}` in
`curation.json`, then recomposed with `compose-atlas`: a shallow, vertical-only breath with no
lag, so the torso rises two to four pixels as one piece instead of rippling. Breathe maps whole
rows and columns, so the head stays the same in every frame and no pixel is resampled. Before the bake, alpha was
snapped to fully opaque or fully clear at 128 (the generated body had alpha near 252 and a faint
halo); that is the only step outside sprite-gen.

The composed atlas is 3072 by 512 pixels: `hero-idle` occupies row zero and `enemy-idle` row one,
twelve 256-by-256 cells each. Both run at 8 fps with uniform 125 ms durations, one breath per
1.5-second loop. Every cell contains artwork and leaves clear borders, and no pixel is partly
transparent. Sprite-gen inspection reports no errors or warnings. The imported PNG run has no raw
row-generation cache; composition uses the imported extracted frames.

The generation prompt, packing recipe and curation sidecar are retained alongside the manifest and
previews.
The engine loads `manifest.json`, verifies the decoded PNG dimensions, and imports dimensions and
named clips into the RPG scene. Runtime animation uses standard `sprite_clips`.
