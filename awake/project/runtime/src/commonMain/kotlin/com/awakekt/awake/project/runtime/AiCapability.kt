/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.ai.behavior.ChaseAiSystem
import com.awakekt.awake.ai.behavior.FleeAiSystem
import com.awakekt.awake.ai.behavior.NavFieldLineOfSight
import com.awakekt.awake.ai.behavior.PatrolAiSystem
import com.awakekt.awake.ai.behavior.PhysicsLineOfSight
import com.awakekt.awake.ai.behavior.registerSpatialKinds
import com.awakekt.awake.ai.btree.BehaviorTreeSystem
import com.awakekt.awake.ai.definition.AiDefinitionLibrary
import com.awakekt.awake.ai.definition.AiRegistry
import com.awakekt.awake.ai.fsm.AiStateMachineSystem
import com.awakekt.awake.ai.perception.FactionTable
import com.awakekt.awake.ai.perception.LineOfSight
import com.awakekt.awake.ai.perception.PerceptionSystem
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.ecs.System
import com.awakekt.awake.navigation.PathRequestSystem
import com.awakekt.awake.navigation.grid.TileField
import com.awakekt.awake.scene.ai.AgentIntentResetSystem
import com.awakekt.awake.scene.ai.AiSceneComponents
import com.awakekt.awake.scene.ai.MovementAgentPlacement
import com.awakekt.awake.scene.ai.chase.SceneChase
import com.awakekt.awake.scene.ai.definition.AiDefinitionAttachSystem
import com.awakekt.awake.scene.ai.definition.SceneBehaviorTree
import com.awakekt.awake.scene.ai.definition.SceneStateMachine
import com.awakekt.awake.scene.ai.flee.SceneFlee
import com.awakekt.awake.scene.ai.patrol.ScenePatrol
import com.awakekt.awake.scene.ai.perception.SceneFactions
import com.awakekt.awake.scene.ai.perception.ScenePerceiver
import com.awakekt.awake.scene.ai.perception.TransformPerceptionSpace
import com.awakekt.awake.scene.character.SceneCharacterController
import com.awakekt.awake.scene.controls.movement.MovementDriver
import com.awakekt.awake.scene.controls.movement.SceneMovementControl
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.navigation.NavigationSceneComponents
import com.awakekt.awake.scene.navigation.SceneNavigation
import com.awakekt.awake.scene.physics.PhysicsBody
import kotlin.reflect.KClass

/**
 * Patrol, chase and flee, and the system that answers their route requests, for a scene that has one
 * of the behaviours and a `navigation` component to route over; and the `behavior_tree` and
 * `state_machine` components, whose definition files it reads and compiles when the scene loads.
 *
 * Definitions may use Core's kinds and the spatial kinds, which move agents as the behaviours do. A
 * definition that cannot be read or compiled stops the project loading, with every problem and the
 * file it is in. The trees and machines run after their entities get them, before the route
 * behaviours, and a scene with a `navigation` component answers their routes too.
 *
 * A scene with a `perceiver` runs perception first, each frame, with the scene's `factions`. Sight is
 * blocked by physics bodies when the host has physics, by the navigation grid's blocked cells when it
 * has one, and by nothing otherwise; at most [MAX_SIGHT_UPDATES] perceivers look in a frame.
 *
 * The behaviours move agents through [MovementAgentPlacement]: an agent with a `movement_control`
 * whose driver is `Agent` is steered through it, so with a `character_controller` walls stop it, and
 * any other agent moves by its transform. Each frame clears those intents first, then runs the
 * behaviours, then the path system: a behaviour asks for a route, and the answer is there for the next
 * frame's step.
 *
 * A project does not load when a scene has the behaviours and no navigation, because nothing could
 * answer them, or an agent with a character controller and no `Agent` movement control, which the
 * controller would hold in place.
 */
internal object AiCapability : SceneCapability {
    override val id = "com.awakekt.awake.ai"
    override val components = AiSceneComponents.bindings + NavigationSceneComponents.bindings

    override suspend fun load(scene: SceneDocument, files: AssetSource, content: SceneContent.Builder) {
        require(!scene.hasRouteBehaviours() || scene.navigation() != null) {
            "the scene has patrol, chase or flee behaviours but no navigation component to route them over"
        }
        val held = scene.nodes.flatMap { it.agentsHeldInPlace() }
        require(held.isEmpty()) {
            "${held.joinToString()}: a patrol, chase or flee agent with a character_controller needs a " +
                "movement_control with \"driver\": \"Agent\" to steer through, or the controller holds it in place"
        }
        val components = scene.nodes.flatMap { it.withDescendants() }.flatMap { it.components }
        val trees = components.filterIsInstance<SceneBehaviorTree>().map { it.asset }.distinct()
        val machines = components.filterIsInstance<SceneStateMachine>().map { it.asset }.distinct()
        if (trees.isEmpty() && machines.isEmpty()) return
        val library = AiDefinitionLibrary(AiRegistry().registerSpatialKinds(MovementAgentPlacement))
        trees.forEach { library.loadTree(it, files.readText(it)) }
        machines.forEach { library.loadStateMachine(it, files.readText(it)) }
        content[AiDefinitions] = library
    }

