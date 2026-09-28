/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.blueprint

import com.awakekt.awake.core.animation.AnimationChannel
import com.awakekt.awake.core.animation.AnimationClip
import com.awakekt.awake.core.animation.AnimationLibrary
import com.awakekt.awake.core.animation.AnimationPlayer
import com.awakekt.awake.core.animation.AnimationProperty
import com.awakekt.awake.core.animation.AnimationSampler
import com.awakekt.awake.core.animation.Bone
import com.awakekt.awake.core.animation.Skeleton
import com.awakekt.awake.core.animation.Skin
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.nodegraph.NodeGraph
import com.awakekt.awake.nodegraph.NodeGraphJson
import com.awakekt.awake.physics.BodyHandle
import com.awakekt.awake.physics.BoxShape
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.jolt.JoltPhysicsWorld
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.physics.PhysicsBody
import com.awakekt.awake.scene.physics.PhysicsSystem
import com.awakekt.awake.scene.rendering.animation.Animator
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The phase 2 gate: a door whose sensor a falling box drops into. Its blueprint, authored as data,
 * plays `open`, waits 2 s, then plays `close`. Real Jolt, headless.
 */
class DoorSampleTest {
    private var physicsWorld: JoltPhysicsWorld? = null

    @AfterTest
    fun tearDown() {
        physicsWorld?.destroy()
        physicsWorld = null
    }

    @Test
    fun theDoorOpensWhenABodyEntersAndClosesTwoSecondsLater() {
        assertOpensThenCloses(runDoor(NodeGraphJson.decode(DOOR_GRAPH)))
    }

    @Test
    fun withTheWireAfterTheDelayRemovedTheCheckFails() {
        val door = NodeGraphJson.decode(DOOR_GRAPH)
        val broken = door.copy(edges = door.edges.filterNot { it.fromNode == "wait" })

        assertFailsWith<AssertionError> { assertOpensThenCloses(runDoor(broken)) }
    }

    @Test
    fun aSensorCanDestroyWhatEntersItBodyFirst() {
        val graph = graph {
            node("enter", "event.sensor.enter")
            node("destroy", "entity.destroy")
            wire("enter.then", "destroy.exec")
            wire("enter.other", "destroy.target")
        }
        val systems = scene(graph)
        val world = systems.world
        val box = world.fallingBox()
        systems.step()
        val handle = assertNotNull(world.get<PhysicsBody>(box)?.handle)

        repeat(STEPS) { systems.step() }

        assertFalse(world.isAlive(box))
        assertFalse(physicsWorld!!.simulates(handle), "the body outlived its entity")
    }

    /** Each change of the door's clip, as (fixed step, clip). */
    private fun runDoor(graph: NodeGraph): List<Pair<Int, String?>> {
        val systems = scene(graph)
        val player = systems.world.get<Animator>(systems.door)!!.player
        systems.world.fallingBox()
        val timeline = ArrayList<Pair<Int, String?>>()
        var clip: String? = null
        for (step in 1..STEPS) {
            systems.step()
            if (player.activeClipId != clip) {
                clip = player.activeClipId
                timeline += step to clip
            }
        }
        return timeline
    }

    private fun assertOpensThenCloses(timeline: List<Pair<Int, String?>>) {
        assertEquals(listOf("open", "close"), timeline.map { it.second }, "clips played: $timeline")
        val (opened, closed) = timeline.map { it.first }
        assertTrue(opened > 1, "opened before the box could have reached the sensor: $timeline")
        assertTrue(abs(closed - opened - 2 * STEPS_PER_SECOND) <= 1, "closed ${closed - opened} steps after opening")
    }

    private class Systems(val door: Entity, val world: World, val physics: PhysicsSystem, val blueprints: BlueprintSystem) {
        fun step() {
            physics.update(world, STEP)
            blueprints.update(world, STEP)
        }
    }

    private fun scene(graph: NodeGraph): Systems {
        val jolt = JoltPhysicsWorld().also { physicsWorld = it }
        val world = World()
        val physics = PhysicsSystem(jolt)
        val blueprints = BlueprintSystem(graphs = { graph }, physics = physics)
        val door = world.create()
        world.add(door, Transform())
        world.add(door, PhysicsBody(BoxShape(Vec3f(1f, 1f, 1f)), MotionType.STATIC, sensor = true))
        world.add(door, Animator(AnimationPlayer(doorClips()), Skin(joints = listOf(0), inverseBindMatrices = listOf(Mat4()))))
        world.add(door, BlueprintComponent("door.graph.json"))
        return Systems(door, world, physics, blueprints)
    }

    private fun World.fallingBox(): Entity = create().also { box ->
        add(box, Transform().apply { position.set(Vec3f(0f, 4f, 0f)) })
        add(box, PhysicsBody(BoxShape(Vec3f(0.25f, 0.25f, 0.25f)), MotionType.DYNAMIC))
    }

    private fun doorClips(): AnimationLibrary {
        val skeleton = Skeleton(bones = listOf(Bone(Vec3f.ZERO, Quat.IDENTITY, Vec3f(1f, 1f, 1f), null, emptyList())), roots = listOf(0))
        return AnimationLibrary(skeleton, listOf("open", "close").associateWith(::swing))
    }

    private fun swing(name: String) = AnimationClip(
        name = name,
        channels = listOf(
            AnimationChannel(
                targetBone = 0,
                property = AnimationProperty.Translation,
                sampler = AnimationSampler(times = floatArrayOf(0f, 1f), values = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f), componentsPerKeyframe = 3),
            ),
        ),
    )

    private fun JoltPhysicsWorld.simulates(handle: BodyHandle): Boolean {
        var found = false
        forEachBodyTransform { visited, _, _ -> if (visited == handle) found = true }
        return found
    }

    private companion object {
        const val STEPS_PER_SECOND = 60
        const val STEPS = 5 * STEPS_PER_SECOND

        /** What Studio saves: a sensor entered, open, wait 2 s, close. */
        val DOOR_GRAPH = """
            {
              "kind": "awake.logic.event-graph",
              "nodes": [
                { "id": "enter", "type": "event.sensor.enter" },
                { "id": "open", "type": "animation.play", "config": { "clip": "open" } },
                { "id": "wait", "type": "flow.delay", "config": { "seconds": 2.0 } },
                { "id": "close", "type": "animation.play", "config": { "clip": "close" } }
              ],
              "edges": [
                { "fromNode": "enter", "fromPort": "then", "toNode": "open", "toPort": "exec" },
                { "fromNode": "open", "fromPort": "then", "toNode": "wait", "toPort": "exec" },
                { "fromNode": "wait", "fromPort": "then", "toNode": "close", "toPort": "exec" }
              ]
            }
        """.trimIndent()
    }
}
