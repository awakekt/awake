/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderdocument

import com.awakekt.awake.asset.shaders.ContentFeatureSource
import com.awakekt.awake.asset.shaders.ShaderSet
import com.awakekt.awake.asset.shaders.spec
import com.awakekt.awake.asset.shaders.stagesFor
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.inverse
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.command.UniformBlock
import com.awakekt.awake.render.passes.ContentFeature
import com.awakekt.awake.render.passes.ContentGeometry
import com.awakekt.awake.render.passes.ContentPaint
import com.awakekt.awake.render.passes.RenderFeature
import com.awakekt.awake.render.passes.RenderFrameContext
import com.awakekt.awake.render.passes.RenderPassSlot
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.pipeline.PipelineVariant
import com.awakekt.awake.render.texture.TextureAsset

/** Builds the content feature for one compiled document: its pipeline, textures, geometry and per-frame recording. */
internal class DocumentContentFeature(
    private val checked: CheckedDocument,
    private val shaders: ShaderSet,
    private val fields: DocumentUniformFields,
) {
    private val document = checked.document

    fun source(inputs: ShaderEffectInputs, textures: Map<String, TextureAsset>, name: String): ContentFeatureSource {
        require(inputs.parameters.size == document.parameters.size * FLOATS_PER_PARAMETER) {
            "These inputs were made for a document with ${inputs.parameters.size / FLOATS_PER_PARAMETER} parameter(s), " +
                "not '${document.name}' with ${document.parameters.size}."
        }
        val bound = bindTextures(textures)
        val geometry = document.plane?.let(::planeGeometry)
        val format = if (geometry == null) VertexFormat.None else VertexFormat.PositionUv
        return ContentFeatureSource { backend ->
            ContentFeature(
                name = name,
                // A fresh layout per attach: an engine registers one pipeline per spec, and the layout is
                // what tells two attaches of one document apart.
                spec = shaders.stagesFor(backend).spec(vertexFormat = format, variant = variant(), uniforms = fields.layout()),
                textures = bound,
                geometry = geometry,
                paint = if (document.surface == ShaderSurface.Overlay || checked.blend != ShaderBlend.Opaque) {
                    ContentPaint.AfterGeometry
                } else {
                    ContentPaint.BeforeGeometry
                },
            ) { pipeline, uniforms, uploaded -> DocumentRenderFeature(pipeline, uniforms, uploaded, inputs, fields) }
        }
    }

    private fun variant(): PipelineVariant = when {
        document.surface == ShaderSurface.Background -> PipelineVariant.Background
        document.surface == ShaderSurface.Overlay -> PipelineVariant.Overlay
        checked.blend == ShaderBlend.Alpha -> PipelineVariant.AlphaBlended
        checked.blend == ShaderBlend.Additive -> PipelineVariant.AdditiveBlended
        else -> PipelineVariant.Opaque
    }

    /** The supplied textures at their bindings, after checking every declared texture is supplied once and nothing else is. */
    private fun bindTextures(supplied: Map<String, TextureAsset>): Map<Int, TextureAsset> {
        val declared = document.textures.map { it.name }
        val issues = buildList {
            declared.filter { it !in supplied }.forEach { add(ShaderDocumentIssue("textures.$it", "the document samples '$it' and no texture was supplied for it")) }
            supplied.keys.filter { it !in declared }.forEach { add(ShaderDocumentIssue("textures.$it", "'$it' is not one of this document's textures")) }
            supplied.filter { (_, asset) -> asset.isCubemap || asset.layerCount != 1 }.keys
                .forEach { add(ShaderDocumentIssue("textures.$it", "'$it' must be a single 2D image, not a cubemap or an array")) }
        }
        if (issues.isNotEmpty()) throw ShaderDocumentException(issues)
        return declared.withIndex().associate { (i, name) -> FIRST_TEXTURE_BINDING + i to supplied.getValue(name) }
    }
}

