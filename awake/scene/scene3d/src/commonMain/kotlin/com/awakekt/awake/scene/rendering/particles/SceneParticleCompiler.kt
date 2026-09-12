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
import com.awakekt.awake.scene.rendering.camera.Camera

/** Converts live particle trees into backend-neutral instanced draw packets. */
internal class SceneParticleCompiler {
    /**
     * Appends every particle emitter in [world] for [camera]. The coordinator delegates the
     * complete particle policy here so it does not know about emitter traversal, billboard basis,
     * or the particle-specific frustum pass.
     */
    fun appendWorldDrawCalls(destination: MutableList<RenderDrawCommand>, world: World, camera: Camera) {
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
        val frustumPlanes = Frustum.planes(camera.lens, DEFAULT_PARTICLE_ASPECT)
        family.forEach { _, emitter ->
            appendDrawCalls(destination, emitter, cameraBasis, frustumPlanes, camera.lens.eye)
        }
    }

    /** Builds and adds [emitter]'s own particle `RenderDrawCommand` (skipped if it has no live particles),
     * then recurses into every [ParticleEmitter.children] entry -- children have no `Entity` of
     * their own, so they're not reachable via `world.family<ParticleEmitter>()` and must be
     * walked here explicitly, same recursion shape [ParticleSystem.simulate] already uses to
     * advance them. [cameraBasis] is shared across the whole tree (computed once per frame by the
     * caller), not recomputed per emitter. */
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
            if (!frustumPlanes.containsSphere(particle.position, particle.scale)) return@forEach
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
            val model = emitter.modelPool[index].setTranslationScale(
                particle.position.x,
                particle.position.y,
                particle.position.z,
                particle.scale,
            )
            // ParticleVisual.stretchWithVelocity: column 1 (m01/m11/m21) is otherwise dead --
            // particle.wgsl only ever reads column 0 (width) and column 3 (center), never
            // column 1's own diagonal scale value `.scale()` happens to leave there -- so a
            // world-space stretch vector rides there for free instead of needing a whole new
            // per-instance GPU buffer/binding just for this one optional capability.
            if (emitter.visual.stretchWithVelocity) {
                val speed = particle.velocity.length3()
                if (speed > 0f) {
                    val stretch = particle.velocity * emitter.visual.stretchFactor
                    model.m01 = stretch.x
                    model.m11 = stretch.y
                    model.m21 = stretch.z
                }
            }
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
                it.w = particle.currentAlpha()
            }
            // Per-PARTICLE desynced sprite-strip frame -- see Particle.currentFrame's own doc
            // comment.
            instanceFrames += particle.currentFrame(emitter)
        }
        if (instanceModels.isNotEmpty()) {
            // Camera basis (shared, every emitter this frame) + this emitter's frame count --
            // see ParticleVisual.frameCount's own doc comment. frameInfo.y is unused/reserved
            // now that frame cycling is per-particle (instanceFrames), not emitter-wide.
            val uniformFloats = emitter.uniformFloatsBuffer
            cameraBasis.copyInto(
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
}

private const val DEFAULT_PARTICLE_ASPECT = 16f / 9f
