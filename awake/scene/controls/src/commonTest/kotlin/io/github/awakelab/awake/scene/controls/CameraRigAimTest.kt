/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.controls

import io.github.awakelab.awake.compose.ui.platform.InputOwnership
import io.github.awakelab.awake.core.input.InputSnapshot
import io.github.awakelab.awake.core.math.Lens
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.scene.controls.camera.ActiveCamera
import io.github.awakelab.awake.scene.controls.camera.CameraMode
import io.github.awakelab.awake.scene.controls.camera.CameraRig
import io.github.awakelab.awake.scene.controls.camera.CameraSystem
import io.github.awakelab.awake.scene.controls.camera.aimAt
import io.github.awakelab.awake.scene.rendering.Camera
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Pointing a rig at a shot leaves the shot where it was.
 *
 * This is the round trip the showcase depends on: a scene authors an eye and a centre, a rig is
 * attached so the viewer can orbit, and the picture must not move at the moment it becomes
 * movable.
 *
 * It is checked against `CameraSystem` rather than against the arithmetic, because the arithmetic
 * is exactly what was wrong. Both angles pick up a sign from the eye sitting *behind* the pivot,
 * and the hand-written derivation this replaced had both of them the wrong way round. That did not
 * look like a mirrored shot either -- the orbit mode eases the eye toward where the rig says it
 * belongs, so what a viewer saw was the camera sliding smoothly down through the terrain over
 * about a second.
 */
class CameraRigAimTest {

    /** Every authored viewpoint in the showcase's own scenes, plus the awkward directions. */
    private val shots = listOf(
        Vec3f(5f, 10f, 10f) to Vec3f(0f, 0.5f, 0f), // the heightfield showcase
        Vec3f(2.2f, 1.4f, 2.8f) to Vec3f(0f, 0.5f, 0f), // the skinned ragdoll
        Vec3f(0f, 1f, 3f) to Vec3f(0f, 1f, 0f), // straight along -Z, no pitch at all
        Vec3f(-4f, 3f, -6f) to Vec3f(1f, 0f, 2f), // behind and to the left
    )

    /** Steeper than the rig will hold; see [aShotSteeperThanTheRigIsReframedAtTheLimit]. */
    private val nearlyVertical = Vec3f(0f, 8f, 0.001f) to Vec3f(0f, 0f, 0f)

    @Test
    fun aRigPointedAtSomewhereFramesExactlyThatPlace() {
        shots.forEach { (eye, center) ->
            val world = World()
            val entity = world.create()
            val lens = Lens.perspective(eye = Vec3f(eye.x, eye.y, eye.z), center = Vec3f(center.x, center.y, center.z))
            world.add(entity, Camera(lens, isPrimary = true))
            world.add(entity, ActiveCamera())
            world.add(
                entity,
                CameraRig().apply {
                    mode = CameraMode.ThirdPerson
                    offsetPosition = Vec3f(center.x, center.y, center.z)
                    aimAt(eye, center)
                    // Last, because setting the mode above asks for a reset that would discard the
                    // angles just derived.
                    needsReset = false
                },
            )

            // Enough steps for the orbit mode's easing to arrive; the rig never changes its mind,
            // so this settles rather than converging on something else.
            val system = CameraSystem { GameplayInput(IDLE, InputOwnership()) }
            repeat(SETTLE_STEPS) { system.update(world, STEP) }

            val settled = requireNotNull(world.get(entity, Camera::class)).lens
            assertNear(eye, settled.eye, "the eye moved after the rig was pointed at it, from $eye")
            assertNear(center, settled.center, "the rig framed somewhere other than $center")
        }
    }

    @Test
    fun aShotSteeperThanTheRigIsReframedAtTheLimit() {
        val (eye, center) = nearlyVertical
        val rig = CameraRig().apply { aimAt(eye, center) }

        // Not an exact round trip, on purpose. A rig clamps pitch at 85 degrees, so a shot authored
        // nearly straight down cannot be reproduced -- and reframing it here is the smaller
        // surprise than the first drag snapping it to the limit under the viewer's hand.
        assertTrue(
            abs(abs(rig.pitch) - PITCH_LIMIT) < 0.001f,
            "a near-vertical shot should sit exactly at the rig's limit but sits at ${rig.pitch}",
        )
        // Still looking down at the pivot from above, and still the same distance away: the clamp
        // tilts the shot, it does not move the camera to the other side of what it was framing.
        assertTrue(rig.pitch < 0f, "the camera should still be looking downward")
        assertTrue(
            abs(rig.distance - 8f) < TOLERANCE,
            "the clamp changed how far away the camera is: ${rig.distance}",
        )
    }

    @Test
    fun theMirroredDerivationWouldHaveBeenCaught() {
        // The control. The bug negated neither angle, which sends the eye to the mirror image of
        // where it belongs -- below the ground, for any camera looking down at something. If this
        // ever stops differing, the test above has stopped measuring the sign at all.
        val eye = Vec3f(5f, 10f, 10f)
        val center = Vec3f(0f, 0.5f, 0f)
        val correct = CameraRig().apply { aimAt(eye, center) }
        val mirrored = CameraRig().apply {
            aimAt(Vec3f(center.x - (eye.x - center.x), center.y - (eye.y - center.y), eye.z), center)
        }

        assertTrue(
            abs(correct.pitch - mirrored.pitch) > 0.5f,
            "a mirrored shot derived the same pitch, so the sign is not being tested",
        )
    }

    private fun assertNear(expected: Vec3f, actual: Vec3f, what: String) {
        assertTrue(
            abs(expected.x - actual.x) < TOLERANCE &&
                abs(expected.y - actual.y) < TOLERANCE &&
                abs(expected.z - actual.z) < TOLERANCE,
            "$what -- ended at $actual",
        )
    }

    private companion object {
        val IDLE = InputSnapshot(
            pointerX = 0f,
            pointerY = 0f,
            pointerDown = false,
            scrollDeltaX = 0f,
            scrollDeltaY = 0f,
            keysDown = emptySet(),
            keysPressed = emptySet(),
            keysReleased = emptySet(),
            typedText = "",
            editActions = emptyList(),
        )
        const val STEP = 1f / 60f
        const val SETTLE_STEPS = 240
        const val TOLERANCE = 0.01f

        /** Mirrors CameraSystem's own clamp; see CameraRigAim. */
        val PITCH_LIMIT = (85.0 * kotlin.math.PI / 180.0).toFloat()
    }
}
