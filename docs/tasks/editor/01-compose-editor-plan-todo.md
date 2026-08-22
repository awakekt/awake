# Editor on compose:ui — rebuild, don't port

Drafted 2026-08-22. Status: todo, blocked on compose capabilities named below.

## Decision

`awake:editor` is written directly against `awake:compose:*`. Studio's 2600 reusable lines are
**not** ported.

Porting looks cheaper and is not. Those panels are built on `ui:ui-core`'s trial-measure model,
`Style{}` state-rule merges and the shadcn recipe layer — three things the compose engine
deliberately does not have. A port would carry the shape of the engine being replaced into the
module meant to outlive it, and then need rewriting a second time.

What carries over is the **decisions**, not the code. Studio already learned things worth keeping:

| Decision | Where it came from |
|---|---|
| Editor camera is a separate persistent entity, not the scene's authored camera | Frustum gizmo and camera preview are both degenerate when you view through the camera you are inspecting |
| Panels render once per frame, called from the shell — never from inside a resizable group's draw | A resizable group measures more than once per frame; `StudioCameraPreview` documents the symptom |
| `drawDebugLines` replaces the frame's line buffer rather than appending | Gizmo and debug-line features silently erased each other |
| Offscreen passes must null `sceneViewport` around `renderToTexture` | Otherwise the inset is clipped to the main viewport's window rect |

## What "thin" means here

Not "fewer lines". The editor owns **no content**. Everything it displays is registered by the
host application:

```
awake:editor          panels, gizmos, selection, the registry, the frame shell
                      knows about: Entity, Component, Scene, DrawCommand
                      knows nothing about: cubes, ducks, particles, any concrete component

samples/studio        registers the demo content, runs the editor
mmo:*                 registers its own content, runs the same editor
```

Three registries, and they are the entire plugin surface:

| Registry | Registers | Editor uses it for |
|---|---|---|
| Components | a component type + how to draw its inspector row | Inspector panel |
| Systems | a `System` + whether it runs in Edit, Play, or both | Mode toggle |
| Assets | a loader + a thumbnail provider per extension | Asset browser, import |

Studio's failure to have this is why `StudioPills` hardcodes wireframe/shadows/frustum toggles and
`ExampleLoader` hardcodes eight demos. Neither is reusable, and neither is a big file — the
problem is the missing seam, not the size.

## Blocked on

The editor cannot start until the compose engine grows four capabilities. From
`docs/reference/compose-engine/README.md`'s own status table:

| Editor surface | Needs | Compose page | State |
|---|---|---|---|
| Hierarchy tree | scrollable list | `08-lazy-lists` | Not started |
| Inspector fields | text input, focus | `06-focus-text-input` | Not started |
| Toolbar, pills, buttons | `clickable`, `background`, `border` | `04-styling-theme` | Not started |
| Viewport gizmo | `DrawScope`, pointer input | `10-graphics-layer`, `12-gestures` | **Landed 2026-08-22** |
| Panel layout, docking | Row/Column/Box, weight | `01-layout`, `02-modifier` | Done |
| Frame timing | frame loop | `:runtime` | Not started |

`04` is the critical one: without `clickable` there is no button, and without a button there is no
toolbar. `08` and `06` gate the two largest panels.

**Do not start the editor before `04` lands.** The alternative is hand-rolling a button on raw
pointer input in the editor module, which becomes the thing that has to be deleted when `04`
arrives.

## Stages

Each stage is independently useful and independently verifiable.

**Stage 0 — seam first, no UI.** `awake:editor` with the three registries, `EditorState`, and
selection. No compose dependency at all. Studio keeps its current UI and registers its content
through the new registries. This de-risks everything after it: if the registry shape is wrong, it
is wrong now, in a module with no rendering to unpick.

**Stage 1 — viewport.** Editor camera, transform gizmo, selection picking, debug overlays. These
need only `DrawScope` and pointer input, both landed. First compose-rendered editor surface.

**Stage 2 — panels.** Hierarchy and Inspector, gated on `08` and `06`. Toolbar gated on `04`.

**Stage 3 — assets.** Import via the asset registry, save via `scene:runtime`'s `SceneWriter`
(already moved there, 2b30b27c9). Thumbnails via `renderToTexture`, the mechanism
`StudioCameraPreview` already proves.

**Stage 4 — retire `samples/studio`'s editor code.** Only once the compose editor is at parity.
Studio becomes what its name says: a sample that registers demo content.

## Non-goals

- **No port of Studio's panels.** Stated above; repeated here because it is the decision most
  likely to be quietly reversed under time pressure.
- **No `awake:editor` before `04-styling-theme`.** Stage 0 has no UI and is exempt.
- **Not a general docking framework.** Fixed layout: viewport centre, panels left and right,
  toolbar top. Studio's current layout is fine and nobody has asked for rearrangeable docks.
- **No new module outside `awake/`.** The editor is published like everything else in `awake/`.
  Naming: `awake:editor` is the library, `samples/studio` stays the app — an `awake:studio` that
  `samples/studio` depends on reads as circular.

## Open

- **Does the editor depend on `scene:*` or invert it?** Registries need `World`, `Entity`,
  `System`. Depending on `awake:scene` is simplest and probably right, but it means the editor
  cannot edit a non-ECS document. Decide at Stage 0, where it is cheap.
- **Where does the asset registry's thumbnail cache live?** `renderToTexture` needs a `Renderer`,
  which the editor otherwise does not hold.
