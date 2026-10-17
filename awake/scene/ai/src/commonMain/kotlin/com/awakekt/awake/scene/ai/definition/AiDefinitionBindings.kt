/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.ai.definition

import com.awakekt.awake.ai.btree.BehaviorTreeComponent
import com.awakekt.awake.ai.definition.AiDefinitionLibrary
import com.awakekt.awake.ai.fsm.AiStateMachineComponent
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.ai.ensurePathRequest
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import kotlin.reflect.KClass

/**
 * Binds [SceneBehaviorTree] to [BehaviorTreeAsset]. The entity also gets a `PathRequest`, which the
 * moving spatial kinds route through.
 */
object BehaviorTreeBinding : SceneComponentBinding<BehaviorTreeAsset, SceneBehaviorTree> {
    override val componentClass: KClass<BehaviorTreeAsset> = BehaviorTreeAsset::class
    override val schemaClass: KClass<SceneBehaviorTree> = SceneBehaviorTree::class
    override val serializer = SceneBehaviorTree.serializer()

    override fun attachTyped(world: World, entity: Entity, component: SceneBehaviorTree, context: SceneResolutionContext) {
        world.add(entity, BehaviorTreeAsset(component.asset, component.seed, component.enabled))
        world.ensurePathRequest(entity)
    }

    override fun export(world: World, entity: Entity, component: BehaviorTreeAsset): SceneBehaviorTree =
        SceneBehaviorTree(component.asset, component.seed, component.enabled)
}

/**
 * Binds [SceneStateMachine] to [StateMachineAsset]. The entity also gets a `PathRequest`, which the
 * moving spatial kinds route through.
 */
object StateMachineBinding : SceneComponentBinding<StateMachineAsset, SceneStateMachine> {
    override val componentClass: KClass<StateMachineAsset> = StateMachineAsset::class
    override val schemaClass: KClass<SceneStateMachine> = SceneStateMachine::class
    override val serializer = SceneStateMachine.serializer()

    override fun attachTyped(world: World, entity: Entity, component: SceneStateMachine, context: SceneResolutionContext) {
        world.add(entity, StateMachineAsset(component.asset, component.seed, component.enabled))
        world.ensurePathRequest(entity)
    }

    override fun export(world: World, entity: Entity, component: StateMachineAsset): SceneStateMachine =
        SceneStateMachine(component.asset, component.seed, component.enabled)
}

/**
 * Gives each [BehaviorTreeAsset] entity a `BehaviorTreeComponent`, and each [StateMachineAsset] entity
 * an `AiStateMachineComponent`, built from [library]. Run it before the behaviour systems.
 *
 * An entity is built once the library holds its definition, and rebuilt, fresh, each time the
 * library loads that path again, so an edited file reloads into a running scene. One whose
 * definition the library drops, or never had, has no behaviour until it does. A definition loaded as
 * the other type does not count.
 *
 * @param library The compiled definitions, by path.
 */
class AiDefinitionAttachSystem(private val library: AiDefinitionLibrary) : System {
    private val treesDue = mutableListOf<Pair<Entity, BehaviorTreeAsset>>()
    private val machinesDue = mutableListOf<Pair<Entity, StateMachineAsset>>()

    override fun update(world: World, delta: Float) {
        // Collected first: adding components while a family iterates would move it under the loop.
        world.family<BehaviorTreeAsset>().forEach { entity, asset ->
            if (asset.builtVersion != library.version(asset.asset)) treesDue += entity to asset
        }
        world.family<StateMachineAsset>().forEach { entity, asset ->
            if (asset.builtVersion != library.version(asset.asset)) machinesDue += entity to asset
        }
        for ((entity, asset) in treesDue) {
            asset.builtVersion = library.version(asset.asset)
            val compiled = library.tree(asset.asset)
            if (compiled == null) {
                world.remove<BehaviorTreeComponent>(entity)
            } else {
                world.add(entity, compiled.newComponent(asset.seed ?: entity.packed).apply { enabled = asset.enabled })
            }
        }
        for ((entity, asset) in machinesDue) {
            asset.builtVersion = library.version(asset.asset)
            val compiled = library.stateMachine(asset.asset)
            if (compiled == null) {
                world.remove<AiStateMachineComponent>(entity)
            } else {
                world.add(entity, compiled.newComponent(asset.seed ?: entity.packed).apply { enabled = asset.enabled })
            }
        }
        treesDue.clear()
        machinesDue.clear()
    }
}
