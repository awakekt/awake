/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.particles

import com.awakekt.awake.core.math.Frustum
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Plane
import com.awakekt.awake.core.math.Vec3
import com.awakekt.awake.core.math.Vec4
import com.awakekt.awake.core.math.containsSphere
import com.awakekt.awake.core.math.planes
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.uniforms.ParticleExtraFields
import com.awakekt.awake.render.passes.uniforms.ParticleExtraUniformLayout
import com.awakekt.awake.render.passes.uniforms.setParticleInstance
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.camera.Camera
import kotlin.math.sqrt

/** Converts live particle trees into backend-neutral instanced draw packets. */
internal class SceneParticleCompiler {
    /** The current emitter entity's quad axes for [ParticleFacing.Flat], laid out like the camera basis. */
    private val planeBasis = FloatArray(PLANE_BASIS_FLOATS)

    /**
     * Appends every particle emitter in [world] for [camera], culled with the frustum of the view
     * it is drawn in, [aspect] wide per unit of height. The coordinator delegates the complete
     * particle policy here so it does not know about emitter traversal, billboard basis, or the
     * particle-specific frustum pass.
     */
    fun appendWorldDrawCalls(
        destination: MutableList<RenderDrawCommand>,
        world: World,
        camera: Camera,
        aspect: Float,
    ) {
        val family = world.family<ParticleEmitter>()
        if (family.size == 0) return
        val forward = (camera.lens.center - camera.lens.eye).normalized()
        val right = forward.cross(camera.lens.up).normalized()
        val cameraUp = right.cross(forward)
        val cameraBasis = floatArrayOf(
            right.x,
            right.y,
            right.z,
            0f,
            cameraUp.x,
            cameraUp.y,
            cameraUp.z,
            0f,
        )
        val frustumPlanes = Frustum.planes(camera.lens, aspect)
        family.forEach { entity, emitter ->
            writePlaneBasis(world.get<Transform>(entity)?.worldMatrix)
            appendDrawCalls(destination, emitter, cameraBasis, frustumPlanes, camera.lens.eye)
        }
    }

    /** Builds and adds [emitter]'s own particle `RenderDrawCommand` (skipped if it has no live particles),
     * then recurses into every [ParticleEmitter.children] entry -- children have no `Entity` of
     * their own, so they're not reachable via `world.family<ParticleEmitter>()` and must be
     * walked here explicitly, same recursion shape [ParticleSystem.simulate] already uses to
     * advance them. [cameraBasis] is shared across the whole tree (computed once per frame by the
     * caller), not recomputed per emitter; a [ParticleFacing.Flat] emitter draws along [planeBasis],
     * its entity's plane, which the caller writes first. */
    fun appendDrawCalls(
        destination: MutableList<RenderDrawCommand>,
        emitter: ParticleEmitter,
        cameraBasis: FloatArray,
        frustumPlanes: List<Plane>,
        eye: Vec3,
    ) {
        // Reused buffers, cleared (not reallocated) every frame -- see
        // ParticleEmitter.instanceModelsBuffer's own doc comment for why this replaced
        // a `particles.filter{}.map{}.map{}` chain (3 list allocations + no-op work on
        // dead slots, every emitter, every frame). visibleParticlesBuffer holds PARTICLE
        // references (not floats) so it can be frustum-filtered and depth-sorted before the
        // instance buffers are built from it, instead of building instance data first and
        // discovering the order/visibility needs fixing after.
        val visible = emitter.visibleParticlesBuffer
        visible.clear()
        emitter.particles.forEach { particle ->
            if (!particle.alive) return@forEach
            if (!frustumPlanes.containsSphere(particle.position, particle.currentScale(emitter))) return@forEach
            visible += particle
        }
        // Back-to-front (farthest first): alpha-blended particles don't write depth, so draw
        // order IS the only thing deciding which one wins where two overlap -- the standard
        // painter's-algorithm fix. Squared distance, not length3(): the ordering is identical
        // and it skips a sqrt plus a Vec3 allocation per comparison.
        visible.sortByDescending { it.position.squaredDistanceTo(eye) }
        val instanceModels = emitter.instanceModelsBuffer
        val instanceColors = emitter.instanceColorsBuffer
        val instanceFrames = emitter.instanceFramesBuffer
        instanceModels.clear()
        instanceColors.clear()
        instanceFrames.clear()
        visible.forEachIndexed { index, particle ->
            // Pooled and mutated in place -- see ParticleEmitter.modelPool's own doc comment.
            while (emitter.modelPool.size <= index) emitter.modelPool += Mat4()
            while (emitter.colorPool.size <= index) emitter.colorPool += Vec4()
            // ParticleVisual.stretchWithVelocity stretches along the motion; otherwise the stretch is
            // zero, which the shader draws as the plain quad.
            val stretch = if (emitter.visual.stretchWithVelocity) emitter.visual.stretchFactor else 0f
            val model = emitter.modelPool[index].setParticleInstance(
                particle.position.x,
                particle.position.y,
                particle.position.z,
                particle.currentScale(emitter),
                particle.velocity.x * stretch,
                particle.velocity.y * stretch,
                particle.velocity.z * stretch,
            )
            instanceModels += model
            // Per-PARTICLE color+alpha (each ages independently, so a burst's
            // later-spawned particles sit at an earlier point in the emitter's
            // startColor->endColor gradient than its first-spawned ones) -- see
            // Particle.currentColor's own doc comment.
            val color = particle.currentColor(emitter)
            instanceColors += emitter.colorPool[index].also {
                it.x = color.x
                it.y = color.y
                it.z = color.z
                it.w = particle.currentAlpha(emitter)
            }
            // Per-PARTICLE desynced sprite-strip frame -- see Particle.currentFrame's own doc
            // comment.
            instanceFrames += particle.currentFrame(emitter)
        }
        if (instanceModels.isNotEmpty()) {
            // The quad axes (the camera's, or the emitter's plane when flat) + this emitter's frame
            // count -- see ParticleVisual.frameCount's own doc comment. frameInfo.y is unused/reserved
            // now that frame cycling is per-particle (instanceFrames), not emitter-wide.
            val uniformFloats = emitter.uniformFloatsBuffer
            val basis = if (emitter.visual.facing == ParticleFacing.Flat) planeBasis else cameraBasis
            basis.copyInto(
                uniformFloats,
                destinationOffset = ParticleExtraUniformLayout.offsetOf(ParticleExtraFields.CameraRight),
            )
            ParticleExtraUniformLayout.writeVec4(
                destination = uniformFloats,
                field = ParticleExtraFields.FrameInfo,
                x = emitter.visual.frameCount.toFloat(),
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
                    additive = emitter.visual.additive,
                ),
            )
        }
        emitter.children.forEach { child ->
            appendDrawCalls(
                destination,
                child,
                cameraBasis,
                frustumPlanes,
                eye,
            )
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
