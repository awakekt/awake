/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.particles

import com.awakekt.awake.core.math.Frustum
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Plane
import com.awakekt.awake.core.math.Vec3
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.Vec4
import com.awakekt.awake.core.math.planes
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.uniforms.ParticleExtraFields
import com.awakekt.awake.render.passes.uniforms.ParticleExtraUniformLayout
import com.awakekt.awake.render.passes.uniforms.setParticleInstance
import kotlin.math.sqrt

/**
 * Converts live particle emitters into backend-neutral instanced draw packets: it culls each
 * emitter's particles against a view's frustum, sorts them back to front, and packs their positions,
 * sizes, colours and sprite frames the way the particle shader reads them.
 *
 * It takes the view as a [Lens] and finds where an entity is through an [EmitterPlacement], so it
 * works for any camera and any way of placing entities. `awake:scene:rendering` calls it with the
 * scene's camera; an app without a scene calls it with its own.
 */
class ParticleDrawBuilder {
    /** The current emitter entity's quad axes for [ParticleFacing.Flat], laid out like the camera basis. */
    private val planeBasis = FloatArray(PLANE_BASIS_FLOATS)

    /** The current emitter entity's quad axes for [ParticleFacing.Upright]: its +X and +Y. */
    private val uprightBasis = FloatArray(PLANE_BASIS_FLOATS)
    private val cameraBasis = FloatArray(PLANE_BASIS_FLOATS)

    /** The current emitter entity's frame, loaded only when one of its emitters is [ParticleSpace.Local]. */
    private val entityFrame = EmitterFrame()

    /** What a [ParticleSpace.World] pool is drawn through: mapping through it leaves every value exact. */
    private val worldFrame = EmitterFrame()
    private val placedPosition = Vec3f(0f, 0f, 0f)
    private val placedStretch = Vec3f(0f, 0f, 0f)

    /**
     * Appends one draw packet to [destination] for every emitter in [world] that has live particles in
     * the view [lens] defines, [aspect] wide per unit of height; the emitters' children are drawn too.
     * The particles are culled with that view's frustum, so pass the aspect of the view being drawn.
     *
     * A [ParticleFacing.Flat] emitter draws along the plane of its entity's [placement] orientation
     * (its +X is the quads' right and its -Z their up, so a flat quad faces its +Y), or along the
     * world's ground when [placement] has none. A [ParticleFacing.Upright] emitter draws along its
     * entity's XY plane (its +X the quads' right and its +Y their up, so an upright quad faces its +Z),
     * or along the world's XY plane when [placement] has none. A [ParticleSpace.Local] emitter's
     * particles are drawn through its entity's frame as [placement] reports it now, so they move, turn
     * and scale with it.
     */
    fun appendWorldDrawCalls(
        destination: MutableList<RenderDrawCommand>,
        world: World,
        placement: EmitterPlacement,
        lens: Lens,
        aspect: Float,
    ) {
        val family = world.family<ParticleEmitter>()
        if (family.size == 0) return
        val forward = (lens.center - lens.eye).normalized()
        val right = forward.cross(lens.up).normalized()
        val cameraUp = right.cross(forward)
        cameraBasis[0] = right.x
        cameraBasis[1] = right.y
        cameraBasis[2] = right.z
        cameraBasis[3] = 0f
        cameraBasis[PLANE_UP_OFFSET] = cameraUp.x
        cameraBasis[PLANE_UP_OFFSET + 1] = cameraUp.y
        cameraBasis[PLANE_UP_OFFSET + 2] = cameraUp.z
        cameraBasis[PLANE_UP_OFFSET + 3] = 0f
        val frustumPlanes = Frustum.planes(lens, aspect)
        family.forEach { entity, emitter ->
            writeEntityBases(placement.orientation(world, entity))
            if (emitter.keepsAnyLocal()) entityFrame.load(placement, world, entity) else entityFrame.setIdentity()
            appendDrawCalls(destination, emitter, cameraBasis, frustumPlanes, lens.eye)
        }
    }

