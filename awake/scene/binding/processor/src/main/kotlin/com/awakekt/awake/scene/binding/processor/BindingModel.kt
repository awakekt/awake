/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.binding.processor

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName

/** A field's type, with its constants when it is an enum, so two enums can be matched by name. */
internal data class FieldType(val name: TypeName, val enumConstants: List<String>? = null)

/** A primary constructor parameter of the live component. */
internal data class LiveParameter(val name: String, val type: FieldType, val hasDefault: Boolean)

/**
 * A public property of the live component that holds state: a constructor property, a `var` with a
 * public setter, or a `val` with a backing field.
 */
internal data class LiveProperty(
    val name: String,
    val type: FieldType,
    val writable: Boolean,
    val runtimeOnly: Boolean,
    val savedAs: String?,
)

/** The live ECS component; [parameters] is null for an `object`. */
internal data class LiveShape(
    val className: ClassName,
    val parameters: List<LiveParameter>?,
    val properties: List<LiveProperty>,
)

/** A primary constructor property of the schema. */
internal data class SavedProperty(val name: String, val type: FieldType, val hasDefault: Boolean, val savedOnly: Boolean)

/** The scene schema; [properties] is null for an `object`. */
internal data class SavedShape(val className: ClassName, val properties: List<SavedProperty>?, val serializable: Boolean)

/** A [com.awakekt.awake.scene.binding.SceneValueConverter] object and the types it converts. */
internal data class Converter(val className: ClassName, val saved: TypeName, val live: TypeName)

/** A [com.awakekt.awake.scene.binding.SceneBindingHooks] object and the hooks it overrides. */
internal data class Hooks(val className: ClassName, val overridden: Set<String>) {
    operator fun contains(hook: String): Boolean = hook in overridden
}

/** Everything one `@SceneBinding` asks for. */
internal data class BindingRequest(
    val binding: ClassName,
    val saved: SavedShape,
    val live: LiveShape,
    val converters: List<Converter>,
    val hooks: Hooks?,
)

/** How one saved field becomes its live field and back. */
internal sealed interface Conversion {
    data object Direct : Conversion

    data class EnumByName(val saved: TypeName, val live: TypeName, val constants: List<String>) : Conversion

    data class Converted(val converter: ClassName) : Conversion
}

/** One field pair; [viaConstructor] when the live side is written through its constructor. */
internal data class FieldPlan(val saved: String, val live: String, val nullable: Boolean, val conversion: Conversion, val viaConstructor: Boolean)

/** A request whose every field is paired, ready to write. */
internal data class BindingPlan(val request: BindingRequest, val fields: List<FieldPlan>) {
    val isObjectPair: Boolean get() = request.live.parameters == null
}

/** A binding the module's registry lists, and the schema it binds when that schema is `@Serializable`. */
internal data class RegistryEntry(val binding: ClassName, val serializableSchema: ClassName?)

/** The problems that stop a binding from being generated. */
internal class BindingErrors(val messages: List<String>) : IllegalArgumentException(messages.joinToString("; "))
