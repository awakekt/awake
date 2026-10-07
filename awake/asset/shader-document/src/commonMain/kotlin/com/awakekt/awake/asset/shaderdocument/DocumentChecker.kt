/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderdocument

/** A document that passed every check, with what its compilation needs to know about it. */
internal class CheckedDocument(
    val document: ShaderDocument,
    /** The blend in effect: the document's own, or its surface's default. */
    val blend: ShaderBlend,
    val vertexUsage: StageUsage,
    val fragmentUsage: StageUsage,
)

/** The outcome of checking a document: the checked document when there were no issues. */
internal class CheckResult(val checked: CheckedDocument?, val issues: List<ShaderDocumentIssue>)

/** Checks a whole document: its structure, both stages, and its size and cost against the limits. */
internal class DocumentChecker(document: ShaderDocument, limits: ShaderDocumentLimits) {
    private val context = CheckContext(document, limits)
    private val expressions = ExpressionChecker(context)
    private val statements = StatementChecker(context, expressions)

    fun check(): CheckResult {
        val document = context.document
        if (document.formatVersion != ShaderDocument.CURRENT_FORMAT_VERSION) {
            context.issue("formatVersion", "format version ${document.formatVersion} is not one this version reads (${ShaderDocument.CURRENT_FORMAT_VERSION})")
        }
        val blend = surfaceAndBlend(document)
        plane(document)
        parameters(document)
        textures(document)
        stages(document)
        costs()
        val checked = if (context.issues.isEmpty()) {
            CheckedDocument(document, blend, context.vertexUsage, context.fragmentUsage)
        } else {
            null
        }
        return CheckResult(checked, context.issues.toList())
    }

    private fun surfaceAndBlend(document: ShaderDocument): ShaderBlend {
        val blend = document.blend ?: defaultBlend(document.surface)
        when {
            document.surface == ShaderSurface.Background && blend != ShaderBlend.Opaque ->
                context.issue("blend", "a background is opaque; it draws behind everything")
            document.surface == ShaderSurface.Overlay && blend != ShaderBlend.Alpha ->
                context.issue("blend", "an overlay blends by alpha over what is drawn")
        }
        return blend
    }

    private fun plane(document: ShaderDocument) {
        val plane = document.plane
        if (document.surface != ShaderSurface.Plane) {
            if (plane != null) context.issue("plane", "only a plane surface has a 'plane'")
            if (document.vertex != null) context.issue("vertex", "only a plane surface has a vertex stage")
            return
        }
        when {
            plane == null -> context.issue("plane", "a plane surface needs 'plane' with its size")
            plane.size.size != 2 || plane.size.any { !it.isFinite() || it <= 0f } ->
                context.issue("plane.size", "a plane's size is two positive numbers, width and depth")
            plane.segments !in 1..context.limits.maxPlaneSegments ->
                context.issue("plane.segments", "a plane has 1 to ${context.limits.maxPlaneSegments} segments per side, got ${plane.segments}")
        }
    }

    private fun parameters(document: ShaderDocument) {
        val parameters = document.parameters
        if (parameters.size > context.limits.maxParameters) {
            context.issue("parameters", "${parameters.size} parameters; the limit is ${context.limits.maxParameters}")
        }
        names(parameters.map { it.name }, "parameters", "parameter")
        parameters.forEachIndexed { i, parameter ->
            val expected = parameter.type.shape.components
            when {
                parameter.default.size != expected ->
                    context.issue("parameters[$i].default", "'${parameter.name}' holds $expected number(s), and its default has ${parameter.default.size}")
                parameter.default.any { !it.isFinite() } ->
                    context.issue("parameters[$i].default", "'${parameter.name}' has a default that is not a finite number")
            }
        }
    }

    private fun textures(document: ShaderDocument) {
        // A document binds its textures to fixed slots, so no limit can raise the count past them.
        val limit = minOf(context.limits.maxTextures, TEXTURE_SLOTS)
        if (document.textures.size > limit) {
            context.issue("textures", "${document.textures.size} textures; the limit is $limit")
        }
        names(document.textures.map { it.name }, "textures", "texture")
    }

    private fun stages(document: ShaderDocument) {
        document.vertex?.let { vertex ->
            val env = BlockEnv(Stage.Vertex, Scope(), weight = 1, loopDepth = 0, topLevel = true)
            statements.checkBlock(vertex.statements, "vertex.statements", env)
            vertex.displacement?.let { displacement ->
                val typed = expressions.check(displacement, "vertex.displacement", env.expressions)
                if (typed != null && typed.shape != ValueShape.F32) {
                    context.issue("vertex.displacement", "a displacement is one number, got ${typed.shape}")
                }
            }
        }
        val env = BlockEnv(Stage.Fragment, Scope(), weight = 1, loopDepth = 0, topLevel = true)
        statements.checkBlock(document.fragment.statements, "fragment.statements", env)
        val color = expressions.check(document.fragment.color, "fragment.color", env.expressions)
        if (color != null && color.shape != ValueShape.Vec4) {
            context.issue("fragment.color", "a colour is four numbers, red, green, blue and alpha, got ${color.shape}")
        }
        document.textures.forEachIndexed { i, texture ->
            if (texture.name !in context.sampledTextures) {
                context.issue("textures[$i]", "'${texture.name}' is never sampled; sample it or remove it")
            }
        }
    }

    private fun costs() {
        val limits = context.limits
        val over = listOf(
            Triple("expressions and statements", context.nodes.toLong(), limits.maxNodes),
            Triple("weighted cost (each node times the loop iterations around it)", context.weightedCost, limits.maxWeightedCost),
            Triple("texture samples per pixel", context.weightedSamples, limits.maxWeightedSamples),
            Triple("locals and loop counters", context.locals.toLong(), limits.maxLocals),
        )
        over.filter { (_, value, limit) -> value > limit }.forEach { (what, value, limit) ->
            context.issue("", "$value $what; the limit is $limit")
        }
    }

    /** Checks that each of [names] is valid and unique within its list. */
    private fun names(names: List<String>, path: String, what: String) {
        val seen = HashSet<String>()
        names.forEachIndexed { i, name ->
            when {
                !DOCUMENT_NAME.matches(name) ->
                    context.issue("$path[$i].name", "'$name' is not a valid $what name: a letter, then letters, digits or underscores, 32 at most")
                !seen.add(name) -> context.issue("$path[$i].name", "$what '$name' is declared twice")
            }
        }
    }
}

/** The blend a surface uses when its document names none. */
internal fun defaultBlend(surface: ShaderSurface): ShaderBlend =
    if (surface == ShaderSurface.Overlay) ShaderBlend.Alpha else ShaderBlend.Opaque
