# D36: scene-bound editor hooks

Status: accepted (2026-10-04)

## Decision

Viewport tools, edit-time scene systems and component inspectors join `:awake:editor:contract` as
interfaces over types Core already owns. Studio keeps hosting them, as D35 requires. No new module:
the contract already depends on `:awake:ecs`, `:awake:core:math` and `:awake:core:input`, and the
only addition is `:awake:core:color`, which has no dependencies.

Studio already runs all three hooks today, through its own library types (`SceneViewportToolContribution`,
`SceneSystemsContribution`, `EditorInspector`, `EditorCommand`). This decision lifts those shapes into
Core with neutral types, so a third-party plugin can use them without linking Studio.

## The shared types

A plugin that edits the scene needs three things from the host. Each is a small interface the host
implements.

| Type | Shape | Studio implements it with |
|---|---|---|
| `EditCommand` | `label`, `apply()`, `revert()`, `mergeKey: Any?`, `absorb(earlier)` | `EditorCommand` (same shape; becomes a typealias) |
| `EditHistory` | `execute(command)`, `record(command)` for an edit already applied | `EditorHistory` |
| `SceneSelection` | `entities: Set<Entity>`, `primary: Entity?`, `select(entity?)` | `EditorSelection` plus the `EditorEntityId` to `Entity` mapping |

Every scene edit a plugin makes goes through `EditHistory`, so undo covers plugin edits the same as
built-in ones. A drag writes the live value each frame and records one command when it ends, which is
what Studio's gizmo does now.

## The hooks

| Kind | Interface | Shape |
|---|---|---|
| `ViewportTool` | `ViewportToolProvider` | `isApplicable(ctx)`, `onHover(ctx): List<OverlayLine>`, `onPointerDrag(ctx): Boolean` (true consumes the input), `onPointerUp(ctx)` |
| `SceneSystems` | `SceneSystemsProvider` | `createSystems(): List<System>`: fresh ECS systems each time the host builds its edit loop, run each frame on the edited world |
| `Component` | `ComponentInspectorProvider` | `componentType: KClass<out Any>`, `fields(scope: InspectorFieldScope, world, entity)` |

`ViewportContext` carries `world`, `selection`, `history`, the edit camera as a `Lens` with its
`viewProjection`, `clipSpace` and viewport size, and the pointer in viewport pixels, whether it is
down, and the keys held, with input the editor UI claimed already removed. `pointerRay()` gives the
pick ray through Core's `Lens.rayThroughViewport`.

`OverlayLine(start: Vec3f, end: Vec3f, color: Color)` is the contract's own line type. The host turns
it into the renderer's `LineSegment`, so the contract never depends on `:awake:engine:render:contract`.

`InspectorFieldScope` is Studio's `EditorFieldScope` moved to Core: `text`, `toggle`, `scalar`,
`slider`, `choices`, `options`, `vector`. Each field writes the value and records undo itself; the host
decides how the fields look.

## Rules the host keeps

- Studio decides which viewport tool is active, the order tools are offered in, and which keys and
  pointer gestures it reserves, such as Alt+drag to orbit.
- Edit-time systems run only on the world being edited, never on the play-mode world. A plugin that
  changes game behaviour ships runtime code instead, and the game registers it.
- Studio's built-in inspectors, such as Transform, come before plugin inspectors. When two plugins
  cover the same component, the host picks one.

## Left out until needed

- A `Fixed` phase for edit-time systems. Every current edit-time system runs per frame.
- Mesh picking. Core has ray-versus-box picking only, and tools can pick with that.
- Overlay meshes. Studio's orientation gizmo is the only mesh overlay, and it is not a plugin.
- Generic inspectors built by reflecting over component fields. Inspectors stay hand-written.

## Consequences

- Core: three providers, the shared types, `OverlayLine`, tests and an API dump, released in the next rc.
- Studio: its contribution types become typealiases or adapters over the Core interfaces. The terrain
  brush moves from its private undo stack to `EditHistory`.
- The starter plugin (`awake-plugin-template`) adds a sample tool for each hook, and Studio's
  end-to-end plugin test loads them.
