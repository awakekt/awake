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
import com.awakekt.awake.scene.document.SceneComponent
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Reads the fields a canvas element's `bind` names from a node's components, through each component's
 * scene binding (#1512). Kept per canvas from frame to frame.
 *
 * A component is exported through its binding, then [FieldEncoder] picks the named fields out of it in
 * one pass, with the text JSON would give them. It builds no JSON, so a frame that only checks whether
 * a bound value changed allocates little more than the exported component. A component holding
 * something [FieldEncoder] cannot read that way is read through JSON from then on.
 */
internal class CanvasFieldReader(private val world: World, private val bindings: List<SceneComponentBinding<*, *>>?) {
    private val byName by lazy {
        (bindings ?: globalCanvasBindings()).mapNotNull { binding -> binding.serializer?.descriptor?.serialName?.let { it to binding } }.toMap()
    }

    private val encoder = FieldEncoder()
    private val jsonOnly = HashSet<String>()
    private val paths = HashMap<String, Path?>()
    private val one = ArrayList<FieldValue>(1)

    /** A path's component, and the value that holds what it reads. */
    class Path(val component: String, val value: FieldValue)

    /** [path] parsed once: its component and the [FieldValue] for the rest, or null when it names no field. */
    fun path(path: String): Path? = paths.getOrPut(path) {
        val dot = path.indexOf('.')
        if (dot <= 0 || dot == path.lastIndex) null else Path(path.substring(0, dot), FieldValue(path.substring(dot + 1).split('.')))
    }

    /** [path]'s text on [entity], as JSON would give it, or null when the entity has no such field. */
    fun field(entity: Entity, path: String): String? {
        val parsed = path(path) ?: return null
        one.clear()
        one.add(parsed.value)
        read(entity, parsed.component, one)
        return parsed.value.content()
    }

    /** Fills [fields], each a field of [component], from [entity] in one export; each is absent when not found. */
    // Safe: a binding's serializer is the serializer of its own schema class, which exportFrom returns.
    @Suppress("UNCHECKED_CAST")
    fun read(entity: Entity, component: String, fields: List<FieldValue>) {
        val binding = byName[component]
        val serializer = binding?.serializer as? KSerializer<SceneComponent>
        val saved = if (serializer != null) binding.exportFrom(world, entity) else null
        if (serializer == null || saved == null) {
            for (index in fields.indices) fields[index].clear()
            return
        }
        try {
            if (component !in jsonOnly) {
                encoder.capture(serializer, saved, fields)
                return
            }
        } catch (_: FieldEncoder.Unreadable) {
            jsonOnly += component
        }
        readJson(component, serializer, saved, fields)
    }

    private fun readJson(component: String, serializer: KSerializer<SceneComponent>, saved: SceneComponent, fields: List<FieldValue>) {
        val tree = try {
            json.encodeToJsonElement(serializer, saved) as? JsonObject
        } catch (unwritable: SerializationException) {
            warnOnce(component, "it could not be read: ${unwritable.message}")
            null
        }
        for (field in fields) {
            var element: JsonElement? = tree
            for (segment in field.segments) element = (element as? JsonObject)?.get(segment)
            val content = (element as? JsonPrimitive)?.content
            if (content == null) field.clear() else field.setText(content)
        }
    }

    companion object {
        // Defaults kept: a field at its default is still a value to show. A NaN or an infinity is a
        // value too, and the scene format's strict default would refuse the whole component.
        private val json = Json {
            encodeDefaults = true
            allowSpecialFloatingPointValues = true
        }

        private val log = Logger("scene-canvas")

        // Paths a scene's bindings name: as many as the scenes write, so it stays small.
        private val warned = HashSet<String>()

        /** Warns once per [path] that a binding reads it but [why]. */
        fun warnOnce(path: String, why: String = "no node's component has it", result: String = "the element shows its own value") {
            if (warned.add(path)) log.warn { "A canvas binding reads '$path', but $why; $result" }
        }
    }
}
