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
| Skinned glTF model | Its first animation clip, on a loop |
| `canvas_element` with an `action` | `CanvasActionSystem`: a `move` Joystick steers, a held `jump` Button jumps |

One camera renders: the scene's primary camera, else its first, else a fallback looking at the
origin. `builtInSceneAssets()` provides the neutral meshes `cube`, `sphere`, `ground` and `plane` and
the `lit-shadow` material; anything else a scene draws is a project asset.

`playProject(project, touchControls = true)` shows the scene's `touchOnly` canvas elements; a host on a
touch screen passes it. The scene decides where the controls sit and how big they are.