/** A [ShaderPlane] as a grid in the XZ plane facing +Y, centred on the origin, with UVs 0 to 1 across it. */
internal fun planeGeometry(plane: ShaderPlane): MeshGeometry {
    val segments = plane.segments
    val (width, depth) = plane.size
    val side = segments + 1
    val vertices = FloatArray(side * side * POSITION_UV_FLOATS)
    for (row in 0..segments) {
        for (column in 0..segments) {
            val u = column.toFloat() / segments
            val v = row.toFloat() / segments
            val at = (row * side + column) * POSITION_UV_FLOATS
            floatArrayOf((u - HALF) * width, 0f, (v - HALF) * depth, u, v).copyInto(vertices, at)
        }
    }
    val indices = IntArray(segments * segments * INDICES_PER_QUAD)
    var next = 0
    for (row in 0 until segments) {
        for (column in 0 until segments) {
            val c00 = row * side + column
            val c10 = c00 + 1
            val c01 = c00 + side
            val c11 = c01 + 1
            // Counter-clockwise seen from +Y, as the built-in plane winds, so back-face culling keeps the top.
            intArrayOf(c00, c11, c10, c11, c00, c01).copyInto(indices, next)
            next += INDICES_PER_QUAD
        }
    }
    return MeshGeometry(vertices, indices, VertexFormat.PositionUv)
}

/** Records one document each frame: writes its uniform block from the frame and its inputs, then draws. */
private class DocumentRenderFeature(
    private val pipeline: PipelineHandle,
    private val uniforms: UniformBlock,
    private val geometry: ContentGeometry?,
    private val inputs: ShaderEffectInputs,
    private val fields: DocumentUniformFields,
) : RenderFeature<RenderFrameContext> {
    override val pass = RenderPassSlot.Scene

    // The inverse is recomputed only when the camera's matrix changes, not every frame.
    private val lastViewProjection = FloatArray(MAT4_FLOATS) { Float.NaN }
    private val inverseViewProjection = Mat4().apply { identity() }

    override fun recordCommands(context: RenderFrameContext): Unit = with(context) {
        if (!inputs.visible) return
        if (!viewProjection.data.contentEquals(lastViewProjection)) {
            viewProjection.data.copyInto(lastViewProjection)
            viewProjection.inverse()?.let { inverseViewProjection.set(it) }
        }
        val width = surfaceWidth.coerceAtLeast(1).toFloat()
        val height = surfaceHeight.coerceAtLeast(1).toFloat()
        uniforms.write(frameIndex) {
            put(fields.viewProjection, viewProjection)
            put(fields.inverseViewProjection, inverseViewProjection)
            putPadded(fields.model, inputs.model)
            put(fields.cameraPosition, cameraEye, 1f)
            put(fields.sunDirection, sunDirection, 0f)
            put(fields.clock, inputs.timeSeconds, inputs.deltaSeconds, 0f, 0f)
            put(fields.viewport, width, height, 1f / width, 1f / height)
            fields.params?.let { put(inputs.parameters, it) }
        }
        recorder.bindPipeline(pipeline)
        recorder.bindMaterial(BindingSemantic.Material, uniforms.binding(frameIndex))
        val mesh = geometry
        if (mesh == null) {
            recorder.draw(FULL_SCREEN_TRIANGLE_VERTICES)
        } else {
            recorder.bindVertexBuffer(VERTEX_BUFFER_BINDING, mesh.vertexBuffer)
            mesh.indexBuffer?.let { recorder.bindIndexBuffer(it) }
            recorder.drawIndexed(mesh.elementCount)
        }
    }

    override fun destroy() = Unit
}

private const val POSITION_UV_FLOATS = 5
private const val INDICES_PER_QUAD = 6
private const val HALF = 0.5f
private const val MAT4_FLOATS = 16
private const val FULL_SCREEN_TRIANGLE_VERTICES = 3
private const val VERTEX_BUFFER_BINDING = 0
