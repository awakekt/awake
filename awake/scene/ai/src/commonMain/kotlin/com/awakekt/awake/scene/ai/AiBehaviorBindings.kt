/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.ai

import com.awakekt.awake.ai.behavior.ChaseBehavior
import com.awakekt.awake.ai.behavior.FleeBehavior
import com.awakekt.awake.ai.behavior.PatrolBehavior
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.navigation.PathRequest
import com.awakekt.awake.scene.ai.chase.ChaseBinding
import com.awakekt.awake.scene.ai.chase.SceneChase
import com.awakekt.awake.scene.ai.flee.FleeBinding
import com.awakekt.awake.scene.ai.flee.SceneFlee
import com.awakekt.awake.scene.ai.patrol.PatrolBinding
import com.awakekt.awake.scene.ai.patrol.ScenePatrol
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.SceneComponentResolver
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.navigation.NavigationBinding
import com.awakekt.awake.scene.navigation.NavigationGrid
import com.awakekt.awake.scene.navigation.SceneNavigation

/**
 * Scene component binding definitions for AI behaviors.
 */
object AiBehaviorBindings {
    /** Resolver binding [PatrolBehavior] to [ScenePatrol]. */
    val PatrolResolver: SceneComponentBinding<PatrolBehavior, ScenePatrol> = PatrolBinding

    /** Resolver binding [ChaseBehavior] to [SceneChase]. */
    val ChaseResolver: SceneComponentBinding<ChaseBehavior, SceneChase> = ChaseBinding

    /** Resolver binding [FleeBehavior] to [SceneFlee]. */
    val FleeResolver: SceneComponentBinding<FleeBehavior, SceneFlee> = FleeBinding

    /** Resolver binding [NavigationGrid] to [SceneNavigation]: where the behaviours may walk. */
    val NavigationResolver: SceneComponentBinding<NavigationGrid, SceneNavigation> = NavigationBinding

    /** All scene component bindings provided by the AI behavior module. */
    val bindings: List<SceneComponentBinding<*, *>> = listOf(
        PatrolBinding,
        ChaseBinding,
        FleeBinding,
        NavigationBinding,
    )

    /** All resolvers provided by the AI behavior module for registration. */
    val all: List<SceneComponentResolver> = listOf(
        PatrolBinding,
        ChaseBinding,
        FleeBinding,
        NavigationBinding,
    )
}

/**
 * Registers all AI behavior scene bindings into this [SceneComponentRegistry].
 *
 * @return This registry instance with AI behaviors registered.
 */
fun SceneComponentRegistry.registerAiBehaviors(): SceneComponentRegistry {
    AiBehaviorBindings.all.forEach { register(it) }
    return this
}

/**
 * Gives [entity] the [PathRequest] its behaviour asks for routes through, unless it has one.
 *
 * A behaviour with no request does nothing, so a scene document that attaches one must attach the
 * other, or every host would have to remember to.
 */
internal fun World.ensurePathRequest(entity: Entity) {
    if (!has(entity, PathRequest::class)) add(entity, PathRequest())
}

internal fun World.referenceName(owner: Entity, reference: Entity?, field: String): String? {
    if (reference == null) return null
    return requireNotNull(get<Name>(reference)?.value) {
        "Cannot export $owner: $field points at $reference, which has no Name to refer to it by."
    }
}
