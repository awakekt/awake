# D34: game UI is scene data; the design system is a style layer

Status: accepted (2026-09-30)

## Decision

Game UI that a scene carries is `awake:scene:canvas`: `canvas_element` components saved in the
scene, edited in Awake Studio, and loaded by `awake:project:runtime` without compiling code. The
shadcn design system (`awake:ui:shadcn`) stays the toolkit for editor UI and for Kotlin-written
game UI in `ui { }`, which draws over the scene's canvas. `scene:canvas` does not depend on it.

## Why

- A Studio project, exported or published, is data: a manifest, scenes and assets played by the
  same engine code everywhere. UI written as Kotlin cannot be authored by a non-coder, emitted by
  a generator, or loaded by the player.
- A UI stack must not be mandatory for core scene modules (`framework-game-boundary`, rule 4).
- It matches how engines split the job: game UI lives on scene objects edited in the scene editor
  (Unity's Canvas and UI Toolkit, Unreal's UMG, Godot's Control nodes), while the editor uses its
  own toolkit. Large games, MMOs especially, often put an HTML/CSS renderer such as Coherent
  Gameface or Ultralight behind the same split.

## Four layers

Game UI in every engine separates:

| Layer | Awake |
|---|---|
| Layout: what is on screen and where | `canvas_element` (today: anchors and offsets) |
| Style: theme and look | Not yet; the design system's tokens, supplied by the host |
| Logic: what an action does | `action` names mapped by the runtime; Blueprints later |
| Binding: widgets follow game state | Not yet; the first gap |

## How it grows

Only as a game needs it, in this order:

1. Data binding, so a Bar or Text follows a component field without code.
2. Layout containers (row, column, grid, scroll list), not only anchors.
3. Reusable UI assets, like prefabs, placed in many scenes.
4. Theming: an optional style the host supplies (for example the shadcn look) through a composition
   local, keeping the saved data portable.
5. Logic through Blueprints: a button that runs a graph.
6. Focus and gamepad navigation, localisation, draggable and saved window layouts, and virtualised
   lists, which MMO-scale UI (inventories, chat, party frames, action bars) depends on.
