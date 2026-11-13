# D39: scene features (capabilities until v0.46)

Status: accepted (2026-10-07)

## Decision

A project played with `loadProject` and `runProject`, or a scene a host runs with `sceneSystemsFor`,
runs what its components need through one contract: `SceneFeature`, in `awake:project:runtime`.
A feature names the scene components it adds, reads what its systems need from the project's
files, and adds the systems a scene that uses it runs. The contract is in the
[runtime README](../../../awake/project/runtime/README.md).

1. **Core uses the same contract it offers.** Controls, physics, AI, motion, particles, the day
   cycle, shader effects and skinned animation are features, run in that order. A game's own
   features, and those of the packages it depends on, run after them, in the order passed. There
   is no second, private list of what a component needs.
2. **Features are linked when the game is built.** Kotlin/Native and wasmJs cannot load code, so
   a feature is an ordinary dependency, as a Unity package, an Unreal `Runtime` module or a Bevy
   `Plugin` is. Behaviour that must load at run time, such as content uploaded to a web player, is
   data the engine interprets (shader documents, blueprint graphs), never code.
3. **Features are code that ships in the game; editor plugins are not.** An `EditorPlugin` only
   edits the data a feature reads (D35, D36), so a game plays the same in Studio as in an export.
   A community package can ship both: a feature for the runtime and, optionally, an editor plugin
   for authoring its components.
4. **What a project needs is checked when it loads.** A plugin the manifest marks `required` must
   have a feature with its `id`, and a scene component no feature registers refuses the load,
   naming the component, instead of failing deep in the decoder.
5. **Every planned system has a stable public name, and a host can drop one by name.** Core's names
   are the constants of `CoreSystems`, listed in `CoreSystems.all`; a game's own are listed in
   `SceneFeature.systemNames`. A name is never changed or reused. `sceneSystemsFor(..., disabled)`,
   `loadedProject.sceneSystems(..., disabled)`, `runProject(project, disabledSystems)` and the scene
   app's `disableSystems(...)` leave the named systems out; a name nothing knows is refused at start,
   listing the known names, so a typo never leaves a system running. `SceneSystemSet.planned`, the
   `ProjectSystemPlan` service of a `runProject` app and `SceneAppLifecycleRuntime.plannedSystems` list
   what a run planned, by name and phase, in run order. A scene app's systems are named by the
   author's own `system(name, ...)` calls and disabled by the same rule; its infrastructure systems
   (transforms, drawing) are not listed. With nothing disabled the plan is exactly what it was.
6. **A system has a role, and a host has a role, so a server plans no presentation systems without a
   list.** `SceneSystemRole` has two members: `Simulation`, the default, which every host runs, so an
   unmarked system plans where it always did; and `Presentation`, for local input, drawing, effects and
   sound. `SceneHostRole` is `Full` (runs both) or `SimulationOnly` (a dedicated server or a headless
   simulation). A system is marked with `plan.frame(name, role) { ... }` (also `fixed` and `preAnimation`),
   or `system(name, phase, role) { ... }` in the scene app, and a host sets its role with
   `SceneHostServices.withHostRole`, `runProject(project, hostRole)` or the scene app's `hostRole(...)`.
   Without one a host is `Full`, a `headless` one too, so a host built before roles plans what it
   always did (a headless host that plays sound keeps doing so); a dedicated server opts in with one line,
   `SceneHostServices.headless(input, physics).withHostRole(SceneHostRole.SimulationOnly)` or
   `runProject(project, SceneHostRole.SimulationOnly)`. Both paths filter at registration with `SceneHostRole.runs`. A server-only role is not added: nothing in Core is
   server-only. `hasRenderer` checks stay, as what a system needs from the host, and some are now
   redundant for a `SimulationOnly` host.

   Core marks as `Presentation`: `playerInput`, `canvas-actions`, `occluder-fade`, `canvas-occlusion`,
   `terrain`, `texture-clips`, `sprite-clips`, `particle-content`, `particles`, `day-cycle`,
   `shader-effects`, `audio-listener` and `audio`. Everything else is `Simulation`, deliberately:
   - `camera` places the active camera, whose basis `movement` and the character controller turn a
     player's intent into world motion with, so a host that simulates needs it. A rule that reads the
     camera-relative basis (a knock-back, a dash) is the same case. Whether such a system could depend on
     the camera through a stated order, rather than by role, is stage C of #1527.
   - `particles-2d` simulates, headless too (`twoDimensionalParticlesGetTheirSimulationSystemEvenWhenHeadless`).
   - `spin`, `spin-clock`, `locomotion` and `animation` write transforms and clip state that simulation
     may read; marking them is a decision for a consumer that knows it does not.
   - `movement` reads the input actions `playerInput` writes, and any other system may write them, so a
     server's networked input needs no `playerInput`.
   - `keyframes` writes the transform that colliders, triggers and the network follow (its alpha track
     already skips without a renderer), so it stays simulation.
   - `occluder-fade`, `canvas-occlusion` and `terrain` only read physics rays or draw, and write opacity,
     canvas visibility or GPU content that no simulation system reads: presentation. Collision for terrain
     is the separate `terrain-collider` system, which stays simulation.
