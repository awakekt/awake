/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.physics.character

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.physics.CapsuleShape
import com.awakekt.awake.physics.jolt.createJoltPhysicsWorld
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The legacy scene package remains an adapter over the independently tested capability. */
class KinematicCharacterControllerTest {
    @Test
    fun legacyConstructorAndOperationsRemainUsable() = runTest {
        val world = createJoltPhysicsWorld(gravity = Vec3f(0f, 0f, 0f))
        val character = KinematicCharacterController(
            world,
            CharacterConfig(CapsuleShape(0.9f, 0.3f), crouchHalfHeight = 0.4f, innerBody = true),
            Vec3f(0f, 2f, 0f),
        )
        try {
            assertNotNull(character.body)
            character.move(Vec3f(0.25f, 0f, 0f))
            assertEquals(0.25f, character.position.x)
            assertFalse(character.isGrounded)
            assertEquals(0f, character.groundVelocity.y)
            assertEquals(1f, character.groundNormal.y)
            character.crouch()
            assertTrue(character.isCrouching)
            assertTrue(character.standUp())
            character.teleport(Vec3f(2f, 3f, 4f))
            assertEquals(3f, character.position.y)
            var contacts = 0
            character.onContact = { _, _ -> contacts++ }
            assertNotNull(character.onContact)
            assertEquals(0, contacts)
        } finally {
            character.dispose()
            world.destroy()
        }
    }
}
