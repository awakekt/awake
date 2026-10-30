# Awake Core Particles

Pure structure-of-arrays particle simulation for 3D worlds, 2D scenes, and canvas UI in
[Awake](../../../README.md) — depends only on `awake:core:math`, with no ECS, scene, Compose, or
renderer dependency. Compiles for every target the engine ships (desktop, Android, iOS, and
wasmJs).

## Installation

```kotlin
implementation(project(":awake:core:particles"))
```

Published Maven coordinate: `com.awakekt.awake.core:particles`.

## What is in it

- `ParticleSimulation` — fixed-capacity structure-of-arrays particle pool (`posX/Y/Z`, `velX/Y/Z`,
  `age`, `lifetime`, `startAlpha`, `scale`, `settled`, `frameOffset`, `rotation`, `spinRate`),
  stepped in place with zero per-frame or per-burst heap allocation.
- `ParticleSpawnShape` — `Point`, `RingXZ`, `RingXY`, `Box`, and `RectEdge` spawn distributions in
  either the 3D `XZ` or 2D/UI `XY` `ParticlePlane`.
- `ParticleAlphaRamp`, `ParticleBurstSchedule`, and `ParticleSpinRange` — opacity curves, repeating
  burst schedules, and per-particle spin ranges shared across 3D, 2D, and canvas emitters.
- `ParticleSimulation.emit(count)` — immediate one-shot burst into free pool capacity, tracking any
  skipped excess in `droppedParticleCount` without allocating.

## Consumers

- [`awake:particles`](../../particles/README.md) wraps `ParticleSimulation` in 3D `ParticleEmitter`
  and `ParticleSystem`, adding ECS entity lifecycle and instanced GPU draw-packet building.
- `awake:scene:scene2d` and `awake:ui:canvas` drive the same `ParticleSimulation` in 2D world units
  and logical canvas `dp` units.
