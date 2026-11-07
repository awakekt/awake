/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls.camera

import com.awakekt.awake.core.camera.CameraCollision
import com.awakekt.awake.core.camera.CameraCollisionSolver
import com.awakekt.awake.core.camera.CameraGround
import com.awakekt.awake.core.camera.CameraObstruction
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.damp
import com.awakekt.awake.ecs.World
import kotlin.math.tan

/**
 * Where a [CameraSystem] finds the ground and the obstacles a rig's [CameraRig.collision] keeps its
 * eye out of. Controls know neither terrain nor physics, so the host answers: a played project from
 * its terrains and its physics world. A `null` answer skips that check.
 */
interface CameraCollisionProbes {
    /** The ground under the eye, or `null` for none. */
    val ground: CameraGround? get() = null

    /** What stands between the pivot and the eye, or `null` for nothing. */
    val obstruction: CameraObstruction? get() = null

    /** Called once a frame with the world being updated, before any rig is placed. */
    fun prepare(world: World) = Unit

    /** Probes that answer nothing. */
    companion object {
        /** No ground and no obstacles: a rig with collision on keeps its eye where it wants it. */
        val None: CameraCollisionProbes = object : CameraCollisionProbes {}
    }
}

/**
 * One rig's collision state: the eye it would have with nothing in the way, eased as before, and the
 * solver that keeps the eye it shows out of the world. Separate, so the solver's ease-out is not eased
 * a second time by the follow smoothing.
 */
internal class CameraRigCollisionState {
    val solver = CameraCollisionSolver()
    val follow = Vec3f()
    var following = false

    fun reset() {
        solver.reset()
        following = false
    }

    /**
     * Eases the free eye toward [desiredEye] over [halfLife] and writes the eye the world allows into
     * [lens], which already looks at the pivot.
     */
    @Suppress("LongParameterList") // Plain per-frame inputs; a holder would allocate or hide them.
    fun place(
        settings: CameraCollision,
        probes: CameraCollisionProbes,
        lens: Lens,
        desiredEye: Vec3f,
        halfLife: Float,
        delta: Float,
    ) {
        if (!following) {
            follow.set(lens.eye)
            following = true
        }
        follow.damp(desiredEye, halfLife, delta)
        solver.solve(lens.center, follow, settings, probes.ground, probes.obstruction, footprint(lens), delta, lens.eye)
    }

    /** Half the near plane's diagonal, for a view up to [WIDEST_ASPECT] wide, so its corners stay out of a slope. */
    private fun footprint(lens: Lens): Float = lens.near * tan(lens.fovYRadians * HALF) * WIDEST_ASPECT

    private companion object {
        const val HALF = 0.5f

        // Covers a view up to twice as wide as it is tall without knowing the viewport.
        const val WIDEST_ASPECT = 2f
    }
}
