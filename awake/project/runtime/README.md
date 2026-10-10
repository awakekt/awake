# `awake:project:runtime`

Plays an Awake project without the editor. It reads the project, then runs only what the entry
scene's components call for. Every speed, distance and size comes from the scene.

```kotlin
val project = loadProject(files, physicsWorld = ::createJoltPhysicsWorld)
app { scene("game") { runProject(project) } }
// when the game stops: project.close()
```

`files` is an `AssetSource` rooted at the project folder. `loadProject` reads and checks
`awake.project.json`, reads the entry scene, loads the glTF models it names, and creates a physics
world only when the scene has `physics_body` or `character_controller` components. The host picks the
physics backend, and the project owns the world: `close()` it once the scene has stopped, since a
physics world's native memory outlives garbage collection. A project is played once; its bodies stay
in its world, so load it again to play the scene again.

A node's `tag` component becomes its entity's `Tags`, which gameplay finds with `world.withTag` and
`world.hasTag` (see `awake:ecs`). A manifest may list the project's tags in `tags`, which editors
offer as choices; `loadProject` then logs a warning for a scene tag the list leaves out, a guard
against a typo rather than a refusal.

Core's capabilities decide what runs, each for the components it owns:

| Scene component | What runs |
|---|---|
| `movement_control`, `input_actions` | `PlayerInputSystem`: the keys and pointer buttons into the scene's input actions, which a player's `movement_control` follows (`move`, `jump`, `run`); moved by physics with `character_controller`, straight through the world without |
| `canvas_element` with an `action` | `CanvasActionSystem`: an element naming one of the scene's input actions adds to it, so a `move` Joystick steers, a `jump` Button jumps and a `run` Button runs as the keys do |
| `canvas_element` with a `style` image | Nothing runs: `loadProject` decodes the images, under `CoreSceneContent.CanvasImages`, and `runProject` gives them to the runtime that draws the canvas. With `hasRenderer = false` it only checks that they resolve |
| `camera_rig` | `CameraSystem` |
| `paged_terrain` | On the fixed step, before physics: its cells streamed around the primary camera from the page index `loadProject` read, drawn as one clipmap (with terrain layers when the index names a palette), and static collision cells near the camera when it is a `collider` and the host gives a physics world. A scene streams one |
| `terrain` | With a renderer, `TerrainContentSystem` draws it: with the surface its `surface` names, from the providers under `CoreSceneContent.TerrainSurfaces` (a project's content holds the layered-terrain kit's, `awake.terrain.layers`), or with the built-in terrain shading. Closing the scene's systems detaches it |
| `physics_body`, `character_controller`, a `terrain` collider | On the fixed step, with a physics world: the terrain collider, `MeshColliderSystem` for `mesh` and `convex_hull` shapes, `PhysicsSystem` and `CharacterControllerSystem` |
| `patrol`, `chase`, `flee` with a `navigation` component | The behaviours and `PathRequestSystem`, which answers their routes over the scene's grid. Each behaviour arrives with its `PathRequest`; a project with a behaviour and no `navigation` is refused at load. An agent with a `movement_control` whose `driver` is `Agent` is steered through it at the behaviour's speed, so with a `character_controller` walls stop it and it climbs slopes and steps; any other agent moves by its transform. An agent with a `character_controller` and no `Agent` control is refused at load |
| `spinControl`, `locomotion_animation`, `keyframe_animation`, `texture_clips` | Spinning, locomotion clips, looping keyframe tracks and sprite-sheet clips |
| `particle_emitter` | `ParticleContentSystem` and `ParticleSystem`, with the sprites `loadProject` read |
| `day_cycle` | `DayCycleSystem`: the sun's path and the blended sky, light and fog |
| `shader_effect` | `ShaderEffectSystem`, with the shader documents `loadProject` read |
| `audio_source` | `AudioSystem`, with the WAV clips `loadProject` read, heard from the active camera. `runProject` plays them through the platform's audio player, opened only for a scene with sound; another host passes its own to `sceneSystems(audio = …)` or `SceneHostServices.withAudio`, and with none they run silently |
| Skinned glTF model | Its first animation clip, on a loop |

One camera renders: the scene's primary camera, else its first, else a fallback looking at the
origin. `builtInSceneAssets()` provides the neutral meshes `cube`, `sphere`, `ground` and `plane` and
the `lit-shadow` material; anything else a scene draws is a project asset.

`runProject(project, touchControls = true)` shows the scene's `touchOnly` canvas elements; a host on a
touch screen passes it. The scene decides where the controls sit and how big they are.

## Playing a scene in a world of your own

An editor that plays the scene it is editing, in an isolated world, wants the same systems without
a project on disk and without an app builder. `sceneSystemsFor(scene, services)` returns them as
plain `System`s: `fixed` ones to run on each fixed step, then `frame` ones once per rendered frame,
in the order `runProject` runs them. `runProject` is built on the same decision, so a component
that gains a system gains it in both. `loadSceneContent` reads what the systems need from the
project's files, such as particle sprites, collision meshes, shader documents and a paged terrain's
index.

An editor that already holds that content, loaded as the scene was edited, passes its own instead,
under the keys in `CoreSceneContent`: `SceneContent.build { this[CoreSceneContent.CollisionMeshes] = meshes }`.
`MeshColliderSystem` needs a mesh for every `mesh` or `convex_hull` collider it meets, so such an
editor leaves out the colliders it has none for. A paged terrain's content comes only from
`loadSceneContent`.

