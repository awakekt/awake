# Awake Scene

`awake:scene` is the wrapper that binds Awake's capabilities into the ECS scene graph and the scene
document above `awake:ecs`: serialized scene documents and their validation, component bindings, the
systems that run a capability over a `World`, runtime loading, and authoring helpers. It is how games,
templates and the editor describe and run a world. It is **not where a capability lives**: Awake is a
library first, and the capabilities (simulation, sampling, culling, clocks, curves) are libraries that
work without a scene.

## Modules

```text
scene:scene-core   Transform, Name, the scene graph's shared components
scene:scene3d      the scene's 3D wrapper: cameras, lights, mesh renderers, render planning
scene:physics      binds physics into the scene: physics-facing components and synchronization
scene:controls     reusable camera and movement controls
scene:runtime      document loading, scene switching, runtime/session plumbing
scene:authoring    scene/document/entity/assets DSL and convenience builders
scene              public facade
```

A scene module keeps a component, its binding and the system that applies it to the world together;
do not split all components from all systems merely because one is data and the other behavior.
`Transform` is in scene-core because it is shared, while `MeshRenderer` stays with the scene's
rendering because it binds meshes into the scene graph.

Some capabilities (terrain, sky, culling) still live in `scene:scene3d` from before the rule below.
They are not a precedent: do not extend them in place; separate them first.

## What belongs in a scene module, and what does not

Before you add to or change anything under `awake/scene/`, sort each piece:

| It is | It belongs in |
| --- | --- |
| A document schema (`Scene<X>`) and its validation, the component binding, the mapping from the schema to the capability's types, the system that runs the capability over the world, loading a document's assets | `awake:scene:<x>` |
| An algorithm, state or behaviour with an API of its own that someone could use without a scene | A module of its own outside `scene/` |

The rules:

1. **A capability module depends on no `awake:scene` module** in its main source sets. The
   `verifyCapabilityLayering` task, part of `awakeVerify`, fails on a new dependency, however it
   arrives:
   - a project dependency in a main configuration, read from the Gradle model, so one a convention
     plugin adds counts as much as one in the build file;
   - a dependency on another module that reaches a scene module, which it reports with the chain;
   - a main source file that imports or fully qualifies a type in an `awake.scene` package (comments
     and strings aside).

   Modules that broke the rule before it existed are listed as debt in `build-logic`'s
   `repository-tooling` plugin. That list only shrinks: the task also fails when an entry no longer
   applies.
2. **A capability's types never hold a `Scene*` schema type.** The scene module maps schema to
   capability types in one file, and a test fails when a capability option is neither mapped from a
   scene field nor listed as code-only, so leaving an option out of the scene file is a decision
   written down, not a slip.
3. **Extending a capability that still lives in a scene module means separating it first**, or saying
   in the PR that it is debt and why.
4. **A new option is, in order:** the capability type, the scene field, the mapping, the docs, and the
   test above.

The pattern already exists for audio: `awake:core:audio` is the capability, with no scene and no ECS
dependency, and `awake:scene:audio` is the wrapper with the components and the system. Particles
follow it too: `awake:particles` has the emitters, the simulation and the draw packets and depends on
no scene module, and `awake:scene:particles` has the `particle_emitter` schema, its binding and the
system that gives each placed emitter its `ParticleEmitter`. A scene document is the authoring
surface for Studio, projects and agents, so the wrapper deserves the same API care as the library; it
just should not contain it.

## Current use

`SceneDocument` is the serializable authored model. `SceneLoader` validates and instantiates it
into a `World`; `SceneManager` owns safe replacement and teardown of the current scene. Renderer
assets resolve after document instantiation through `SceneAssetLibrary`, which owns only the
current mesh/material factory/cache role.

`SceneAppLifecycleRuntime` is the current app-lifecycle bridge. It is supported, but new
responsibilities should follow the transition below rather than accumulating there.

## Session model

The target:

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
- A capability with an API of its own lives outside `scene/` and depends on no scene module; a scene
  module is its binding (see the section above).
- `TransformSystem` precedes render systems; `SceneSchedule` owns that order.
- Documents contain authored data, not GPU handles or generated-preview output.
- Renderer and asset/provider resolution stay outside the serialized document.
- Scene owns no `ComposeHost`; optional UI integration is composed above it.

## Related modules

- [`awake:ecs`](../ecs/README.md) — ECS storage, entities, components, and systems.
- [`awake:engine:platform`](../engine/platform/README.md) — UI-free app lifecycle contracts.
- [`awake:engine:bootstrap`](../engine/bootstrap/README.md) — app/module composition DSL.
- [`awake:engine:compose`](../engine/compose/README.md) — optional app-level UI host integration.
