/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.particles

import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.RenderSystem3D
import com.awakekt.awake.scene.rendering.mesh.InstancedMeshRenderer
import com.awakekt.awake.scene.rendering.mesh.LodGroup
import com.awakekt.awake.scene.rendering.particles.BOUNCE_STOP_VELOCITY
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private const val DEGREES_TO_RADIANS = kotlin.math.PI.toFloat() / 180f
private const val FULL_TURN_RADIANS = 2f * kotlin.math.PI.toFloat()

/** Three axes of three floats. See [ParticleSystem]'s `orientation`. */
private const val ORIENTATION_FLOATS = 9

/**
 * Spawns/advances every [ParticleEmitter]'s particle pool -- kept separate from
 * [RenderSystem3D] (which already does draw-call assembly, culling, and LOD selection) so this
 * stays a single-responsibility simulation step, same "own component, own system" shape
 * [InstancedMeshRenderer]/[LodGroup] already established. `RenderSystem3D` reads the pool's
 * current live particles fresh each frame; this system owns writing to it.
 *
 * Also owns [ParticleEmitter.lifecycle]'s `burstCount` cleanup: a one-shot emitter's entity is
 * destroyed once every particle it will ever spawn has died, so [spawnParticleBurst] callers never
 * need their own bookkeeping. That removes the whole entity, which is why a scene's `particle_emitter`
 * with a `burstCount` belongs on a node of its own.
 */
class ParticleSystem : System {
    private val spentEntities = ArrayList<Entity>()

    /** The emitter entity's rotation as three unit columns (x, y, z axes), row-major by axis:
     * `[x.x, x.y, x.z, y.x, y.y, y.z, z.x, z.y, z.z]`. Loaded once per top-level emitter by
     * [loadOrientation] and shared with its children; reused so no frame allocates. */
    private val orientation = FloatArray(ORIENTATION_FLOATS)

    override fun update(world: World, delta: Float) {
        spentEntities.clear()
        world.queryEach(ParticleEmitter::class) { entity, emitter ->
            followOrigin(world, emitter)
            simulate(world, entity, emitter, delta)
        }
        spentEntities.forEach { entity ->
            // Only ever return an emitter that actually came from BurstEmitterPool.obtain -- a
            // hand-attached emitter that happens to also set burstCount is destroyed normally,
            // never pooled (see ParticleEmitter.pooledForBurst's own doc comment).
            val emitter = world.get<ParticleEmitter>(entity)
            if (emitter != null && emitter.pooledForBurst) BurstEmitterPool.release(emitter)
            world.destroy(entity)
        }
    }

    /** Advances [emitter] itself, then every entry in [ParticleEmitter.children] -- each child's
     * `origin` is recomposed as `emitter.origin + child.localOffset` first (see [ParticleEmitter
     * .children]'s own doc comment), so a child rides along with its parent's current position
     * every frame without needing its own [Transform]/[Entity]. [isSpent] is only ever checked
     * against [isTopLevel] -- a child's own burst still caps ITS spawning, but a child finishing
     * its burst must never destroy the shared entity out from under a still-emitting parent (or
     * sibling), so only the top-level emitter's exhaustion queues the entity for destruction. */
    private fun simulate(
        world: World,
        entity: Entity,
        emitter: ParticleEmitter,
        delta: Float,
        isTopLevel: Boolean = true,
    ) {
        if (isTopLevel) loadOrientation(world, entity)
        emitter.elapsedTime += delta
        spawn(emitter, delta)
        advance(world, emitter, delta)
        emitter.children.forEach { child ->
            child.origin.set(
                emitter.origin.x + child.localOffset.x,
                emitter.origin.y + child.localOffset.y,
                emitter.origin.z + child.localOffset.z,
            )
            simulate(world, entity, child, delta, isTopLevel = false)
        }
        if (isTopLevel && isSpent(emitter)) spentEntities += entity
    }

