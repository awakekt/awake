/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.effects

import com.awakekt.awake.asset.shaderdsl.AslShaderDefinition
import com.awakekt.awake.asset.shaderdsl.a
import com.awakekt.awake.asset.shaderdsl.bindingsByGroup
import com.awakekt.awake.asset.shaderdsl.clamp
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.mix
import com.awakekt.awake.asset.shaderdsl.rgb
import com.awakekt.awake.asset.shaderdsl.sampler
import com.awakekt.awake.asset.shaderdsl.shader
import com.awakekt.awake.asset.shaderdsl.texture2d
import com.awakekt.awake.asset.shaderdsl.textureSample
import com.awakekt.awake.asset.shaderdsl.vec4
import com.awakekt.awake.asset.shaderdsl.w
import com.awakekt.awake.asset.shaderdsl.xy
import com.awakekt.awake.asset.shaderdsl.zw
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.geometry.VertexAttribute
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.geometry.VertexSemantic
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.render.imagepass.ImagePassRect
import com.awakekt.awake.render.imagepass.ImagePassShaderSpec
import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.render.renderer.UniformField
import com.awakekt.awake.render.renderer.UniformLayout
import com.awakekt.awake.render.renderer.UniformWriter

/**
 * Interleaved 2D position (`vec2`) + UV (`vec2`) vertex layout shared by image-effect quads.
 */
val ImageEffectVertexFormat: VertexFormat = VertexFormat(
    listOf(
        VertexAttribute(VertexSemantic.Position, GpuDataShape.Vec2, location = 0),
        VertexAttribute(VertexSemantic.Uv, GpuDataShape.Vec2, location = 1),
    ),
)

/**
 * Uniform layouts and ASL shader definitions for built-in image effects.
 */
object ImageEffectShaders {
    /** Identifier for the bounded UV copy shader. */
    const val COPY_SHADER_ID: String = "image_effect_copy"

    /** Identifier for the bounded UV tint shader. */
    const val TINT_SHADER_ID: String = "image_effect_tint"

    /** Normalized UV clamp bounds `(minU, minV, maxU, maxV)`. */
    val UvBoundsField: UniformField = UniformField("uvBounds", GpuDataShape.Vec4)

    /** Tint RGB color in `xyz` and mix strength `[0..1]` in `w`. */
    val TintColorField: UniformField = UniformField("tintColor", GpuDataShape.Vec4)

    /** Uniform block layout for [CopyShader]. */
    val CopyUniformLayout: UniformLayout = UniformLayout(UvBoundsField)

    /** Uniform block layout for [TintShader]. */
    val TintUniformLayout: UniformLayout = UniformLayout(UvBoundsField, TintColorField)

    /**
     * ASL shader that samples a source texture within clamped [UvBoundsField] coordinates.
     */
    val CopyShader: AslShaderDefinition = shader(COPY_SHADER_ID) {
        val uniforms = uniformBlock("Uniforms", group = 0, binding = 0)
        val uvBounds by uniforms.field(GpuDataShape.Vec4)

        val sourceTexture by texture2d(group = 0, binding = 1)
        val sourceSampler by sampler(group = 0, binding = 2)

        val out = varyings("VertexOutput")
        val uv by out.varying(GpuDataShape.Vec2, location = 0)

        vertex {
            val inPos by input(GpuDataShape.Vec2, location = 0)
            val inUv by input(GpuDataShape.Vec2, location = 1)
            out.position set vec4(inPos, 0f.lit, 1f.lit)
            uv set inUv
        }

        fragment {
            val clampedUv = let("clampedUv", clamp(uv, uvBounds.xy, uvBounds.zw))
            val sampled = let("sampled", textureSample(sourceTexture, sourceSampler, clampedUv))
            colorOutput(sampled)
        }
    }

