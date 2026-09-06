/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase

import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.physics.BoxShape
import com.awakekt.awake.physics.CapsuleShape
import com.awakekt.awake.physics.ContactPhase
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.jolt.JoltPhysicsWorld
import com.awakekt.awake.scene.physics.character.CharacterConfig
import com.awakekt.awake.scene.physics.character.KinematicCharacterController
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * That a character carrying its own body can be seen by the rest of the simulation.
 *
 * A controller moves by sweeping shapes, so without a body Jolt has nothing to report contacts
 * about and every trigger volume in a game is deaf to the one thing it exists for. This is the
 * whole reason the inner body exists, and it can only be shown against a real Jolt world -- a fake
 * would happily report whatever it was told to.
 */
class CharacterInnerBodyJoltTest {

    private var physics: JoltPhysicsWorld? = null
    private var controller: KinematicCharacterController? = null

    @AfterTest
    fun tearDown() {
        controller?.dispose()
        controller = null
        physics?.destroy()
        physics = null
    }

    private fun world(): JoltPhysicsWorld = JoltPhysicsWorld(gravity = Vec3f(0f, 0f, 0f)).also {
        physics = it
    }

    private fun JoltPhysicsWorld.character(at: Vec3f, innerBody: Boolean) =
        KinematicCharacterController(
            this,
            CharacterConfig(shape = CapsuleShape(halfHeight = 0.9f, radius = 0.3f), innerBody = innerBody),
            at,
        ).also { controller = it }

    @Test
    fun aSensorNoticesACharacterThatWalksIntoIt() {
        val world = world()
        val trigger = world.createBody(
            BoxShape(Vec3f(1.5f, 2f, 1.5f)),
            Vec3f(3f, 0f, 0f),
            Quat.IDENTITY,
            MotionType.STATIC,
            sensor = true,
        )
        val character = world.character(Vec3f(0f, 0f, 0f), innerBody = true)

        var entered = false
        repeat(120) {
            character.move(Vec3f(0.05f, 0f, 0f), deltaTime = 1f / 60f)
            world.step(1f / 60f)
            world.drainContacts { event ->
                if (event.phase == ContactPhase.BEGAN && (event.a == trigger || event.b == trigger)) {
                    entered = true
                }
            }
        }

        assertTrue(entered, "the trigger never noticed the character; it walked to ${character.position}")
    }

    @Test
    fun withoutAnInnerBodyTheSensorStaysDeaf() {
        val world = world()
        val trigger = world.createBody(
            BoxShape(Vec3f(1.5f, 2f, 1.5f)),
            Vec3f(3f, 0f, 0f),
            Quat.IDENTITY,
            MotionType.STATIC,
            sensor = true,
        )
        val character = world.character(Vec3f(0f, 0f, 0f), innerBody = false)

        var entered = false
        repeat(120) {
            character.move(Vec3f(0.05f, 0f, 0f), deltaTime = 1f / 60f)
            world.step(1f / 60f)
            world.drainContacts { event ->
                if (event.phase == ContactPhase.BEGAN && (event.a == trigger || event.b == trigger)) {
                    entered = true
                }
            }
        }

        // The control, and the reason the flag exists at all: a controller with no body is
        // invisible to physics no matter how far it walks. Without this the test above would pass
        // for a sensor that had detected something else entirely.
        assertTrue(character.position.x > 2f, "the character never reached the trigger")
        assertTrue(!entered, "a bodyless character was somehow detected")
    }

    @Test
    fun theCharacterIsNotBlockedByItsOwnBody() {
        val world = world()
        val character = world.character(Vec3f(0f, 0f, 0f), innerBody = true)

        repeat(60) {
            character.move(Vec3f(0.1f, 0f, 0f), deltaTime = 1f / 60f)
            world.step(1f / 60f)
        }

        // A body at the character's own position is nearer than anything else in every sweep it
        // makes, so a controller that could see its own body would stop dead on the first frame.
        // That is what shapeCast's `ignore` is for.
        assertTrue(character.position.x > 5f, "the character stopped on itself: ${character.position}")
    }

    @Test
    fun theControllerStopsAtACrateRatherThanPushingItThrough() {
        val world = world()
        val crate = world.createBody(
            BoxShape(Vec3f(0.4f, 0.4f, 0.4f)),
            Vec3f(2f, 0f, 0f),
            Quat.IDENTITY,
            MotionType.DYNAMIC,
        )
        val character = world.character(Vec3f(0f, 0f, 0f), innerBody = true)

        repeat(180) {
            character.move(Vec3f(0.03f, 0f, 0f), deltaTime = 1f / 60f)
            world.step(1f / 60f)
        }

        // An inner body does NOT give pushing for free, which is worth pinning because the opposite
        // is the obvious guess. The controller sweeps and refuses to move into the crate, so the
        // kinematic body never drives through it and there is no contact to solve. Pushing is still
        // the caller's job, through the impulse it applies on `onContact`.
        var crateX = 2f
        world.forEachBodyTransform { handle, position, _ -> if (handle == crate) crateX = position.x }
        assertTrue(crateX < 2.05f, "the crate moved, so this backend does push after all: x=$crateX")
        // Stopped just short of it: crate face at 1.6, character radius 0.3.
        assertTrue(
            character.position.x in 1f..1.8f,
            "the character did not stop at the crate: ${character.position}",
        )
    }

    @Test
    fun teleportingTakesTheBodyWithIt() {
        val world = world()
        val character = world.character(Vec3f(0f, 0f, 0f), innerBody = true)
        val trigger = world.createBody(
            BoxShape(Vec3f(1.5f, 2f, 1.5f)),
            Vec3f(20f, 0f, 0f),
            Quat.IDENTITY,
            MotionType.STATIC,
            sensor = true,
        )

        character.teleport(Vec3f(20f, 0f, 0f))
        var entered = false
        repeat(30) {
            character.move(Vec3f(0f, 0f, 0f), deltaTime = 1f / 60f)
            world.step(1f / 60f)
            world.drainContacts { event ->
                if (event.phase == ContactPhase.BEGAN && (event.a == trigger || event.b == trigger)) {
                    entered = true
                }
            }
        }

        // A respawn rebuilds the body rather than driving it there: moveKinematic would derive a
        // velocity from the whole distance and send it through the level shoving everything.
        assertTrue(entered, "the body did not follow the character across a teleport")
    }
}
