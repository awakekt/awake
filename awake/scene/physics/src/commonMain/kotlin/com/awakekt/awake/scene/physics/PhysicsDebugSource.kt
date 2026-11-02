/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.physics

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.diagnostics.DebugLines
import com.awakekt.awake.core.diagnostics.DebugRows
import com.awakekt.awake.core.diagnostics.DebugSource
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.MotionType

/**
 * The physics bodies of [world] on the debug panel and as wireframes over the scene. Its id is [ID], `physics`:
 * `--debug=physics` on desktop, `?debug=physics` on the web.
 *
 * - **Rows**: *Static*, *Dynamic* and *Kinematic*, how many [PhysicsBody] components have each [MotionType];
 *   *Sensors*, how many of them are sensors; and *Unbuilt*, how many have no backing body yet, shown as a warning
 *   when it isn't 0. A body is unbuilt until [PhysicsSystem] next runs, so one that stays unbuilt has no physics
 *   world to live in.
 * - **Lines**: the collider of every entity with a `Transform` and a [PhysicsBody], at that transform, the same
 *   lines [physicsDebugLines] returns. Boxes are their twelve edges; spheres three rings; capsules a ring at each
 *   cap centre, the line between their extremes and four sides; heightfields the outline of their footprint at
 *   their lowest sample; meshes and convex hulls the box around their points. Coloured by motion type: [STATIC],
 *   [DYNAMIC] and [KINEMATIC].
 *
 * It reads the components as they are and measures nothing. Writing its lines and rows allocates nothing; the
 * lines walk a heightfield's samples once a frame to find its lowest one.
 */
class PhysicsDebugSource(private val world: World) : DebugSource {
    override val id: String = ID
    override val title: String = "Physics"

    private val writer = PhysicsDebugWriter()

    override fun rows(out: DebugRows) {
        var static = 0
        var dynamic = 0
        var kinematic = 0
        var sensors = 0
        var unbuilt = 0
        world.queryEach(PhysicsBody::class) { _, body ->
            when (body.motionType) {
                MotionType.STATIC -> static++
                MotionType.DYNAMIC -> dynamic++
                MotionType.KINEMATIC -> kinematic++
            }
            if (body.sensor) sensors++
            if (body.handle == null) unbuilt++
        }
        out.row("Static", static)
        out.row("Dynamic", dynamic)
        out.row("Kinematic", kinematic)
        out.row("Sensors", sensors)
        out.row("Unbuilt", unbuilt, warn = unbuilt > 0)
    }

    override fun lines(out: DebugLines) {
        writer.write(world, out)
    }

    /** The source's id and its colours. */
    companion object {
        /** What turns it on: `--debug=physics`, `?debug=physics`. */
        const val ID: String = "physics"

        /** A static body, the things that never move, drawn coolest so they recede: blue. */
        val STATIC: Color = Color(r = 0.25f, g = 0.55f, b = 0.95f)

        /** A dynamic body, what the solver is actually pushing around: green. */
        val DYNAMIC: Color = Color(r = 0.35f, g = 0.95f, b = 0.45f)

        /** A kinematic body, driven by something outside the simulation: amber. */
        val KINEMATIC: Color = Color(r = 0.95f, g = 0.75f, b = 0.25f)
    }
}
