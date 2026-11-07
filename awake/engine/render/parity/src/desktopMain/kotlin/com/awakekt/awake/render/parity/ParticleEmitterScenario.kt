/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.particles.EmitterPlacement
import com.awakekt.awake.particles.ParticleDrawBuilder
import com.awakekt.awake.particles.ParticleDynamics
import com.awakekt.awake.particles.ParticleEmitter
import com.awakekt.awake.particles.ParticleMotion
import com.awakekt.awake.particles.ParticleSpace
import com.awakekt.awake.particles.ParticleSystem
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.ParticleUniformLayout
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.createMaterial
import kotlinx.coroutines.runBlocking
import kotlin.random.Random

/**
 * A real `awake:particles` emitter, stepped by [ParticleSystem] and drawn by [ParticleDrawBuilder], on
 * an entity that slides from x = [EMITTER_START_X] to x = [EMITTER_END_X] while it emits red sprites
 * that rise at [RISE_SPEED]. Seen head-on through an orthographic lens, as [size]-square RGBA.
 *
 * In [ParticleSpace.World] each sprite stays where it was emitted, so the frame shows a rising trail
 * across its width: the oldest sprites, emitted on the left, highest. In [ParticleSpace.Local] they are
 * kept relative to the entity and drawn through its frame, so they all ride with it to the right edge
 * and stand in one column above it, and the left of the frame stays empty.
 */
fun Renderer.renderParticleEmitterScene(space: ParticleSpace, size: Int): ByteArray {
    val target = createRenderTarget(size, size)
    val quad = createMesh(SpriteQuad)
    val material = createMaterial(ParticleUniformLayout, texture = RedSprite)
    return try {
        val world = World()
        val carrier = Carrier()
        val entity = world.create()
        world.add(entity, carrier)
        world.add(entity, emitter(quad, material, space, entity))
        val system = ParticleSystem(CarrierPlacement)
        for (step in 0 until STEPS) {
            carrier.moveTo(EMITTER_START_X + (EMITTER_END_X - EMITTER_START_X) * step / (STEPS - 1))
            system.update(world, STEP_SECONDS)
        }
        val lens = Lens(eye = Vec3f(0f, 0f, EYE_DISTANCE), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 50f).apply {
            projection = Lens.Projection.Orthographic
            orthoHalfHeight = VIEW_HALF_HEIGHT
        }
        val draws = ArrayList<RenderDrawCommand>()
        ParticleDrawBuilder().appendWorldDrawCalls(draws, world, CarrierPlacement, lens, 1f)
        renderToTexture(
            target,
            ScenePassCompiler.compile(
                lens = lens,
                drawCalls = draws,
                light = SceneLight(direction = Vec3f(0f, 1f, 0f), color = Vec3f(1f, 1f, 1f)),
                environment = EnvironmentUniforms.Default.copy(shadowsEnabled = false),
                clipSpace = clipSpace,
                aspect = 1f,
                drawPreparer = (this as? GpuDrawPreparationSource)?.gpuDrawPreparer,
            ),
        )
        runBlocking { readPixels(target) }.data.copyOf(size * size * RGBA)
    } finally {
        quad.destroy()
        material.destroy()
        target.destroy()
    }
}

/** Where the scenario's emitter starts, in world units: the left of the frame. */
const val EMITTER_START_X: Float = -1.5f

/** Where it ends: the right of the frame. */
const val EMITTER_END_X: Float = 1.5f

private fun emitter(mesh: Mesh, material: Material, space: ParticleSpace, entity: Entity) =
    ParticleEmitter(
        mesh = mesh,
        material = material,
        origin = Vec3f(EMITTER_START_X, 0f, 0f),
        maxParticles = STEPS * PER_STEP,
        spawnRate = PER_STEP / STEP_SECONDS,
        lifetime = STEPS * STEP_SECONDS * 2f,
        startAlpha = 1f,
        scale = SPRITE_SCALE,
        motion = ParticleMotion(baseVelocity = Vec3f(0f, RISE_SPEED, 0f), space = space),
        dynamics = ParticleDynamics(followEntity = entity),
    ).apply { random = Random(SEED) }

/** Where the scenario's entity is: a position and its matrix, kept equal, with no rotation. */
private class Carrier {
    val position = Vec3f(EMITTER_START_X, 0f, 0f)
    val matrix = Mat4()

    fun moveTo(x: Float) {
        position.set(x, 0f, 0f)
        matrix.m03 = x
    }
}

/** Places an entity by its [Carrier], as an app's own placement would; parity has no scene. */
private object CarrierPlacement : EmitterPlacement {
    override fun position(world: World, entity: Entity, into: Vec3f): Boolean {
        val carrier = world.get<Carrier>(entity) ?: return false
        into.set(carrier.position)
        return true
    }

    override fun orientation(world: World, entity: Entity): Mat4? = world.get<Carrier>(entity)?.matrix
}

private const val STEPS = 24
private const val PER_STEP = 2
private const val STEP_SECONDS = 1f / 24f
private const val RISE_SPEED = 1.5f
private const val SPRITE_SCALE = 0.25f
private const val EYE_DISTANCE = 5f
private const val VIEW_HALF_HEIGHT = 2f
private const val SEED = 1400
private const val RGBA = 4
