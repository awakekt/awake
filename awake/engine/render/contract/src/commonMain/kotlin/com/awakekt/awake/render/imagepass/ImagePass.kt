/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.imagepass

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.graphics2d.BlendMode
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.render.pipeline.EffectQuality
import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.render.renderer.UniformLayout
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.render.texture.TextureAsset
import kotlin.math.max
import kotlin.math.min

/**
 * Axis-aligned pixel rectangle within a render target or swapchain surface.
 *
 * Coordinates are in top-left origin framebuffer pixels (`+X` right, `+Y` down).
 *
 * @property x Left coordinate in framebuffer pixels.
 * @property y Top coordinate in framebuffer pixels.
 * @property width Rectangle width in framebuffer pixels.
 * @property height Rectangle height in framebuffer pixels.
 */
data class ImagePassRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
) {
    /** Right edge in framebuffer pixels (`x + width`). */
    val right: Float get() = x + width

    /** Bottom edge in framebuffer pixels (`y + height`). */
    val bottom: Float get() = y + height

    /** True when either dimension is zero or negative. */
    val isEmpty: Boolean get() = width <= 0f || height <= 0f

    /**
     * Clamps this rectangle to `[0, maxWidth] x [0, maxHeight]`.
     */
    fun clampTo(maxWidth: Float, maxHeight: Float): ImagePassRect {
        if (maxWidth <= 0f || maxHeight <= 0f) return Empty
        val left = x.coerceIn(0f, maxWidth)
        val top = y.coerceIn(0f, maxHeight)
        val clampedRight = max(left, min(right, maxWidth))
        val clampedBottom = max(top, min(bottom, maxHeight))
        return ImagePassRect(
            x = left,
            y = top,
            width = clampedRight - left,
            height = clampedBottom - top,
        )
    }

    /**
     * Converts this pixel rectangle into normalized `[0, 1]` UV bounds inside a target of size
     * `[targetWidth] x [targetHeight]`.
     */
    fun normalizedIn(targetWidth: Int, targetHeight: Int): ImagePassRect {
        if (targetWidth <= 0 || targetHeight <= 0) return Empty
        val w = targetWidth.toFloat()
        val h = targetHeight.toFloat()
        val clamped = clampTo(w, h)
        return ImagePassRect(
            x = clamped.x / w,
            y = clamped.y / h,
            width = clamped.width / w,
            height = clamped.height / h,
        )
    }

    /** Pre-defined [ImagePassRect] instances. */
    companion object {
        /** Zero-area rectangle at the origin. */
        val Empty: ImagePassRect = ImagePassRect(0f, 0f, 0f, 0f)
    }
}

/**
 * Pixel format of a transient target allocated by an image-pass executor.
 */
enum class ImagePassTargetFormat {
    /** Matches the active destination color format. */
    DestinationMatch,

    /** 8-bit normalized RGBA (`RGBA8Unorm`). */
    Rgba8Unorm,

    /** 16-bit floating-point RGBA (`RGBA16Float`) for HDR chains. */
    Rgba16Float,
}

/**
 * Declares a transient render target required by an [ImagePassChain].
 *
 * When [width] and [height] are zero, the executor derives dimensions by dividing the reference
 * bounds by [scaleDivisor] (clamped to at least `1x1`).
 *
 * @property id Identifier used to reference this target in subsequent steps or bindings.
 * @property scaleDivisor Downscale factor relative to reference bounds when [width]/[height] are zero.
 * @property width Explicit target width in pixels (or 0 to derive from [scaleDivisor]).
 * @property height Explicit target height in pixels (or 0 to derive from [scaleDivisor]).
 * @property format Pixel format to allocate for this transient target.
 */
data class ImagePassTargetSpec(
    val id: String,
    val scaleDivisor: Int = 1,
    val width: Int = 0,
    val height: Int = 0,
    val format: ImagePassTargetFormat = ImagePassTargetFormat.DestinationMatch,
) {
    init {
        require(id.isNotBlank()) { "ImagePassTargetSpec.id must not be blank." }
        require(scaleDivisor >= 1) { "ImagePassTargetSpec.scaleDivisor must be >= 1, was $scaleDivisor." }
        require(width >= 0 && height >= 0) { "ImagePassTargetSpec dimensions must be >= 0." }
    }

    /** Resolves target width in pixels against [referenceWidth]. */
    fun scaledWidth(referenceWidth: Int): Int =
        if (width > 0) width else (referenceWidth.coerceAtLeast(1) / scaleDivisor).coerceAtLeast(1)

    /** Resolves target height in pixels against [referenceHeight]. */
    fun scaledHeight(referenceHeight: Int): Int =
        if (height > 0) height else (referenceHeight.coerceAtLeast(1) / scaleDivisor).coerceAtLeast(1)
}

/** Texture filtering mode for an [ImagePassTextureBinding]. */
enum class ImagePassFilterMode {
    /** Nearest-neighbor texel sampling. */
    Nearest,

    /** Bilinear texel filtering. */
    Linear,
}

