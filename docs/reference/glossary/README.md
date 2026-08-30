# Glossary

Plain-English meanings for terms used in Awake's code, docs and tests. For people who prefer
clear technical English; it is not a language test.

| Page | Covers |
|---|---|
| [2d.md](2d.md) | UI rendering — primitives, the UI pass, layout and paint order |
| [3d.md](3d.md) | Scene rendering — pipelines, draw calls, passes, uniforms, backends |
| [physics.md](physics.md) | Collision contracts — shapes, heightfields, motion, and backend capability gaps |
| [ui-testing.md](ui-testing.md) | Test vocabulary — fixtures, snapshots, parity, probes |

## What belongs here

A term earns an entry when **the word is ordinary English but means something narrower in this
repo**, or when two nearby terms are routinely confused. `draw` versus `drawUi`, capability versus
content, declaration versus resolution — each of those has cost real time here.

A term does **not** earn an entry just for being a type name. `Mesh` means what you think; the
KDoc on the type is the right place for its detail, and duplicating it here creates a second
description to keep in step. Prefer linking the type over restating it.

Each entry says what the term means and, where it matters, what it is *not* — the confusion is
usually the reason the entry exists.