    /** Re-anchors [ParticleEmitter.origin] to [ParticleEmitter.dynamics]' `followEntity`'s
     * world position, if set: a root node's own position, a child node's from its world matrix
     * as of the last transform pass -- the lightweight "sub-emitter" trailing-effect
     * path (see [com.awakekt.awake.scene.rendering.particles.ParticleDynamics]'s
     * own doc comment). A no-op when the followed entity has no [Transform] (already destroyed,
     * or was never one) -- the emitter just keeps spawning from wherever [origin] last was,
     * rather than crashing. */
    private fun followOrigin(world: World, emitter: ParticleEmitter) {
        val target = emitter.dynamics.followEntity ?: return
        val transform = world.get<Transform>(target) ?: return
        if (transform.parent == null) {
            emitter.origin.set(transform.position)
        } else {
            val placed = transform.worldMatrix
            emitter.origin.set(placed.m03, placed.m13, placed.m23)
        }
    }

    private fun isSpent(emitter: ParticleEmitter): Boolean {
        val burstCount = emitter.lifecycle.burstCount ?: return false
        return emitter.spawnedTotal >= burstCount && emitter.particles.none { it.alive }
    }

    private fun spawn(emitter: ParticleEmitter, delta: Float) {
        val burstCount = emitter.lifecycle.burstCount
        if (burstCount != null && emitter.spawnedTotal >= burstCount) return
        val burstCycle = emitter.lifecycle.burstCycle
        if (burstCycle != null) {
            // A pulsing schedule replaces the continuous rate: queue every burst that started
            // since the last frame, so a long frame still fires each one exactly once.
            val started = burstCycle.burstsStartedBy(emitter.elapsedTime)
            emitter.spawnAccumulator += (started - emitter.burstsFired) * burstCycle.burstSize
            emitter.burstsFired = started
        } else {
            // Context-driven emission: a caller-supplied rate read fresh every frame (player
            // speed, distance to a target, ...) overrides the static spawnRate when set -- see
            // ParticleDynamics.dynamicSpawnRate's own doc comment.
            val spawnRate = emitter.dynamics.dynamicSpawnRate?.invoke() ?: emitter.spawnRate
            emitter.spawnAccumulator += spawnRate * delta
        }
        // Known limit: clamps the worst case to "refill the whole pool in one frame" rather than
        // true unbounded growth -- a pool that stays completely full for a long stretch would
        // otherwise accumulate an ever-growing backlog that dumps as one mega-burst the moment
        // a slot frees up. Good enough for a first slice; a real rate-limited drain is the
        // upgrade if a demo ever needs a perfectly steady stream under sustained pool pressure.
        emitter.spawnAccumulator =
            emitter.spawnAccumulator.coerceAtMost(emitter.maxParticles.toFloat())
        while (emitter.spawnAccumulator >= 1f) {
            if (burstCount != null && emitter.spawnedTotal >= burstCount) break
            val slot = emitter.particles.firstOrNull { !it.alive } ?: break
            emitter.spawnAccumulator -= 1f
            emitter.spawnedTotal += 1
            slot.alive = true
            val spawnPosition = spawnPosition(emitter)
            slot.position.set(spawnPosition.x, spawnPosition.y, spawnPosition.z)
            slot.velocity.set(spawnVelocity(emitter, spawnPosition))
            slot.age = 0f
            slot.lifetime = emitter.lifetime
            slot.startAlpha = emitter.startAlpha
            slot.scale = emitter.scale
            slot.settled = false
            slot.frameOffset = Random.nextInt(emitter.visual.frameCount.coerceAtLeast(1))
        }
        // A burst that finds the pool full spawns only what fits: it is not carried over to
        // refire when a slot frees up, unlike a continuous rate's fractional remainder.
        if (burstCycle != null) emitter.spawnAccumulator = 0f
    }

    /** Loads [entity]'s `Transform` rotation into [orientation], as unit axes with scale divided
     * out; identity when it has no `Transform`. */
    private fun loadOrientation(world: World, entity: Entity) {
        val placed = world.get<Transform>(entity)?.worldMatrix
        if (placed == null) {
            orientation.fill(0f)
            orientation[0] = 1f
            orientation[4] = 1f
            orientation[8] = 1f
            return
        }
        writeAxis(0, placed.m00, placed.m10, placed.m20)
        writeAxis(3, placed.m01, placed.m11, placed.m21)
        writeAxis(6, placed.m02, placed.m12, placed.m22)
    }

