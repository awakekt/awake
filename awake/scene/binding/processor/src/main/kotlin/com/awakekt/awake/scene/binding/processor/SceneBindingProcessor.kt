/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.binding.processor

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclarationContainer
import com.google.devtools.ksp.symbol.KSFile
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo

/** Creates the [SceneBindingProcessor]; KSP finds it through `META-INF/services`. */
class SceneBindingProcessorProvider : SymbolProcessorProvider {
    // The processor uses no visitors, so the behaviour KSP plans to change cannot affect it.
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        SceneBindingProcessor(environment.codeGenerator, environment.logger, environment.options[Names.REGISTRY_OPTION])
            .also(environment.registerProcessorForNewFeatures)
}

/**
 * Generates a binding for every `@SceneBinding` schema and the module's `SceneComponentModule`, and fails
 * the build when a scene component or a public binding object of the module would be left out of it.
 */
class SceneBindingProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val registry: String?,
) : SymbolProcessor {
    private var done = false

    // KSP's Dependencies takes its source files only as varargs.
    @Suppress("SpreadOperator")
    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (done) return emptyList()
        done = true
        val files = resolver.getAllFiles().toList()
        val classes = files.flatMap { it.allClasses() }
        val entries = mutableListOf<RegistryEntry>()
        val bound = mutableSetOf<ClassName>()
        for (schema in classes.filter { it.hasAnnotation(Names.SCENE_BINDING) }) {
            val annotation = schema.findAnnotation(Names.SCENE_BINDING)!!
            readBinding(schema, annotation).mapCatching { plan(it).getOrThrow() }
                .onSuccess { plan ->
                    writeBinding(plan).writeTo(codeGenerator, Dependencies(false, *listOfNotNull(schema.containingFile).toTypedArray()))
                    entries += RegistryEntry(plan.request.binding, schema.toClassName().takeIf { plan.request.saved.serializable })
                    bound += schema.toClassName()
                }
                .onFailure { error -> report(schema, error) }
        }
        val schemas = classes.filter { isSceneComponent(it) }.associateBy { it.toClassName() }
        for (binding in classes.filter { it.isBindingObject() }) {
            val schema = boundSchema(binding)
            when {
                binding.hasAnnotation(Names.REGISTERED_BINDING) -> {
                    entries += RegistryEntry(binding.toClassName(), schema?.takeIf { schemas[it]?.hasAnnotation(Names.SERIALIZABLE) == true })
                    schema?.let { bound += it }
                }
                binding.isPublic() -> logger.error(
                    "${binding.simpleName.asString()} is a scene component binding the module's registry doesn't list: " +
                        "mark it @RegisteredBinding, or generate it with @SceneBinding",
                    binding,
                )
            }
        }
        schemas.filterKeys { it !in bound }.values.filterNot { it.hasAnnotation(Names.UNBOUND) }.forEach {
            logger.error(
                "${it.simpleName.asString()} is a scene component with no binding in this module: add @SceneBinding, " +
                    "mark its hand-written binding @RegisteredBinding, or mark it @UnboundSceneComponent with the reason",
                it,
            )
        }
        writeModule(files, entries)
        return emptyList()
    }

    @Suppress("SpreadOperator") // as in process
    private fun writeModule(files: List<KSFile>, entries: List<RegistryEntry>) {
        if (registry.isNullOrBlank()) {
            if (entries.isNotEmpty()) logger.error("Set the KSP option ${Names.REGISTRY_OPTION} to the module's registry object name")
            return
        }
        val registryName = ClassName(registry.substringBeforeLast('.', ""), registry.substringAfterLast('.'))
        writeRegistry(registryName, entries).writeTo(codeGenerator, Dependencies(true, *files.toTypedArray()))
    }

    private fun report(schema: KSClassDeclaration, error: Throwable) {
        val messages = (error as? BindingErrors)?.messages ?: listOf(error.message ?: error.toString())
        messages.forEach { logger.error("@SceneBinding on ${schema.simpleName.asString()}: $it", schema) }
    }

    private fun KSClassDeclaration.isBindingObject(): Boolean = classKind == ClassKind.OBJECT &&
        !hasAnnotation(Names.SCENE_BINDING) &&
        getAllSuperTypes().any { it.declaration.qualifiedName?.asString() == Names.BINDING.canonicalName }

    private fun KSDeclarationContainer.allClasses(): List<KSClassDeclaration> = declarations
        .filterIsInstance<KSClassDeclaration>()
        .flatMap { listOf(it) + it.allClasses() }
        .toList()
}
