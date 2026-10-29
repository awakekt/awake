/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.ai

import com.awakekt.awake.ai.behavior.PatrolBehavior
import com.awakekt.awake.ai.behavior.PatrolStyle
import com.awakekt.awake.ai.perception.FactionId
import com.awakekt.awake.ai.perception.HearingSettings
import com.awakekt.awake.ai.perception.Perceivable
import com.awakekt.awake.ai.perception.Perceiver
import com.awakekt.awake.ai.perception.PerceptionKeys
import com.awakekt.awake.ai.perception.PerceptionSettings
import com.awakekt.awake.ai.perception.SightSettings
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.navigation.PathRequest
import com.awakekt.awake.scene.ai.definition.BehaviorTreeAsset
import com.awakekt.awake.scene.ai.definition.BehaviorTreeBinding
import com.awakekt.awake.scene.ai.definition.StateMachineAsset
import com.awakekt.awake.scene.ai.definition.StateMachineBinding
import com.awakekt.awake.scene.ai.patrol.PatrolBinding
import com.awakekt.awake.scene.ai.perception.PerceivableBinding
import com.awakekt.awake.scene.ai.perception.PerceiverBinding
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneSerializers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The saved form each generated `scene:ai` binding writes, byte for byte, recorded from the
 * hand-written bindings before they were generated. Every float is a dyadic fraction, so it prints
 * the same on every target.
 */
class AiBindingGoldenTest {
    @Test
    fun patrolSavesAndLoadsAsTheHandWrittenBindingDidAndAttachesPathRequest() {
        val saved =
            """{"stops":[{"x":1.0,"y":0.0,"z":2.5},{"x":-3.5,"y":0.25,"z":4.0}],""" +
                """"style":"pingPong","dwellSeconds":1.5,"speed":2.25,"repathInterval":0.5,"waypointRadius":0.25}"""
        val dirtyLive = PatrolBehavior(
            stops = listOf(Vec3f(1f, 0f, 2.5f), Vec3f(-3.5f, 0.25f, 4f)),
            style = PatrolStyle.PingPong,
            dwellSeconds = 1.5f,
            speed = 2.25f,
            repathInterval = 0.5f,
            waypointRadius = 0.25f,
        ).apply {
            path = listOf(Vec3f(0f, 0f, 0f))
            waypointIndex = 2
            timeSinceRepath = 0.125f
            stopIndex = 1
            direction = -1
            dwellRemaining = 0.75f
            finished = true
        }
        assertExports(saved, PatrolBinding, dirtyLive)

        val (world, entity, live) = assertRoundTripsWithEntity(saved, PatrolBinding)
        assertEquals(listOf(Vec3f(1f, 0f, 2.5f), Vec3f(-3.5f, 0.25f, 4f)), live.stops)
        assertEquals(PatrolStyle.PingPong, live.style)
        assertEquals(1.5f, live.dwellSeconds)
        assertEquals(2.25f, live.speed)
        assertEquals(0.5f, live.repathInterval)
        assertEquals(0.25f, live.waypointRadius)
        assertEquals(0, live.stopIndex)
        assertEquals(1, live.direction)
        assertEquals(0f, live.dwellRemaining)
        assertFalse(live.finished)
        assertTrue(world.has<PathRequest>(entity))
    }

    @Test
    fun perceiverSavesAndLoadsAsTheHandWrittenBindingDid() {
        val saved =
            """{"faction":"watch","settings":{"sight":{"focusRange":16.0,"focusAngle":60.0,""" +
                """"peripheralRange":6.0,"peripheralAngle":180.0,"eyeHeight":1.5,"lineOfSight":false},""" +
                """"hearing":{"range":8.0,"muffledBy":0.25},"forgetAfter":12.0,"updateInterval":0.25,""" +
                """"keys":{"target":"ai.target","targetPosition":"ai.target.pos","seesHostile":"ai.sees",""" +
                """"hostilesInSight":"ai.count","noisePosition":"ai.noise"}}}"""
        val settings = PerceptionSettings(
            sight = SightSettings(
                focusRange = 16f,
                focusAngle = 60f,
                peripheralRange = 6f,
                peripheralAngle = 180f,
                eyeHeight = 1.5f,
                lineOfSight = false,
            ),
            hearing = HearingSettings(range = 8f, muffledBy = 0.25f),
            forgetAfter = 12f,
            updateInterval = 0.25f,
            keys = PerceptionKeys(
                target = "ai.target",
                targetPosition = "ai.target.pos",
                seesHostile = "ai.sees",
                hostilesInSight = "ai.count",
                noisePosition = "ai.noise",
            ),
        )
        assertExports(saved, PerceiverBinding, Perceiver(FactionId("watch"), settings))

        val live = assertRoundTrips(saved, PerceiverBinding)
        assertEquals(FactionId("watch"), live.faction)
        assertEquals(settings, live.settings)
        assertTrue(live.memory.entities.isEmpty())
    }