    private fun writeAxis(offset: Int, x: Float, y: Float, z: Float) {
        val length = sqrt(x * x + y * y + z * z)
        val unit = if (length > 0f) 1f / length else 0f
        orientation[offset] = x * unit
        orientation[offset + 1] = y * unit
        orientation[offset + 2] = z * unit
        if (length == 0f) orientation[offset + offset / 3] = 1f
    }

    /** Turns [this], a vector in the emitter's own axes, into world axes with [orientation]. */
    private fun Vec3f.turnedByOrientation(): Vec3f {
        val localX = x
        val localY = y
        val localZ = z
        return set(
            orientation[0] * localX + orientation[3] * localY + orientation[6] * localZ,
            orientation[1] * localX + orientation[4] * localY + orientation[7] * localZ,
            orientation[2] * localX + orientation[5] * localY + orientation[8] * localZ,
        )
    }

    /** [ParticleMotion.spawnRadius] > 0 spawns on a random point around a flat horizontal ring
     * of that radius centered on [ParticleEmitter.origin], instead of exactly at `origin` (the
     * `0f` default) -- the "charging circle" cast-VFX spawn shape, meant to pair with
     * [ParticleMotion.convergeToOrigin]. */
    private fun spawnPosition(emitter: ParticleEmitter): Vec3f {
        val radius = emitter.motion.spawnRadius
        if (radius <= 0f) return emitter.origin
        val angle = Random.nextFloat() * FULL_TURN_RADIANS
        val offset = Vec3f(cos(angle) * radius, 0f, sin(angle) * radius)
        if (emitter.motion.inheritOrientation) offset.turnedByOrientation()
        return Vec3f(
            emitter.origin.x + offset.x,
            emitter.origin.y + offset.y,
            emitter.origin.z + offset.z,
        )
    }

    /** Velocity resolution, in priority order -- see [ParticleMotion]'s own doc comment for the full
     * 3-case breakdown (converge-to-origin, cone burst, per-axis jitter). */
    private fun spawnVelocity(emitter: ParticleEmitter, spawnPosition: Vec3f): Vec3f {
        val motion = emitter.motion
        if (motion.convergeToOrigin) {
            val toOrigin = emitter.origin - spawnPosition
            val direction =
                if (toOrigin.length3() > 0f) toOrigin.normalized() else Vec3f(0f, 1f, 0f)
            return direction * motion.baseVelocity.length3()
        }
        val coneHalfAngleDegrees = motion.coneHalfAngleDegrees
        val speed = motion.baseVelocity.length3()
        val velocity = if (coneHalfAngleDegrees != null && speed > 0f) {
            coneDirection(motion.baseVelocity.normalized(), coneHalfAngleDegrees) * speed
        } else {
            Vec3f(
                motion.baseVelocity.x + jitter(motion.velocityJitter),
                motion.baseVelocity.y + jitter(motion.velocityJitter),
                motion.baseVelocity.z + jitter(motion.velocityJitter),
            )
        }
        if (motion.inheritOrientation) velocity.turnedByOrientation()
        return if (motion.radialSpeed == 0f) velocity else velocity.pushedOutward(emitter.origin, spawnPosition, motion.radialSpeed)
    }

    /** Adds [speed] horizontally away from [origin] through [spawnPosition], or in a random
     * horizontal direction when the two share a vertical line. See [ParticleMotion.radialSpeed]. */
    private fun Vec3f.pushedOutward(origin: Vec3f, spawnPosition: Vec3f, speed: Float): Vec3f {
        var awayX = spawnPosition.x - origin.x
        var awayZ = spawnPosition.z - origin.z
        val away = sqrt(awayX * awayX + awayZ * awayZ)
        if (away > 0f) {
            awayX /= away
            awayZ /= away
        } else {
            val angle = Random.nextFloat() * FULL_TURN_RADIANS
            awayX = cos(angle)
            awayZ = sin(angle)
        }
        return set(x + awayX * speed, y, z + awayZ * speed)
    }

