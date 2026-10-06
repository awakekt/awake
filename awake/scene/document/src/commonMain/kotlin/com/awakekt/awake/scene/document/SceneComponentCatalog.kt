/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.document

import com.awakekt.awake.core.schema.PropertyKind
import com.awakekt.awake.core.schema.PropertySchema
import com.awakekt.awake.core.schema.SchemaOptions
import com.awakekt.awake.core.schema.deriveDefaults
import com.awakekt.awake.core.schema.propertySchemaOf
import com.awakekt.awake.core.schema.toPropertySchema
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor

/**
 * The scene components this process can load, and what each one looks like, for an editor, a reference
 * page or a schema exporter that must not hard-code them.
 *
 * It reflects what [SceneSerializers] holds **when it is asked**. The engine's kits and any plugin
 * register their components at install time, so ask after they are installed: a catalog read earlier
 * lacks the components registered later. Nothing is cached, because the registry can change.
 *
 * The component id is the value of the `component` key in a document, which is the serial name of the
 * component's class. A node is not a component, so [nodeSchema] describes it separately: its
 * `transform` lives there, not under a component id.
 */
object SceneComponentCatalog {
    /**
     * The types read as a richer [PropertyKind] than their structure says: [SceneVec3] as a vector and
     * [SceneColor] as a colour, keyed by serial name.
     */
    val semanticTypes: Map<String, PropertyKind> = mapOf(
        SceneVec3.serializer().descriptor.serialName to PropertyKind.Vector3,
        SceneColor.serializer().descriptor.serialName to PropertyKind.Color,
    )

    /**
     * The id of every registered component, sorted.
     *
     * @throws IllegalStateException when two registered components share an id, which would make a
     * document ambiguous.
     */
    fun ids(): List<String> = serializers().keys.toList()

    /**
     * The descriptor of the component with [id].
     *
     * @param id A component id, such as `spin_control`.
     * @return Its descriptor, or null when no component with that id is registered.
     */
    fun descriptor(id: String): SerialDescriptor? = serializers()[id]?.descriptor

    /**
     * The property schema of the component with [id], with its defaults filled in where they can be learned.
     *
     * @param id A component id, such as `spin_control`.
     * @return Its schema, or null when no component with that id is registered.
     */
    fun schema(id: String): PropertySchema? = serializers()[id]?.let(::schemaOf)

    /**
     * The property schema of every registered component, by id, sorted.
     *
     * @throws IllegalStateException when two registered components share an id.
     */
    fun schemas(): Map<String, PropertySchema> = serializers().mapValues { (_, serializer) -> schemaOf(serializer) }

    /**
     * The property schema of a [SceneNode]: its name, its [SceneTransform], its components and its children.
     *
     * Read `components` as the list of attached components, each one of [ids], and `children` as the scene
     * hierarchy. Both are lists of a recursive or polymorphic type, so the schema reads only
     * [NODE_SCHEMA_DEPTH] levels and does not describe a component or a child in full.
     */
    fun nodeSchema(): PropertySchema {
        val serializer = SceneNode.serializer()
        val defaults = deriveDefaults(serializer, SceneSerializers.createJson())
        return serializer.descriptor.toPropertySchema(SchemaOptions(semanticTypes, defaults, NODE_SCHEMA_DEPTH))
    }

    /** How many levels of a node [nodeSchema] reads: enough for `transform.position.x`, and one nested child. */
    const val NODE_SCHEMA_DEPTH: Int = 4

    private fun serializers(): Map<String, KSerializer<out SceneComponent>> =
        SceneSerializers.registeredSerializers().byId()

    @Suppress("UNCHECKED_CAST") // A KSerializer<out SceneComponent> is a KSerializer<SceneComponent> for reading its descriptor.
    private fun schemaOf(serializer: KSerializer<out SceneComponent>): PropertySchema =
        propertySchemaOf(serializer as KSerializer<SceneComponent>, SceneSerializers.createJson(), semanticTypes)
}

/** These serializers keyed by component id, sorted by id; two with the same id are an error. */
internal fun List<KSerializer<out SceneComponent>>.byId(): Map<String, KSerializer<out SceneComponent>> {
    val grouped = groupBy { it.descriptor.serialName }
    val clashes = grouped.filterValues { it.size > 1 }.keys
    check(clashes.isEmpty()) { "Scene components share an id, so a document would be ambiguous: ${clashes.sorted().joinToString()}" }
    return grouped.entries.sortedBy { it.key }.associate { it.key to it.value.single() }
}
