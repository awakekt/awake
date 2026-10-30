# `awake:particles`

Particle emitters: simulation, ground bounce, burst scheduling and instanced draw packets. It has no
scene dependency, so an app with its own world and camera can use it as it is.

```kotlin
implementation(project(":awake:particles"))
```

## What is in it

- `ParticleEmitter` -- a fixed-capacity pool of particles backed by `awake:core:particles`'s
  `ParticleSimulation`, and every option that shapes them: `ParticleMotion`, `ParticleVisual`,
  `ParticleGround`, `ParticleLifecycle`, `ParticleDynamics`. `emit(count)` fires an immediate burst,
  `spawn` emits one by hand, and `liveParticleCount`, `droppedParticleCount`, and
  `forEachLiveParticle` read pool state.
- `ParticleSystem` -- an ECS `System` that spawns, moves and ages the particles of every
  `ParticleEmitter` in a `World`.
- `ParticleDrawBuilder` -- culls, sorts and packs the live particles of a `World` into
  `RenderDrawCommand`s for a view, given as a `Lens` and an aspect.
- `BurstEmitterPool` and `spawnParticleBurst` -- a one-shot "play this effect here" helper that reuses
  its emitters.
- `ParticleEmitter2D`, `Particle2DSystem` and `BurstEmitter2DPool` -- the 2D emitter: the same
  `ParticleSimulation` in the `XY` plane in 2D world units, with `awake:core:particles`'s
  `ParticleSpawnRate` and `ParticleDeathListener` hooks, which allocate nothing. `appendSpriteDraws` turns its live particles
  into `SpriteDrawInput` quads that sort alongside sprites and tilemaps. `BurstEmitter2DPool` keeps
  resident entities that need no placement and fires a template emitter's settings at a point.

## Why it is its own module

It was a package in `scene:scene3d`, which made every option a scene author could not set (rotation,
a new shape, a new fade) a decision about the scene's file format, and made the particle library
unusable without a scene.

The dependency runs one way: `scene:particles`, `scene:scene3d` and `scene:scene2d` use this, and
nothing here knows a scene exists. `awake:scene:particles` and `awake:scene:scene2d` are the wrappers
that let a scene document place a 3D or a 2D emitter; this is the capability they wrap.

## Where an entity is

An emitter can follow an entity (`ParticleDynamics.followEntity`) and can turn its spawns by the
entity's rotation (`ParticleMotion.inheritOrientation`). This module does not know what places an
entity, so `ParticleSystem`, `ParticleDrawBuilder` and `Particle2DSystem` ask an `EmitterPlacement`.
An app supplies the one it has; `awake:scene:particles` supplies one for the scene's `Transform`, and
`awake:scene:scene2d` supplies `Transform2DPlacement` for 2D nodes. Pass
`EmitterPlacement.None` when disabling entity following/orientation on purpose: with it, no emitter
follows anything and none is turned.

## What it does not do

It does not read a scene document, load a sprite, or create a GPU mesh or material: an emitter takes
the `Mesh` and `Material` it draws with. It has no rendering-backend code; it builds draw packets and
the app's `Renderer` draws them.