/** UV address wrapping mode for an [ImagePassTextureBinding]. */
enum class ImagePassAddressMode {
    /** Clamps UV coordinates to `[0, 1]` so edge texels do not bleed across borders. */
    ClampToEdge,

    /** Wraps UV coordinates periodically. */
    Repeat,
}

/**
 * Sampler configuration for an [ImagePassTextureBinding].
 *
 * @property filter Filtering mode (nearest or bilinear).
 * @property addressMode Address wrap mode for UV coordinates.
 */
data class ImagePassSamplerSpec(
    val filter: ImagePassFilterMode = ImagePassFilterMode.Linear,
    val addressMode: ImagePassAddressMode = ImagePassAddressMode.ClampToEdge,
)

/**
 * Source image bound as a sampled texture input to an [ImagePassStep].
 */
sealed interface ImagePassSource {
    /**
     * Snapshot of the currently active render target or swapchain color attachment at the point
     * where the [ImagePassChain] begins executing.
     */
    data object DestinationSnapshot : ImagePassSource

    /**
     * Output of a previously written transient target declared in [ImagePassChain.transientTargets].
     *
     * @property id Identifier of the transient target to read from.
     */
    data class TransientTarget(val id: String) : ImagePassSource

    /**
     * Color attachment of an explicit offscreen [RenderTarget].
     *
     * @property target Offscreen target to read from.
     */
    data class RenderTargetColor(val target: RenderTarget) : ImagePassSource

    /**
     * Uploaded static or dynamic [TextureAsset].
     *
     * @property texture Texture asset to sample.
     */
    data class Texture(val texture: TextureAsset) : ImagePassSource
}

/**
 * Binds an [ImagePassSource] and [ImagePassSamplerSpec] to a shader texture/sampler slot.
 *
 * @property binding Pipeline resource slot index.
 * @property source Image resource to bind.
 * @property sampler Sampler filtering and addressing configuration.
 */
data class ImagePassTextureBinding(
    val binding: Int,
    val source: ImagePassSource,
    val sampler: ImagePassSamplerSpec = ImagePassSamplerSpec(),
)

/**
 * Destination surface rendered into by an [ImagePassStep].
 */
sealed interface ImagePassDestination {
    /**
     * Renders into the transient target identified by [id] in [ImagePassChain.transientTargets].
     *
     * @property id Identifier of the target to write to.
     */
    data class TransientTarget(val id: String) : ImagePassDestination

    /**
     * Renders back into the active destination target (resuming the interrupted pass with
     * `loadOp = LOAD`).
     *
     * @property scissorRect Optional scissor rectangle constraining rendered pixels.
     * @property blendMode Blending equation to apply when writing to the active target.
     * @property isPremultiplied True when shader outputs premultiplied alpha.
     * @property blendEnabled True to enable alpha blending into the active target.
     */
    data class ActiveTarget(
        val scissorRect: ImagePassRect? = null,
        val blendMode: BlendMode = BlendMode.SourceOver,
        val isPremultiplied: Boolean = true,
        val blendEnabled: Boolean = true,
    ) : ImagePassDestination
}

/**
 * Self-contained shader specification for an [ImagePassStep].
 *
 * Carries the generated WGSL source, bind group layout, vertex format, and optional uniform block
 * layout so backend executors compile and cache pipelines by [id] without knowing which visual
 * effect produced the shader.
 *
 * @property id Unique cache key for this shader pipeline.
 * @property wgslSource WGSL shader source code.
 * @property groupBindings Resource bindings layout for group 0.
 * @property vertexFormat Vertex buffer layout descriptor.
 * @property uniformLayout Optional uniform buffer layout for packing uniforms.
 */
data class ImagePassShaderSpec(
    val id: String,
    val wgslSource: String,
    val groupBindings: GroupBindings,
    val vertexFormat: VertexFormat,
    val uniformLayout: UniformLayout? = null,
) {
    init {
        require(id.isNotBlank()) { "ImagePassShaderSpec.id must not be blank." }
        require(wgslSource.isNotBlank()) { "ImagePassShaderSpec.wgslSource must not be blank." }
    }
}

/**
 * One GPU draw step in an [ImagePassChain].
 *
 * A step binds [shader], uploads [uniformData] (packed through [ImagePassShaderSpec.uniformLayout]),
 * binds [inputs], and draws either the supplied [quadVertices]/[quadIndices] mesh or a full-target
 * quad into [destination].
 *
 * @property shader Compiled shader specification to execute.
 * @property inputs Textures and samplers bound to the shader.
 * @property destination Surface to draw into.
 * @property uniformData Packed float uniform payload.
 * @property quadVertices Quad vertex attributes in screen space or NDC.
 * @property quadIndices Quad index array.
 * @property clearDestination True to clear [destination] before drawing.
 */