    @Test
    fun perceivableSavesAndLoadsAsTheHandWrittenBindingDid() {
        val saved = """{"faction":"wildlife","visible":false,"height":1.25}"""
        assertExports(saved, PerceivableBinding, Perceivable(FactionId("wildlife"), visible = false, height = 1.25f))

        val live = assertRoundTrips(saved, PerceivableBinding)
        assertEquals(FactionId("wildlife"), live.faction)
        assertFalse(live.visible)
        assertEquals(1.25f, live.height)
    }

    @Test
    fun behaviorTreeSavesAndLoadsAsTheHandWrittenBindingDidAndAttachesPathRequest() {
        val defaultSaved = """{"asset":"ai/guard.btree.json","enabled":true}"""
        assertExports(defaultSaved, BehaviorTreeBinding, BehaviorTreeAsset("ai/guard.btree.json"))
        val (world, entity, defaultLive) = assertRoundTripsWithEntity(defaultSaved, BehaviorTreeBinding)
        assertEquals("ai/guard.btree.json", defaultLive.asset)
        assertNull(defaultLive.seed)
        assertTrue(defaultLive.enabled)
        assertTrue(world.has<PathRequest>(entity))

        val seededSaved = """{"asset":"ai/scout.btree.json","seed":42,"enabled":false}"""
        assertExports(seededSaved, BehaviorTreeBinding, BehaviorTreeAsset("ai/scout.btree.json", seed = 42L, enabled = false))
        val seededLive = assertRoundTrips(seededSaved, BehaviorTreeBinding)
        assertEquals("ai/scout.btree.json", seededLive.asset)
        assertEquals(42L, seededLive.seed)
        assertFalse(seededLive.enabled)
    }

    @Test
    fun stateMachineSavesAndLoadsAsTheHandWrittenBindingDidAndAttachesPathRequest() {
        val defaultSaved = """{"asset":"ai/sentry.fsm.json","enabled":true}"""
        assertExports(defaultSaved, StateMachineBinding, StateMachineAsset("ai/sentry.fsm.json"))
        val (world, entity, defaultLive) = assertRoundTripsWithEntity(defaultSaved, StateMachineBinding)
        assertEquals("ai/sentry.fsm.json", defaultLive.asset)
        assertNull(defaultLive.seed)
        assertTrue(defaultLive.enabled)
        assertTrue(world.has<PathRequest>(entity))

        val seededSaved = """{"asset":"ai/turret.fsm.json","seed":7,"enabled":false}"""
        assertExports(seededSaved, StateMachineBinding, StateMachineAsset("ai/turret.fsm.json", seed = 7L, enabled = false))
        val seededLive = assertRoundTrips(seededSaved, StateMachineBinding)
        assertEquals("ai/turret.fsm.json", seededLive.asset)
        assertEquals(7L, seededLive.seed)
        assertFalse(seededLive.enabled)
    }
}

private val json = SceneSerializers.createJson()

private class NoLinks(override val world: World) : SceneResolutionContext {
    override fun deferNodeLink(targetNodeName: String, onResolved: (target: Entity) -> Unit) = Unit

    override fun recordRequest(request: Any) = Unit
}

private fun <C : Any, S : SceneComponent> assertExports(expected: String, binding: SceneComponentBinding<C, S>, live: C) {
    val world = World()
    val entity = world.create()
    world.add(entity, binding.componentClass, live)
    assertEquals(expected, json.encodeToString(binding.serializer!!, binding.exportFrom(world, entity)!!))
}

private fun <C : Any, S : SceneComponent> assertRoundTrips(saved: String, binding: SceneComponentBinding<C, S>): C =
    assertRoundTripsWithEntity(saved, binding).third

private fun <C : Any, S : SceneComponent> assertRoundTripsWithEntity(
    saved: String,
    binding: SceneComponentBinding<C, S>,
): Triple<World, Entity, C> {
    val world = World()
    val entity = world.create()
    binding.attach(world, entity, json.decodeFromString(binding.serializer!!, saved), NoLinks(world))
    assertEquals(saved, json.encodeToString(binding.serializer!!, binding.exportFrom(world, entity)!!))
    return Triple(world, entity, world.get(entity, binding.componentClass)!!)
}
