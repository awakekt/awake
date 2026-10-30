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
| `canvas_element` with an `action` | `CanvasActionSystem`: an element naming one of the scene's input actions adds to it, and a Joystick past its `joystick.sprintThreshold` holds its `sprintAction`, so a `move` Joystick steers, a `jump` Button jumps and a `run` Button runs as the keys do |
| `canvas_element` with a `style` image | Nothing runs: `loadProject` decodes the images, under `CoreSceneContent.CanvasImages`, and `runProject` gives them to the runtime that draws the canvas. With `hasRenderer = false` it only checks that they resolve |
| `canvas_element` with a `textKey` | Nothing runs: `loadProject` loads the project's `strings/` tables (`LoadedProject.strings`), in the device's locale and the default, English, and `runProject` gives them to the runtime, which shows each key's text and the new locale's the frame after `setLocale`. A project with no `strings/en.json` has none, and its keys show as they are |
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

## Switching to another scene

A project plays its entry scene first and can go on to any other scene in its files, as a menu's Play
button or a level's exit does. Ask with the scene's path relative to the project root:

```kotlin
// In a system a capability plans:
services.sceneSwitch?.request("scenes/level-2.scene.json")
// From the app that runs the project:
game.requireService<SceneSwitch>().request("scenes/level-2.scene.json")
```

A blueprint asks with its Load Scene node (`scene.load`, in
[`awake:scene:blueprint`](../../scene/blueprint/README.md)), which `BlueprintCapability` connects to
the switch.

- **When.** The next scene loads off the frame thread, through the project's files, the way
  `loadProject` loads the entry scene: decoded and checked, its models and its capabilities' content
  read (collision meshes, particle sprites, canvas images, shader effects, terrain surfaces), and a
  physics world made when it has bodies. The scene that asked plays on meanwhile. The next one takes
  its place at the start of the next fixed step, or of the frame's own systems once the fixed steps
  have run, never inside a system's update: a request made during a fixed step finishes that step
  with the old scene around it.
- **What goes.** The old scene's systems are closed, its sounds stop, the nodes spawned into it with
  `spawn(project, node)` are despawned, every entity in the world is destroyed (its blueprints with
  them, and whatever else joined the scene: a fallback camera, what its systems made), its meshes
  and materials are given back to the asset library, and its physics world is destroyed.
- **What comes.** The next scene's entities, the systems its components call for (planned through the
  same capabilities, Core's then the game's), its canvas images and its primary camera. The project's strings stay the same across scenes, in the locale last set.
- **What carries across.** Nothing of the scene: positions, variables, scores and spawned nodes start
  over. The app does: the renderer, the UI, the touch-controls setting, the platform's audio player,
  and a mesh both scenes draw, which stays loaded. A game that keeps something across scenes keeps it
  outside the world.
- **Failure.** A scene that can't be read, decoded, checked or drawn is logged once, as an error naming
  it, and the scene playing goes on as it was. A scene that fails while it is put in is taken out
  again first. The switch state (below) ends in `Failed` with the scene's path and the cause.
- **Again and again.** Asking again for the scene on its way changes nothing, and asking for another
  instead replaces the request. Asking for the scene that is playing starts it again from its file.

### Watching a switch: loading, ready, failed

`SceneSwitch` only asks. What a request is doing is the app's `SceneSwitchStatus` service (and
`SceneHostServices.sceneSwitchStatus` in a system), whose `state` is a `StateFlow<SceneSwitchState>`,
the same idiom as `Strings.locale`. A loading curtain, a progress bar or an error message is the
game's to draw from it; `awake:project:runtime` draws nothing, so a played project still needs no
display.

```kotlin
val status = game.requireService<SceneSwitchStatus>()
// Each frame, in a system or a UI:
when (val state = status.state.value) {
    SceneSwitchState.Idle -> Unit
    is SceneSwitchState.Loading -> drawCurtain(state.progress)
    is SceneSwitchState.Ready -> liftCurtain()
    is SceneSwitchState.Failed -> showError(state.path, state.cause)
}
```

| State | Meaning |
| --- | --- |
| `Idle` | No scene has been asked for. |
| `Loading(path, progress)` | The scene at `path` is being read, and after that swapped in. It lasts until the new scene has had its first frame. |
| `Ready(path)` | The new scene is playing and its first frame is over. |
| `Failed(path, cause)` | The scene couldn't be read, decoded, checked or put in; the old scene plays on. |

- **Order.** A request goes `Idle`, `Loading(path, 0)`, ..., `Loading(path, 1)`, `Ready(path)`, or ends
  in `Failed`. Asking for another scene while one loads makes it `Loading(newPath, 0)`. The state
  holds its last value until the next request, and changes on the frame thread. A `StateFlow`
  conflates, so a collector can miss a short-lived state: read `state.value` each frame.
- **Progress** is the share of the target scene's content bytes that have arrived, from 0 to 1. It
  never decreases within a request and is 1 once the scene has loaded, before `Ready`. The bytes are
  those of the scene file and every file it names by project path (a model, an image, a sound, a
  prefab), counted through `PrefetchableAssetSource.prefetch` when the project's files can prefetch:
  an `IndexedAssetSource` with a `ContentCache`, or a source wrapping one that passes the contract on,
  as `verifiedByAssetsLock()` does. A source that can't report bytes (local files, an index with no
  cache, or a wrapper over either) goes from 0 straight to 1. Files named only inside another file,
  such as a model's textures, are not counted ahead.
