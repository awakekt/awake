/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.behavior

import com.awakekt.awake.ai.behavior.chase.ChaseBinding
import com.awakekt.awake.ai.behavior.chase.SceneChase
import com.awakekt.awake.ai.behavior.flee.FleeBinding
import com.awakekt.awake.ai.behavior.flee.SceneFlee
import com.awakekt.awake.ai.behavior.patrol.PatrolBinding
import com.awakekt.awake.ai.behavior.patrol.ScenePatrol
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.SceneComponentResolver
import com.awakekt.awake.scene.core.Name

object AiBehaviorBindings {
    val PatrolResolver: SceneComponentBinding<PatrolBehavior, ScenePatrol> = PatrolBinding
    val ChaseResolver: SceneComponentBinding<ChaseBehavior, SceneChase> = ChaseBinding
    val FleeResolver: SceneComponentBinding<FleeBehavior, SceneFlee> = FleeBinding

    val bindings: List<SceneComponentBinding<*, *>> = listOf(
        PatrolBinding,
        ChaseBinding,
        FleeBinding,
    )

    val all: List<SceneComponentResolver> = listOf(
        PatrolBinding,
        ChaseBinding,
        FleeBinding,
    )
}

fun SceneComponentRegistry.registerAiBehaviors(): SceneComponentRegistry {
    AiBehaviorBindings.all.forEach { register(it) }
    return this
}

internal fun World.referenceName(owner: Entity, reference: Entity?, field: String): String? {
    if (reference == null) return null
    return requireNotNull(get<Name>(reference)?.value) {
        "Cannot export $owner: $field points at $reference, which has no Name to refer to it by."
    }
}
