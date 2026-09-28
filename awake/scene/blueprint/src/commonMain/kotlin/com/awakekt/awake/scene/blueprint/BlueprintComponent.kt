/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.blueprint

import com.awakekt.awake.blueprint.BlueprintInstance
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlin.reflect.KClass

/** Runs the blueprint at [graph] on its entity. [BlueprintSystem] starts it on its next fixed step. */
class BlueprintComponent(
    /** The graph's path, as [BlueprintSystem]'s graph source understands it. */
    val graph: String,
    /** Starting values for the graph's variables, by name, set before On Start fires. */
    val variables: Map<String, JsonPrimitive> = emptyMap(),
) {
    /** The running instance once started, for reading variables and the trace. */
    var instance: BlueprintInstance? = null
        internal set
}

/** The `blueprint` scene component: a graph path plus variable overrides. */
@Serializable
@SerialName("blueprint")
data class SceneBlueprint(
    val graph: String,
    val variables: Map<String, JsonPrimitive> = emptyMap(),
) : SceneComponent {
    override fun validate(path: String): List<SceneValidationIssue> =
        if (graph.isBlank()) listOf(SceneValidationIssue(path, "blueprint.graph must name a graph")) else emptyList()
}

object BlueprintBinding : SceneComponentBinding<BlueprintComponent, SceneBlueprint> {
    override val componentClass: KClass<BlueprintComponent> = BlueprintComponent::class
    override val schemaClass: KClass<SceneBlueprint> = SceneBlueprint::class
    override val serializer = SceneBlueprint.serializer()

    override fun attachTyped(world: World, entity: Entity, component: SceneBlueprint, context: SceneResolutionContext) {
        world.add(entity, BlueprintComponent(component.graph, component.variables))
    }

    override fun export(world: World, entity: Entity, component: BlueprintComponent): SceneBlueprint =
        SceneBlueprint(component.graph, component.variables)
}

/** Registers the `blueprint` scene component. */
fun SceneComponentRegistry.registerBlueprints(): SceneComponentRegistry = register(BlueprintBinding)
