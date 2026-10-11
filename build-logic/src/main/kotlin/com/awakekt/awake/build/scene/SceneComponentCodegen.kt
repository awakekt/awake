/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.scene

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName

/**
 * Writes a [SceneComponentSpec] as Kotlin: the scene document class and the binding that copies
 * every field by name between it and the runtime component.
 *
 * Names every field explicitly and uses no reflection, so the mapping survives an obfuscated
 * release, and is common Kotlin, so it compiles for every platform.
 */
object SceneComponentCodegen {
    /** The document class and the binding, one file each, by file name. */
    fun generate(spec: SceneComponentSpec): List<FileSpec> = listOf(documentFile(spec), bindingFile(spec))

    private fun documentFile(spec: SceneComponentSpec): FileSpec {
        val document = TypeSpec.classBuilder(spec.sceneClass)
            .addModifiers(KModifier.DATA)
            .addKdoc(documentKdoc(spec))
            .addAnnotation(SERIALIZABLE)
            .addAnnotation(AnnotationSpec.builder(SERIAL_NAME).addMember("%S", spec.serialName).build())
            .addSuperinterface(SCENE_COMPONENT)
            .primaryConstructor(
                FunSpec.constructorBuilder().apply {
                    spec.fields.forEach { field ->
                        addParameter(
                            ParameterSpec.builder(field.name, typeOf(field.type, spec))
                                .defaultValue("%L", field.default)
                                .build(),
                        )
                    }
                }.build(),
            )
        spec.fields.forEach { field ->
            document.addProperty(
                PropertySpec.builder(field.name, typeOf(field.type, spec))
                    .initializer(field.name)
                    .apply { field.range?.let { addAnnotation(rangeAnnotation(it)) } }
                    .build(),
            )
        }
        spec.validate?.let { hook ->
            document.addFunction(
                FunSpec.builder("validate")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("path", STRING)
                    .returns(LIST.parameterizedBy(VALIDATION_ISSUE))
                    .addStatement("return %N(path)", hook)
                    .build(),
            )
        }
        return file(spec, spec.sceneClass).addType(document.build()).build()
    }

    private fun bindingFile(spec: SceneComponentSpec): FileSpec {
        val document = ClassName(spec.packageName, spec.sceneClass)
        val component = ClassName(spec.packageName, spec.component)
        val binding = TypeSpec.objectBuilder(spec.binding)
            .addKdoc("Loads and saves [%L]s as `%L` scene components, copying every field by name.\n", spec.component, spec.serialName)
            .addSuperinterface(BINDING.parameterizedBy(component, document))
            .addProperty(
                PropertySpec.builder("componentClass", KCLASS.parameterizedBy(component), KModifier.OVERRIDE)
                    .initializer("%T::class", component)
                    .build(),
            )
            .addProperty(
                PropertySpec.builder("schemaClass", KCLASS.parameterizedBy(document), KModifier.OVERRIDE)
                    .initializer("%T::class", document)
                    .build(),
            )
            .addProperty(
                PropertySpec.builder("serializer", K_SERIALIZER.parameterizedBy(document), KModifier.OVERRIDE)
                    .initializer("%T.serializer()", document)
                    .build(),
            )
            .addFunction(
                FunSpec.builder("attachTyped")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("world", WORLD)
                    .addParameter("entity", ENTITY)
                    .addParameter("component", document)
                    .addParameter("context", RESOLUTION_CONTEXT)
                    .addStatement("world.add(entity, component.toComponent())")
                    .build(),
            )
            .addFunction(
                FunSpec.builder("export")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("world", WORLD)
                    .addParameter("entity", ENTITY)
                    .addParameter("component", component)
                    .returns(document)
                    .addStatement("return component.toSceneComponent()")
                    .build(),
            )
            .addFunction(
                FunSpec.builder("toComponent")
                    .receiver(document)
                    .returns(component)
                    .addKdoc("This document as the runtime [%L] it describes.\n", spec.component)
                    .addCode(toComponent(spec, component))
                    .build(),
            )
            .addFunction(
                FunSpec.builder("toSceneComponent")
                    .receiver(component)
                    .returns(document)
                    .addKdoc("This component as the [%L] a scene saves.\n", spec.sceneClass)
                    .addCode(namedArguments(document, spec.fields))
                    .build(),
            )
        return file(spec, spec.binding).addType(binding.build()).build()
    }

    private fun toComponent(spec: SceneComponentSpec, component: ClassName): CodeBlock = when (spec.construction) {
        SceneComponentSpec.Construction.Constructor -> namedArguments(component, spec.fields)
        SceneComponentSpec.Construction.Properties -> CodeBlock.builder()
            .add("return %T().also {\n", component)
            .indent()
            .apply { spec.fields.forEach { add("it.%N = %N\n", it.name, it.name) } }
            .unindent()
            .add("}\n")
            .build()
    }

