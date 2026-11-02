/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.perception

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.diagnostics.DebugLines
import com.awakekt.awake.core.diagnostics.DebugRows
import com.awakekt.awake.core.diagnostics.DebugSource
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * What every [Perceiver] in [world] sees, hears and remembers, on the debug panel and over the scene. Its id is
 * [ID], `sight`: `--debug=sight` on desktop, `?debug=sight` on the web.
 *
 * Give it the [PerceptionSpace] the [PerceptionSystem] was made with, so it draws from where the system looks.
 *
 * - **Rows**: *Perceivers*; *In sight*, the entities perceivers see now, summed over them; *Remembered*, the entities
 *   they remember, in sight or not; *Heard*, the perceivers that remember a noise.
 * - **Lines**, for each perceiver, flat at its eye ([SightSettings.eyeHeight] over its position):
 *   - its focus cone ([FOCUS]) and peripheral cone ([PERIPHERAL]): two edge rays out to the cone's range and an arc of
 *     [ARC_SEGMENTS] lines joining them. A cone of 360 degrees is a circle of [CIRCLE_SEGMENTS] lines. A perceiver
 *     with no facing (or one straight up or down) sees with its peripheral cone all round, drawn as a circle, and with
 *     no focus cone, which is not drawn.
 *   - its hearing range for a sound of loudness 1 ([HearingSettings.range]), a circle ([HEARING]).
 *   - a line from its eye to each remembered entity's [PerceivedEntity.lastKnownPosition], [IN_SIGHT] when it is in
 *     sight and [REMEMBERED] when it is only remembered, with a cross of [CROSS_LINES] lines, [CROSS_SIZE] metres
 *     across, where it was.
 *   - a cross in [HEARING] where the noise it remembers came from.
 *
 * The cones are drawn on the ground plane, so they show the sight [PerceptionSystem] tests in three dimensions as
 * seen from above. Attack and aggro ranges are a game's rules, not perception's; a game draws them with a source of
 * its own.
 *
 * It measures nothing: it reads the perceivers' settings and memories, which the system keeps whether or not this
 * source is on. Writing its rows and lines allocates nothing, as it reuses points of its own.
 *
 * @param world The world whose perceivers it shows.
 * @param space Where entities are and which way they face: the one the [PerceptionSystem] uses.
 */
class PerceptionDebugSource(private val world: World, private val space: PerceptionSpace) : DebugSource {
    override val id: String = ID
    override val title: String = "Sight"

    // The eye, the facing and the pen. Reused, so drawing allocates nothing.
    private val eye = Vec3f(0f, 0f, 0f)
    private val facing = Vec3f(0f, 0f, 0f)
    private val a = Vec3f(0f, 0f, 0f)
    private val b = Vec3f(0f, 0f, 0f)

    override fun rows(out: DebugRows) {
        val perceivers = world.family<Perceiver>()
        var inSight = 0
        var remembered = 0
        var heard = 0
        perceivers.forEachComponent { perceiver ->
            val known = perceiver.memory.inOrder
            for (i in known.indices) if (known[i].inSight) inSight++
            remembered += known.size
            if (perceiver.memory.noise != null) heard++
        }
        out.row("Perceivers", perceivers.size)
        out.row("In sight", inSight)
        out.row("Remembered", remembered)
        out.row("Heard", heard)
    }

    override fun lines(out: DebugLines) {
        world.family<Perceiver>().forEach { entity, perceiver ->
            if (space.position(world, entity, eye)) {
                val sight = perceiver.settings.sight
                eye.y += sight.eyeHeight
                val faces = space.forward(world, entity, facing) && facing.x * facing.x + facing.z * facing.z > 0f
                // Measured from +Z towards +X, as a direction (sin, cos) on the ground plane.
                val heading = if (faces) atan2(facing.x, facing.z) else 0f
                if (faces) cone(out, heading, sight.focusAngle, sight.focusRange, FOCUS)
                cone(out, heading, if (faces) sight.peripheralAngle else FULL_TURN_DEGREES, sight.peripheralRange, PERIPHERAL)
                circle(out, perceiver.settings.hearing.range, HEARING)
                memory(out, perceiver.memory)
            }
        }
    }

