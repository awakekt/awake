/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.document.SceneComponent
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Game state an element shows without code: a Bar's fill and a Text's or Button's words, read each
 * frame from the components of the node named [node].
 *
 * A field is addressed as `component.field`, such as `health.current`: the component's saved name,
 * then its field as the scene document writes it, with dots for a field inside another. A game's own
 * components are read the same way once their capability registers them.
 *
 * @property node The name of the node whose components are read.
 * @property value A Bar's fill: this field, divided by [max].
 * @property max The field, or a plain number, [value] is a share of; empty when [value] is already
 * a share from 0 to 1.
 * @property text The words, with `{component.field}` replaced by the field's value, such as
 * `HP {health.current}/{health.max}`.
 */
@Serializable
data class CanvasBinding(
    val node: String,
    val value: String = "",
    val max: String = "",
    val text: String = "",
) {
    internal fun problems(): List<String> = buildList {
        if (node.isBlank()) add("node must name a node")
        if (value.isBlank() && text.isBlank()) add("needs a value or a text to show")
        listOf("value" to value, "max" to max).forEach { (name, path) ->
            if (path.isNotBlank() && path.toFloatOrNull() == null && !FIELD_PATH.matches(path)) {
                add("$name \"$path\" must be component.field or a number")
            }
        }
    }
}

/**
 * Reads the fields bindings name from a [world], exporting each component once per frame through
 * its scene binding in [bindings], so it reads exactly what a saved scene would hold.
 */
internal class CanvasData(private val world: World, bindings: List<SceneComponentBinding<*, *>>) {
    private val byName = bindings.mapNotNull { binding -> binding.serializer?.descriptor?.serialName?.let { it to binding } }.toMap()
    private val exported = HashMap<Pair<Entity, String>, JsonObject?>()

    /**
     * [path]'s number on [entity], or a number [path] spells, or null when there is neither or it is
     * not finite: a NaN health shows the element's own value rather than an empty bar.
     */
    fun number(entity: Entity, path: String): Float? {
        val number = path.toFloatOrNull() ?: read(entity, path)?.let { primitive ->
            primitive.floatOrNull ?: primitive.booleanOrNull?.let { if (it) 1f else 0f }
        }
        return number?.takeIf { it.isFinite() }
    }

    /** [template] with each `{component.field}` replaced by that field's value on [entity]. */
    fun text(entity: Entity, template: String): String = PLACEHOLDER.replace(template) { match ->
        val primitive = read(entity, match.groupValues[1]) ?: return@replace match.value
        primitive.floatOrNull?.let(::formatNumber) ?: primitive.content
    }

    private fun read(entity: Entity, path: String): JsonPrimitive? {
        val component = path.substringBefore('.')
        val fields = path.substringAfter('.', "").split('.')
        val json = exported.getOrPut(entity to component) { export(entity, component) }
        var element: JsonElement? = json
        for (field in fields) element = (element as? JsonObject)?.get(field)
        return (element as? JsonPrimitive).also { if (it == null) warnOnce(path) }
    }

    // Safe: a binding's serializer is the serializer of its own schema class, which export returns.
    @Suppress("UNCHECKED_CAST")
    private fun export(entity: Entity, component: String): JsonObject? {
        val binding = byName[component]
        val serializer = binding?.serializer as? KSerializer<SceneComponent>
        val saved = if (serializer != null) binding.exportFrom(world, entity) else null
        return try {
            if (serializer != null && saved != null) json.encodeToJsonElement(serializer, saved) as? JsonObject else null
        } catch (unwritable: SerializationException) {
            warnOnce(component, "it could not be read: ${unwritable.message}")
            null
        }
    }

    private companion object {
        val PLACEHOLDER = Regex("""\{([A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+)}""")

        // Defaults kept: a field at its default is still a value to show. A NaN or an infinity is a
        // value too, and the scene format's strict default would refuse the whole component.
        val json = Json {
            encodeDefaults = true
            allowSpecialFloatingPointValues = true
        }

        val log = Logger("scene-canvas")

        // Paths a scene's bindings name: as many as the scenes write, so it stays small.
        val warned = HashSet<String>()

        fun warnOnce(path: String, why: String = "no node's component has it") {
            if (warned.add(path)) log.warn { "A canvas binding reads '$path', but $why; the element shows its own value" }
        }

        /** To one decimal place, without it when it is 0: `120`, `0.5`, `-1.5`; past a trillion, or not finite, as it is. */
        fun formatNumber(value: Float): String {
            // First: roundToLong refuses NaN.
            if (!value.isFinite() || abs(value) > LARGEST_FORMATTED) return value.toString()
            val tenths = (value * TENTHS).roundToLong()
            val sign = if (tenths < 0) "-" else ""
            return if (tenths % 10 == 0L) (tenths / 10).toString() else "$sign${abs(tenths) / 10}.${abs(tenths) % 10}"
        }

        const val TENTHS = 10f
        const val LARGEST_FORMATTED = 1e12f
    }
}

private val FIELD_PATH = Regex("""[A-Za-z0-9_]+(\.[A-Za-z0-9_]+)+""")

/** The bindings of every scene component registered globally now: what a scene loaded without a scope uses. */
fun globalCanvasBindings(): List<SceneComponentBinding<*, *>> = SceneComponentRegistry().bindings