    /** A random unit vector within [halfAngleDegrees] of [axis] -- builds an orthonormal
     * (right, up) basis perpendicular to [axis] (Gram-Schmidt against an arbitrary non-parallel
     * fallback axis), then picks a uniform-random polar angle in `[0, halfAngle]` and azimuth
     * in `[0, 2*PI)` around it. Uniform in ANGLE, not solid angle -- biases slightly toward the
     * cone's edge rather than its center; fine for a visual burst, not a physically exact
     * sampler. */
    private fun coneDirection(axis: Vec3f, halfAngleDegrees: Float): Vec3f {
        val fallback = if (kotlin.math.abs(axis.y) > 0.99f) Vec3f(1f, 0f, 0f) else Vec3f(0f, 1f, 0f)
        val right = axis.cross(fallback).normalized()
        val up = right.cross(axis)
        val theta = Random.nextFloat() * halfAngleDegrees * DEGREES_TO_RADIANS
        val phi = Random.nextFloat() * FULL_TURN_RADIANS
        val cosTheta = cos(theta)
        val sinTheta = sin(theta)
        return (axis * cosTheta) + (right * (sinTheta * cos(phi))) + (up * (sinTheta * sin(phi)))
    }

    private fun advance(world: World, emitter: ParticleEmitter, delta: Float) {
        emitter.particles.forEach { particle ->
            if (!particle.alive) return@forEach
            particle.age += delta
            if (particle.age >= particle.lifetime) {
                // Sub-emitter chaining: fires BEFORE reset() clears position, so the callback
                // sees exactly where this particle died -- see ParticleLifecycle.onParticleDeath's
                // own doc comment.
                emitter.lifecycle.onParticleDeath?.invoke(world, particle.position)
                particle.reset()
                return@forEach
            }
            if (particle.settled) return@forEach
            val acceleration = emitter.motion.acceleration
            particle.velocity.x += acceleration.x * delta
            particle.velocity.y += acceleration.y * delta
            particle.velocity.z += acceleration.z * delta
            val turbulence = emitter.motion.turbulence
            if (turbulence != 0f) {
                val flow = turbulenceOffset(
                    particle.position,
                    emitter.elapsedTime,
                    emitter.motion.turbulenceFrequency,
                )
                particle.velocity.x += flow.x * turbulence * delta
                particle.velocity.y += flow.y * turbulence * delta
                particle.velocity.z += flow.z * turbulence * delta
            }
            particle.position.x += particle.velocity.x * delta
            particle.position.y += particle.velocity.y * delta
            particle.position.z += particle.velocity.z * delta
            val groundHeight = groundHeightAt(emitter, particle.position.x, particle.position.z)
            if (groundHeight != null && particle.position.y <= groundHeight) {
                particle.position.y = groundHeight
                resolveGroundHit(emitter.ground, particle)
            }
        }
    }

    /** What happens to [particle]'s velocity the instant it clamps at ground height -- see
     * [ParticleGround.restitution]'s own doc comment for the bounce/settle rule this implements. */
    private fun resolveGroundHit(ground: ParticleGround, particle: Particle) {
        val bounceVelocity = -particle.velocity.y * ground.restitution
        if (ground.restitution > 0f && kotlin.math.abs(bounceVelocity) >= BOUNCE_STOP_VELOCITY) {
            particle.velocity.set(
                particle.velocity.x * ground.friction,
                bounceVelocity,
                particle.velocity.z * ground.friction,
            )
        } else {
            particle.velocity.set(0f, 0f, 0f)
            particle.settled = true
        }
    }

    /** Resolves the ground height a falling particle clamps against at ([x], [z]), in
     * [ParticleGround]'s documented priority: [ParticleGround.groundHeightProvider] (real
     * terrain) first, then the first [ParticleGround.colliders] box whose world-space XZ
     * footprint contains this point (its `max.y` is the landing height -- real collision against
     * authored scene geometry, reusing [Aabb] the same way `MeshBounds`/`Occluder` already do),
     * then the flat [ParticleGround.groundY] plane. `null` if none apply. */
    private fun groundHeightAt(emitter: ParticleEmitter, x: Float, z: Float): Float? {
        val ground = emitter.ground
        ground.groundHeightProvider?.invoke(x, z)?.let { return it }
        val collider = ground.colliders.firstOrNull { box ->
            x >= box.min.x && x <= box.max.x && z >= box.min.z && z <= box.max.z
        }
        if (collider != null) return collider.max.y
        return ground.groundY
    }

