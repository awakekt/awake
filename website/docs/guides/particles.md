# Particles

<p class="awake-lede">Sparks, smoke and dust: an emitter spawns sprites that face the camera or lie flat, and move, fade and change colour over their life, all drawn in one instanced draw call.</p>

<div class="awake-badges" markdown>
<span class="awake-badge">ECS: <code>ParticleEmitter</code></span>
<span class="awake-badge awake-badge--ok">Vulkan</span>
<span class="awake-badge awake-badge--ok">WebGPU</span>
<span class="awake-badge">Desktop · Android · iOS · Web</span>
</div>

A scene document places an emitter with `particle_emitter`; the Kotlin API below has every option.
AwakeKt Studio has no particle editor yet.

## Place an emitter from a scene document

```json title="Scene document"
{
  "component": "particle_emitter",
  "texture": "assets/fx/dust.png",
  "maxParticles": 120,
  "spawnRate": 200,
  "lifetime": 0.33,
  "startAlpha": 0.4,
  "scale": 0.1,
  "endScale": 0.5,
  "velocity": {"x": 0, "y": 0, "z": 0},
  "spawnRadius": 0.3,
  "radialSpeed": 3,
  "additive": true
}
```

The emitter spawns at its node's world position and follows the node; the node's scale does not
apply. `playProject` reads every emitter's `texture` while it loads the project and runs the
particle systems when the scene has an emitter. In your own app, read the sprites with
`loadParticleSprites(document, assets)` and add
`frameSystem("particle-content") { ParticleContentSystem(renderer, sprites) }` next to
`ParticleSystem`; call its `release()` when the scene goes. The fields are in the
[component reference](../reference/scene-document-components.md#particle_emitter).

## Lay particles flat

Particles face the camera unless `facing` is `Flat`. A flat particle lies in the plane perpendicular
to its node's up axis, on the ground for an upright node, and keeps its position, size, colour and
fade. Ground glows, ripples and magic circles spread this way:

```json title="Scene document"
{
  "component": "particle_emitter",
  "texture": "assets/fx/ring.png",
  "maxParticles": 4,
  "spawnRate": 2,
  "lifetime": 2,
  "scale": 0.5,
  "endScale": 4,
  "velocity": {"x": 0, "y": 0, "z": 0},
  "facing": "Flat",
  "additive": true
}
```

Rotating the node tilts the plane the quads lie in, with the texture's top along the node's -Z;
motion, `spawnRadius` and `radialSpeed` stay in world space. Lift the node a little above the ground so the quads do
not fight it for depth. In Kotlin, set `ParticleVisual(facing = ParticleFacing.Flat)`; the plane
comes from the `Transform` of the entity carrying the `ParticleEmitter`, or is the world's ground
plane when that entity has none.

## Register the quad and the material

A particle is a quad with positions and UVs (`VertexFormat.PositionUv`), drawn with a material sized
for `ParticleUniformLayout` whose texture is the sprite.

```kotlin title="Kotlin"
--8<-- "awake/project/runtime/src/desktopTest/kotlin/com/awakekt/awake/project/runtime/ParticlesDocsSampleTest.kt:assets"
```

## Add an emitter

Put a `ParticleEmitter` on an entity. This one sprays up to 200 orange sparks upward in a 20° cone,
fading to dark red.

```kotlin title="Kotlin"
--8<-- "awake/project/runtime/src/desktopTest/kotlin/com/awakekt/awake/project/runtime/ParticlesDocsSampleTest.kt:emitter"
```

## Run the particle system

`ParticleSystem` spawns and moves particles. It is not one of the default scene systems, so add it:

```kotlin title="Kotlin"
--8<-- "awake/project/runtime/src/desktopTest/kotlin/com/awakekt/awake/project/runtime/ParticlesDocsSampleTest.kt:system"
```

The render plan also needs a particle pipeline:
`ScenePipeline(PipelineKey.Particle, PackShaderSets.Particle, VertexFormat.PositionUv, variant = PipelineVariant.AlphaBlendedParticle, materialBindings = GroupBindings.ParticleMaterial)`.
Add `buildAdditive = true` to it for emitters with `ParticleVisual.additive`.
The plan on [Render plans and shaders](shaders.md) has one.

## Properties

`ParticleEmitter`:

| Property | Type | Default | What it does |
| --- | --- | --- | --- |
| `mesh` | `Mesh` | required | The quad. Must be `VertexFormat.PositionUv`. |
| `material` | `Material` | required | Its texture is the sprite. |
| `origin` | `Vec3f` | required | Spawn point in world space. Move the emitter with `origin.set(...)`. |
| `maxParticles` | `Int` | required | Pool size. Spawning pauses while every slot is live. |
| `spawnRate` | `Float` | required | Particles per second. |
| `lifetime` | `Float` | required | Seconds each particle lives. |
| `startAlpha` | `Float` | required | Opacity at birth; it fades linearly to 0 over the lifetime. |
| `scale` | `Float` | required | Quad size in world units. |
| `motion` | `ParticleMotion` | no spread | Direction and spread. |
| `visual` | `ParticleVisual` | white | Colour over life, sprite frames, stretch. |
| `ground` | `ParticleGround` | none | Where particles stop or bounce. |
| `lifecycle` | `ParticleLifecycle` | endless | Burst limits and death callbacks. |
| `dynamics` | `ParticleDynamics` | none | Following an entity, a live spawn rate. |
| `children` | `List<ParticleEmitter>` | empty | Emitters that ride along; each child's `origin` is an offset from this one. |

`ParticleMotion`:

| Property | Default | What it does |
| --- | --- | --- |
| `baseVelocity` | `(0, 1, 0)` | Starting velocity. |
| `velocityJitter` | `0` | Random per-axis variation added to `baseVelocity`. Ignored in cone mode. |
| `coneHalfAngleDegrees` | none | Randomises direction within this angle around `baseVelocity`, keeping its speed. |
| `spawnRadius` | `0` | Spawns on a horizontal ring of this radius around `origin`. |
| `convergeToOrigin` | `false` | Aims each particle back at `origin`, at `baseVelocity`'s speed. |
| `turbulence` | `0` | Strength of a smooth flow-field wobble. |
| `turbulenceFrequency` | `1` | How tight that wobble is. |
| `radialSpeed` | `0` | Adds this speed horizontally away from `origin`, through the spawn point on the `spawnRadius` ring (any direction when the radius is 0). Ignored with `convergeToOrigin`. |

`ParticleVisual`:

| Property | Default | What it does |
| --- | --- | --- |
| `startColor` | `(1, 1, 1)` | Tint at birth. |
| `endColor` | `startColor` | Tint at death, blended linearly. |
| `frameCount` | `1` | Treats the texture as a horizontal strip of this many frames. |
| `frameRate` | `8` | Frames per second; each particle starts on a random frame. |
| `stretchWithVelocity` | `false` | Stretches the quad along its screen motion, for streaks. |
| `stretchFactor` | `0.05` | World units of stretch per unit of speed. |
| `endScale` | none | Quad size at death, reached linearly from `scale`. None keeps `scale`. |
| `additive` | `false` | Adds each particle's colour to what is behind it, for glows and sparks. Needs the particle pipeline built with `buildAdditive = true`; without it the particles blend. |
| `facing` | `Camera` | `ParticleFacing.Flat` lays each quad in the emitter entity's horizontal plane instead of turning it to the camera. |

`ParticleGround`: `groundY` (a flat floor), `groundHeightProvider` (a `(x, z) -> height` function,
used first when set), `colliders` (world-space `Aabb` boxes), `restitution` (0 stops a particle on
contact; above 0 bounces) and `friction` (1 keeps horizontal speed on a bounce).

`ParticleLifecycle`: `burstCount` stops spawning after that many particles and destroys the entity
once they have all died; `onParticleDeath(world, position)` runs for each particle that dies of age.

`ParticleDynamics`: `followEntity` moves `origin` to that entity's world position every frame (a
child node's from its world matrix as of the last transform pass);
`dynamicSpawnRate` replaces `spawnRate` with its result every frame.

For a one-shot burst, `spawnParticleBurst(world, mesh, material, position, count, spawnRate,
lifetime, startAlpha, baseVelocity, ...)` creates an entity with a `burstCount` emitter and returns
it. `ParticleSystem` destroys it when the burst is over.

## How it works

Each frame, `ParticleSystem` spawns into free slots at `spawnRate`, moves live particles, applies
ground and turbulence, and ages them. `RenderSystem3D` then turns each emitter into one instanced
draw: one matrix, colour and frame per live particle, sorted back to front and culled against the
camera. The particle shader lays each quad along two axes the draw carries: the camera's right and
up, or for a flat emitter its entity's +X and -Z. The quad's own orientation never matters.

!!! warning "The quad must be `PositionUv`"
    The renderer recognises a particle draw by its mesh's vertex format. A quad in any other format
    is not drawn as particles.

!!! warning "Nothing moves without `ParticleSystem`"
    Without the system, emitters never spawn and the scene draws nothing for them.

!!! tip "Particles cast shadows"
    With `DepthCasterKind.Particle` in the plan's `depthPrePassVariants`, particles render into the
    shadow map too. The Engine Showcase plan maps it to `PackShaderSets.ParticleShadowDepth`.

## See also

- [Meshes and materials](meshes-and-materials.md) for instancing static meshes.
- [Render plans and shaders](shaders.md) for the particle pipeline.
