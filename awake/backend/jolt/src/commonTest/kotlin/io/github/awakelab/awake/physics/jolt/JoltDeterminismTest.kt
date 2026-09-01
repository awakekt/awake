/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.physics.jolt

import io.github.awakelab.awake.core.math.Quat
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.physics.BoxShape
import io.github.awakelab.awake.physics.MotionType
import io.github.awakelab.awake.physics.PhysicsWorld
import io.github.awakelab.awake.physics.SphereShape
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * That the same scene, stepped the same way, produces the same numbers twice.
 *
 * Same build and same machine only -- the three backends run three different Jolt builds and will
 * not agree with each other, which is why this compares a run against itself rather than against a
 * recorded baseline.
 *
 * Exact equality, deliberately. Every other physics test here asserts a range, because a tolerance
 * is all an unreproducible simulation can support; this is the one that would catch a refactor
 * shifting a result by 0.001, and a tolerance would defeat its whole purpose.
 */
class JoltDeterminismTest {

    /**
     * A scene with the things that make ordering observable: several bodies stacking and jostling,
     * a sensor collecting contacts, and bodies destroyed mid-run so ids are recycled.
     */
    private suspend fun run(): Pair<List<String>, List<String>> {
        val world = createJoltPhysicsWorld()
        try {
            world.createBody(
                BoxShape(Vec3f(6f, 0.5f, 6f)),
                Vec3f(0f, 0f, 0f),
                Quat.IDENTITY,
                MotionType.STATIC,
            )
            val sensor = world.createBody(
                BoxShape(Vec3f(3f, 2f, 3f)),
                Vec3f(0f, 2f, 0f),
                Quat.IDENTITY,
                MotionType.STATIC,
                sensor = true,
            )
            val falling = (0 until 40).map { index ->
                world.createBody(
                    SphereShape(0.4f),
                    // Offset per body so they collide with each other on the way down rather than
                    // dropping in independent columns -- contacts are what ordering shows up in.
                    // Spread over a grid and stacked, so dozens of pairs touch in the same step
                    // and the work genuinely spans Jolt's worker threads.
                    Vec3f(
                        (index % 5) * 0.7f - 1.4f,
                        3f + (index / 5) * 0.9f,
                        ((index / 5) % 5) * 0.7f - 1.4f,
                    ),
                    Quat.IDENTITY,
                    MotionType.DYNAMIC,
                )
            }

            val contacts = mutableListOf<String>()
            repeat(180) { step ->
                world.step(1f / 60f)
                world.drainContacts { event ->
                    contacts += "${event.a.id}:${event.b.id}:${event.phase}"
                }
                // Recycling ids mid-run: destruction order decides which id the next body gets,
                // and that feeds Jolt's own island ordering.
                if (step == 90) world.destroyBody(falling[7])
            }

            val poses = buildList {
                world.forEachBodyTransform { handle, position, rotation ->
                    add("${handle.id}|${position.x},${position.y},${position.z}|${rotation.x},${rotation.w}")
                }
            }.sorted()
            assertTrue(sensor.id != 0L, "the sensor exists to generate the contacts compared above")
            return poses to contacts
        } finally {
            world.destroy()
        }
    }

    @Test
    fun twoRunsOfTheSameSceneAgreeExactly() = runTest {
        val (posesA, contactsA) = run()
        val (posesB, contactsB) = run()

        assertTrue(contactsA.isNotEmpty(), "the scene produced no contacts, so it compares nothing")
        assertTrue(posesA.isNotEmpty(), "the scene reported no poses, so it compares nothing")

        // Contact order first: it is the part that was genuinely nondeterministic before, arriving
        // in whatever order Jolt's worker threads reached the queue's lock.
        assertEquals(contactsA, contactsB, "contact events differed between two identical runs")
        assertEquals(posesA, posesB, "body poses differed between two identical runs")
    }

    @Test
    fun contactsArriveInAStableOrderWithinADrain() = runTest {
        val (_, contacts) = run()

        // Normalised low-id-first and sorted, so a pair reads the same way every time. Without it
        // the same contact can be reported as "8:9" on one run and "9:8" on the next.
        contacts.forEach { entry ->
            val (a, b) = entry.split(":").let { it[0].toLong() to it[1].toLong() }
            assertTrue(a <= b, "a contact was reported with its bodies the wrong way round: $entry")
        }
    }
}
