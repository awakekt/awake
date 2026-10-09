# Scene capabilities

<p class="awake-lede">A scene capability adds components to scene documents and the systems that run them, so a project made in AwakeKt Studio runs a game's own gameplay with no Kotlin of its own. Core's controls, physics, AI, particles and shader effects are capabilities too.</p>

<div class="awake-badges" markdown>
<span class="awake-badge">module: <code>awake:project:runtime</code></span>
<span class="awake-badge awake-badge--ok">Vulkan</span>
<span class="awake-badge awake-badge--ok">WebGPU</span>
</div>

`loadProject` and `runProject` run what a scene's components need. Each component's systems come from
the capability that owns it. A game adds its own components, such as a grappling hook or a lighthouse
beacon, by writing a capability and passing it in. A package can ship one for other games to use.

## Write a capability

This one adds a `lighthouse_beacon` component, reads how far each beacon pulses from a file in the project,
and runs a system that pulses every beacon:

```kotlin title="Kotlin"
--8<-- "awake/project/runtime/src/commonTest/kotlin/com/awakekt/awake/project/runtime/SceneCapabilityTest.kt:capability"
```

- **`id`** is a reverse-domain id. A project's manifest names it in `plugins`, and a plugin marked
  `required` refuses to load without a capability of that id.
- **`components`** are the bindings for the components the capability adds. They are registered before
  the scene decodes, so a document can name them.
- **`load`** reads what the systems need from the project's files and puts it into `SceneContent`,
  under a `SceneContentKey` the capability declares. It runs once per load, before the physics world
  exists. Throw `IllegalArgumentException` for a scene the capability cannot run; the load is refused
  with the scene's path.
- **`plan`** adds the systems a scene uses, with `plan.fixed` for the fixed step and `plan.frame` for
  each rendered frame. `scene.uses(type)` says whether any node has a component. Each system needs a
  name of its own. A system that holds something to give back, such as GPU content, implements
  `AutoCloseable` and is closed when the scene stops.

## Run it

Pass the capability when the project loads:

```kotlin title="Kotlin"
--8<-- "awake/project/runtime/src/commonTest/kotlin/com/awakekt/awake/project/runtime/SceneCapabilityTest.kt:load"
```

Core's capabilities run first, then yours, in the order you pass them. A host that runs a scene in a
world of its own, as an editor's Play does, passes the same list to `loadSceneContent` and
`sceneSystemsFor` instead.

A scene that names a component no capability registers is refused when it loads, naming the
component, so a missing capability is found before anything plays. An editor that opens the scene
without the game's code keeps such a component as data instead; see
[Scene documents](scene-documents.md#components-from-other-modules).

## What a capability is not

- **Not loaded at run time.** Capabilities are linked when the game is built, like any dependency:
  Kotlin/Native and wasmJs cannot load code. Behaviour that must arrive with content, such as a
  project shared on the web, is data the engine interprets, like a
  [shader document](shaders.md#ship-a-shader-with-a-project).
- **Not an editor plugin.** A capability is code that ships in the game. An editor plugin only edits
  the data a capability reads, so a game plays the same in AwakeKt Studio as in an export. A package
  can ship both.

## See also

- [Scene documents](scene-documents.md) for how components are registered and decoded.
- [Scene DSL](scene-dsl.md) for writing a game's scenes and systems directly in Kotlin.
- The [`awake:project:runtime` README](https://github.com/awakekt/awake/tree/main/awake/project/runtime)
  for what each of Core's capabilities runs.