    private fun memory(out: DebugLines, memory: PerceptionMemory) {
        val known = memory.inOrder
        for (i in known.indices) {
            val remembered = known[i]
            val color = if (remembered.inSight) IN_SIGHT else REMEMBERED
            out.line(eye, remembered.lastKnownPosition, color)
            cross(out, remembered.lastKnownPosition, color)
        }
        memory.noise?.let { cross(out, it.position, HEARING) }
    }

    /**
     * A cone [degrees] wide around [heading], out to [range]: its two edge rays and the arc between them, or a circle
     * when it goes all round. Nothing for a cone of no range.
     */
    private fun cone(out: DebugLines, heading: Float, degrees: Float, range: Float, color: Color) {
        if (range <= 0f) return
        if (degrees >= FULL_TURN_DEGREES) {
            circle(out, range, color)
            return
        }
        val half = degrees * DEGREES_TO_RADIANS / 2f
        val from = heading - half
        val to = heading + half
        moveTo(heading = from, range = range)
        out.line(eye, a, color)
        for (i in 1..ARC_SEGMENTS) lineTo(out, from + (to - from) * i / ARC_SEGMENTS, range, color)
        out.line(eye, a, color)
    }

    /** A circle of [radius] around the eye, in [CIRCLE_SEGMENTS] lines, each starting where the last ended. */
    private fun circle(out: DebugLines, radius: Float, color: Color) {
        if (radius <= 0f) return
        moveTo(heading = 0f, range = radius)
        for (i in 1..CIRCLE_SEGMENTS) lineTo(out, TURN * i / CIRCLE_SEGMENTS, radius, color)
    }

    /** A cross flat at [at], one line along X and one along Z. */
    private fun cross(out: DebugLines, at: Vec3f, color: Color) {
        val half = CROSS_SIZE / 2f
        a.set(at.x - half, at.y, at.z)
        b.set(at.x + half, at.y, at.z)
        out.line(a, b, color)
        a.set(at.x, at.y, at.z - half)
        b.set(at.x, at.y, at.z + half)
        out.line(a, b, color)
    }

    /** Puts the pen [range] from the eye towards [heading]. */
    private fun moveTo(heading: Float, range: Float) {
        a.set(eye.x + range * sin(heading), eye.y, eye.z + range * cos(heading))
    }

    /** A line from where the last one ended to [range] from the eye towards [heading]. */
    private fun lineTo(out: DebugLines, heading: Float, range: Float, color: Color) {
        b.set(eye.x + range * sin(heading), eye.y, eye.z + range * cos(heading))
        out.line(a, b, color)
        a.set(b)
    }

    /** The source's id, and its colours and sizes. */
    companion object {
        /** What turns it on: `--debug=sight`, `?debug=sight`. */
        const val ID: String = "sight"

        /** Lines the arc of a cone narrower than a full turn takes; its two edge rays add two more. */
        const val ARC_SEGMENTS: Int = 16

        /** Lines a full circle takes: a cone all round, or the hearing range. */
        const val CIRCLE_SEGMENTS: Int = 32

        /** Lines a cross at a remembered position or a noise takes. */
        const val CROSS_LINES: Int = 2

        /** How wide a cross is, in metres. */
        const val CROSS_SIZE: Float = 0.5f

        /** The focus cone: amber. */
        val FOCUS: Color = Color(1f, 0.8f, 0.2f)

        /** The peripheral cone: blue. */
        val PERIPHERAL: Color = Color(0.3f, 0.65f, 1f)

        /** The hearing range and the remembered noise: a dim violet. */
        val HEARING: Color = Color(0.45f, 0.35f, 0.6f)

        /** A line to something in sight now: red. */
        val IN_SIGHT: Color = Color(1f, 0.25f, 0.2f)

        /** A line to something only remembered: grey. */
        val REMEMBERED: Color = Color(0.55f, 0.55f, 0.55f)

        private const val FULL_TURN_DEGREES = 360f
        private const val TURN = (2 * PI).toFloat()
        private const val DEGREES_TO_RADIANS = (PI / 180.0).toFloat()
    }
}
