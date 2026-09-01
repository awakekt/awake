/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.showcase

import io.github.awakelab.awake.compose.ui.platform.InputOwnership
import io.github.awakelab.awake.core.input.InputSnapshot
import io.github.awakelab.awake.core.math.Lens
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.scene.controls.GameplayInput
import io.github.awakelab.awake.scene.controls.camera.ActiveCamera
import io.github.awakelab.awake.scene.controls.camera.CameraMode
import io.github.awakelab.awake.scene.controls.camera.CameraRig
import io.github.awakelab.awake.scene.controls.camera.CameraSystem
import io.github.awakelab.awake.scene.core.transform.Transform
import io.github.awakelab.awake.scene.rendering.Camera
import io.github.awakelab.awake.showcase.examples.CharacterExampleDriver
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/**
 * That a camera following a character stays on the character.
 *
 * `CameraSystem` writes the pivot it computes back into `lens.center`, and a rig whose
 * `offsetPosition` is the *same* `Vec3f` as that lens then has its offset overwritten by the
 * pivot. With no target the pivot is the offset and nothing changes; with one the pivot is
 * `target.position + offsetPosition`, so the target's position is added again every frame and the
 * camera accelerates away from the scene. Nothing throws, nothing logs, and the render simply
 * leaves -- which is why this is asserted rather than looked at.
 */
class FollowCameraDoesNotDriftTest {

    /** Nothing pressed, nothing moved: the camera must hold still on its own. */
    private val idleInput = InputSnapshot(
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

    private fun cameraSystem() = CameraSystem(
        inputProvider = { GameplayInput(idleInput, InputOwnership()) },
    )

    @Test
    fun aFollowCameraKeepsItsPivotOnTheTargetOverManyFrames() {
        val world = World()
        val player = world.create()
        world.add(player, Transform(position = Vec3f(2f, 1f, 3f)))

        val cameraEntity = world.create()
        val lens = Lens(
            eye = Vec3f(8f, 6f, 9f),
            center = Vec3f(2f, 1f, 3f),
            fovYRadians = 1f,
            near = 0.1f,
            far = 100f,
        )
        world.add(cameraEntity, Camera(lens = lens))
        world.add(cameraEntity, ActiveCamera())
        // Exactly what EngineShowcaseLoader builds, including sharing the lens's own vector -- the
        // shape this test exists to keep honest.
        world.add(
            cameraEntity,
            CameraRig().apply {
                mode = CameraMode.ThirdPerson
                offsetPosition = lens.center
                needsReset = false
            },
        )

        CharacterExampleDriver.followWithCamera(world, player)
        val rig = world.get<CameraRig>(cameraEntity)!!
        assertNotSame(lens.center, rig.offsetPosition, "the rig must not share the lens's vector")

        repeat(120) { cameraSystem().update(world, 1f / 60f) }

        // The player never moves, so the pivot must not either. Drift here is unbounded: it grows
        // by the player's position every frame.
        assertTrue(
            abs(lens.center.x - 2f) < 0.01f && abs(lens.center.z - 3f) < 0.01f,
            "the pivot drifted off the character to (${lens.center.x}, ${lens.center.z})",
        )
        assertTrue(
            lens.eye.length3() < 50f,
            "the camera left the scene entirely: eye at ${lens.eye.x}, ${lens.eye.y}, ${lens.eye.z}",
        )
    }
}