    private fun jitter(magnitude: Float): Float = (Random.nextFloat() * 2f - 1f) * magnitude
}

/** This particle's current linear fade -- [ParticleEmitter.startAlpha] at spawn, 0 at death.
 * Read by [RenderSystem3D] to build [com.awakekt.awake.render.passes.RenderDrawCommand
 * .instanceColors]; not stored on [Particle] itself since it's fully derived from [Particle
 * .age]/`lifetime`/`startAlpha`, not independent state. */
internal fun Particle.currentAlpha(): Float = startAlpha * (1f - (age / lifetime).coerceIn(0f, 1f))

/** This particle's current opacity under [emitter]'s [ParticleVisual.alphaCurve]: `startAlpha`
 * times the curve's factor at this particle's age, or the plain linear fade of [currentAlpha]
 * when the emitter has no curve. */
internal fun Particle.currentAlpha(emitter: ParticleEmitter): Float {
    val curve = emitter.visual.alphaCurve ?: return currentAlpha()
    return startAlpha * curve.factorAt(age / lifetime)
}

/** This particle's current size: the emitter's `scale` at spawn, moving linearly to
 * [ParticleVisual.endScale] at death when that is set. */
internal fun Particle.currentScale(emitter: ParticleEmitter): Float {
    val endScale = emitter.visual.endScale ?: return scale
    return scale + (endScale - scale) * (age / lifetime).coerceIn(0f, 1f)
}

/** This particle's current sprite-strip frame index, `0 until` [ParticleVisual.frameCount] --
 * this particle's own [Particle.age]-derived frame advance offset by its own [Particle
 * .frameOffset] (chosen once at spawn), so particles sharing one emitter cycle frames desynced
 * rather than in lockstep. Read by [RenderSystem3D] to build [com.awakekt.awake.render
 * .renderer.RenderDrawCommand.instanceFrames]. */
internal fun Particle.currentFrame(emitter: ParticleEmitter): Float {
    val frameCount = emitter.visual.frameCount.coerceAtLeast(1)
    val elapsedFrames = (age * emitter.visual.frameRate).toInt()
    return ((elapsedFrames + frameOffset) % frameCount).toFloat()
}

/** This particle's current tint -- linearly interpolated from [ParticleEmitter.visual]'s
 * `startColor` to `endColor` over its life (constant when the two are equal, the common case).
 * Per-particle, not per-emitter: every particle ages independently, so a burst's later-spawned
 * particles show an earlier point in the gradient than its first-spawned ones at any given
 * frame -- exactly what "fire fades orange to yellow within one burst" needs. */
internal fun Particle.currentColor(emitter: ParticleEmitter): Vec3f {
    val t = (age / lifetime).coerceIn(0f, 1f)
    val visual = emitter.visual
    return Vec3f(
        visual.startColor.x + (visual.endColor.x - visual.startColor.x) * t,
        visual.startColor.y + (visual.endColor.y - visual.startColor.y) * t,
        visual.startColor.z + (visual.endColor.z - visual.startColor.z) * t,
    )
}

/** A smooth, deterministic flow-field velocity offset at [position]/[time] -- see
 * [com.awakekt.awake.scene.rendering.particles.ParticleMotion]'s own doc comment
 * for why this is a cheap sine-based approximation, not true Perlin/simplex/curl noise. Each
 * axis samples a different phase-shifted combination of the other two spatial axes plus time,
 * so the field varies smoothly in space and animates smoothly over time without an explicit
 * noise table/library. */
internal fun turbulenceOffset(position: Vec3f, time: Float, frequency: Float): Vec3f {
    val x = position.x * frequency
    val y = position.y * frequency
    val z = position.z * frequency
    return Vec3f(
        sin(y + time) * cos(z * 0.7f + time * 1.3f),
        sin(z + time * 0.8f) * cos(x * 0.7f + time),
        sin(x + time * 1.1f) * cos(y * 0.7f + time * 0.9f),
    )
}
