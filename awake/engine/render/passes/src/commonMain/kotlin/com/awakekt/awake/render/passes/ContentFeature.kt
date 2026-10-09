/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.render.command.BufferHandle
import com.awakekt.awake.render.command.GpuEnvironmentState
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.command.PreparedDraw
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
 * binding index. Shape and bindings are fixed at construction; opted-in base-level images can
 * receive regions through [textureUpdates]. A binding declared `arrayed` takes a multi-layer
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
 * @property depth The pipeline this feature's geometry casts into the engine's depth pass with,
 * or null when it casts nothing. Built over [spec]'s own group, so it reads the same uniform block
 * and textures and must share [spec]'s vertex format and uniform layout; its shader declares only
 * the group-0 bindings it reads. The feature [build] returns must then be a [ContentDepthSource].
 * @property textureUpdates Optional frame-slot journal for mutable base-level images.
 * @property samplerTextures Sampler binding to image binding for independent filtering.
 * @property build Turns the registry's output into the feature that records with it.
 *
 * Mutable textures opt into [textureUpdates]. Backends prepare regions before any depth or scene
 * pass, retain staging through submission completion, and isolate resources belonging to frames
 * still in flight. Texture shape, descriptor layout and the feature's geometry remain fixed.
 */
class ContentFeature(
    val name: String,
    val spec: PipelineSpec,
    val textures: Map<Int, TextureAsset> = emptyMap(),
    val geometry: MeshGeometry? = null,
    val paint: ContentPaint = ContentPaint.BeforeGeometry,
    val samplesSceneDepth: Boolean = false,
    val depth: PipelineSpec? = null,
    val textureUpdates: ContentTextureUpdates? = null,
    /** Sampler binding to texture binding, when different samplers are required. */
    val samplerTextures: Map<Int, Int> = emptyMap(),
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
        require(textureUpdates?.bindings.orEmpty().all { it in declared && textures.getValue(it).filtering != com.awakekt.awake.render.texture.TextureFiltering.Linear }) {
            "Mutable content textures must be declared base-level textures."
        }
        require(
            samplerTextures.all { (binding, texture) ->
                spec.materialBindings?.entries?.any { it.binding == binding && it.kind == ResourceKind.Sampler } == true && texture in declared
            },
        ) { "Sampler sources must name declared sampler and texture bindings." }
        // Both directions, because both fail late and neither fails clearly. A supplied texture
        // with no declared binding is written nowhere; a declared binding with no texture leaves
        // a descriptor the shader samples unwritten -- undefined reads on Vulkan, and a
        // bind-group rejection on WebGPU.
        require(textures.keys == declared) {
            "Content feature '$name' supplies textures for ${textures.keys.sorted()} but its " +
                "pipeline declares sampled textures at ${declared.sorted()}. Every declared " +
                "binding needs pixel data and every supplied texture needs a binding."
        }
        // The texture's shape -- 2D, array or cube -- is the one property the shader also states,
        // so it is the one that can disagree. Sampling a 2D view through a `texture_2d_array`
        // declaration is a Vulkan validation error naming a descriptor index, far from the asset.
        sampled.forEach { entry ->
            val texture = textures.getValue(entry.binding)
            val (declaredAs, matches) = when {
                entry.cubemap -> "texture_cube" to texture.isCubemap
                entry.arrayed -> "texture_2d_array" to (texture.layerCount > 1 && !texture.isCubemap)
                else -> "texture_2d" to (texture.layerCount == 1)
            }
            require(matches) {
                val supplied = if (texture.isCubemap) "a cubemap" else "${texture.layerCount} layer(s)"
                "Content feature '$name' declares binding ${entry.binding} as $declaredAs but supplies $supplied there."
            }
        }
        require(depth == null || (depth.vertexFormat == spec.vertexFormat && depth.uniforms === spec.uniforms)) {
            "Content feature '$name' casts through a depth pipeline whose vertex format or uniform " +
                "layout differs from its own. The depth pass draws the same geometry with the same " +
                "uniform block, so the two have to read them alike."
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

/**
 * What a [ContentFeature] with a [ContentFeature.depth] pipeline hands the engine's depth pass:
 * its draw for one frame, recorded in every depth sub-pass that frame renders.
 *
 * The depth pass is recorded before the scene pass, so the draw's uniform block holds whatever
 * the feature writes when it records the scene pass; both backends submit the frame only after
 * that, so the depth pass reads this frame's values.
 */
fun interface ContentDepthSource {
    /**
     * The draw in [frameIndex]'s uniform slot, or null when the feature draws nothing this frame.
     * Read once per depth sub-pass and not kept past it, so a feature may reuse one object.
     */
    fun depthDraw(frameIndex: Int, environment: GpuEnvironmentState): PreparedDraw?
}
