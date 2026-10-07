# Lantern firefly

Original sample artwork generated on 2026-10-06 using
[sprite-gen](https://github.com/aldegad/sprite-gen) v2.35.0 and its Codex image route.
The generated asset is distributed under the repository's Apache-2.0 license.

The base design is a friendly lantern firefly with a rounded dark teal body, copper shell,
expressive eyes, antennae, translucent veined wings, and a golden abdomen lantern. The style is
a polished hand-painted 2D game illustration with a centered, fully visible body.

The accepted base was used as the identity reference for the four-frame idle row. The row
pipeline ran `prepare`, `gen-set --provider codex`, `extract`, `compose-atlas`, `compose-gif`,
and `inspect`. Extraction and inspection passed without warnings: all four frames are populated,
with zero edge or chroma-adjacent pixels. Visual review confirms a blink, small wing movement,
and a stable return to the open-eyed pose. The motion is a short idle pose loop.

`manifest.json.frame_layout.rows.idle` gives the atlas rectangles; the scene maps its four
regular 256×256 cells to `sprite`; the showcase driver steps `Sprite.frame` at 4 fps. The PNG retains true transparency.
`idle.gif` is a preview; the engine reads the PNG. The generated request and composition report
are retained alongside the atlas, along with the accepted `base-source.png` identity reference.