    /** Builds and adds [emitter]'s own particle `RenderDrawCommand` (skipped if it has no live particles),
     * then recurses into every [ParticleEmitter.children] entry -- children have no `Entity` of
     * their own, so they're not reachable via `world.family<ParticleEmitter>()` and must be
     * walked here explicitly, same recursion shape [ParticleSystem] already uses to
     * advance them. [cameraBasis] is shared across the whole tree (computed once per frame by the
     * caller), not recomputed per emitter; a [ParticleFacing.Flat] emitter draws along [planeBasis],
     * its entity's plane, and a [ParticleFacing.Upright] one along [uprightBasis], its entity's XY plane;
     * the caller writes both first. */
    private fun appendDrawCalls(
        destination: MutableList<RenderDrawCommand>,
        emitter: ParticleEmitter,
        cameraBasis: FloatArray,
        frustumPlanes: List<Plane>,
        eye: Vec3,
    ) {
        val frame = if (emitter.motion.space == ParticleSpace.Local) emitter.frame.apply { copyFrom(entityFrame) } else worldFrame
        emitter.syncToSimulation()
        val sim = emitter.simulation
        val sizeScale = frame.sizeScale
        val placed = placedPosition
        val depthOrder = emitter.depthOrder
        val visibleIndices = depthOrder.indices
        val visibleDistances = depthOrder.distances
        var visibleCount = 0
        for (slot in 0 until sim.maxParticles) {
            if (sim.isAlive(slot)) {
                frame.toWorld(sim.posX[slot], sim.posY[slot], sim.posZ[slot], placed)
                val radius = sim.scaleAt(slot) * sizeScale
                if (isSphereInFrustum(frustumPlanes, placed.x, placed.y, placed.z, radius)) {
                    val dx = placed.x - eye.x
                    val dy = placed.y - eye.y
                    val dz = placed.z - eye.z
                    visibleIndices[visibleCount] = slot
                    visibleDistances[visibleCount] = dx * dx + dy * dy + dz * dz
                    visibleCount += 1
                }
            }
        }
        depthOrder.sortFarToNear(visibleCount)
        val instanceModels = emitter.instanceModelsBuffer
        val instanceColors = emitter.instanceColorsBuffer
        val instanceFrames = emitter.instanceFramesBuffer
        instanceModels.clear()
        instanceColors.clear()
        instanceFrames.clear()
        val visual = emitter.visual
        val stretch = if (visual.stretchWithVelocity) visual.stretchFactor else 0f
        for (index in 0 until visibleCount) {
            val slot = visibleIndices[index]
            while (emitter.modelPool.size <= index) emitter.modelPool += Mat4()
            while (emitter.colorPool.size <= index) emitter.colorPool += Vec4()
            frame.toWorld(sim.posX[slot], sim.posY[slot], sim.posZ[slot], placed)
            val streak = placedStretch
            frame.toWorld(sim.velX[slot] * stretch, sim.velY[slot] * stretch, sim.velZ[slot] * stretch, streak, point = false)
            val model = emitter.modelPool[index].setParticleInstance(
                placed.x,
                placed.y,
                placed.z,
                sim.scaleAt(slot) * sizeScale,
                streak.x,
                streak.y,
                streak.z,
                rotation = if (visual.stretchWithVelocity) 0f else sim.rotation[slot],
            )
            instanceModels += model
            instanceColors += emitter.writeInstanceColor(slot, emitter.colorPool[index])
            instanceFrames += sim.frameAt(slot).toFloat()
        }
        if (instanceModels.isNotEmpty()) {
            val uniformFloats = emitter.uniformFloatsBuffer
            val basis = when (visual.facing) {
                ParticleFacing.Camera -> cameraBasis
                ParticleFacing.Flat -> planeBasis
                ParticleFacing.Upright -> uprightBasis
            }
            basis.copyInto(
                uniformFloats,
                destinationOffset = ParticleExtraUniformLayout.offsetOf(ParticleExtraFields.CameraRight),
            )
            ParticleExtraUniformLayout.writeVec4(
                destination = uniformFloats,
                field = ParticleExtraFields.FrameInfo,
                x = visual.frameCount.toFloat(),
                y = 0f,
                z = 0f,
                w = 0f,
            )
            destination.add(
                RenderDrawCommand(
                    mesh = emitter.mesh,
                    material = emitter.material,
                    instanceModels = instanceModels,
                    instanceColors = instanceColors,
                    instanceFrames = instanceFrames,
                    extraUniformFloats = uniformFloats,
                    model = instanceModels[instanceModels.size / 2],
                    transparent = true,
                    additive = visual.additive,
                ),
            )
        }
        for (i in emitter.children.indices) {
            appendDrawCalls(destination, emitter.children[i], cameraBasis, frustumPlanes, eye)
        }
    }

    private fun isSphereInFrustum(
        planes: List<Plane>,
        x: Float,
        y: Float,
        z: Float,
        radius: Float,
    ): Boolean {
        for (i in planes.indices) {
            val plane = planes[i]
            val n = plane.normal
            if (n.x * x + n.y * y + n.z * z + plane.distance < -radius) return false
        }
        return true
    }

    /**
     * [placed]'s +X as the quad's right in both bases; its -Z as a flat quad's up, so a flat quad faces
     * its +Y, and its +Y as an upright quad's up, so an upright quad faces its +Z. The world's axes
     * without one.
     */
    private fun writeEntityBases(placed: Mat4?) {
        val rightX = placed?.m00 ?: 1f
        val rightY = placed?.m10 ?: 0f
        val rightZ = placed?.m20 ?: 0f
        planeBasis.writeAxis(0, rightX, rightY, rightZ)
        planeBasis.writeAxis(PLANE_UP_OFFSET, -(placed?.m02 ?: 0f), -(placed?.m12 ?: 0f), -(placed?.m22 ?: 1f))
        uprightBasis.writeAxis(0, rightX, rightY, rightZ)
        uprightBasis.writeAxis(PLANE_UP_OFFSET, placed?.m01 ?: 0f, placed?.m11 ?: 1f, placed?.m21 ?: 0f)
    }

    private fun FloatArray.writeAxis(offset: Int, x: Float, y: Float, z: Float) {
        val length = sqrt(x * x + y * y + z * z).takeIf { it > 0f } ?: 1f
        this[offset] = x / length
        this[offset + 1] = y / length
        this[offset + 2] = z / length
        this[offset + 3] = 0f
    }
}

private const val PLANE_UP_OFFSET = 4
private const val PLANE_BASIS_FLOATS = 8
