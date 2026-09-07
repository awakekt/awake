# Awake Scene

`awake:scene` is the public facade for reusable ECS scene capabilities above `awake:ecs`: scene
components and systems, serialized scene documents, runtime loading, and authoring helpers.

## Capability modules

```text
scene:scene-core   Transform, Name, generic transform/spin behavior
scene:rendering    cameras, lights, mesh renderers, animation, render systems
scene:physics      physics-facing components and synchronization
scene:controls     reusable camera and movement controls
scene:runtime      document loading, scene switching, runtime/session plumbing
scene:authoring    scene/document/entity/assets DSL and convenience builders
scene              public facade
```

Components and their primary systems stay together by capability. Do not split all components
from all systems merely because one is data and the other behavior; `Transform` is in scene-core
because it is shared, while `MeshRenderer` remains with rendering.

## Current use

`SceneDocument` is the serializable authored model. `SceneLoader` validates and instantiates it
into a `World`; `SceneManager` owns safe replacement and teardown of the current scene. Renderer
assets resolve after document instantiation through `SceneAssetLibrary`, which owns only the
current mesh/material factory/cache role.

`SceneAppLifecycleRuntime` is the current app-lifecycle bridge. It is supported, but new
responsibilities should follow the transition below rather than accumulating there.

## Session model

The [scene-session simplification plan](../../docs/tasks/2026-08-25-scene-session-simplification-plan.md)
defines the target:

```text
composed app root
  ├── engine:platform       lifecycle, input, window, backend frame handoff; no UI
  ├── engine:compose        optional Compose app module
  └── SceneSession          World, document load/switch, SceneSchedule, asset/extensions
```

Compose presents a session through locals; entities do not become composables. `SceneSession` and
`SceneSchedule` now own ECS state and ordering, while `SceneAppLifecycleRuntime` remains the
compatibility lifecycle bridge. The document model will gain versioned extension payloads so private
asset, environment, animation, terrain, and generation providers can integrate without placing
their policy in Awake.

For new UI-bearing scenes, install the app-level UI module before the scene session:

```kotlin
app {
    module(sceneComposeAppModule(content = { GameHud() }))
    sceneSession {
        scene(loadScene("island.scene.json"))
    }
}
```

## Rules

- A scene system belongs in Awake only when generic and reusable; authored gameplay belongs in a
  game or sample.
- `TransformSystem` precedes render systems; `SceneSchedule` owns that order.
- Documents contain authored data, not GPU handles or generated-preview output.
- Renderer and asset/provider resolution stay outside the serialized document.
- Scene owns no `ComposeHost`; optional UI integration is composed above it.

## Related modules

- [`awake:ecs`](../ecs/README.md) — ECS storage, entities, components, and systems.
- [`awake:engine:platform`](../engine/platform/README.md) — UI-free app lifecycle contracts.
- [`awake:engine:bootstrap`](../engine/bootstrap/README.md) — app/module composition DSL.
- [`awake:engine:compose`](../engine/compose/README.md) — optional app-level UI host integration.
