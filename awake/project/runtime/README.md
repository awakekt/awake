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

Core's capabilities decide what runs, each for the components it owns:

| Scene component | What runs |
|---|---|
| `movement_control` | Keyboard intent; moved by physics with `character_controller`, straight through the world without |
| `canvas_element` with an `action` | `CanvasActionSystem`: a `move` Joystick steers, a held `jump` Button jumps |
| `camera_rig` | `CameraSystem` |
| `paged_terrain` | On the fixed step, before physics: its cells streamed around the primary camera from the page index `loadProject` read, drawn as one clipmap (with terrain layers when the index names a palette), and static collision cells near the camera when it is a `collider` and the host gives a physics world. A scene streams one |
| `physics_body`, `character_controller`, a `terrain` collider | On the fixed step, with a physics world: the terrain collider, `MeshColliderSystem` for `mesh` and `convex_hull` shapes, `PhysicsSystem` and `CharacterControllerSystem` |
| `patrol`, `chase`, `flee` with a `navigation` component | The behaviours and `PathRequestSystem`, which answers their routes over the scene's grid. Each behaviour arrives with its `PathRequest`; a project with a behaviour and no `navigation` is refused at load |
| `spinControl`, `locomotion_animation`, `keyframe_animation`, `texture_clips` | Spinning, locomotion clips, looping keyframe tracks and sprite-sheet clips |
| `particle_emitter` | `ParticleContentSystem` and `ParticleSystem`, with the sprites `loadProject` read |
| `day_cycle` | `DayCycleSystem`: the sun's path and the blended sky, light and fog |
| `shader_effect` | `ShaderEffectSystem`, with the shader documents `loadProject` read |
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
