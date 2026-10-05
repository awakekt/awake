# `awake:scene:particles`

The `particle_emitter` scene component: its schema, binding, sprite loading and the system that gives
each placed emitter its `ParticleEmitter`. It is a wrapper over [`awake:particles`](../../particles/README.md),
which holds the emitters, the simulation and the draw packets.

```kotlin
implementation(project(":awake:scene:particles"))
```

## What is in it

- `SceneParticleEmitter` -- the document's `particle_emitter` component. Its field names are the file
  format.
- `ParticleEmitterBinding` and `ParticleEmitterSource` -- bind that component onto an entity.
- `ParticleEmitterMapping` -- turns the document's options into the library's `ParticleMotion`,
  `ParticleVisual`, `ParticleGround` and `ParticleLifecycle`.
- `loadParticleSprites` -- loads the sprites a document names.
- `ParticleContentSystem` -- gives each `ParticleEmitterSource` entity its `ParticleEmitter` once its
  sprite is loaded.
- `TransformPlacement` -- tells `ParticleSystem` where an entity is, from its `Transform`. Pass it:
  `ParticleSystem(TransformPlacement)`.

## What stays out of it

Anything about how particles behave: a new option is added to `awake:particles` first, and this module
then exposes it as a field. `ParticleExposureTest` fails when a library option has no field here and
is not listed as code-only.

The scene's camera and `Transform` reach the draw builder through `awake:scene:scene3d`, which draws
every scene's particles; this module does not draw.
