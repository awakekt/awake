/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.asset.shaders.RenderPlan
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuDrawRequest
import com.awakekt.awake.render.command.GpuResolvedDraw
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.pipeline.CullMode
import com.awakekt.awake.render.pipeline.PipelineKey
import com.awakekt.awake.render.pipeline.depthRenderKey
import com.awakekt.awake.render.testing.NoopRenderer
import kotlin.io.encoding.Base64

/**
 * A GPU-free renderer that instances what a backend built from [plan] could, as both engines map a
 * plan's instanced pipelines: the primary format and each `InstancedFormat`, unculled and back-culled,
 * since every plain instanced pipeline gets its back-culled twin. Every request resolves, so the scene
 * compiler hands each pass what a backend's would.
 */
internal class PlanInstancingRenderer(plan: RenderPlan) :
    NoopRenderer(),
    GpuDrawPreparationSource {
    private val instanced: Set<VertexFormat> = plan.scenePipelines.mapNotNullTo(HashSet()) { pipeline ->
        when (val key = pipeline.key) {
            PipelineKey.Instanced -> plan.primary.vertexFormat
            is PipelineKey.InstancedFormat -> key.vertexFormat
            else -> null
        }
    }

    override val gpuDrawPreparer = object : GpuDrawPreparer {
        override fun prepare(request: GpuDrawRequest, sourceIndex: Int, context: com.awakekt.awake.render.command.GpuDrawPreparationContext) =
            GpuResolvedDraw(
                pipeline = Handle,
                vertexFormat = request.mesh.format,
                depthPipeline = Handle,
                depthRenderKey = request.depthRenderKey(),
                materialBinding = Binding,
                vertexBuffer = null,
                indexBuffer = null,
                elementCount = TRIANGLE,
                transparent = request.transparent,
                instances = request.instanceModels?.size ?: 1,
            )

        override fun canInstance(format: VertexFormat): Boolean = format in instanced

        override fun canInstance(format: VertexFormat, cullMode: CullMode): Boolean = cullMode != CullMode.Front && format in instanced
    }

    private object Handle : PipelineHandle

    private object Binding : MaterialBinding

    private companion object {
        const val TRIANGLE = 3
    }
}

/** How a [triangleModel] is drawn: textured or not, cut out by its texture's alpha, skinned to an animated joint. */
internal data class ModelKind(val textured: Boolean, val masked: Boolean = false, val skinned: Boolean = false)

/**
 * One triangle as a glTF model of [kind]: textured with a 1×1 PNG, its material `MASK`ed at 0.5 and
 * double-sided, and skinned to two joints whose second turns over one second, as [skinnedArmGltf]'s does.
 */
internal fun triangleModel(kind: ModelKind): String {
    val positions = floats(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f)
    val uvs = floats(0f, 0f, 1f, 0f, 0f, 1f)
    val indices = byteArrayOf(0, 0, 1, 0, 2, 0, 0, 0)
    val skin = if (kind.skinned) skinChunks() else emptyList()
    val chunks = listOf(positions, uvs, indices) + skin
    val buffer = chunks.reduce(ByteArray::plus)
    val offsets = chunks.runningFold(0) { offset, chunk -> offset + chunk.size }
    fun view(index: Int) = """{"buffer":0,"byteOffset":${offsets[index]},"byteLength":${chunks[index].size}}"""
    val attributes = buildString {
        append(""""POSITION": 0""")
        if (kind.textured) append(""", "TEXCOORD_0": 1""")
        if (kind.skinned) append(""", "JOINTS_0": 3, "WEIGHTS_0": 4""")
    }
    val alpha = if (kind.masked) ""","alphaMode":"MASK","alphaCutoff":0.5,"doubleSided":true""" else ""
    val material = if (kind.textured) """, "material": 0""" else ""
    val textures = if (kind.textured) {
        """"materials": [{"pbrMetallicRoughness":{"baseColorTexture":{"index":0}}$alpha}],
           "textures": [{"source":0}], "images": [{"uri":"data:image/png;base64,$PIXEL"}],"""
    } else {
        ""
    }
    val skinAccessors = if (kind.skinned) {
        """,{"bufferView": 3, "componentType": 5121, "count": 3, "type": "VEC4"},
            {"bufferView": 4, "componentType": 5126, "count": 3, "type": "VEC4"},
            {"bufferView": 5, "componentType": 5126, "count": 2, "type": "MAT4"},
            {"bufferView": 6, "componentType": 5126, "count": 2, "type": "SCALAR"},
            {"bufferView": 7, "componentType": 5126, "count": 2, "type": "VEC4"}"""
    } else {
        ""
    }
    val nodes = if (kind.skinned) {
        """"nodes": [{"name": "Root", "children": [1]}, {"name": "Tip", "translation": [0, 1, 0]}, {"name": "Body", "mesh": 0, "skin": 0}],
           "skins": [{"inverseBindMatrices": 5, "joints": [0, 1]}],
           "animations": [{"channels": [{"sampler": 0, "target": {"node": 1, "path": "rotation"}}],
                           "samplers": [{"input": 6, "output": 7, "interpolation": "LINEAR"}]}],
           "scenes": [{"nodes": [0, 2]}],"""
    } else {
        """"nodes": [{"name": "Body", "mesh": 0}], "scenes": [{"nodes": [0]}],"""
    }
    return """
    {
      "asset": {"version": "2.0"},
      "buffers": [{"uri": "data:application/octet-stream;base64,${Base64.encode(buffer)}", "byteLength": ${buffer.size}}],
      "bufferViews": [${chunks.indices.joinToString(",") { view(it) }}],
      "accessors": [
        {"bufferView": 0, "componentType": 5126, "count": 3, "type": "VEC3", "min": [0,0,0], "max": [1,1,0]},
        {"bufferView": 1, "componentType": 5126, "count": 3, "type": "VEC2"},
        {"bufferView": 2, "componentType": 5123, "count": 3, "type": "SCALAR"}$skinAccessors
      ],
      $textures
      "meshes": [{"primitives": [{"attributes": {$attributes}, "indices": 2$material}]}],
      $nodes
      "scene": 0
    }
    """.trimIndent()
}

/** Joints, weights, inverse binds and the second joint's rotation clip, as [skinnedArmGltf] has them. */
private fun skinChunks(): List<ByteArray> {
    val joints = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0)
    val weights = floats(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f)
    val identity = floats(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
    val inverseBind = identity + floats(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, -1f, 0f, 1f)
    return listOf(joints, weights, inverseBind, floats(0f, 1f), floats(0f, 0f, 0f, 1f, 0f, 0f, 0.7071f, 0.7071f))
}

private fun floats(vararg values: Float): ByteArray {
    val bytes = ByteArray(values.size * 4)
    values.forEachIndexed { index, value ->
        val bits = value.toRawBits()
        for (byte in 0 until 4) bytes[index * 4 + byte] = (bits ushr (byte * 8)).toByte()
    }
    return bytes
}

/** A 1×1 opaque PNG. */
private const val PIXEL = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
