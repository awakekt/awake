/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.schema.PropertyKind
import com.awakekt.awake.core.schema.PropertySchema

/**
 * One property somewhere below a component's schema root.
 *
 * @property component The component id the property belongs to.
 * @property steps The names from the root to this property. A list or map element is the step `[]`.
 * @property schema The property's own schema.
 */
internal class SchemaNode(val component: String, val steps: List<String>, val schema: PropertySchema) {
    /** The property written as a path, such as `particle_emitter.ground.colliders[].min.x`. */
    val path: String get() = steps.fold(component) { path, step -> if (step == "[]") "$path[]" else "$path.$step" }

    /** Whether the property is a number. */
    val isNumeric: Boolean get() = schema.kind == PropertyKind.Float || schema.kind == PropertyKind.Int
}

/**
 * Every property below [root], at any depth: the children of objects and of vectors and colours, and
 * the element of every list or map whatever its kind, so a scalar element and a vector inside an object
 * element are both reached.
 */
internal fun descendantsOf(component: String, root: PropertySchema): List<SchemaNode> {
    fun below(steps: List<String>, schema: PropertySchema): List<SchemaNode> = buildList {
        schema.children.forEach { child ->
            val childSteps = steps + child.name
            add(SchemaNode(component, childSteps, child))
            addAll(below(childSteps, child))
        }
        schema.element?.let { element ->
            val elementSteps = steps + "[]"
            add(SchemaNode(component, elementSteps, element))
            addAll(below(elementSteps, element))
        }
    }
    return below(emptyList(), root)
}
