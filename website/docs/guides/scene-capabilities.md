# Scene features

<p class="awake-lede">A scene feature (`SceneFeature`) adds components to scene documents and the systems that run them, so a project made in AwakeKt Studio runs a game's own gameplay with no Kotlin of its own. Core's controls, physics, AI, particles and shader effects are features too.</p>

<div class="awake-badges" markdown>
<span class="awake-badge">module: <code>awake:project:runtime</code></span>
<span class="awake-badge awake-badge--ok">Vulkan</span>
<span class="awake-badge awake-badge--ok">WebGPU</span>
</div>

`loadProject` and `runProject` run what a scene's components need. Each component's systems come from
the feature that owns it. A game adds its own components, such as a grappling hook or a lighthouse
beacon, by writing a feature and passing it in. A package can ship one for other games to use.

## Write a feature

This one adds a `lighthouse_beacon` component, reads how far each beacon pulses from a file in the project,
and runs a system that pulses every beacon:

```kotlin title="Kotlin"
--8<-- "awake/project/runtime/src/commonTest/kotlin/com/awakekt/awake/project/runtime/SceneFeatureTest.kt:capability"
```

- **`id`** is a reverse-domain id. A project's manifest names it in `plugins`, and a plugin marked
  `required` refuses to load without a feature of that id.
- **`components`** are the bindings for the components the feature adds. They are registered before
  the scene decodes, so a document can name them.
- **`load`** reads what the systems need from the project's files and puts it into `SceneContent`,
  under a `SceneContentKey` the feature declares. It runs once per load, before the physics world
  exists. Throw `IllegalArgumentException` for a scene the feature cannot run; the load is refused
  with the scene's path.
- **`plan`** adds the systems a scene uses, with `plan.fixed` for the fixed step, `plan.preAnimation`
  for pose and snapshot updates run each rendered frame before world matrices are propagated for
  animation and cameras, and `plan.frame` for each rendered frame after that. `scene.uses(type)` says
  whether any node has a component. Each system needs a
  name of its own. A system that holds something to give back, such as GPU content, implements
  `AutoCloseable` and is closed when the scene stops.
  A system that must account for every fixed step reads `services.fixedStep`: the steps each frame
  ran and the time it dropped past the scene's cap. See
  [Dropped time in a networked game](app-lifecycle.md#dropped-time-in-a-networked-game).

## Run it

Pass the feature when the project loads, in `capabilities`:

```kotlin title="Kotlin"
--8<-- "awake/project/runtime/src/commonTest/kotlin/com/awakekt/awake/project/runtime/SceneFeatureTest.kt:load"
```

Core's features run first, then yours, in the order you pass them. A host that runs a scene in a
world of its own, as an editor's Play does, passes the same list to `loadSceneContent` and
`sceneSystemsFor` instead.

A scene that names a component no feature registers is refused when it loads, naming the
component, so a missing feature is found before anything plays. An editor that opens the scene
without the game's code keeps such a component as data instead; see
[Scene documents](scene-documents.md#components-from-other-modules).

## What a feature is not

- **Not loaded at run time.** Features are linked when the game is built, like any dependency:
  Kotlin/Native and wasmJs cannot load code. Behaviour that must arrive with content, such as a
  project shared on the web, is data the engine interprets, like a
  [shader document](shaders.md#ship-a-shader-with-a-project).
- **Not an editor plugin.** A feature is code that ships in the game. An editor plugin only edits
  the data a feature reads, so a game plays the same in AwakeKt Studio as in an export. A package
  can ship both.

## See also

- [Scene documents](scene-documents.md) for how components are registered and decoded.
- [Scene DSL](scene-dsl.md) for writing a game's scenes and systems directly in Kotlin.
- The [`awake:project:runtime` README](https://github.com/awakekt/awake/tree/main/awake/project/runtime)
  for what each of Core's features runs.
