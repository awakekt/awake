/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.ai.behavior.ChaseAiSystem
import com.awakekt.awake.ai.behavior.FleeAiSystem
import com.awakekt.awake.ai.behavior.PatrolAiSystem
import com.awakekt.awake.navigation.PathRequestSystem
import com.awakekt.awake.scene.ai.TransformAgentPlacement
import com.awakekt.awake.scene.ai.chase.SceneChase
import com.awakekt.awake.scene.ai.flee.SceneFlee
import com.awakekt.awake.scene.ai.patrol.ScenePatrol
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.navigation.SceneNavigation
import com.awakekt.awake.scene.runtime.SceneSystemPhase

/**
 * Patrol, chase and flee, and the system that answers their route requests, for a scene that has one
 * of the behaviours and a `navigation` component to route over.
 *
 * The behaviours run first and the path system after them, the order the AI guide runs them in: a
 * behaviour asks for a route, and the answer is there for the next frame's step. A scene with the
 * behaviours and no navigation gets none of this, because nothing could answer them;
 * [loadPlayableProject] refuses such a project.
 */
internal fun MutableList<PlaySpec>.addAiSpecs(scene: SceneDocument) {
    val navigation = scene.navigation() ?: return
    val behaviours = listOf(
        ScenePatrol::class to PlaySpec("ai-patrol", SceneSystemPhase.Frame) { PatrolAiSystem(TransformAgentPlacement) },
        SceneChase::class to PlaySpec("ai-chase", SceneSystemPhase.Frame) { ChaseAiSystem(TransformAgentPlacement) },
        SceneFlee::class to PlaySpec("ai-flee", SceneSystemPhase.Frame) { FleeAiSystem(TransformAgentPlacement) },
    ).filter { (type, _) -> scene.has(type) }
    if (behaviours.isEmpty()) return
    val grid = navigation.toGrid()
    behaviours.forEach { (_, spec) -> add(spec) }
    add(PlaySpec("path-requests", SceneSystemPhase.Frame) { PathRequestSystem(grid) })
}

/** The first `navigation` component in the scene, or null when it has none. */
internal fun SceneDocument.navigation(): SceneNavigation? = nodes.firstNotNullOfOrNull { it.navigation() }

/** True when the scene has a behaviour that asks for routes, and so needs a `navigation` component to answer it. */
internal fun SceneDocument.hasRouteBehaviours(): Boolean =
    has(ScenePatrol::class) || has(SceneChase::class) || has(SceneFlee::class)

private fun SceneNode.navigation(): SceneNavigation? =
    components.filterIsInstance<SceneNavigation>().firstOrNull() ?: children.firstNotNullOfOrNull { it.navigation() }
