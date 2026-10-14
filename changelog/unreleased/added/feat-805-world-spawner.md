- **A host can spawn a scene node into a `World` it owns.** `SceneAppLifecycleRuntime.spawn` gains
  overloads that take a `world: World` alongside `node`, so a host that plays a scene in a world of
  its own, such as an editor's Play mode, gets a working `SpawnedNode` back for it instead of only
  the runtime's own world. `SpawnedNode.attachRenderers` now attaches into that node's own `world`
  (a new public property) rather than the runtime's, and the no-`world` overloads keep spawning into
  the runtime's own world as before.
