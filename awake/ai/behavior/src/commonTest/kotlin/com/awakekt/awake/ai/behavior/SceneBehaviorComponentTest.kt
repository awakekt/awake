/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.behavior

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.fromWorld
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.ai.behavior.chase.SceneChase
import com.awakekt.awake.ai.behavior.flee.SceneFlee
import com.awakekt.awake.ai.behavior.patrol.ScenePatrol
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.document.SceneValidator
import com.awakekt.awake.scene.document.SceneVec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Behaviour *parameters* are authored data; which behaviour runs when is not. These cover the two
 * ways that data can be quietly lost — a value that does not survive the round trip, and a node
 * reference that resolves to nothing — plus the validation that catches a bad value before a
 * designer has to notice an NPC behaving oddly.
 */
class SceneBehaviorComponentTest {

    private fun aiRegistry(): SceneComponentRegistry = SceneComponentRegistry().registerAiBehaviors()

    @Test
    fun patrolParametersSurviveTheRoundTrip() {
        val document = SceneDocument(
            nodes = listOf(
                SceneNode(
                    name = "guard",
                    components = listOf(
                        ScenePatrol(
                            stops = listOf(SceneVec3(1f, 0f, 2f), SceneVec3(5f, 0f, 6f)),
                            style = ScenePatrol.Style.PingPong,
                            dwellSeconds = 2.5f,
                            speed = 1.25f,
                        ),
                    ),
                ),
            ),
        )

        val scene = SceneLoader.instantiate(document, componentRegistry = aiRegistry())
        val patrol = assertNotNull(scene.world.firstPatrol())
        assertEquals(listOf(Vec3f(1f, 0f, 2f), Vec3f(5f, 0f, 6f)), patrol.stops)
        assertEquals(PatrolStyle.PingPong, patrol.style)
        assertEquals(2.5f, patrol.dwellSeconds)
        assertEquals(1.25f, patrol.speed)

        val exported = SceneLoader.fromWorld(scene.world, componentRegistry = aiRegistry()).nodes.single().components.single()
        assertEquals(document.nodes.single().components.single(), exported)
    }

    @Test
    fun aChaseTargetIsResolvedFromTheNodeItNames() {
        val document = SceneDocument(
            nodes = listOf(
                SceneNode(name = "player"),
                SceneNode(name = "hunter", components = listOf(SceneChase(target = "player", speed = 4f))),
            ),
        )

        val scene = SceneLoader.instantiate(document, componentRegistry = aiRegistry())

        val chase = assertNotNull(scene.world.firstChase())
        val target = assertNotNull(chase.target)
        assertEquals("player", scene.world.get<Name>(target)?.value)
        assertEquals(4f, chase.speed)
    }

    /** A chaser whose quarry spawns at runtime is the reason null is allowed at all. */
    @Test
    fun anUnnamedTargetLoadsAsNoTarget() {
        val document = SceneDocument(nodes = listOf(SceneNode(name = "hunter", components = listOf(SceneChase()))))

        val scene = SceneLoader.instantiate(document, componentRegistry = aiRegistry())

        assertNull(assertNotNull(scene.world.firstChase()).target)
    }

    /** The other case is a content error, and silence would show up as an NPC that never moves. */
    @Test
    fun aTargetNamingNothingIsRejected() {
        val document = SceneDocument(
            nodes = listOf(SceneNode(name = "hunter", components = listOf(SceneFlee(threat = "ghost")))),
        )

        val failure = assertFailsWith<IllegalArgumentException> {
            SceneLoader.instantiate(document, componentRegistry = aiRegistry())
        }

        assertEquals(true, failure.message?.contains("ghost"), failure.message)
    }

    @Test
    fun exportingAReferenceToAnUnnamedEntityFailsRatherThanDroppingIt() {
        val world = World()
        val quarry = world.create()
        val hunter = world.create()
        world.add(quarry, Transform())
        world.add(hunter, Transform())
        world.add(hunter, ChaseBehavior(target = quarry))

        assertFailsWith<IllegalArgumentException> {
            SceneLoader.fromWorld(world, componentRegistry = aiRegistry())
        }
    }

    @Test
    fun fleeRadiiThatCannotHystereseAreAValidationIssue() {
        val document = SceneDocument(
            nodes = listOf(
                SceneNode(
                    name = "deer",
                    components = listOf(SceneFlee(panicRadius = 8f, safeRadius = 8f)),
                ),
            ),
        )

        val issues = SceneValidator.validate(document)

        assertEquals(1, issues.size, issues.toString())
        assertEquals(true, issues.single().message.contains("safeRadius"), issues.toString())
    }

    @Test
    fun aStoppedBehaviourIsAValidationIssue() {
        val document = SceneDocument(
            nodes = listOf(SceneNode(name = "guard", components = listOf(ScenePatrol(speed = 0f)))),
        )

        val issues = SceneValidator.validate(document)

        assertEquals(true, issues.single().message.contains("patrol.speed"), issues.toString())
    }

    @Test
    fun fleeParametersSurviveTheRoundTrip() {
        val world = World()
        val threat = world.create()
        val deer = world.create()
        world.add(threat, Transform())
        world.add(threat, Name("wolf"))
        world.add(deer, Transform())
        world.add(deer, Name("deer"))
        world.add(deer, FleeBehavior(threat = threat, panicRadius = 4f, safeRadius = 9f))

        val document = SceneLoader.fromWorld(world, componentRegistry = aiRegistry())
        val exported = document.nodes.first { it.name == "deer" }.components.single()

        assertEquals(SceneFlee(threat = "wolf", panicRadius = 4f, safeRadius = 9f), exported)
    }

    private fun World.firstPatrol(): PatrolBehavior? {
        var found: PatrolBehavior? = null
        family<PatrolBehavior>().forEach { _, patrol -> if (found == null) found = patrol }
        return found
    }

    private fun World.firstChase(): ChaseBehavior? {
        var found: ChaseBehavior? = null
        family<ChaseBehavior>().forEach { _, chase -> if (found == null) found = chase }
        return found
    }
}
