# D39: scene capabilities

Status: accepted (2026-10-07)

## Decision

A project played with `loadProject` and `runProject`, or a scene a host runs with `sceneSystemsFor`,
runs what its components need through one contract: `SceneCapability`, in `awake:project:runtime`.
A capability names the scene components it adds, reads what its systems need from the project's
files, and adds the systems a scene that uses it runs. The contract is in the
[runtime README](../../../awake/project/runtime/README.md).

1. **Core uses the same contract it offers.** Controls, physics, AI, motion, particles, the day
   cycle, shader effects and skinned animation are capabilities, run in that order. A game's own
   capabilities, and those of the packages it depends on, run after them, in the order passed. There
   is no second, private list of what a component needs.
2. **Capabilities are linked when the game is built.** Kotlin/Native and wasmJs cannot load code, so
   a capability is an ordinary dependency, as a Unity package, an Unreal `Runtime` module or a Bevy
   `Plugin` is. Behaviour that must load at run time, such as content uploaded to a web player, is
   data the engine interprets (shader documents, blueprint graphs), never code.
3. **Capabilities are code that ships in the game; editor plugins are not.** An `EditorPlugin` only
   edits the data a capability reads (D35, D36), so a game plays the same in Studio as in an export.
   A community package can ship both: a capability for the runtime and, optionally, an editor plugin
   for authoring its components.
4. **What a project needs is checked when it loads.** A plugin the manifest marks `required` must
   have a capability with its `id`, and a scene component no capability registers refuses the load,
   naming the component, instead of failing deep in the decoder.

## Why "capability"

Scene modules already "bind capabilities into the ECS scene graph: a scene schema, its binding, and
the system that runs the capability" (`AGENTS.md`, `awake/scene/README.md`), and
`verifyCapabilityLayering` guards that boundary. `SceneCapability` is that binding as the runtime
consumes it. "Plugin" already means an editor package (`EditorPlugin`, `plugin.json`) and "module"
an app installer (`AppModule`), so neither was used.

## Left out until needed

| Left out | Add it when |
|---|---|
| Ordering a capability's systems between Core's, rather than after them | A capability needs to run before Core's frame systems |
| Dependencies between capabilities, by id | Two community packages need to state that one builds on the other |
| Resolving a manifest's plugins to build dependencies | The export pipeline links a project's plugins for it |
| A capability that owns a service the host provides, such as audio | A capability needs more than the renderer, input and physics world |

None of these changes the contract's existing members, so each can be added without breaking
capabilities.
