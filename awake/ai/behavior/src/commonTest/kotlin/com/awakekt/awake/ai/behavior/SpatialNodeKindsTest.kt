/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.behavior

import com.awakekt.awake.ai.btree.BehaviorStatus
import com.awakekt.awake.ai.btree.BehaviorStatus.FAILURE
import com.awakekt.awake.ai.btree.BehaviorStatus.RUNNING
import com.awakekt.awake.ai.btree.BehaviorStatus.SUCCESS
import com.awakekt.awake.ai.btree.BehaviorTreeComponent
import com.awakekt.awake.ai.btree.BehaviorTreeSnapshot
import com.awakekt.awake.ai.btree.BehaviorTreeSystem
import com.awakekt.awake.ai.definition.AiDefinitionJson
import com.awakekt.awake.ai.definition.AiRegistry
import com.awakekt.awake.ai.definition.BehaviorTreeDefinition
import com.awakekt.awake.ai.definition.NodeDefinition
import com.awakekt.awake.ai.definition.NodeKindId
import com.awakekt.awake.ai.definition.validate
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.navigation.NavMesh
import com.awakekt.awake.navigation.PathRequest
import com.awakekt.awake.navigation.PathRequestSystem
import com.awakekt.awake.navigation.PathStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.math.atan2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpatialNodeKindsTest {
    /** A straight-line route to anywhere except inside the pond, a disc of radius 2 around (20, 0). */
    private object OpenField : NavMesh {
        override fun findPath(start: Vec3f, end: Vec3f): List<Vec3f> =
            if ((end.x - 20f) * (end.x - 20f) + end.z * end.z < 4f) emptyList() else listOf(Vec3f(end.x, 0f, end.z))
    }

    /** [PlacedAgents], remembering the yaw each entity was turned to. */
    private object FacingAgents : AgentPlacement by PlacedAgents {
        val yaw = HashMap<Entity, Float>()

        override fun face(world: World, entity: Entity, directionX: Float, directionZ: Float) {
            yaw[entity] = atan2(directionX, directionZ)
        }
    }

    private val world = World()
    private val registry = AiRegistry().registerSpatialKinds(FacingAgents)
    private val paths = PathRequestSystem(OpenField)
    private val trees = BehaviorTreeSystem()
    private val agent = spawn(0f, 0f).also { world.add(it, PathRequest()) }
    private val other = spawn(10f, 0f)

    private fun spawn(x: Float, z: Float): Entity = world.create().also { world.add(it, Placed(Vec3f(x, 0f, z))) }

    private fun at(entity: Entity) = world.get<Placed>(entity)!!.position

    private fun node(kind: String, params: String, vararg children: NodeDefinition) =
        NodeDefinition(NodeKindId(kind), params = Json.parseToJsonElement(params).jsonObject, children = children.toList())

    private fun run(root: NodeDefinition, seed: Long = 0L): BehaviorTreeComponent {
        val component = registry.compileTree(BehaviorTreeDefinition(root = root)).newComponent(seed)
        world.add(agent, component)
        component.blackboard["other"] = other
        return component
    }

    private fun frames(count: Int, component: BehaviorTreeComponent): List<BehaviorStatus> = List(count) {
        trees.update(world, 0.25f)
        paths.update(world, 0.25f)
        component.activeStatus
    }

    @Test
    fun moveToWalksToAnEntityAndSucceedsThere() {
        val tree = run(node("ai.move-to", """{"target":{"key":"other"},"speed":4,"arriveRadius":0.5}"""))
        val statuses = frames(12, tree)
        assertEquals(RUNNING, statuses.first())
        assertTrue(SUCCESS in statuses, "$statuses")
        assertTrue(groundDistance(at(agent), at(other)) <= 0.5f, "${at(agent)}")
        assertEquals(PathStatus.Idle, world.get<PathRequest>(agent)!!.status)
    }

    @Test
    fun moveToFailsWhenTheTargetCannotBeReachedOrIsUnset() {
        val tree = run(node("ai.move-to", """{"target":{"point":{"x":20,"z":0}}}"""))
        assertTrue(FAILURE in frames(4, tree))
        world.remove(agent, BehaviorTreeComponent::class)
        val unset = run(node("ai.move-to", """{"target":{"key":"nobody"}}"""))
        assertEquals(FAILURE, frames(1, unset).single())
    }

    @Test
    fun followKeepsItsDistanceAndKeepsRunning() {
        val tree = run(node("ai.follow", """{"target":{"key":"other"},"distance":3,"speed":4}"""))
        val statuses = frames(16, tree)
        assertTrue(statuses.all { it == RUNNING })
        val gap = groundDistance(at(agent), at(other))
        assertTrue(gap in 2f..3.01f, "gap $gap")
    }

    @Test
    fun fleeRunsUntilSafe() {
        at(other).set(1f, 0f, 0f)
        val tree = run(node("ai.flee", """{"from":{"key":"other"},"safeDistance":6,"fleeDistance":4,"speed":4}"""))
        val statuses = frames(16, tree)
        assertTrue(SUCCESS in statuses, "$statuses")
        assertTrue(at(agent).x < -4f, "ran the other way: ${at(agent)}")
    }

    @Test
    fun wanderStaysNearHomeAndRepeatsForASeed() {
        fun visits(seed: Long): List<Vec3f> {
            at(agent).set(0f, 0f, 0f)
            world.remove(agent, BehaviorTreeComponent::class)
            world.remove(agent, PathRequest::class)
            world.add(agent, PathRequest())
            val tree = run(node("ai.wander", """{"radius":4,"speed":2}"""), seed)
            return List(30) {
                frames(1, tree)
                Vec3f(at(agent).x, 0f, at(agent).z)
            }
        }
        val first = visits(3)
        assertEquals(first, visits(3))
        assertTrue(first.all { it.x * it.x + it.z * it.z <= 4.6f * 4.6f }, "$first")
        assertTrue(first != visits(4))
    }

    @Test
    fun faceTurnsTowardTheTarget() {
        val tree = run(node("ai.face", """{"target":{"point":{"x":0,"z":-5}}}"""))
        assertEquals(SUCCESS, frames(1, tree).single())
        assertEquals(atan2(0f, -5f), FacingAgents.yaw[agent])
    }

    @Test
    fun distanceAndAreaTestPositions() {
        fun check(kind: String, params: String): BehaviorStatus {
            world.remove(agent, BehaviorTreeComponent::class)
            return frames(1, run(node(kind, params))).single()
        }
        assertEquals(SUCCESS, check("ai.distance", """{"target":{"key":"other"},"op":"<","distance":10.5}"""))
        assertEquals(FAILURE, check("ai.distance", """{"target":{"key":"other"},"distance":9}"""))
        assertEquals(SUCCESS, check("ai.distance", """{"target":{"key":"other"},"op":">=","distance":9}"""))
        assertEquals(SUCCESS, check("ai.in-area", """{"center":{"x":1,"z":1},"radius":2}"""))
        assertEquals(FAILURE, check("ai.in-area", """{"min":{"x":5,"z":-1},"max":{"x":8,"z":1}}"""))
        assertEquals(SUCCESS, check("ai.in-area", """{"min":{"x":5,"z":-1},"max":{"x":12,"z":1},"who":{"key":"other"}}"""))
    }

    @Test
    fun aRestoredMoveContinuesTheSameRoute() {
        val definition = BehaviorTreeDefinition(root = node("ai.move-to", """{"target":{"point":{"x":8,"z":0}},"speed":2}"""))
        val compiled = registry.compileTree(definition)
        val component = compiled.newComponent()
        world.add(agent, component)
        frames(3, component)
        val saved = Json.encodeToString(BehaviorTreeSnapshot.serializer(), component.tree.snapshot())
        val copy = compiled.newTree()
        copy.restore(Json.decodeFromString(BehaviorTreeSnapshot.serializer(), saved))
        assertEquals(component.tree.snapshot(), copy.snapshot())
    }

    @Test
    fun spatialParametersAreCheckedAndDescribed() {
        val problems = registry.validate(
            BehaviorTreeDefinition(root = node("ai.move-to", """{"target":{"key":"a","point":{"x":0,"z":0}}}""")),
        )
        assertEquals("node 0 (ai.move-to): a target is a key or a point, not both or neither.", problems.single().toString())
        assertEquals(
            listOf("target", "arriveRadius", "speed", "repathInterval", "waypointRadius"),
            registry.parameterSchema(NodeKindId("ai.move-to")).children.map { it.name },
        )
        val guard = AiDefinitionJson.Default.decodeTree(
            """
            {"root":{"kind":"ai.sequence","children":[
              {"kind":"ai.move-to","params":{"target":{"point":{"x":3,"z":0}}}},
              {"kind":"ai.face","params":{"target":{"key":"other"}}},
              {"kind":"ai.repeat","params":{"count":2},"children":[{"kind":"ai.wander","params":{"radius":2}}]}
            ]}}
            """.trimIndent(),
        )
        assertEquals(emptyList(), registry.validate(guard))
    }
}
