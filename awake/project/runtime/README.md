# `awake:project:runtime`

Plays an Awake project without the editor. It reads the project, then runs only what the entry
scene's components call for. Every speed, distance and size comes from the scene.

```kotlin
val project = loadPlayableProject(files, physicsWorld = ::createJoltPhysicsWorld)
app { scene("game") { playProject(project) } }
```

`files` is an `AssetSource` rooted at the project folder. `loadPlayableProject` reads and checks
`awake.project.json`, reads the entry scene, loads the glTF models it names, and creates a physics
world only when the scene has `physics_body` or `character_controller` components. The host picks the
physics backend.

| Scene component | What runs |
|---|---|
| `movement_control` | Keyboard intent; moved by physics with `character_controller`, straight through the world without |
| `physics_body`, `character_controller` | `PhysicsSystem` and `CharacterControllerSystem` on the fixed step |
| `camera_rig` | `CameraSystem` |
| `spinControl` | Spinning |
| `keyframe_animation` | `KeyframeAnimationSystem`: its looping tracks |
| `particle_emitter` | `ParticleContentSystem` and `ParticleSystem`, with the sprites `loadPlayableProject` read |
| `patrol`, `chase`, `flee` with a `navigation` component | The behaviours and `PathRequestSystem`, which answers their routes over the scene's grid. Each behaviour arrives with its `PathRequest`; a project with a behaviour and no `navigation` is refused at load |
| Skinned glTF model | Its first animation clip, on a loop |
| `canvas_element` with an `action` | `CanvasActionSystem`: a `move` Joystick steers, a held `jump` Button jumps |

One camera renders: the scene's primary camera, else its first, else a fallback looking at the
origin. `builtInSceneAssets()` provides the neutral meshes `cube`, `sphere`, `ground` and `plane` and
the `lit-shadow` material; anything else a scene draws is a project asset.

`playProject(project, touchControls = true)` shows the scene's `touchOnly` canvas elements; a host on a
touch screen passes it. The scene decides where the controls sit and how big they are.

## Playing a scene in a world of your own

An editor that plays the scene it is editing, in an isolated world, wants the same systems without
a project on disk and without an app builder. `playSystemsFor(scene, services)` returns them as
plain `System`s: `fixed` ones to run on each fixed step, then `frame` ones once per rendered frame,
in the order `playProject` runs them. `playProject` is built on the same decision, so a component
that gains a system gains it in both.

```kotlin
val play = playSystemsFor(
    scene,
    PlayServices(input = { gameplayInput }, renderer = renderer, physics = physicsWorld, particleSprites = sprites),
)
// each fixed step:        play.fixed.forEach { it.update(world, step) }
// each rendered frame:    play.frame.forEach { it.update(world, delta) }
// when the scene stops:   play.close()
```

It builds only the scene's own systems. The host still places the scene, resolves its assets, picks
the camera, resolves transforms and draws. Pass the physics world the scene needs (`physics_body`,
`character_controller` or a terrain collider), or those systems are left out.
