/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.ai.behavior.ChaseAiSystem
import com.awakekt.awake.ai.behavior.FleeAiSystem
import com.awakekt.awake.ai.behavior.PatrolAiSystem
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.ecs.System
import com.awakekt.awake.navigation.PathRequestSystem
import com.awakekt.awake.scene.ai.AgentIntentResetSystem
import com.awakekt.awake.scene.ai.AiBehaviorBindings
import com.awakekt.awake.scene.ai.MovementAgentPlacement
import com.awakekt.awake.scene.ai.chase.SceneChase
import com.awakekt.awake.scene.ai.flee.SceneFlee
import com.awakekt.awake.scene.ai.patrol.ScenePatrol
import com.awakekt.awake.scene.character.SceneCharacterController
import com.awakekt.awake.scene.controls.movement.MovementDriver
import com.awakekt.awake.scene.controls.movement.SceneMovementControl
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.navigation.SceneNavigation
import kotlin.reflect.KClass

/**
 * Patrol, chase and flee, and the system that answers their route requests, for a scene that has one
 * of the behaviours and a `navigation` component to route over.
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
    override val components = AiBehaviorBindings.bindings

    override suspend fun load(scene: SceneDocument, files: AssetSource, content: SceneContent.Builder) {
        require(!scene.hasRouteBehaviours() || scene.navigation() != null) {
            "the scene has patrol, chase or flee behaviours but no navigation component to route them over"
        }
        val held = scene.nodes.flatMap { it.agentsHeldInPlace() }
        require(held.isEmpty()) {
            "${held.joinToString()}: a patrol, chase or flee agent with a character_controller needs a " +
                "movement_control with \"driver\": \"Agent\" to steer through, or the controller holds it in place"
        }
    }

    override fun plan(scene: SceneDocument, plan: SceneSystemPlan) {
        val navigation = scene.navigation() ?: return
        val behaviours = ROUTE_BEHAVIOURS.filter { scene.uses(it.type) }
        if (behaviours.isEmpty()) return
        val grid = navigation.toGrid()
        plan.frame("ai-intents") { AgentIntentResetSystem() }
        behaviours.forEach { behaviour -> plan.frame(behaviour.name) { behaviour.create() } }
        plan.frame("path-requests") { PathRequestSystem(grid) }
    }
}

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
