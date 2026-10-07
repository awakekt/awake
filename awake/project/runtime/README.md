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

| Scene component | What runs |
|---|---|
| `movement_control` | Keyboard intent; moved by physics with `character_controller`, straight through the world without |
| `physics_body`, `character_controller` | `PhysicsSystem` and `CharacterControllerSystem` on the fixed step |
| `camera_rig` | `CameraSystem` |
| `spinControl` | Spinning |
| `keyframe_animation` | `KeyframeAnimationSystem`: its looping tracks |
| `particle_emitter` | `ParticleContentSystem` and `ParticleSystem`, with the sprites `loadProject` read |
| `patrol`, `chase`, `flee` with a `navigation` component | The behaviours and `PathRequestSystem`, which answers their routes over the scene's grid. Each behaviour arrives with its `PathRequest`; a project with a behaviour and no `navigation` is refused at load |
| Skinned glTF model | Its first animation clip, on a loop |
| `canvas_element` with an `action` | `CanvasActionSystem`: a `move` Joystick steers, a held `jump` Button jumps |

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
that gains a system gains it in both.

```kotlin
val systems = sceneSystemsFor(
    scene,
    SceneHostServices(input = { gameplayInput }, renderer = renderer, physics = physicsWorld, particleSprites = sprites),
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
`character_controller` or a terrain collider), or those systems are left out.
