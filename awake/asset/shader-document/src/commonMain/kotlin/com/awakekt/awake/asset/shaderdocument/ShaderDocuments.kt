/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderdocument

import com.awakekt.awake.asset.shaderdsl.AslDefinitionException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Reads, checks and compiles [ShaderDocument]s.
 *
 * ```
 * val compiled = ShaderDocuments.compile(text)       // throws ShaderDocumentException with every issue
 * val inputs = compiled.newInputs()
 * compiled.packParameters(mapOf("tint" to listOf(1f, 0.5f, 0f, 1f)), inputs)
 * host.attachContentFeature(compiled.contentFeature(inputs))
 * ```
 */
object ShaderDocuments {
    /**
     * The JSON format: strict, so a misspelt key is an error rather than a silently ignored setting,
     * and with no special floating-point values.
     */
    val json: Json = Json {
        ignoreUnknownKeys = false
        allowSpecialFloatingPointValues = false
        explicitNulls = false
    }

    /**
     * Parses [text] into a document, after checking its size and nesting against [limits] so a hostile
     * file is refused before it is parsed. The document is not checked further; [compile] does that.
     *
     * @throws ShaderDocumentException When [text] is too large, nests too deep, or is not a document.
     */
    fun decode(text: String, limits: ShaderDocumentLimits = ShaderDocumentLimits.Default): ShaderDocument {
        val size = sizeIssues(text, limits)
        if (size.isNotEmpty()) throw ShaderDocumentException(size)
        return try {
            json.decodeFromString(ShaderDocument.serializer(), text)
        } catch (error: SerializationException) {
            throw ShaderDocumentException(listOf(ShaderDocumentIssue("", "not a valid shader document: ${error.message}")), error)
        }
    }

    /** [document] as JSON. */
    fun encode(document: ShaderDocument): String = json.encodeToString(ShaderDocument.serializer(), document)

    /** Every problem with [document] against [limits]; empty when it compiles. */
    fun check(document: ShaderDocument, limits: ShaderDocumentLimits = ShaderDocumentLimits.Default): List<ShaderDocumentIssue> =
        DocumentChecker(document, limits).check().issues

    /**
     * Checks [document] against [limits] and compiles it.
     *
     * @throws ShaderDocumentException With every problem, when there is any.
     */
    fun compile(document: ShaderDocument, limits: ShaderDocumentLimits = ShaderDocumentLimits.Default): CompiledShaderDocument {
        val result = DocumentChecker(document, limits).check()
        val checked = result.checked ?: throw ShaderDocumentException(result.issues)
        return try {
            CompiledShaderDocument(checked)
        } catch (error: AslDefinitionException) {
            throw ShaderDocumentException(
                listOf(ShaderDocumentIssue("", "passed its checks but could not be compiled, which is a bug in awake:asset:shader-document: ${error.message}")),
                error,
            )
        }
    }

    /**
     * Parses, checks and compiles [text].
     *
     * @throws ShaderDocumentException With every problem, when there is any.
     */
    fun compile(text: String, limits: ShaderDocumentLimits = ShaderDocumentLimits.Default): CompiledShaderDocument =
        compile(decode(text, limits), limits)

    private fun sizeIssues(text: String, limits: ShaderDocumentLimits): List<ShaderDocumentIssue> = buildList {
        val bytes = text.encodeToByteArray().size
        if (bytes > limits.maxBytes) add(ShaderDocumentIssue("", "the document is $bytes bytes; the limit is ${limits.maxBytes}"))
        val nesting = jsonNesting(text)
        if (nesting > limits.maxJsonNesting) {
            add(ShaderDocumentIssue("", "the JSON nests $nesting deep; the limit is ${limits.maxJsonNesting}"))
        }
    }
}

/**
 * How deep objects and arrays nest in [text], brackets inside strings aside. A parser recurses once per
 * level, so this is checked before parsing: a deep enough file would otherwise exhaust the stack, which
 * on some platforms cannot be caught.
 */
internal fun jsonNesting(text: String): Int {
    var depth = 0
    var deepest = 0
    var inString = false
    var escaped = false
    for (char in text) {
        when {
            escaped -> escaped = false
            inString && char == '\\' -> escaped = true
            char == '"' -> inString = !inString
            inString -> Unit
            char == '{' || char == '[' -> deepest = maxOf(deepest, ++depth)
            char == '}' || char == ']' -> depth--
        }
    }
    return deepest
}