data class ImagePassStep(
    val shader: ImagePassShaderSpec,
    val inputs: List<ImagePassTextureBinding>,
    val destination: ImagePassDestination,
    val uniformData: FloatArray = EmptyUniformData,
    val quadVertices: FloatArray = EmptyVertexData,
    val quadIndices: ShortArray = EmptyIndexData,
    val clearDestination: Boolean = false,
) {
    /** Identifier of the shader executed by this step. */
    val shaderId: String get() = shader.id

    /** True when this step supplies custom quad geometry instead of a full-target quad. */
    val hasCustomGeometry: Boolean
        get() = quadVertices.isNotEmpty() && quadIndices.isNotEmpty()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ImagePassStep) return false
        return shader == other.shader &&
            inputs == other.inputs &&
            destination == other.destination &&
            uniformData.contentEquals(other.uniformData) &&
            quadVertices.contentEquals(other.quadVertices) &&
            quadIndices.contentEquals(other.quadIndices) &&
            clearDestination == other.clearDestination
    }

    override fun hashCode(): Int {
        var result = shader.hashCode()
        result = 31 * result + inputs.hashCode()
        result = 31 * result + destination.hashCode()
        result = 31 * result + uniformData.contentHashCode()
        result = 31 * result + quadVertices.contentHashCode()
        result = 31 * result + quadIndices.contentHashCode()
        result = 31 * result + clearDestination.hashCode()
        return result
    }

    /** Pre-allocated empty arrays for default step geometry and uniforms. */
    companion object {
        /** Shared empty float buffer for steps without uniforms. */
        val EmptyUniformData: FloatArray = FloatArray(0)

        /** Shared empty vertex buffer for full-target steps. */
        val EmptyVertexData: FloatArray = FloatArray(0)

        /** Shared empty index buffer for full-target steps. */
        val EmptyIndexData: ShortArray = ShortArray(0)
    }
}

/**
 * Multi-step GPU image-processing chain lowered from a visual effect and executed by a backend's
 * generic image-pass executor.
 *
 * @property sourceBounds Pixel region in the active target that this chain snapshots or processes.
 * @property transientTargets Transient ping-pong or downscaled targets needed by [steps].
 * @property steps Ordered sequence of shader passes to execute.
 */
data class ImagePassChain(
    val sourceBounds: ImagePassRect,
    val transientTargets: List<ImagePassTargetSpec>,
    val steps: List<ImagePassStep>,
) {
    /** True when this chain has no steps or covers an empty region. */
    val isEmpty: Boolean
        get() = steps.isEmpty() || sourceBounds.isEmpty

    /** True when any step reads from [ImagePassSource.DestinationSnapshot]. */
    val requiresDestinationSnapshot: Boolean
        get() = steps.any { step ->
            step.inputs.any { it.source == ImagePassSource.DestinationSnapshot }
        }

    /** Shared chain instances. */
    companion object {
        /** Empty chain that performs no GPU work. */
        val Empty: ImagePassChain = ImagePassChain(
            sourceBounds = ImagePassRect.Empty,
            transientTargets = emptyList(),
            steps = emptyList(),
        )
    }
}

/**
 * What the colour already in an image-pass target holds, so an effect knows whether it still has
 * to be tone mapped and encoded for display.
 */
enum class ImagePassColorEncoding {
    /**
     * Display-ready colour: the shaders that drew it already applied exposure, a tone curve and
     * the gamma encode. Every lit scene shader writes this, so it is what a scene camera's target
     * holds.
     */
    DisplayReferred,

    /**
     * Scene-referred linear radiance, possibly above 1, that a tone-mapping step must still turn
     * into display colour. Only a target drawn by shaders that skip the display transform holds it.
     */
    LinearHdr,
}

/**
 * Target properties used when lowering an effect into an [ImagePassChain].
 *
 * @property width Framebuffer width in pixels.
 * @property height Framebuffer height in pixels.
 * @property clipSpace Coordinate system conventions for NDC.
 * @property snapshotSupported True when target supports destination snapshots.
 * @property effectQuality Active effect quality tier.
 * @property colorEncoding What the target's colour holds when the chain snapshots it.
 */
data class ImagePassTargetContext(
    val width: Int,
    val height: Int,
    val clipSpace: ClipSpace,
    val snapshotSupported: Boolean = true,
    val effectQuality: EffectQuality = EffectQuality.High,
    val colorEncoding: ImagePassColorEncoding = ImagePassColorEncoding.DisplayReferred,
)

/**
 * Schedules an [ImagePassChain] at [primitiveIndex] in a 2D primitive stream.
 *
 * Primitives before [primitiveIndex] are drawn first, then [chain] snapshots and processes the
 * target before resuming the target render pass with `loadOp = LOAD` for primitives at and after
 * [primitiveIndex]. When [chain] is empty or requires an unsupported destination snapshot,
 * [fallbackPrimitives] are emitted at [primitiveIndex] instead.
 */
data class ImagePassPlacement(
    val primitiveIndex: Int,
    val chain: ImagePassChain,
    val fallbackPrimitives: List<UiDrawPrimitive> = emptyList(),
) {
    init {
        require(primitiveIndex >= 0) { "ImagePassPlacement.primitiveIndex must be >= 0, was $primitiveIndex." }
    }
}