    /**
     * ASL shader that samples a source texture within clamped [UvBoundsField] coordinates and
     * mixes its RGB channels toward [TintColorField] by `tintColor.w`.
     */
    val TintShader: AslShaderDefinition = shader(TINT_SHADER_ID) {
        val uniforms = uniformBlock("Uniforms", group = 0, binding = 0)
        val uvBounds by uniforms.field(GpuDataShape.Vec4)
        val tintColor by uniforms.field(GpuDataShape.Vec4)

        val sourceTexture by texture2d(group = 0, binding = 1)
        val sourceSampler by sampler(group = 0, binding = 2)

        val out = varyings("VertexOutput")
        val uv by out.varying(GpuDataShape.Vec2, location = 0)

        vertex {
            val inPos by input(GpuDataShape.Vec2, location = 0)
            val inUv by input(GpuDataShape.Vec2, location = 1)
            out.position set vec4(inPos, 0f.lit, 1f.lit)
            uv set inUv
        }

        fragment {
            val clampedUv = let("clampedUv", clamp(uv, uvBounds.xy, uvBounds.zw))
            val sampled = let("sampled", textureSample(sourceTexture, sourceSampler, clampedUv))
            val mixedRgb = let("mixedRgb", mix(sampled.rgb, tintColor.rgb, tintColor.w))
            colorOutput(vec4(mixedRgb, sampled.a))
        }
    }

    /** Self-contained [ImagePassShaderSpec] for [CopyShader]. */
    val CopySpec: ImagePassShaderSpec = ImagePassShaderSpec(
        id = COPY_SHADER_ID,
        wgslSource = CopyShader.emitWgsl(),
        groupBindings = CopyShader.bindingsByGroup()[0] ?: GroupBindings(emptyList()),
        vertexFormat = ImageEffectVertexFormat,
        uniformLayout = CopyUniformLayout,
    )

    /** Self-contained [ImagePassShaderSpec] for [TintShader]. */
    val TintSpec: ImagePassShaderSpec = ImagePassShaderSpec(
        id = TINT_SHADER_ID,
        wgslSource = TintShader.emitWgsl(),
        groupBindings = TintShader.bindingsByGroup()[0] ?: GroupBindings(emptyList()),
        vertexFormat = ImageEffectVertexFormat,
        uniformLayout = TintUniformLayout,
    )

    /** Packs [CopyUniformLayout] using [UniformWriter]. */
    fun packCopyUniforms(minU: Float, minV: Float, maxU: Float, maxV: Float): FloatArray =
        UniformWriter(CopyUniformLayout)
            .put(UvBoundsField, minU, minV, maxU, maxV)
            .build()

    /** Packs [TintUniformLayout] using [UniformWriter]. */
    fun packTintUniforms(
        uvBounds: ImagePassRect,
        tintColor: Color,
        strength: Float,
    ): FloatArray {
        val clampedStrength = strength.coerceIn(0f, 1f)
        return UniformWriter(TintUniformLayout)
            .put(UvBoundsField, uvBounds.x, uvBounds.y, uvBounds.right, uvBounds.bottom)
            .put(TintColorField, tintColor.r, tintColor.g, tintColor.b, clampedStrength)
            .build()
    }
}

/**
 * Builds interleaved `(posX, posY, u, v)` vertices for a pixel [rect] inside a target of size
 * `[targetWidth] x [targetHeight]` using the active backend's [clipSpace].
 */
fun buildImagePassQuadVertices(
    rect: ImagePassRect,
    targetWidth: Int,
    targetHeight: Int,
    clipSpace: ClipSpace,
    u0: Float = 0f,
    v0: Float = 0f,
    u1: Float = 1f,
    v1: Float = 1f,
): FloatArray {
    val w = targetWidth.coerceAtLeast(1).toFloat()
    val h = targetHeight.coerceAtLeast(1).toFloat()
    val leftNdc = (rect.x / w) * 2f - 1f
    val rightNdc = (rect.right / w) * 2f - 1f
    val topNdc = if (clipSpace.flipY) (rect.y / h) * 2f - 1f else 1f - (rect.y / h) * 2f
    val bottomNdc = if (clipSpace.flipY) (rect.bottom / h) * 2f - 1f else 1f - (rect.bottom / h) * 2f
    return floatArrayOf(
        leftNdc, topNdc, u0, v0,
        rightNdc, topNdc, u1, v0,
        rightNdc, bottomNdc, u1, v1,
        leftNdc, bottomNdc, u0, v1,
    )
}

/** Standard two-triangle index list `(0, 1, 2, 0, 2, 3)` for a single image-pass quad. */
val ImagePassQuadIndices: ShortArray = shortArrayOf(0, 1, 2, 0, 2, 3)
