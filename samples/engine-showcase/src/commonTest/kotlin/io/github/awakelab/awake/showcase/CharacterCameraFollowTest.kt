/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.showcase

import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.scene.controls.camera.ActiveCamera
import io.github.awakelab.awake.scene.controls.camera.CameraMode
import io.github.awakelab.awake.scene.controls.camera.CameraRig
import io.github.awakelab.awake.showcase.examples.CharacterExampleDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * That pointing the orbit rig at the character actually frames the character.
 *
 * `CameraSystem` orbits `target.position + offsetPosition`, so `offsetPosition` means a world
 * point on an untargeted rig and a local offset on a targeted one. The showcase loader fills it
 * with the authored look-at point for the untargeted case; leaving that in place when a target is
 * set puts the pivot metres away from the character, and the demonstration looks broken while
 * every part of it works.
 */
class CharacterCameraFollowTest {

    @Test
    fun targetingTheCharacterReplacesTheAuthoredWorldPointWithALocalOffset() {
        val world = World()
        val player = world.create()
        val camera = world.create()
        world.add(
            camera,
            CameraRig().apply {
                mode = CameraMode.ThirdPerson
                // What EngineShowcaseLoader leaves behind: the scene's authored look-at point.
                offsetPosition = Vec3f(6f, 0.5f, 6f)
                needsReset = false
            },
        )
        world.add(camera, ActiveCamera())

        CharacterExampleDriver.followWithCamera(world, player)

        val rig = world.get<CameraRig>(camera)!!
        assertEquals(player, rig.targetEntity, "the rig has to follow the character")
        assertEquals(0f, rig.offsetPosition.x, "a horizontal offset would orbit beside the character")
        assertEquals(0f, rig.offsetPosition.z, "a horizontal offset would orbit beside the character")
        assertTrue(
            rig.offsetPosition.y in 0f..2f,
            "the pivot belongs on the character, not above the scene: ${rig.offsetPosition.y}",
        )
    }
}
