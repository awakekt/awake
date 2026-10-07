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
import com.awakekt.awake.scene.ai.AiBehaviorBindings
import com.awakekt.awake.scene.ai.TransformAgentPlacement
import com.awakekt.awake.scene.ai.chase.SceneChase
import com.awakekt.awake.scene.ai.flee.SceneFlee
import com.awakekt.awake.scene.ai.patrol.ScenePatrol
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.navigation.SceneNavigation
import kotlin.reflect.KClass

/**
 * Patrol, chase and flee, and the system that answers their route requests, for a scene that has one
 * of the behaviours and a `navigation` component to route over.
 *
 * The behaviours run first and the path system after them, the order the AI guide runs them in: a
 * behaviour asks for a route, and the answer is there for the next frame's step. A scene with the
 * behaviours and no navigation gets none of this, because nothing could answer them, and a project
 * with such a scene does not load.
 */
internal object AiCapability : SceneCapability {
    override val id = "com.awakekt.awake.ai"
    override val components = AiBehaviorBindings.bindings

    override suspend fun load(scene: SceneDocument, files: AssetSource, content: SceneContent.Builder) {
        require(!scene.hasRouteBehaviours() || scene.navigation() != null) {
            "the scene has patrol, chase or flee behaviours but no navigation component to route them over"
        }
    }

    override fun plan(scene: SceneDocument, plan: SceneSystemPlan) {
        val navigation = scene.navigation() ?: return
        val behaviours = ROUTE_BEHAVIOURS.filter { scene.uses(it.type) }
        if (behaviours.isEmpty()) return
        val grid = navigation.toGrid()
        behaviours.forEach { behaviour -> plan.frame(behaviour.name) { behaviour.create() } }
        plan.frame("path-requests") { PathRequestSystem(grid) }
    }
}

/** A behaviour that asks for routes: its component and the system that runs it. */
private class RouteBehaviour(val type: KClass<out SceneComponent>, val name: String, val create: () -> System)

/** The behaviours that need a `navigation` component to route over, in the order they run. */
private val ROUTE_BEHAVIOURS = listOf(
    RouteBehaviour(ScenePatrol::class, "ai-patrol") { PatrolAiSystem(TransformAgentPlacement) },
    RouteBehaviour(SceneChase::class, "ai-chase") { ChaseAiSystem(TransformAgentPlacement) },
    RouteBehaviour(SceneFlee::class, "ai-flee") { FleeAiSystem(TransformAgentPlacement) },
)

internal fun SceneDocument.navigation(): SceneNavigation? = nodes.firstNotNullOfOrNull { it.navigation() }

internal fun SceneDocument.hasRouteBehaviours(): Boolean = ROUTE_BEHAVIOURS.any { uses(it.type) }

private fun SceneNode.navigation(): SceneNavigation? =
    components.filterIsInstance<SceneNavigation>().firstOrNull() ?: children.firstNotNullOfOrNull { it.navigation() }
