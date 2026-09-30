/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.behavior

import com.awakekt.awake.ai.btree.BehaviorStatus
import com.awakekt.awake.ai.btree.BehaviorTreeComponent
import com.awakekt.awake.ai.btree.BehaviorTreeSystem
import com.awakekt.awake.ai.btree.behaviorTree
import com.awakekt.awake.ai.behavior.chase.ChaseBinding
import com.awakekt.awake.ai.behavior.flee.FleeBinding
import com.awakekt.awake.ai.behavior.patrol.PatrolBinding
import com.awakekt.awake.ai.fsm.AiState
import com.awakekt.awake.ai.fsm.AiStateMachine
import com.awakekt.awake.ai.fsm.AiStateMachineComponent
import com.awakekt.awake.ai.fsm.AiStateMachineSystem
import com.awakekt.awake.ai.fsm.StateTransition
import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.navigation.PathRequest
import com.awakekt.awake.navigation.PathRequestSystem
import com.awakekt.awake.navigation.grid.NavGrid
import com.awakekt.awake.navigation.grid.bakeNavGrid
import com.awakekt.awake.scene.authoring.dsl.scene
import com.awakekt.awake.scene.authoring.dsl.transform
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.runtime.DefaultSceneComponentResolvers
import java.io.File
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The "AI" guide shows a patrol, a chase and a flee as a scene document and in the scene DSL, then
 * runs them over a navigation grid. Every sample is included from here.
 */
class AiDocsSampleTest {

    @Test
    fun theSceneDocumentAndTheSceneDslDescribeTheSameBehaviours() {
        val world = World()
        // --8<-- [start:behaviours-dsl]
        world.scene {
            val player = entity("player") { transform() }
            entity("guard") {
                transform(x = -4f, z = -4f)
                with(
                    PatrolBehavior(
                        stops = listOf(Vec3f(-4f, 0f, -4f), Vec3f(4f, 0f, -4f), Vec3f(4f, 0f, 4f)),
                        style = PatrolStyle.PingPong,
                        dwellSeconds = 2f,
                        speed = 1.5f,
                    ),
                )
                with(PathRequest())
            }
            entity("hound") {
                transform(x = 6f)
                with(ChaseBehavior(target = player, speed = 3f))
                with(PathRequest())
            }
            entity("deer") {
                transform(z = 3f)
                with(FleeBehavior(threat = player, panicRadius = 5f, safeRadius = 10f))
                with(PathRequest())
            }
        }
        // --8<-- [end:behaviours-dsl]

        val fromDocument = loadGuardsDocument()

        assertEquals(fromDocument.patrol(), world.patrol())
        assertEquals(fromDocument.chase(), world.chase())
        assertEquals(fromDocument.flee(), world.flee())
    }

    @Test
    fun behavioursWalkOverANavigationGrid() {
        val world = loadGuardsDocument()
        val hound = world.named("hound")
        val deer = world.named("deer")
        val guard = world.named("guard")
        val guardStart = world.position(guard).copy()

        // --8<-- [start:systems]
        val ground = Heightmap(FloatArray(21 * 21), width = 21, depth = 21, scale = Vec3f(1f, 1f, 1f))
        val navMesh = NavGrid(ground.bakeNavGrid(cellSize = 1f))
        val systems = listOf(PatrolAiSystem(), ChaseAiSystem(), FleeAiSystem(), PathRequestSystem(navMesh))

        repeat(300) { systems.forEach { it.update(world, 1f / 30f) } }
        // --8<-- [end:systems]

        assertTrue(world.planarDistance(hound, world.named("player")) < 1f, "the hound reached the player")
        assertTrue(world.planarDistance(deer, world.named("player")) > 5f, "the deer ran out of panic range")
        assertTrue(world.position(guard) != guardStart, "the guard walked its beat")
    }

    @Test
    fun aBehaviourTreePicksABranch() {
        val world = World()
        val entity = world.create()
        // --8<-- [start:behavior-tree]
        val tree = behaviorTree {
            selector {
                sequence {
                    condition { ctx -> ctx.blackboard.get<Boolean>("alarm") == true }
                    action { ctx ->
                        ctx.blackboard["state"] = "alert"
                        BehaviorStatus.SUCCESS
                    }
                }
                action { ctx ->
                    ctx.blackboard["state"] = "idle"
                    BehaviorStatus.SUCCESS
                }
            }
        }
        val brain = BehaviorTreeComponent(tree)
        world.add(entity, brain)

        brain.blackboard["alarm"] = true
        BehaviorTreeSystem().update(world, 1f / 30f)
        // --8<-- [end:behavior-tree]
        assertEquals("alert", brain.blackboard.get<String>("state"))
    }

    @Test
    fun aStateMachineTransitions() {
        val world = World()
        val entity = world.create()
        // --8<-- [start:state-machine]
        val machine = AiStateMachine(
            states = mapOf("idle" to AiState("idle"), "alert" to AiState("alert")),
            transitions = mapOf(
                "idle" to listOf(StateTransition("alert") { ctx -> ctx.blackboard.get<Boolean>("alarm") == true }),
            ),
            initialStateName = "idle",
        )
        val brain = AiStateMachineComponent(machine)
        world.add(entity, brain)

        brain.blackboard["alarm"] = true
        AiStateMachineSystem().update(world, 1f / 30f)
        // --8<-- [end:state-machine]
        assertEquals("alert", machine.currentState.name)
    }

    private fun loadGuardsDocument(): World {
        DefaultSceneComponentResolvers.install()
        // --8<-- [start:load]
        val registry = SceneComponentRegistry().registerAiBehaviors()
        val document = SceneLoader.decode(File(DOCS_SNIPPETS, "world/guards.scene.json").readText())
        val world = SceneLoader.instantiate(document, componentRegistry = registry).world

        // The behaviours ask for routes through a PathRequest, which scene documents do not carry.
        val agents = world.query(PatrolBehavior::class) + world.query(ChaseBehavior::class) + world.query(FleeBehavior::class)
        agents.forEach { world.add(it, PathRequest()) }
        // --8<-- [end:load]
        return world
    }

    private fun World.patrol() = named("guard").let { PatrolBinding.export(this, it, requireNotNull(get<PatrolBehavior>(it))) }

    private fun World.chase() = named("hound").let { ChaseBinding.export(this, it, requireNotNull(get<ChaseBehavior>(it))) }

    private fun World.flee() = named("deer").let { FleeBinding.export(this, it, requireNotNull(get<FleeBehavior>(it))) }

    private fun World.position(entity: Entity): Vec3f = requireNotNull(get<Transform>(entity)).position

    private fun World.planarDistance(a: Entity, b: Entity): Float {
        val pa = position(a)
        val pb = position(b)
        val dx = pa.x - pb.x
        val dz = pa.z - pb.z
        return sqrt(dx * dx + dz * dz)
    }

    private fun World.named(name: String): Entity {
        var found: Entity? = null
        queryEach(Name::class) { entity, value -> if (value.value == name) found = entity }
        return requireNotNull(found) { "no entity named $name" }
    }

    private companion object {
        /** Tests run from the module directory; the snippets live with the docs. */
        val DOCS_SNIPPETS = File("../../../website/docs/snippets")
    }
}