7. **A code-first scene app plans through the same function.** `SceneAppDsl.features(scene, features, ...)`
   registers the systems `sceneSystemsFor` and `runProject` plan for the same scene and the same
   features, by name, phase and role, in the same order (`sceneSystemSpecsOf` is the one function both
   call). Core's are the typed handles of `CoreFeatures` (`CoreFeatures.all` is what a project runs with
   before its own); a game's are its own objects. Explicit `system(...)` registrations and other
   `features` calls run in the order the calls were made within each phase, so `features` placed first
   runs its systems first; nothing is reordered across calls. The app's renderer and input are wired;
   physics and content are parameters; a project runner's spawning and scene switching are not. The
   project runtime still builds a plan per scene, as it switches scenes, through that same function.

## Host services at registration

A feature gets what its host owns through one typed hand-off, `FeatureHost` (an alias of
`SceneHostServices`, so every released signature that takes the latter is unchanged). The host adds a
service Core does not know, such as a network link or a game's own backend, with
`provide<T>(value)`; the project runner and the scene app's `features(..., provided = { it.provide(...) })`
both build the host this way. A feature reads it in its `plan` factory, which runs once when the scene's
systems are built: `host.require<T>()` refuses, naming the feature, the system and the type, when the
host provided none, and `host.optional<T>()` returns null. The feature passes the value to its system by
constructor. **A system never looks a dependency up during `update`**: that is why the lookup is a
registration step and not a per-frame call, and why a missing service fails at load. Lookup is by
`KClass`, and `core:di`'s `Container` is not a dependency of the scene or project modules; a host with one
adapts it in a leaf module, as bootstrap does. The alias is the new name for new code; `SceneHostServices`
is not deprecated yet because renaming a final class breaks released binaries (#1527 D2).

## Why "feature"

Scene modules "bind features into the ECS scene graph: a scene schema, its binding, and the system that
runs the feature" (`AGENTS.md`, `awake/scene/README.md`), and `verifyFeatureLayering` guards that
boundary. `SceneFeature` is that binding as the runtime consumes it, and the net modules already say
`GameFeature` and `ClientNetFeature`. Until v0.46 the same type was `SceneCapability`; it, `BlueprintCapability`,
`loadCapabilityContent` and the `verifyCapabilityLayering` task stay as deprecated names for one release.
The `capabilityClass` key of a manifest's `plugins` entry is saved data and keeps its name. "Plugin" already
means an editor package (`EditorPlugin`, `plugin.json`) and "module" an app installer (`AppModule`), so
neither was used.

## Left out until needed

| Left out | Add it when |
|---|---|
| Ordering a feature's systems between Core's, rather than after them | A feature needs to run before Core's frame systems |
| Dependencies between features, by id | Two community packages need to state that one builds on the other |
| Resolving a manifest's plugins to build dependencies | The export pipeline links a project's plugins for it. A plugin reference already names its `artifact` (`group`, `name`, `version`) and `capabilityClass` (#537), so the pipeline has what it needs to read |
| The host passing the audio player in, and the runner no longer opening one | A host needs its own player; today `runProject` opens the platform's, so defaults are unchanged |

None of these changes the contract's existing members, so each can be added without breaking
features.
