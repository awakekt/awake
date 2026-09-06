/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.render.command.BufferHandle
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.command.UniformBlock
import com.awakekt.awake.render.pipeline.PipelineSpec
import com.awakekt.awake.render.pipeline.ResourceKind
import com.awakekt.awake.render.texture.TextureAsset

/**
 * A scene-pass feature an app opts into: what pipeline it needs, and what to do with it.
 *
 * One shared type, not one per backend. The two it replaces (`VulkanContentFeature` and
 * `WebGpuContentFeature`) each handed a feature its own backend's device and swapchain so the
 * feature could build a pipeline by hand -- which meant every content feature was written twice.
 * A feature now *declares* a [PipelineSpec] and the engine's existing `PipelineRegistry` builds
 * it, so nothing here names a backend.
 *
 * [build] receives what the registry produced: the pipeline as an opaque [PipelineHandle], and
 * its own [UniformBlock] (non-null because [spec] must declare `uniforms` -- a content feature
 * has no per-draw material to read them from).
 *
 * [textures] needs no addition to that signature. [UniformBlock.binding] already answers with
 * the whole descriptor set or bind group, not just the buffer inside it, so a texture the
 * backend wrote into that same group is bound by the call a feature already makes.
 *
 * [geometry] does, and that is the honest difference: a vertex buffer is not in the descriptor
 * set, so nothing a feature already holds can reach it. A feature that declares one receives the
 * uploaded [ContentGeometry] as [build]'s third argument; a vertex-less feature receives null and
 * ignores it, which is every feature the sky's shape covers.
 *
 * @property name Identifies this feature's pipeline in the registry and in logs. Two features
 * sharing a name is a collision, not a merge.
 * @property spec The pipeline this feature needs, including the uniform layout it owns.
 * @property textures Pixel data for each sampled-texture binding [spec] declares, keyed by
 * binding index. Uploaded once when the feature is built -- see the class note below on why a
 * per-frame swap has no path here. A binding declared `arrayed` takes a multi-layer
 * [TextureAsset]; a single-layer one there is rejected rather than left to the GPU to catch.
 * @property geometry Mesh uploaded once with the feature, or null when the feature supplies its
 * own. Load-time data, for the same reason the textures above are.
 * @property paint Which side of the scene's geometry this feature draws on. A sky draws before
 * it; anything reading [com.awakekt.awake.render.pipeline.BindingSemantic.SceneDepth]
 * to cover what is already there -- fog, water, soft particles -- draws after.
 * @property samplesSceneDepth Whether this feature's pipeline declares the scene-depth group. A
 * declaration, not a request: the pass itself is opted into by `RenderPlan.sceneDepthShaderSet`,
 * and a feature that sets this without the plan supplying that pass gets an empty layout at the
 * slot and samples nothing. Needed because a Vulkan pipeline's set layouts are positional and
 * fixed at creation, so the engine has to know before it compiles the pipeline.
 * @property build Turns the registry's output into the feature that records with it.
 *
 * ### Textures are uploaded once
 *
 * A `Material` allocates a descriptor set per frame in flight and per draw slot, so it can be
 * rewritten between frames. A content feature's group is built once, when the feature is. That
 * suits a splat weightmap or a layer texture, which are load-time data; it does not suit
 * anything a feature wants to replace per frame, and there is deliberately no API here that
 * looks like it would work.
 */
class ContentFeature(
    val name: String,
    val spec: PipelineSpec,
    val textures: Map<Int, TextureAsset> = emptyMap(),
    val geometry: MeshGeometry? = null,
    val paint: ContentPaint = ContentPaint.BeforeGeometry,
    val samplesSceneDepth: Boolean = false,
    val build: (PipelineHandle, UniformBlock, ContentGeometry?) -> RenderFeature<RenderFrameContext>,
) {
    init {
        require(spec.uniforms != null) {
            "Content feature '$name' declares no uniforms. A content feature owns its uniform " +
                "block -- there is no per-draw material to read one from -- so a null layout " +
                "would leave build() with nothing to hand its feature."
        }
        val sampled = spec.materialBindings
            ?.entries
            .orEmpty()
            .filter { it.kind == ResourceKind.SampledTexture }
        val declared = sampled.map { it.binding }.toSet()
        // Both directions, because both fail late and neither fails clearly. A supplied texture
        // with no declared binding is written nowhere; a declared binding with no texture leaves
        // a descriptor the shader samples unwritten -- undefined reads on Vulkan, and a
        // bind-group rejection on WebGPU.
        require(textures.keys == declared) {
            "Content feature '$name' supplies textures for ${textures.keys.sorted()} but its " +
                "pipeline declares sampled textures at ${declared.sorted()}. Every declared " +
                "binding needs pixel data and every supplied texture needs a binding."
        }
        // Layer count is the one texture property the shader also states, so it is the one that
        // can disagree. Sampling a 2D view through a `texture_2d_array` declaration is a Vulkan
        // validation error naming a descriptor index, far from the mismatched asset.
        sampled.forEach { entry ->
            val layers = textures.getValue(entry.binding).layerCount
            require(entry.arrayed == (layers > 1)) {
                val declaredAs = if (entry.arrayed) "texture_2d_array" else "texture_2d"
                "Content feature '$name' declares binding ${entry.binding} as $declaredAs but " +
                    "supplies $layers layer(s) there."
            }
        }
        require(geometry == null || spec.vertexFormat == geometry.format) {
            "Content feature '$name' draws ${geometry?.format} geometry through a " +
                "${spec.vertexFormat} pipeline. A mismatch here reads a vertex buffer with the " +
                "wrong stride, which renders scrambled rather than failing."
        }
    }
}

/**
 * Where a [ContentFeature] sits in the scene pass's paint order.
 *
 * Two cases rather than an integer priority: the scene pass has exactly one thing to be ordered
 * against, and a number would let two features claim an order between themselves that neither
 * engine's feature list can honour anyway.
 */
enum class ContentPaint {
    /** Before the opaque geometry, which then paints over it -- a sky. */
    BeforeGeometry,

    /** After it, covering what it drew -- fog, and anything else reading the scene depth. */
    AfterGeometry,
}

/**
 * A content feature's own uploaded geometry: the subset of `PreparedDraw` a feature that records
 * its own draw actually needs.
 *
 * Backend-neutral because [BufferHandle] is a marker interface each backend's own handle type
 * implements -- so this carries real GPU buffers without the render contract's `Mesh` having to
 * expose them, which it deliberately does not.
 *
 * @property vertexBuffer The uploaded vertices.
 * @property indexBuffer The uploaded indices, or null for a non-indexed draw.
 * @property elementCount Index count when [indexBuffer] is set, vertex count otherwise -- the
 * same convention `PreparedDraw.elementCount` uses.
 */
class ContentGeometry(
    val vertexBuffer: BufferHandle,
    val indexBuffer: BufferHandle?,
    val elementCount: Int,
)
