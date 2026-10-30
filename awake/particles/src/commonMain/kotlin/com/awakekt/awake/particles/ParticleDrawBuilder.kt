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
    private val cameraBasis = FloatArray(PLANE_BASIS_FLOATS)

    /**
     * Appends one draw packet to [destination] for every emitter in [world] that has live particles in
     * the view [lens] defines, [aspect] wide per unit of height; the emitters' children are drawn too.
     * The particles are culled with that view's frustum, so pass the aspect of the view being drawn.
     *
     * A [ParticleFacing.Flat] emitter draws along the plane of its entity's [placement] orientation
     * (its +X is the quads' right and its -Z their up, so a flat quad faces its +Y), or along the
     * world's ground when [placement] has none.
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
            writePlaneBasis(placement.orientation(world, entity))
            appendDrawCalls(destination, emitter, cameraBasis, frustumPlanes, lens.eye)
        }
    }

    /** Builds and adds [emitter]'s own particle `RenderDrawCommand` (skipped if it has no live particles),
     * then recurses into every [ParticleEmitter.children] entry -- children have no `Entity` of
     * their own, so they're not reachable via `world.family<ParticleEmitter>()` and must be
     * walked here explicitly, same recursion shape [ParticleSystem] already uses to
     * advance them. [cameraBasis] is shared across the whole tree (computed once per frame by the
     * caller), not recomputed per emitter; a [ParticleFacing.Flat] emitter draws along [planeBasis],
     * its entity's plane, which the caller writes first. */
    private fun appendDrawCalls(
        destination: MutableList<RenderDrawCommand>,
        emitter: ParticleEmitter,
        cameraBasis: FloatArray,
        frustumPlanes: List<Plane>,
        eye: Vec3,
    ) {
        emitter.syncToSimulation()
        val sim = emitter.simulation
        val visibleIndices = emitter.visibleIndicesBuffer
        val visibleDistances = emitter.visibleDistancesBuffer
        var visibleCount = 0
        for (slot in 0 until sim.maxParticles) {
            if (sim.alive[slot]) {
                val px = sim.posX[slot]
                val py = sim.posY[slot]
                val pz = sim.posZ[slot]
                val radius = sim.scaleAt(slot)
                if (isSphereInFrustum(frustumPlanes, px, py, pz, radius)) {
                    val dx = px - eye.x
                    val dy = py - eye.y
                    val dz = pz - eye.z
                    visibleIndices[visibleCount] = slot
                    visibleDistances[visibleCount] = dx * dx + dy * dy + dz * dz
                    visibleCount += 1
                }
            }
        }
        sortDescendingByDistance(visibleIndices, visibleDistances, visibleCount)
        val instanceModels = emitter.instanceModelsBuffer
        val instanceColors = emitter.instanceColorsBuffer
        val instanceFrames = emitter.instanceFramesBuffer
        instanceModels.clear()
        instanceColors.clear()
        instanceFrames.clear()
        val visual = emitter.visual
        val stretch = if (visual.stretchWithVelocity) visual.stretchFactor else 0f
        val startColor = visual.startColor
        val endColor = visual.endColor
        for (index in 0 until visibleCount) {
            val slot = visibleIndices[index]
            while (emitter.modelPool.size <= index) emitter.modelPool += Mat4()
            while (emitter.colorPool.size <= index) emitter.colorPool += Vec4()
            val model = emitter.modelPool[index].setParticleInstance(
                sim.posX[slot],
                sim.posY[slot],
                sim.posZ[slot],
                sim.scaleAt(slot),
                sim.velX[slot] * stretch,
                sim.velY[slot] * stretch,
                sim.velZ[slot] * stretch,
                rotation = if (visual.stretchWithVelocity) 0f else sim.rotation[slot],
            )
            instanceModels += model
            val life = sim.lifetime[slot]
            val t = if (life > 0f) (sim.age[slot] / life).coerceIn(0f, 1f) else 1f
            instanceColors += emitter.colorPool[index].also {
                it.x = startColor.x + (endColor.x - startColor.x) * t
                it.y = startColor.y + (endColor.y - startColor.y) * t
                it.z = startColor.z + (endColor.z - startColor.z) * t
                it.w = sim.alphaAt(slot)
            }
            instanceFrames += sim.frameAt(slot).toFloat()
        }
        if (instanceModels.isNotEmpty()) {
            val uniformFloats = emitter.uniformFloatsBuffer
            val basis = if (visual.facing == ParticleFacing.Flat) planeBasis else cameraBasis
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

    private fun sortDescendingByDistance(indices: IntArray, distances: FloatArray, count: Int) {
        for (i in 1 until count) {
            val keySlot = indices[i]
            val keyDist = distances[i]
            var j = i - 1
            while (j >= 0 && distances[j] < keyDist) {
                indices[j + 1] = indices[j]
                distances[j + 1] = distances[j]
                j -= 1
            }
            indices[j + 1] = keySlot
            distances[j + 1] = keyDist
        }
    }

    /** [placed]'s +X as the quad's right and its -Z as the quad's up, so a flat quad faces its +Y;
     * the world's axes without one. */
    private fun writePlaneBasis(placed: Mat4?) {
        planeBasis.writeAxis(0, placed?.m00 ?: 1f, placed?.m10 ?: 0f, placed?.m20 ?: 0f)
        planeBasis.writeAxis(PLANE_UP_OFFSET, -(placed?.m02 ?: 0f), -(placed?.m12 ?: 0f), -(placed?.m22 ?: 1f))
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
