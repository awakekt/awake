/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.ai.definition

import com.awakekt.awake.core.schema.AssetReference
import com.awakekt.awake.core.schema.PropertyHint
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.ai.ensurePathRequest
import com.awakekt.awake.scene.binding.SceneBinding
import com.awakekt.awake.scene.binding.SceneBindingHooks
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * An entity that runs the behaviour tree in the definition file [asset], a JSON
 * `BehaviorTreeDefinition` among the project's files.
 *
 * @property asset The definition's path among the project's files.
 * @property seed Seeds the tree's random nodes; left out, each entity gets its own from its handle.
 * @property enabled Whether the tree runs.
 */
@Serializable
@SerialName("behavior_tree")
@SceneBinding(BehaviorTreeAsset::class, name = "BehaviorTreeBinding", hooks = BehaviorTreeBindingHooks::class)
data class SceneBehaviorTree(
    @AssetReference(AI_DEFINITION_ASSET) val asset: String,
    @PropertyHint("Seeds the random nodes; left out, each entity gets its own.") val seed: Long? = null,
    val enabled: Boolean = true,
) : SceneComponent {
    override fun validate(path: String): List<SceneValidationIssue> =
        if (asset.isBlank()) listOf(SceneValidationIssue(path, "behavior_tree.asset must not be blank")) else emptyList()
}

/**
 * An entity that runs the state machine in the definition file [asset], a JSON
 * `StateMachineDefinition` among the project's files.
 *
 * @property asset The definition's path among the project's files.
 * @property seed Seeds the machine's random nodes; left out, each entity gets its own from its handle.
 * @property enabled Whether the machine runs.
 */
@Serializable
@SerialName("state_machine")
@SceneBinding(StateMachineAsset::class, name = "StateMachineBinding", hooks = StateMachineBindingHooks::class)
data class SceneStateMachine(
    @AssetReference(AI_DEFINITION_ASSET) val asset: String,
    @PropertyHint("Seeds the random nodes; left out, each entity gets its own.") val seed: Long? = null,
    val enabled: Boolean = true,
) : SceneComponent {
    override fun validate(path: String): List<SceneValidationIssue> =
        if (asset.isBlank()) listOf(SceneValidationIssue(path, "state_machine.asset must not be blank")) else emptyList()
}

/** The asset kind an editor offers for [SceneBehaviorTree.asset] and [SceneStateMachine.asset]. */
const val AI_DEFINITION_ASSET: String = "ai-definition"

/**
 * The runtime side of [SceneBehaviorTree]: which definition the entity runs.
 * [AiDefinitionAttachSystem] gives the entity its `BehaviorTreeComponent` from it.
 *
 * @property asset The definition's path.
 * @property seed The seed, or `null` for the entity's own.
 * @property enabled Whether the tree runs.
 */
class BehaviorTreeAsset(var asset: String, var seed: Long? = null, var enabled: Boolean = true) {
    internal var builtVersion = 0
}

/**
 * The runtime side of [SceneStateMachine]: which definition the entity runs.
 * [AiDefinitionAttachSystem] gives the entity its `AiStateMachineComponent` from it.
 *
 * @property asset The definition's path.
 * @property seed The seed, or `null` for the entity's own.
 * @property enabled Whether the machine runs.
 */
class StateMachineAsset(var asset: String, var seed: Long? = null, var enabled: Boolean = true) {
    internal var builtVersion = 0
}

internal object BehaviorTreeBindingHooks : SceneBindingHooks<BehaviorTreeAsset, SceneBehaviorTree> {
    override fun afterAttach(
        world: World,
        entity: Entity,
        saved: SceneBehaviorTree,
        live: BehaviorTreeAsset,
        context: SceneResolutionContext,
    ) {
        world.ensurePathRequest(entity)
    }
}

internal object StateMachineBindingHooks : SceneBindingHooks<StateMachineAsset, SceneStateMachine> {
    override fun afterAttach(
        world: World,
        entity: Entity,
        saved: SceneStateMachine,
        live: StateMachineAsset,
        context: SceneResolutionContext,
    ) {
        world.ensurePathRequest(entity)
    }
}
