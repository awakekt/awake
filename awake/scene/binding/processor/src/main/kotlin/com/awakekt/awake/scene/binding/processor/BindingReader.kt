/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.binding.processor

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName

/** Reads one `@SceneBinding` schema into a [BindingRequest], or the reasons it can't. */
internal fun readBinding(schema: KSClassDeclaration, annotation: KSAnnotation): Result<BindingRequest> {
    val errors = mutableListOf<String>()
    val component = (annotation.argument("component") as KSType).declaration as KSClassDeclaration
    val runtimeOnly = annotation.stringList("runtimeOnly")
    val renamed = annotation.annotationList("renamed").associate { it.string("property") to it.string("name") }
    val live = readLive(component, runtimeOnly.toSet(), renamed)
    val liveNames = live.properties.map { it.name }.toSet()
    (runtimeOnly + renamed.keys).filterNot { it in liveNames }.forEach {
        errors += "${component.simpleName.asString()} has no state property $it to name in @SceneBinding"
    }
    val converters = annotation.typeList("converters").mapNotNull { type ->
        readConverter(type).onFailure { errors += it.message.orEmpty() }.getOrNull()
    }
    val hooksType = annotation.argument("hooks") as? KSType
    val hooks = hooksType?.takeUnless { it.declaration.qualifiedName?.asString() == Names.HOOKS.canonicalName }?.let { type ->
        val declaration = type.declaration as KSClassDeclaration
        val expected = Names.HOOKS.parameterizedBy(live.className, schema.toClassName())
        when {
            declaration.classKind != ClassKind.OBJECT -> errors += "hooks ${declaration.simpleName.asString()} must be an object"
            declaration.superTypeNames().none { it == expected } -> errors += "hooks ${declaration.simpleName.asString()} must implement $expected"
        }
        Hooks(declaration.toClassName(), declaration.getDeclaredFunctions().map { it.simpleName.asString() }.toSet())
    }
    val name = annotation.string("name").ifEmpty { "${component.simpleName.asString()}Binding" }
    val request = BindingRequest(ClassName(schema.packageName.asString(), name), readSaved(schema), live, converters, hooks)
    return if (errors.isEmpty()) Result.success(request) else Result.failure(BindingErrors(errors))
}

/** Whether [declaration] is a concrete class or object implementing the scene document's component interface. */
internal fun isSceneComponent(declaration: KSClassDeclaration): Boolean =
    declaration.classKind in setOf(ClassKind.CLASS, ClassKind.OBJECT) &&
        Modifier.ABSTRACT !in declaration.modifiers && Modifier.SEALED !in declaration.modifiers &&
        declaration.getAllSuperTypes().any { it.declaration.qualifiedName?.asString() == Names.SCENE_COMPONENT.canonicalName }

/** The schema class a binding object binds, from its `SceneComponentBinding<C, S>` supertype. */
internal fun boundSchema(binding: KSClassDeclaration): ClassName? = binding.getAllSuperTypes()
    .firstOrNull { it.declaration.qualifiedName?.asString() == Names.BINDING.canonicalName }
    ?.arguments?.getOrNull(1)?.type?.resolve()?.expanded()?.declaration?.let { (it as? KSClassDeclaration)?.toClassName() }

private fun readLive(component: KSClassDeclaration, runtimeOnly: Set<String>, renamed: Map<String, String>): LiveShape {
    val className = component.toClassName()
    if (component.classKind == ClassKind.OBJECT) return LiveShape(className, null, emptyList())
    val constructor = component.primaryConstructor
    val parameters = constructor?.parameters.orEmpty().map {
        LiveParameter(it.name!!.asString(), fieldType(it.type.resolve()), it.hasDefault)
    }
    val constructorProperties = constructor?.parameters.orEmpty().filter { it.isVal || it.isVar }.map { it.name!!.asString() }
    val properties = component.getAllProperties()
        .filter { it.isPublic() && it.extensionReceiver == null }
        .filter { it.simpleName.asString() in constructorProperties || it.isMutable || it.hasBackingField }
        .map { property ->
            val name = property.simpleName.asString()
            val writable = property.isMutable && property.setter?.modifiers?.none { it in hiddenSetters } != false
            LiveProperty(
                name = name,
                type = fieldType(property.type.resolve()),
                writable = writable,
                runtimeOnly = name in runtimeOnly || property.hasAnnotation(Names.RUNTIME_ONLY),
                savedAs = renamed[name] ?: property.findAnnotation(Names.SAVED_AS)?.string("name"),
            )
        }
        .toList()
    return LiveShape(className, parameters, properties)
}

private val hiddenSetters = setOf(Modifier.PRIVATE, Modifier.PROTECTED, Modifier.INTERNAL)

private fun readSaved(schema: KSClassDeclaration): SavedShape {
    val serializable = schema.hasAnnotation(Names.SERIALIZABLE)
    if (schema.classKind == ClassKind.OBJECT) return SavedShape(schema.toClassName(), null, serializable)
    val properties = schema.getAllProperties().associateBy { it.simpleName.asString() }
    val saved = schema.primaryConstructor?.parameters.orEmpty().map { parameter ->
        val name = parameter.name!!.asString()
        SavedProperty(
            name = name,
            type = fieldType(parameter.type.resolve()),
            hasDefault = parameter.hasDefault,
            savedOnly = parameter.hasAnnotation(Names.SAVED_ONLY) || properties[name]?.hasAnnotation(Names.SAVED_ONLY) == true,
        )
    }
    return SavedShape(schema.toClassName(), saved, serializable)
}

private fun readConverter(type: KSType): Result<Converter> {
    val declaration = type.declaration as KSClassDeclaration
    val name = declaration.simpleName.asString()
    val supertype = declaration.getAllSuperTypes().firstOrNull { it.declaration.qualifiedName?.asString() == Names.CONVERTER.canonicalName }
    return when {
        declaration.classKind != ClassKind.OBJECT -> Result.failure(IllegalArgumentException("converter $name must be an object"))
        supertype == null -> Result.failure(IllegalArgumentException("converter $name must implement SceneValueConverter"))
        else -> {
            val (saved, live) = supertype.arguments.map { it.type!!.resolve().expanded().toTypeName() }
            Result.success(Converter(declaration.toClassName(), saved, live))
        }
    }
}

private fun fieldType(type: KSType): FieldType {
    val expanded = type.expanded()
    val declaration = expanded.declaration as? KSClassDeclaration
    val constants = declaration?.takeIf { it.classKind == ClassKind.ENUM_CLASS }?.declarations
        ?.filterIsInstance<KSClassDeclaration>()?.filter { it.classKind == ClassKind.ENUM_ENTRY }
        ?.map { it.simpleName.asString() }?.toList()
    return FieldType(expanded.toTypeName(), constants)
}