    private fun namedArguments(type: ClassName, fields: List<SceneFieldSpec>): CodeBlock = CodeBlock.builder()
        .add("return %T(\n", type)
        .indent()
        .apply { fields.forEach { add("%N = %N,\n", it.name, it.name) } }
        .unindent()
        .add(")\n")
        .build()

    private fun documentKdoc(spec: SceneComponentSpec): CodeBlock = CodeBlock.builder()
        .add("%L\n\n", spec.doc)
        .apply { spec.fields.forEach { add("@property %L %L\n", it.name, it.doc) } }
        .build()

    private fun rangeAnnotation(range: FieldRange): AnnotationSpec = AnnotationSpec.builder(PROPERTY_RANGE).apply {
        range.min?.let { addMember("min = %L", it.toString()) }
        range.max?.let { addMember("max = %L", it.toString()) }
        if (range.exclusiveMin) addMember("exclusiveMin = true")
        if (range.exclusiveMax) addMember("exclusiveMax = true")
    }.build()

    private fun file(spec: SceneComponentSpec, name: String): FileSpec.Builder = FileSpec.builder(spec.packageName, name)
        .addFileComment("Generated from %L by com.awakekt.awake.plugin.scene-components. Edit the spec, not this file.", spec.sourcePath)
        .apply {
            // The defaults are Kotlin source the spec writes out, so what they name is imported as a whole.
            spec.imports.forEach { addImport(it.substringBeforeLast('.'), it.substringAfterLast('.')) }
        }

    /** [source], a Kotlin type as a spec writes it, resolved against the spec's package and imports. */
    internal fun typeOf(source: String, spec: SceneComponentSpec): TypeName {
        val text = source.trim()
        if (text.endsWith("?")) return typeOf(text.dropLast(1), spec).copy(nullable = true)
        val open = text.indexOf('<')
        if (open >= 0) {
            require(text.endsWith(">")) { "${spec.sourcePath}: can't read the type $source" }
            val raw = typeOf(text.substring(0, open), spec) as ClassName
            return raw.parameterizedBy(splitArguments(text.substring(open + 1, text.length - 1)).map { typeOf(it, spec) })
        }
        BUILT_IN[text]?.let { return it }
        spec.imports.firstOrNull { it.substringAfterLast('.') == text }?.let { return ClassName(it.substringBeforeLast('.'), text) }
        return ClassName(spec.packageName, text.split('.'))
    }

    /** `A, Map<B, C>` as `A` and `Map<B, C>`: commas inside angle brackets belong to the inner type. */
    private fun splitArguments(arguments: String): List<String> {
        val parts = mutableListOf<String>()
        var depth = 0
        var start = 0
        arguments.forEachIndexed { index, c ->
            when (c) {
                '<' -> depth++
                '>' -> depth--
                ',' -> if (depth == 0) {
                    parts += arguments.substring(start, index)
                    start = index + 1
                }
            }
        }
        parts += arguments.substring(start)
        return parts.map(String::trim)
    }

    private val BUILT_IN: Map<String, TypeName> = mapOf(
        "Float" to Float::class.asClassName(),
        "Double" to Double::class.asClassName(),
        "Int" to Int::class.asClassName(),
        "Long" to Long::class.asClassName(),
        "Boolean" to Boolean::class.asClassName(),
        "String" to STRING,
        "List" to LIST,
        "Set" to ClassName("kotlin.collections", "Set"),
        "Map" to ClassName("kotlin.collections", "Map"),
    )

    private val SERIALIZABLE = ClassName("kotlinx.serialization", "Serializable")
    private val SERIAL_NAME = ClassName("kotlinx.serialization", "SerialName")
    private val K_SERIALIZER = ClassName("kotlinx.serialization", "KSerializer")
    private val KCLASS = ClassName("kotlin.reflect", "KClass")
    private val SCENE_COMPONENT = ClassName("com.awakekt.awake.scene.document", "SceneComponent")
    private val VALIDATION_ISSUE = ClassName("com.awakekt.awake.scene.document", "SceneValidationIssue")
    private val PROPERTY_RANGE = ClassName("com.awakekt.awake.core.schema", "PropertyRange")
    private val BINDING = ClassName("com.awakekt.awake.scene.binding", "SceneComponentBinding")
    private val RESOLUTION_CONTEXT = ClassName("com.awakekt.awake.scene.binding", "SceneResolutionContext")
    private val WORLD = ClassName("com.awakekt.awake.ecs", "World")
    private val ENTITY = ClassName("com.awakekt.awake.ecs", "Entity")
}
