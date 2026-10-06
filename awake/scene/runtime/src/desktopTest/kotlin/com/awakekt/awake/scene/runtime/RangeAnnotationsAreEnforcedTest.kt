/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.schema.NumberRange
import com.awakekt.awake.core.schema.PropertyKind
import com.awakekt.awake.core.schema.PropertySchema
import com.awakekt.awake.scene.document.SceneComponentCatalog
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.document.SceneSerializers
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A `@PropertyRange` is a promise that a value outside it is invalid. For every one on a registered
 * component this loads a value just outside the range and expects the component's own `validate()`
 * to report something it did not report before, so an annotation cannot drift from the rule that
 * enforces it.
 */
class RangeAnnotationsAreEnforcedTest {
    init {
        installEveryComponentKit()
    }

    private val json = SceneSerializers.createJson()

    /** A constrained property, found by [steps] from the component's document root. */
    private class Constrained(val component: String, val steps: List<String>, val schema: PropertySchema)

    private fun constrainedProperties(): List<Constrained> {
        installEveryComponentKit()
        return SceneComponentCatalog.schemas().flatMap { (id, schema) -> constrainedIn(id, emptyList(), schema) }
    }

    private fun constrainedIn(component: String, steps: List<String>, schema: PropertySchema): List<Constrained> =
        schema.children.flatMap { child ->
            val here = steps + child.name
            when {
                child.constraints.range != null -> listOf(Constrained(component, here, child))
                child.kind == PropertyKind.Object -> constrainedIn(component, here, child)
                // Found so a range on a list element is reported by withValue, not silently left unchecked.
                child.kind == PropertyKind.List || child.kind == PropertyKind.Map ->
                    child.element?.takeIf { it.kind == PropertyKind.Object }?.let { constrainedIn(component, here + "[]", it) }.orEmpty()
                else -> emptyList()
            }
        }

    /** Values just outside [range]: on its excluded edge, or one past an included one. */
    private fun outside(range: NumberRange): List<Double> = buildList {
        range.min?.let { add(if (range.exclusiveMin) it else it - 1.0) }
        range.max?.let { add(if (range.exclusiveMax) it else it + 1.0) }
    }

    private fun number(value: Double, kind: PropertyKind): JsonElement =
        if (kind == PropertyKind.Int) JsonPrimitive(value.toLong()) else JsonPrimitive(value)

    /** [root] with the property at [steps] set to [value], leaving every other property as it was. */
    private fun withValue(root: JsonObject, steps: List<String>, value: JsonElement): JsonObject {
        val head = steps.first()
        check("[]" !in steps) { "A range on a list element (${steps.joinToString(".")}) is not checked by this test yet; extend it before annotating one" }
        if (steps.size == 1) return JsonObject(root + (head to value))
        val inner = root[head] as? JsonObject
            ?: error("`$head` has no default object to set `${steps.drop(1).joinToString(".")}` inside; give this test a seed")
        return JsonObject(root + (head to withValue(inner, steps.drop(1), value)))
    }

    private fun issuesFor(component: String, fields: JsonObject): Set<String> {
        val node = json.decodeFromJsonElement(
            SceneNode.serializer(),
            JsonObject(mapOf("components" to JsonArray(listOf(JsonObject(fields + ("component" to JsonPrimitive(component))))))),
        )
        return node.components.single().validate("test").map { it.message }.toSet()
    }

    @Test
    fun everyRangeRejectsAValueJustOutsideIt() {
        val failures = constrainedProperties().flatMap { property ->
            val range = checkNotNull(property.schema.constraints.range)
            val base = property.component.let { id -> SceneComponentCatalog.schema(id)?.default as? JsonObject }
                ?: error("${property.component} has no default document to start from; give this test a seed")
            val before = issuesFor(property.component, base)
            outside(range).mapNotNull { value ->
                val patched = withValue(base, property.steps, number(value, property.schema.kind))
                val added = issuesFor(property.component, patched) - before
                if (added.isEmpty()) "${property.component}.${property.steps.joinToString(".")} = $value is outside $range but validate() reports nothing new" else null
            }
        }

        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun theCheckSeesTheFirstBatch() {
        val paths = constrainedProperties().map { "${it.component}.${it.steps.joinToString(".")}" }

        assertTrue(
            paths.containsAll(listOf("camera.near", "camera.fovYDegrees", "light.shadowDistance", "tone_mapping.exposure", "spin_control.speed")),
            "the annotations this test exists to check were not found: $paths",
        )
    }
}