- **Ready** is the start of the frame after the one that first ran the new scene's systems, so on a
  host with a renderer the scene has been handed to the renderer once, and on a host without one it
  is the first completed frame. It does not wait for pipeline compilation or texture uploads a
  backend defers past its first frame, so a curtain may still see a short hitch on a slow device.
- **`loadProject` progress.** `loadProject(files, ..., onProgress = { share -> ... })` reports the
  entry scene's content the same way: 0 as loading starts, rising, and 1 as the project is ready,
  on the loading coroutine. Pass `physicsWorld` by name or as the trailing lambda; `onProgress` sits
  before it.

`project.scene` is the scene playing now, and `project.close()` closes it, whichever it is: a switch
closes the scene it leaves, the entry scene included. The manifest lists no scenes, so any scene file
the project has can be asked for, and a build has to ship the scenes a switch names: Awake Studio's
exports and its publish copy the whole project folder, so they all do. The project-content Gradle
plugin's browser index covers `assets/`, `scenes/`, `plugins/` and `strings/` only, so keep a
project's scenes under one of the first three. A host that runs the systems in a world of its own, with `sceneSystemsFor` or
`project.sceneSystems`, has no switch unless it gives one with `SceneHostServices.withSceneSwitch`;
without it, Load Scene only warns.

A host with no frame loop, such as a test, a server or a project check, moves the project itself:
`project.switchTo("scenes/level-2.scene.json")` loads that scene as `loadProject` loads the entry
scene, with the project's registry, capabilities and physics world factory, then closes the scene it
leaves. Close the systems and entities of the scene it leaves first. A path that leaves the project,
names no file or can't be loaded throws `IllegalArgumentException`, and the scene playing stays. A
project moved this way and then played with `runProject` starts at the scene it was moved to; once
`runProject` plays it, `switchTo` throws, and the project moves through its `SceneSwitch`.

```kotlin
val registry = SceneComponentRegistry.scoped()
val project = loadProject(files, registry, capabilities, hasRenderer = false, physicsWorld = ::createJoltPhysicsWorld)
for (path in scenes) {
    project.switchTo(path)
    val world = World().also { project.scene.instantiate(it, registry) }
    project.sceneSystems(input).use { systems -> systems.fixed.forEach { it.update(world, 1f / 60f) } }
}
project.close()
```

`checkProjectOpens(files, scenes, capabilities, physicsWorld)` is built on this. It opens each of a
project's scenes with its capabilities, as an editor does: decoded, prefabs expanded, instantiated,
switched to, systems run for a step, and saved and reopened. It returns a typed `ProjectOpenIssue` for
each problem, with its file and node path. `awake validate --code` runs it with the project's own built
code; see the [CLI](../cli/README.md#validating-with-the-projects-code).

## Routing to a project loaded after the app starts

A routed app (`app { scenes { ... } }`) declares its routes before it starts. A project that can only be
read later, such as protected content read with a token the player gets by signing in, is given to its
route as a loader with `projectRoute`:

```kotlin
val signedIn = CompletableDeferred<AssetSource>() // the sign-in scene completes it
app {
    scenes {
        route("sign-in") { /* the game's sign-in screen */ }
        projectRoute("world", loading = { ui { Text("Loading...") } }) {
            loadProject(signedIn.await(), physicsWorld = ::createJoltPhysicsWorld)
        }
    }
}
```

The router (`DeferredSceneRoute` in `awake:scene:runtime`) runs the loader off the frame thread the
first time the route is entered, or when `SceneRouterRuntime.preload` asks, and plays the `loading`
scene until it returns; `routeStatus` reports `Loading`, `Loaded` or `Failed(cause)`. A loader that
throws leaves the route on its loading scene, and entering the route again loads again. A load the
player leaves keeps going and waits, unplayed, for the next entry; the router never switches back on its
own. Because a project plays once, the route owns it: leaving the route closes the project, the next
entry calls the loader again, and the app stopping closes one that loaded and never played. See
[Protected content](../../../website/docs/guides/protected-content.md#play-a-protected-project-after-sign-in).

## Failures reach the app

`runProject` and `projectRoute` catch what fails while a project plays and hand it to the
`AppErrorHandler` the app registered (`service(AppErrorHandler::class, handler)`); Core never presents
it. A scene a `SceneSwitch` cannot read or play is reported as `AppErrorSource.SceneSwitch` with a
retry that asks for the scene again, and the scene playing goes on. A `projectRoute` loader that
throws is reported as `ProjectLoad` with a retry that loads the route again. A scene system that
throws in a frame is reported once as `System`, with no retry, and is skipped for the rest of that
scene while the other systems, the renderer and the UI run on. With no handler registered a failure
is logged (switch, load) or thrown out of the frame (system) as before. See
[App lifecycle](../../../website/docs/guides/app-lifecycle.md#handle-errors).

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

The runtime brings no display and no HTTP stack with it. `awake:project` and `awake:project:runtime`
reach no GPU backend, no window module and no content transport module (`:awake:backend:*`,
`:awake:engine:window`, `:awake:project:transport-*`): the host picks the backends a project draws and
simulates with and passes them in, as it passes the physics world, and builds the HTTP client behind
its `ContentTransport` (the ready-made `transport-ktor` and `transport-browser` modules are opt-in).
`awakeVerify` runs `verifyHeadlessRuntime`, which fails a change that puts one on that path, naming
the chain.

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