    override fun plan(scene: SceneDocument, plan: SceneSystemPlan) {
        val navigation = scene.navigation()
        val behaviours = if (navigation == null) emptyList() else ROUTE_BEHAVIOURS.filter { scene.uses(it.type) }
        val definitions = scene.uses(SceneBehaviorTree::class) || scene.uses(SceneStateMachine::class)
        val perceives = scene.uses(ScenePerceiver::class)
        if (behaviours.isEmpty() && !definitions && !perceives) return
        plan.frame("ai-intents") { AgentIntentResetSystem() }
        if (perceives) planPerception(scene, navigation, plan)
        if (definitions) {
            plan.frame("ai-definitions") { AiDefinitionAttachSystem(it.content[AiDefinitions] ?: AiDefinitionLibrary()) }
            plan.frame("ai-trees") { BehaviorTreeSystem() }
            plan.frame("ai-machines") { AiStateMachineSystem() }
        }
        behaviours.forEach { behaviour -> plan.frame(behaviour.name) { behaviour.create() } }
        if (navigation != null) {
            val grid = navigation.toGrid()
            plan.frame("path-requests") { PathRequestSystem(grid) }
        }
    }
}

/** Perception over the scene's transforms and factions, seeing through physics, the grid, or nothing. */
private fun planPerception(scene: SceneDocument, navigation: SceneNavigation?, plan: SceneSystemPlan) {
    val factions = scene.nodes.flatMap { it.withDescendants() }.flatMap { it.components }
        .filterIsInstance<SceneFactions>().firstOrNull()?.toTable() ?: FactionTable()
    val grid = navigation?.toGrid()
    plan.frame("ai-perception") { services ->
        val physics = services.physics
        val sight = when {
            physics != null -> PhysicsLineOfSight(physics, bodyOf = { world, entity -> world.get<PhysicsBody>(entity)?.handle })
            grid != null -> NavFieldLineOfSight(TileField(grid.tile))
            else -> LineOfSight.Clear
        }
        PerceptionSystem(TransformPerceptionSpace, factions, sight, maxSightUpdates = MAX_SIGHT_UPDATES)
    }
}

/** The most perceivers that look in one frame of a played scene; the rest look on the next. */
private const val MAX_SIGHT_UPDATES = 128

/** The compiled behaviour tree and state machine definitions a scene's entities name. */
internal val AiDefinitions: SceneContentKey<AiDefinitionLibrary> = SceneContentKey("AI definitions")

private fun SceneNode.withDescendants(): List<SceneNode> = listOf(this) + children.flatMap { it.withDescendants() }

/** A behaviour that asks for routes: its component and the system that runs it. */
private class RouteBehaviour(val type: KClass<out SceneComponent>, val name: String, val create: () -> System)

/** The behaviours that need a `navigation` component to route over, in the order they run. */
private val ROUTE_BEHAVIOURS = listOf(
    RouteBehaviour(ScenePatrol::class, "ai-patrol") { PatrolAiSystem(MovementAgentPlacement) },
    RouteBehaviour(SceneChase::class, "ai-chase") { ChaseAiSystem(MovementAgentPlacement) },
    RouteBehaviour(SceneFlee::class, "ai-flee") { FleeAiSystem(MovementAgentPlacement) },
)

/** The names of this node and its descendants that have a route behaviour and a character controller but no `Agent` movement control. */
private fun SceneNode.agentsHeldInPlace(): List<String> {
    val behaves = components.any { component -> ROUTE_BEHAVIOURS.any { it.type.isInstance(component) } }
    val controlled = components.any { it is SceneCharacterController }
    val steerable = components.any { it is SceneMovementControl && it.driver == MovementDriver.Agent }
    val own = if (behaves && controlled && !steerable) listOf(name ?: "an unnamed node") else emptyList()
    return own + children.flatMap { it.agentsHeldInPlace() }
}

internal fun SceneDocument.navigation(): SceneNavigation? = nodes.firstNotNullOfOrNull { it.navigation() }

internal fun SceneDocument.hasRouteBehaviours(): Boolean = ROUTE_BEHAVIOURS.any { uses(it.type) }

private fun SceneNode.navigation(): SceneNavigation? =
    components.filterIsInstance<SceneNavigation>().firstOrNull() ?: children.firstNotNullOfOrNull { it.navigation() }
