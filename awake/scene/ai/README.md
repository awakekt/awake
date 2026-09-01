# `awake:scene:ai`

Steering behaviours: `PatrolBehavior`, `ChaseBehavior`, `FleeBehavior`, the `RouteFollower` they
share, and the systems that drive them.

## Why it is its own module

It was a package inside `scene:scene-core`, which meant every game linked NPC behaviour whether or
not it had NPCs, and `scene-core` — the module holding `Transform` and `Name` — carried a
pathfinding dependency to serve it.

The dependency now runs one way and only one way:

```
scene:scene-core     Transform, Name, world partitioning
      ↓
scene:navigation     NavMesh, PathRequest, NavGrid, A*
      ↓
scene:ai             behaviours and their systems
```

A game can use pathfinding without linking any AI. It could not before, because the two were
entangled: `scene:navigation`'s debug overlay read `ChaseBehavior` out of the world to draw routes,
so navigation depended on behaviours *and* behaviours needed navigation. That cycle is why this
module could not exist until the overlay was given its routes instead of finding them — see
`AgentRoute` in `scene:navigation`.

Fixing it also fixed a bug the entanglement hid: the overlay drew only chasers, and `path` belongs
to `RouteFollower`, so patrol and flee routes were silently missing.

## What is here and what is not

Behaviours carry the *authored* tuning — speed, radii, dwell time — and the runtime state a system
recomputes: the current `path`, which waypoint it is heading to, and time since the last repath.
Only the first group is authored in `scene.json` or restored by the editor; see
`awake:editor:ai`'s snapshotters for why the second is deliberately not.

`target` and `threat` are `Entity` references. A system skips a null or destroyed one rather than
failing, which is what keeps a chaser standing still instead of pursuing a recycled handle.
