/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.render.pipeline

/**
 * The pipeline-variant shapes a backend builds on top of its otherwise-fixed vertex/render-pass
 * setup -- a closed set of named presets, not 5 independent booleans, so a caller can't write a
 * combo nobody means (e.g. [instanceAlpha] without [instanced]). A new pipeline shape (the next
 * particle-style variant) adds one named object here instead of growing each backend's pipeline
 * constructor again.
 *
 * Backend-neutral on purpose: every property below is a rendering-state question both Vulkan and
 * WebGPU answer, just with different spellings (`VkPipelineColorBlendAttachmentState` vs
 * `BlendState`). Keeping the vocabulary here is what lets `PipelineSpec` describe a pipeline
 * once and each backend translate rather than re-decide.
 */
sealed interface PipelineVariant {
    /** Adds a SECOND, instance-rate vertex binding (stride 64) carrying one `mat4` model matrix
     * per instance -- see `instanced.wgsl`. */
    val instanced: Boolean

    /** Only meaningful alongside [instanced]. Adds a THIRD instance-rate binding (stride 16)
     * carrying one `vec4f` RGBA color+alpha per instance -- see `particle.wgsl` and
     * `DrawCall.instanceColors`. */
    val instanceAlpha: Boolean

    /** Only meaningful alongside [instanced]. Adds a FOURTH instance-rate binding (stride 4)
     * carrying one `f32` sprite-strip frame index per instance -- see `particle.wgsl`'s
     * `inFrame` and `DrawCall.instanceFrames`. */
    val instanceFrame: Boolean

    /** `true` enables standard straight-alpha blending (`SRC_ALPHA`/`ONE_MINUS_SRC_ALPHA`, both
     * color and alpha) -- the same blend state each backend's UI texture pipeline already uses. */
    val blendEnabled: Boolean

    /** `false` disables depth WRITE only (depth TEST stays on) -- for order-independent content
     * that shouldn't self-occlude (e.g. particles). */
    val depthWriteEnabled: Boolean

    /** `false` disables the depth TEST as well, so nothing occludes this pipeline's output and it
     * occludes nothing -- for a background drawn before all geometry (a sky). Every other preset
     * leaves this on: [depthWriteEnabled] alone is the weaker "still tested, just not recorded". */
    val depthTestEnabled: Boolean

    /** Byte-for-byte the pipeline both backends built before any variant existed -- the default
     * for every non-instanced draw (primary/wireframe/textured/skinned/shadow). */
    data object Opaque : PipelineVariant {
        override val instanced = false
        override val instanceAlpha = false
        override val instanceFrame = false
        override val blendEnabled = false
        override val depthWriteEnabled = true
        override val depthTestEnabled = true
    }

    /** Plain GPU instancing (`InstancedMeshRenderer`/`InstancedSkinnedMeshRenderer`) -- one
     * model matrix per instance, opaque depth behavior unchanged. */
    data object Instanced : PipelineVariant {
        override val instanced = true
        override val instanceAlpha = false
        override val instanceFrame = false
        override val blendEnabled = false
        override val depthWriteEnabled = true
        override val depthTestEnabled = true
    }

    /**
     * A transparent surface: alpha-blended, depth-tested, no depth write. Not instanced.
     *
     * Depth write off is the whole point -- see `DrawCall.transparent`. Depth *test* stays on so
     * a transparent surface behind opaque geometry is still hidden by it.
     */
    data object AlphaBlended : PipelineVariant {
        override val instanced = false
        override val instanceAlpha = false
        override val instanceFrame = false
        override val blendEnabled = true
        override val depthWriteEnabled = false
        override val depthTestEnabled = true
    }

    /** Billboard particles -- instanced + per-instance alpha + per-instance sprite frame +
     * straight-alpha blend + no depth write (order-independent draws don't self-occlude). See
     * `particle.wgsl`. */
    data object AlphaBlendedParticle : PipelineVariant {
        override val instanced = true
        override val instanceAlpha = true
        override val instanceFrame = true
        override val blendEnabled = true
        override val depthWriteEnabled = false
        override val depthTestEnabled = true
    }

    /**
     * A background drawn before all geometry: depth test and write both off, no blend, not
     * instanced. See `skybox.wgsl`.
     *
     * Depth test off rather than "test against a cleared buffer": nothing has drawn yet when this
     * runs, so there is nothing to test against, and turning the test off is cheaper and clearer
     * than relying on the clear value. Paired with [io.github.awakelab.awake.core.geometry
     * .VertexFormat.None] this is the whole of a full-screen-triangle pipeline's state.
     */
    data object Background : PipelineVariant {
        override val instanced = false
        override val instanceAlpha = false
        override val instanceFrame = false
        override val blendEnabled = false
        override val depthWriteEnabled = false
        override val depthTestEnabled = false
    }

    /**
     * A full-screen effect drawn AFTER geometry: blended, depth test and write both off. See
     * `AslDepthFogShader`.
     *
     * [Background] with blending, and the difference is which side of the geometry it paints on.
     * The depth test is off for the same reason it is there -- a full-screen triangle has no
     * meaningful depth of its own -- but here that means it covers what has already been drawn
     * rather than being covered by what comes next. What it blends by is the scene depth it
     * samples, not the depth buffer it is tested against.
     */
    data object Overlay : PipelineVariant {
        override val instanced = false
        override val instanceAlpha = false
        override val instanceFrame = false
        override val blendEnabled = true
        override val depthWriteEnabled = false
        override val depthTestEnabled = false
    }
}
