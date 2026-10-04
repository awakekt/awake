# D36: scene-bound editor hooks

Status: accepted (2026-10-04)

## Decision

Viewport tools, edit-time scene systems and component inspectors are part of `:awake:editor:contract`,
typed against Core-only types, and hosted by Studio (D35). The interfaces are in the
[contract README](../../../awake/editor/contract/README.md). Two rules hold for every plugin:

1. **Every scene edit a plugin makes goes through the host's undo** (`EditHistory`). A plugin never
   keeps its own undo stack, so Ctrl+Z undoes plugin and built-in edits in one order.
2. **Edit-time systems never run in play mode.** They run only on the world being edited. Gameplay
   ships as runtime code the game registers, so the game plays the same in Studio as in an export,
   and a paid editor plugin cannot change how a game runs.

## Left out until needed

| Left out | Add it when |
|---|---|
| A fixed-timestep phase for edit-time systems | A plugin needs physics-accurate simulation while editing |
| Ray-versus-mesh picking (Core picks by bounding box) | A tool needs exact surface hits, such as decal painting |
| Mesh overlays (tools draw lines only) | A plugin needs solid handles |
| Inspectors generated from component fields | Hand-written inspectors become a burden |

None of these changes the hook interfaces, so each can be added without breaking plugins.
