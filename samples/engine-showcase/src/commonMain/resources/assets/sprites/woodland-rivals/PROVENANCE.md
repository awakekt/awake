# Woodland rivals

Original sample art generated on 2026-10-07 with the built-in Codex image generation tool.
The generated art is distributed under this repository's Apache-2.0 license.

The hero is a teal-cloaked woodland ranger with a saffron scarf and short sword. The enemy is
an amber-eyed purple thorn beast with pale crystal spines. Both use original pixel-art-inspired
three-quarter designs. Each has four generated idle poses; these are idle loops, not walk or attack
animations. The enemy is mirrored in scene data to face the hero.

The original transparent source sheet is preserved in `source-sheet.png`. Sprite-gen v2.35.0
(commit `47e985b6320eb000e587632c2cd62a98b1bc5c99`) imported the generated poses through
`slice-sheet`, `unpack-atlas --pngs-dir`, `compose-atlas`, `compose-gif` and `inspect`.
The packing workflow normalized each pose to a 160-pixel main-body height on a transparent
256-by-256 cell, with the feet baseline at pixel 232. Tiny disconnected noise was removed by the
slicer's default component filter. Existing alpha was retained: key distance, fringe reach and
spill processing were disabled. No new artwork was painted by the packing tools.

The composed atlas is 1024 by 512 pixels: `hero-idle` occupies row zero and `enemy-idle` row one.
Both run at 4 fps with uniform 250 ms durations. All eight cells contain artwork and leave clear
borders. Sprite-gen inspection reports no errors or warnings. The imported PNG run has no raw
row-generation cache; composition uses the imported extracted frames.

The generation prompt and packing recipe are retained alongside the manifest and previews.
The engine loads `manifest.json`, verifies the decoded PNG dimensions, and imports dimensions and
named clips into the RPG scene. Runtime animation uses standard `sprite_clips`.
