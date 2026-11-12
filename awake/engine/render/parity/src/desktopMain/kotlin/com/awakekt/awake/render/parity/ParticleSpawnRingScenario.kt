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
import com.awakekt.awake.particles.ParticleFacing
import com.awakekt.awake.particles.ParticleMotion
import com.awakekt.awake.particles.ParticleSystem
import com.awakekt.awake.particles.ParticleVisual
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
import com.awakekt.awake.render.texture.TextureAsset
import kotlinx.coroutines.runBlocking
import kotlin.random.Random

/**
 * A real `awake:particles` [ParticleFacing.Flat] emitter with [ParticleMotion.orientSpawnRing], on an
 * entity at the origin turned a quarter about +X, so its XZ spawn ring and its flat sprites both stand
 * in the world's XY plane, facing the camera on +Z. Its red sprites do not move, so the frame, seen
 * head-on through an orthographic lens as [size]-square RGBA, shows the ring itself: a circle of
 * [RING_RADIUS] around an empty centre.
 *
 * With [orientSpawnRing] `false` the ring stays horizontal and is seen edge on, a flat band of sprites
 * across the middle of the frame, while the sprites still lie flat in the turned plane.
 */
fun Renderer.renderTiltedSpawnRingScene(size: Int, orientSpawnRing: Boolean = true): ByteArray =
    renderSpawnRingScene(size, SpawnRing(turn = quarterAboutX(), orientSpawnRing = orientSpawnRing))

/**
 * One spawn ring of [RING_RADIUS], its sprites [SpawnRing.facing] and [SpawnRing.spriteScale] across,
 * on an entity at the origin turned by [SpawnRing.turn], seen head-on from +Z through an orthographic
 * lens of [RING_VIEW_HALF_HEIGHT] as [size]-square RGBA.
 */
internal class SpawnRing(
    val turn: Mat4,
    val orientSpawnRing: Boolean = true,
    val facing: ParticleFacing = ParticleFacing.Flat,
    val spriteScale: Float = RING_SPRITE_SCALE,
    val texture: TextureAsset = RedSprite,
)

internal fun Renderer.renderSpawnRingScene(size: Int, ring: SpawnRing): ByteArray {
    val target = createRenderTarget(size, size)
    val quad = createMesh(SpriteQuad)
    val material = createMaterial(ParticleUniformLayout, texture = ring.texture)
    return try {
        val world = World()
        val entity = world.create()
        world.add(entity, EmitterTurn(ring.turn))
        world.add(entity, ringEmitter(quad, material, entity, ring))
        val system = ParticleSystem(TurnedPlacement)
        repeat(RING_STEPS) { system.update(world, RING_STEP_SECONDS) }
        val lens = Lens(eye = Vec3f(0f, 0f, RING_EYE_DISTANCE), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 50f).apply {
            projection = Lens.Projection.Orthographic
            orthoHalfHeight = RING_VIEW_HALF_HEIGHT
        }
        val draws = ArrayList<RenderDrawCommand>()
        ParticleDrawBuilder().appendWorldDrawCalls(draws, world, TurnedPlacement, lens, 1f)
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
        runBlocking { readPixels(target) }.data.copyOf(size * size * RING_RGBA)
    } finally {
        quad.destroy()
        material.destroy()
        target.destroy()
    }
}

/** The tilted spawn ring's radius in world units; the frame spans [RING_VIEW_HALF_HEIGHT] each way. */
const val RING_RADIUS: Float = 1.2f

/** Half the frame's height (and width) in world units. */
const val RING_VIEW_HALF_HEIGHT: Float = 2f

private fun ringEmitter(mesh: Mesh, material: Material, entity: Entity, ring: SpawnRing) =
    ParticleEmitter(
        mesh = mesh,
        material = material,
        origin = Vec3f.ZERO,
        maxParticles = RING_STEPS * RING_PER_STEP,
        spawnRate = RING_PER_STEP / RING_STEP_SECONDS,
        lifetime = RING_STEPS * RING_STEP_SECONDS * 2f,
        startAlpha = 1f,
        scale = ring.spriteScale,
        motion = ParticleMotion(baseVelocity = Vec3f.ZERO, spawnRadius = RING_RADIUS, orientSpawnRing = ring.orientSpawnRing),
        dynamics = ParticleDynamics(followEntity = entity),
    ).apply {
        visual = ParticleVisual(facing = ring.facing)
        random = Random(RING_SEED)
    }

/** A quarter turn about +X: the entity's +Y (a flat quad's face) is the world's +Z, its +Z the world's -Y. */
private fun quarterAboutX() = Mat4().apply {
    m11 = 0f
    m21 = 1f
    m12 = -1f
    m22 = 0f
}

/** The emitter entity's rotation. */
private class EmitterTurn(val matrix: Mat4)

/** Places the entity at the origin, turned by its [EmitterTurn]; parity has no scene. */
private object TurnedPlacement : EmitterPlacement {
    override fun position(world: World, entity: Entity, into: Vec3f): Boolean {
        if (world.get<EmitterTurn>(entity) == null) return false
        into.set(0f, 0f, 0f)
        return true
    }

    override fun orientation(world: World, entity: Entity): Mat4? = world.get<EmitterTurn>(entity)?.matrix
}

private const val RING_STEPS = 24
private const val RING_PER_STEP = 3
private const val RING_STEP_SECONDS = 1f / 24f
private const val RING_SPRITE_SCALE = 0.25f
private const val RING_EYE_DISTANCE = 5f
private const val RING_SEED = 1416
private const val RING_RGBA = 4
