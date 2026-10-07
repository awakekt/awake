/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderdocument

import com.awakekt.awake.asset.shaders.ContentFeatureSource
import com.awakekt.awake.asset.shaders.ShaderSet
import com.awakekt.awake.asset.shaders.aslShaderSet
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.render.texture.TextureAsset

/**
 * A [ShaderDocument] that passed every check, compiled to WGSL for both backends.
 *
 * Make one with [ShaderDocuments.compile]. It is immutable and can back any number of running effects:
 * each gets its own [ShaderEffectInputs] and its own [contentFeature], which an engine attaches through
 * its `ContentFeatureHost` and which draws with a pipeline of its own.
 */
class CompiledShaderDocument internal constructor(private val checked: CheckedDocument) {
    /** The document this was compiled from. */
    val document: ShaderDocument get() = checked.document

    /** The blend in effect: the document's own, or its surface's default. */
    val blend: ShaderBlend get() = checked.blend

    private val fields = DocumentUniformFields(document.parameters.size)
    private val lowering = DocumentLowering(checked, fields)

    /** The shader for both backends, one WGSL source per clip space, emitted once here. */
    val shaders: ShaderSet = aslShaderSet { clipSpace -> lowering.lower(clipSpace) }

    private val feature = DocumentContentFeature(checked, shaders, fields)

    /** The WGSL this document compiles to for [clipSpace], for tools and tests. */
    fun emitWgsl(clipSpace: ClipSpace): String = lowering.lower(clipSpace).emitWgsl()

    /** A fresh set of per-frame inputs for one running effect, its parameters at their defaults. */
    fun newInputs(): ShaderEffectInputs = ShaderEffectInputs(document.parameters.size).also { packParameters(emptyMap(), it) }

    /**
     * Writes parameter values into [into]: each of [values], by name, and each parameter it does not
     * name at its default. A value with an unknown name, the wrong count of numbers or a number that is
     * not finite is left out and reported, and that parameter keeps its default.
     *
     * @param values Parameter name to its numbers: as many as the parameter's type holds.
     * @param into Inputs from this document's [newInputs].
     * @return The problems, each naming the parameter; empty when every value was used.
     */
    fun packParameters(values: Map<String, List<Float>>, into: ShaderEffectInputs): List<ShaderDocumentIssue> {
        require(into.parameters.size == document.parameters.size * FLOATS_PER_PARAMETER) {
            "These inputs were made for another document."
        }
        val names = document.parameters.map { it.name }.toSet()
        val issues = values.keys.filter { it !in names }
            .map { ShaderDocumentIssue("parameters.$it", "'$it' is not a parameter of '${document.name}'") }
            .toMutableList()
        document.parameters.forEachIndexed { i, parameter ->
            val expected = parameter.type.shape.components
            val supplied = values[parameter.name]
            val problem = when {
                supplied == null -> null
                supplied.size != expected -> "'${parameter.name}' takes $expected number(s), got ${supplied.size}"
                supplied.any { !it.isFinite() } -> "'${parameter.name}' has a value that is not a finite number"
                else -> null
            }
            problem?.let { issues += ShaderDocumentIssue("parameters.${parameter.name}", it) }
            val chosen = if (supplied == null || problem != null) parameter.default else supplied
            val start = i * FLOATS_PER_PARAMETER
            into.parameters.fill(0f, start, start + FLOATS_PER_PARAMETER)
            chosen.forEachIndexed { component, value -> into.parameters[start + component] = value }
        }
        return issues
    }

    /**
     * The content feature that draws one running effect of this document.
     *
     * @param inputs This effect's inputs, from [newInputs]; the feature reads them every frame.
     * @param textures Texture name to its image, one for each of the document's textures and no others.
     * @param name The feature's name in the engine's logs.
     * @throws ShaderDocumentException When a texture is missing, unknown, or not a single 2D image.
     */
    fun contentFeature(
        inputs: ShaderEffectInputs,
        textures: Map<String, TextureAsset> = emptyMap(),
        name: String = "shader-document",
    ): ContentFeatureSource = feature.source(inputs, textures, name)
}
