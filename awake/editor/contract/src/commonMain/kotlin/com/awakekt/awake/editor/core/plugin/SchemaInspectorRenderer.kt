/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.editor.core.plugin

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.schema.PropertyKind
import com.awakekt.awake.core.schema.PropertySchema
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Turns a [PropertySchema] into calls on [InspectorFieldScope] so that an inspector can be
 * generated automatically without hand-written UI.
 */
@Suppress("TooManyFunctions")
object SchemaInspectorRenderer {

    /**
     * Traverses [schema] and invokes appropriate field methods on [scope].
     *
     * @param schema The property schema (usually of a component or an object property).
     * @param scope The target inspector field scope.
     * @param values Current property values by property name, if known.
     * @param onPropertyChange Callback invoked when a property value changes: `(propertyName, newValue) -> Unit`.
     */
    fun render(
        schema: PropertySchema,
        scope: InspectorFieldScope,
        values: Map<String, Any?> = emptyMap(),
        onPropertyChange: (name: String, value: Any?) -> Unit = { _, _ -> },
    ) {
        val properties = if (schema.kind == PropertyKind.Object) schema.children else listOf(schema)
        for (prop in properties) {
            renderProperty(prop, scope, values[prop.name], onWrite = { onPropertyChange(prop.name, it) })
        }
    }

    private fun renderProperty(
        prop: PropertySchema,
        scope: InspectorFieldScope,
        currentValue: Any?,
        onWrite: (Any?) -> Unit,
    ) {
        if (prop.hints.hidden) return

        val label = prop.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

        if (prop.hints.readOnly) {
            val display = currentValue?.toString() ?: prop.default?.let(::jsonDisplayString) ?: ""
            scope.readOnly(label, display)
        } else if (prop.nullable && prop.kind != PropertyKind.Object) {
            scope.nullable(
                label = label,
                value = currentValue,
                set = { prop.default?.let(::jsonToJavaValue) ?: defaultForKind(prop.kind) },
                clear = { onWrite(null) },
            ) { activeValue ->
                renderValueKind(prop, label, scope, activeValue, onWrite)
            }
        } else {
            renderValueKind(prop, label, scope, currentValue, onWrite)
        }
    }

    private fun renderValueKind(
        prop: PropertySchema,
        label: String,
        scope: InspectorFieldScope,
        currentValue: Any?,
        onWrite: (Any?) -> Unit,
    ) {
        when (prop.kind) {
            PropertyKind.Float -> renderFloat(prop, label, scope, currentValue, onWrite)
            PropertyKind.Int -> renderInt(prop, label, scope, currentValue, onWrite)
            PropertyKind.Boolean -> renderBoolean(label, scope, currentValue, prop.default, onWrite)
            PropertyKind.Text -> renderText(prop, label, scope, currentValue, onWrite)
            PropertyKind.Enum -> renderEnum(prop, label, scope, currentValue, onWrite)
            PropertyKind.Vector3 -> renderVector(label, scope, currentValue, prop.default, onWrite)
            PropertyKind.Color -> renderColor(label, scope, currentValue, prop.default, onWrite)
            PropertyKind.Object -> renderObject(prop, label, scope, currentValue)
            PropertyKind.List -> renderList(prop, label, scope, currentValue)
            PropertyKind.Map, PropertyKind.Polymorphic, PropertyKind.Unknown -> {
                val display = currentValue?.toString() ?: prop.default?.let(::jsonDisplayString) ?: ""
                scope.readOnly(label, display)
            }
        }
    }

    private fun renderFloat(
        prop: PropertySchema,
        label: String,
        scope: InspectorFieldScope,
        currentValue: Any?,
        onWrite: (Any?) -> Unit,
    ) {
        val floatVal = (currentValue as? Number)?.toFloat()
            ?: (prop.default as? JsonPrimitive)?.floatOrNull
            ?: 0f
        val slider = prop.hints.slider
        val range = prop.constraints.range
        val min = slider?.softMin?.toFloat() ?: range?.min?.toFloat()
        val max = slider?.softMax?.toFloat() ?: range?.max?.toFloat()
        val step = slider?.step?.toFloat() ?: prop.hints.step?.toFloat() ?: 0f

        if (min != null && max != null) {
            scope.slider(label, floatVal, min, max, step) { onWrite(it) }
        } else {
            scope.scalar(label, floatVal) { onWrite(it) }
        }
    }

    private fun renderInt(
        prop: PropertySchema,
        label: String,
        scope: InspectorFieldScope,
        currentValue: Any?,
        onWrite: (Any?) -> Unit,
    ) {
        val intVal = (currentValue as? Number)?.toInt()
            ?: (prop.default as? JsonPrimitive)?.intOrNull
            ?: 0
        val slider = prop.hints.slider
        val range = prop.constraints.range
        val min = slider?.softMin?.toInt() ?: range?.min?.toInt()
        val max = slider?.softMax?.toInt() ?: range?.max?.toInt()
        val step = (slider?.step ?: prop.hints.step ?: 1.0).toInt().coerceAtLeast(1)

        if (min != null && max != null) {
            scope.integer(label, intVal, min, max, step) { onWrite(it) }
        } else {
            scope.integer(label, intVal) { onWrite(it) }
        }
    }

