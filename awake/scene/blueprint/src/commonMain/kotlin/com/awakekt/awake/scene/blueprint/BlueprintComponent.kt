/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.blueprint

import com.awakekt.awake.blueprint.BlueprintInstance
import com.awakekt.awake.scene.binding.RuntimeOnly
import com.awakekt.awake.scene.binding.SceneBinding
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

/**
 * Runs the blueprint at [graph] on its entity. [BlueprintSystem] starts it on its next fixed step.
 *
 * @property graph The graph's path, as [BlueprintSystem]'s graph source understands it.
 * @property variables Starting values for the graph's variables, by name, set before On Start fires.
 */
class BlueprintComponent(
    val graph: String,
    val variables: Map<String, JsonPrimitive> = emptyMap(),
) {
    /** The running instance once started, for reading variables and the trace. */
    @RuntimeOnly
    var instance: BlueprintInstance? = null
        internal set
}

/**
 * The `blueprint` scene component: a graph path plus variable overrides.
 *
 * @property graph The asset path to the serialized blueprint node graph.
 * @property variables Initial variable override mapping applied prior to graph execution.
 */
@Serializable
@SerialName("blueprint")
@SceneBinding(BlueprintComponent::class, name = "BlueprintBinding")
data class SceneBlueprint(
    val graph: String,
    val variables: Map<String, JsonPrimitive> = emptyMap(),
) : SceneComponent {
    /**
     * Validates that the blueprint references a non-blank graph asset path.
     *
     * @param path The JSON/document tree path to this component.
     * @return List of validation issues discovered, or empty list if valid.
     */
    override fun validate(path: String): List<SceneValidationIssue> =
        if (graph.isBlank()) listOf(SceneValidationIssue(path, "blueprint.graph must name a graph")) else emptyList()
}

/**
 * Registers the `blueprint` scene component with this registry.
 *
 * @return This registry instance with blueprint bindings registered.
 */
fun SceneComponentRegistry.registerBlueprints(): SceneComponentRegistry = register(BlueprintSceneComponents)