```kotlin
val systems = sceneSystemsFor(
    scene,
    SceneHostServices(input = { gameplayInput }, renderer = renderer, physics = physicsWorld, content = loadSceneContent(scene, files)),
)
// each fixed step:        systems.fixed.forEach { it.update(world, step) }
// each rendered frame:    systems.interpolate(world, alpha); systems.frame.forEach { it.update(world, delta) }
// when the scene stops:   systems.close()
```

`alpha` is how far the frame lies between the last fixed step and the next, as the host's fixed-step
loop reports it. `interpolate` places the physics bodies there before the frame systems read them, as
`runProject` does; a host that skips it sees bodies move in fixed-step jumps. The host owns the
physics world it passes in and destroys it after `close()`.

It builds only the scene's own systems. The host still places the scene, resolves its assets, picks
the camera, resolves transforms and draws. Pass the physics world the scene needs (`physics_body`,
`character_controller`, a terrain collider or a colliding `paged_terrain`), or those systems are left
out.

## Playing a project with no renderer

A game server or a CI runner plays a loaded project with `project.sceneSystems(input)`, passing no
renderer: the systems that make GPU content, such as particle sprites and shader effects, are left
out, and everything else simulates as it does in a drawn game, streamed terrain and its collision
included. `SceneHostServices.headless(...)` builds the same services for a host that builds its own.

Load the project with `loadProject(files, hasRenderer = false)` as well (or `loadSceneContent(scene, files,
hasRenderer = false)`), so it skips what only drawing reads. The scene's canvas images are then read, to
check that their paths resolve, and a missing one is logged as it is for a drawn host, but none is
decoded: a sheet of 2048 by 1856 pixels takes 15 MB of memory and a decode, which a server would spend
on a picture nothing draws. Such a project, if it is played by `runProject` after
all, draws its canvas without those images.

The runtime brings no display with it. `awake:project` and `awake:project:runtime` reach no GPU
backend and no window module (`:awake:backend:*`, `:awake:engine:window`): the host picks the backends
a project draws and simulates with and passes them in, as it passes the physics world. `awakeVerify`
runs `verifyHeadlessRuntime`, which fails a change that puts one on that path, naming the chain.

## Loading more than one project

`loadProject(files, capabilities)` registers the project's components in the process-wide registry,
which is right for a game that plays one project. A host that opens one project after another, such
as an editor, gives each its own scope, so two projects whose capabilities use one component name for
different components never meet:

```kotlin
val registry = SceneComponentRegistry.scoped()
val project = loadProject(files, registry, capabilities)
// runProject(project) attaches the scene's components with the same registry.
```

A scoped registry holds only what is registered on it and keeps its serializers out of the global
set; its `sceneJson()` decodes only those components. Drop it with the project. A host that decodes
the project's scenes itself prepares the scope with `registry.registerProjectComponents(capabilities)`,
decodes with `SceneLoader.decode(text, registry.sceneJson())`, and instantiates with
`SceneLoader.instantiate(scene, world, registry)`. An editor that may lack the game's code decodes
with `registry.sceneJson(keepUnknownComponents = true)`, which keeps a component no capability
registers as a `SceneUnknownComponent` instead of refusing the scene; `loadProject` never does. `loadSceneContent` and `sceneSystemsFor` work on
an already-decoded scene and take no registry.

## Adding components and systems: scene capabilities

A game, or a package it depends on, adds its own components and the systems that run them as a
`SceneCapability`, the same contract Core's controls, physics, AI, particles and shader effects use:

```kotlin
object GrapplingHookCapability : SceneCapability {
    override val id = "com.example.grappling-hook"
    override val components = listOf(GrapplingHookBinding)

    override fun plan(scene: SceneDocument, plan: SceneSystemPlan) {
        if (scene.uses(SceneGrapplingHook::class)) plan.frame("grappling-hook") { GrapplingHookSystem(it.input) }
    }
}

val project = loadProject(files, capabilities = listOf(GrapplingHookCapability), physicsWorld = ::createJoltPhysicsWorld)
```

- `components` are registered before the scene decodes, so a document can name them. A scene that
  names a component no capability registers is refused, naming the component.
- `load` reads what the systems need from the project's files into `SceneContent`, under a
  `SceneContentKey` the capability declares; systems read it from `SceneHostServices.content`. It runs
  before the physics world exists.
- `plan` adds the systems the scene uses, with `plan.fixed` or `plan.frame`. A system that holds
  something to give back implements `AutoCloseable` and is closed when the scene stops.
- Core's capabilities run first, then the ones passed in, in order. Pass the same list to
  `loadProject`, or to `loadSceneContent` and `sceneSystemsFor`.
- A plugin the manifest marks `required` must have a capability with its `id`, or the load is refused.
  A published plugin's reference can also name where its code is and which object it is, so an export
  or an editor can resolve what to link; a project's own `capabilities/` module needs neither:

  ```json
  { "id": "com.example.docks", "path": "plugins/docks.awakeplugin", "required": true,
    "artifact": { "group": "com.example", "name": "docks-capability", "version": "1.2.0" },
    "capabilityClass": "com.example.docks.DocksCapability" }
  ```

  Naming them loads nothing: the capability is still linked when the game is built, and a refused
  load names the plugin's id, its capability class and its artifact.

Capabilities are linked when the game is built, like any dependency: Kotlin/Native and wasmJs cannot
load code. They are code that ships in the game; an editor plugin only edits the data they read. See
[D39](../../../docs/architecture/decisions/D39-scene-capabilities.md).