    private fun renderBoolean(
        label: String,
        scope: InspectorFieldScope,
        currentValue: Any?,
        default: JsonElement?,
        onWrite: (Any?) -> Unit,
    ) {
        val boolVal = (currentValue as? Boolean)
            ?: (default as? JsonPrimitive)?.booleanOrNull
            ?: false
        scope.toggle(label, boolVal) { onWrite(it) }
    }

    private fun renderText(
        prop: PropertySchema,
        label: String,
        scope: InspectorFieldScope,
        currentValue: Any?,
        onWrite: (Any?) -> Unit,
    ) {
        val textVal = (currentValue as? String)
            ?: (prop.default as? JsonPrimitive)?.contentOrNull
            ?: ""
        scope.text(label, textVal) { onWrite(it) }
    }

    private fun renderEnum(
        prop: PropertySchema,
        label: String,
        scope: InspectorFieldScope,
        currentValue: Any?,
        onWrite: (Any?) -> Unit,
    ) {
        val cases = prop.enumValues
        val currentStr = (currentValue as? Enum<*>)?.name
            ?: (currentValue as? String)
            ?: (prop.default as? JsonPrimitive)?.contentOrNull
            ?: cases.firstOrNull()
            ?: ""

        scope.choices(label, currentStr, cases) { onWrite(it) }
    }

    private fun renderVector(
        label: String,
        scope: InspectorFieldScope,
        currentValue: Any?,
        default: JsonElement?,
        onWrite: (Any?) -> Unit,
    ) {
        val vec = (currentValue as? Vec3f)
            ?: (default as? JsonObject)?.let { obj ->
                Vec3f(
                    obj["x"]?.let { (it as? JsonPrimitive)?.floatOrNull } ?: 0f,
                    obj["y"]?.let { (it as? JsonPrimitive)?.floatOrNull } ?: 0f,
                    obj["z"]?.let { (it as? JsonPrimitive)?.floatOrNull } ?: 0f,
                )
            }
            ?: Vec3f(0f, 0f, 0f)

        scope.vector(label, vec) { onWrite(it) }
    }

    private fun renderColor(
        label: String,
        scope: InspectorFieldScope,
        currentValue: Any?,
        default: JsonElement?,
        onWrite: (Any?) -> Unit,
    ) {
        val col = (currentValue as? Color)
            ?: (default as? JsonObject)?.let { obj ->
                Color(
                    obj["r"]?.let { (it as? JsonPrimitive)?.floatOrNull } ?: 1f,
                    obj["g"]?.let { (it as? JsonPrimitive)?.floatOrNull } ?: 1f,
                    obj["b"]?.let { (it as? JsonPrimitive)?.floatOrNull } ?: 1f,
                    obj["a"]?.let { (it as? JsonPrimitive)?.floatOrNull } ?: 1f,
                )
            }
            ?: Color(1f, 1f, 1f, 1f)

        scope.color(label, col) { onWrite(it) }
    }

    private fun renderObject(
        prop: PropertySchema,
        label: String,
        scope: InspectorFieldScope,
        currentValue: Any?,
    ) {
        scope.section(label) {
            @Suppress("UNCHECKED_CAST")
            val childValues = (currentValue as? Map<String, Any?>) ?: emptyMap()
            for (child in prop.children) {
                renderProperty(child, this, childValues[child.name]) { /* nested write */ }
            }
        }
    }

    private fun renderList(
        prop: PropertySchema,
        label: String,
        scope: InspectorFieldScope,
        currentValue: Any?,
    ) {
        val listVal = (currentValue as? List<*>) ?: emptyList<Any?>()
        val elementSchema = prop.element ?: return
        scope.list(label, listVal) { index, item ->
            renderProperty(elementSchema, this, item) { /* list item write */ }
        }
    }

    private fun defaultForKind(kind: PropertyKind): Any = when (kind) {
        PropertyKind.Float -> 0f
        PropertyKind.Int -> 0
        PropertyKind.Boolean -> false
        PropertyKind.Text -> ""
        PropertyKind.Vector3 -> Vec3f(0f, 0f, 0f)
        PropertyKind.Color -> Color(1f, 1f, 1f, 1f)
        else -> ""
    }

    private fun jsonDisplayString(element: JsonElement): String = when (element) {
        is JsonPrimitive -> element.content
        is JsonNull -> "null"
        is JsonArray, is JsonObject -> element.toString()
    }

    private fun jsonToJavaValue(element: JsonElement): Any? = when (element) {
        is JsonPrimitive -> element.booleanOrNull ?: element.intOrNull ?: element.floatOrNull ?: element.doubleOrNull ?: element.contentOrNull
        is JsonNull -> null
        is JsonArray, is JsonObject -> element.toString()
    }
}
