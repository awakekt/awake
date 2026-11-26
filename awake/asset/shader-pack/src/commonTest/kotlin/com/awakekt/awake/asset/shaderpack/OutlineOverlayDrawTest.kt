/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.BufferHandle
import com.awakekt.awake.render.command.COVERING_EXEMPT_MASK_LAYER
import com.awakekt.awake.render.command.CommandRecorder
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.command.GpuSubPass
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.command.PreparedDraw
import com.awakekt.awake.render.command.UniformBlock
import com.awakekt.awake.render.imagepass.ImagePassRect
import com.awakekt.awake.render.passes.RenderFrameContext
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.pipeline.EffectQuality
import com.awakekt.awake.render.renderer.OutlineStyle
import com.awakekt.awake.render.renderer.OutlineUniformLayout
import com.awakekt.awake.render.renderer.UniformWriter
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The outline overlay draws only when a mask layer is styled to draw (#1709). A draw exempt from
 * coverings, an unlit mesh, puts a mask pass in every frame, outlined or not; the full-screen overlay
 * then read all 34 of its mask samples per pixel and wrote nothing.
 */
class OutlineOverlayDrawTest {
    @Test
    fun aCoveringExemptPassAloneDrawsNoOverlay() {
        assertEquals(0, draws(GpuSubPass(target = null, targetLayer = COVERING_EXEMPT_MASK_LAYER, viewProjection = Mat4())))
    }

    @Test
    fun aLayerStyledWithNoWidthOrNoColourDrawsNoOverlay() {
        assertEquals(0, draws(layer(0, OutlineStyle(Color(1f, 0f, 0f, 1f), widthPixels = 0f))))
        assertEquals(0, draws(layer(1, OutlineStyle(Color(1f, 0f, 0f, 0f), widthPixels = 4f))))
    }

    @Test
    fun aStyledLayerDrawsTheOverlayOnce() {
        assertEquals(1, draws(layer(0, OutlineStyle(Color(1f, 0f, 0f, 1f), widthPixels = 4f))))
        assertEquals(
            1,
            draws(
                layer(1, OutlineStyle(Color(0f, 0f, 1f, 1f), widthPixels = 2f)),
                GpuSubPass(target = null, targetLayer = COVERING_EXEMPT_MASK_LAYER, viewProjection = Mat4()),
            ),
        )
    }

    @Test
    fun theOverlayIsScissoredToTheMaskedBoundsPaddedByTheOutline() {
        // A quarter of the 64 px scene at (16, 16), outlined 4 px wide: 4 + 1 px of padding each side.
        val mask = layer(0, OutlineStyle(Color(1f, 0f, 0f, 1f), widthPixels = 4f)).also { it.screenBounds = ImagePassRect(0.25f, 0.25f, 0.25f, 0.25f) }
        val recorder = record(mask)
        assertEquals(1, recorder.draws)
        assertEquals(listOf(listOf(11, 11, 26, 26), listOf(0, 0, SIZE, SIZE)), recorder.scissors)
    }

    @Test
    fun anOutlineOffScreenDrawsNothingAndUnknownBoundsDrawTheWholeScene() {
        val style = OutlineStyle(Color(1f, 0f, 0f, 1f), widthPixels = 2f)
        assertEquals(0, draws(layer(0, style).also { it.screenBounds = ImagePassRect(2f, 2f, 0.5f, 0.5f) }))
        val unknown = record(layer(0, style), layer(1, style).also { it.screenBounds = ImagePassRect(0.1f, 0.1f, 0.1f, 0.1f) })
        assertEquals(1, unknown.draws)
        assertEquals(emptyList(), unknown.scissors)
    }

    @Test
    fun styleSoftnessRoundTripsAndLegacyFiveFloatPayloadDefaultsToCrisp() {
        val soft = OutlineStyle(Color(1f, 0.9f, 0.5f, 0.95f), widthPixels = 10f, softness = 0.85f)
        assertEquals(soft, OutlineStyle.unpack(soft.packed()))
        val legacy = OutlineStyle.unpack(floatArrayOf(1f, 0f, 0f, 1f, 4f))
        assertEquals(OutlineStyle(Color(1f, 0f, 0f, 1f), widthPixels = 4f, softness = 0f), legacy)
    }

    @Test
    fun effectQualityOffSkipsOutlineOverlayCompletely() {
        val soft = layer(0, OutlineStyle(Color(1f, 0f, 0f, 1f), widthPixels = 10f, softness = 1f))
        val crisp = layer(1, OutlineStyle(Color(0f, 0f, 1f, 1f), widthPixels = 4f, softness = 0f))
        val result = record(EffectQuality.Off, soft, crisp)
        assertEquals(0, result.recorder.draws)
        assertEquals(0, result.uniforms.writes)
    }

    @Test
    fun effectQualityLowDowngradesSoftGlowToCrispAndTightensScissorWhilePreservingCrispLayers() {
        val bounds = ImagePassRect(0.25f, 0.25f, 0.25f, 0.25f)
        val soft = layer(0, OutlineStyle(Color(1f, 0f, 0f, 1f), widthPixels = 10f, softness = 1f)).also { it.screenBounds = bounds }
        val high = record(EffectQuality.High, soft)
        val low = record(EffectQuality.Low, soft)

        assertEquals(1, high.recorder.draws)
        assertEquals(listOf(listOf(5, 5, 38, 38), listOf(0, 0, SIZE, SIZE)), high.recorder.scissors)
        assertEquals(10f, high.uniforms.last[WIDTH0_OFFSET])
        assertEquals(1f, high.uniforms.last[SOFTNESS0_OFFSET])

        // At Low, the 64-tap soft glow downgrades to a 16-tap crisp silhouette (softness = 0, width capped at 2 px),
        // shrinking the scissored fragment area accordingly.
        assertEquals(1, low.recorder.draws)
        assertEquals(listOf(listOf(13, 13, 22, 22), listOf(0, 0, SIZE, SIZE)), low.recorder.scissors)
        assertEquals(2f, low.uniforms.last[WIDTH0_OFFSET])
        assertEquals(0f, low.uniforms.last[SOFTNESS0_OFFSET])

        // Already-crisp outlines keep their authored width at Low.
        val crisp = layer(1, OutlineStyle(Color(0f, 0f, 1f, 1f), widthPixels = 4f, softness = 0f)).also { it.screenBounds = bounds }
        val mixed = record(EffectQuality.Low, soft, crisp)
        assertEquals(2f, mixed.uniforms.last[WIDTH0_OFFSET])
        assertEquals(4f, mixed.uniforms.last[WIDTH1_OFFSET])
        assertEquals(0f, mixed.uniforms.last[SOFTNESS0_OFFSET])
        assertEquals(0f, mixed.uniforms.last[SOFTNESS1_OFFSET])
    }

    @Test
    fun effectQualityMediumPreservesAuthoredSoftGlowAndSelectsLeanBlurKernel() {
        val soft = layer(0, OutlineStyle(Color(1f, 0.9f, 0.5f, 0.95f), widthPixels = 10f, softness = 0.85f))
        val high = record(EffectQuality.High, soft)
        val medium = record(EffectQuality.Medium, soft)
        assertEquals(1, high.recorder.draws)
        assertEquals(0f, high.uniforms.last[SOFT_QUALITY_OFFSET])
        assertEquals(1, medium.recorder.draws)
        assertEquals(10f, medium.uniforms.last[WIDTH0_OFFSET])
        assertEquals(0.85f, medium.uniforms.last[SOFTNESS0_OFFSET])
        assertEquals(1f, medium.uniforms.last[SOFT_QUALITY_OFFSET])
    }

    @Test
    fun disjointClustersIssueSeparateNonOverlappingScissorsAndOverlappingClustersMerge() {
        val style = OutlineStyle(Color(1f, 0f, 0f, 1f), widthPixels = 2f)
        // Two distant draws in the same mask layer: left [0.05..0.15] and right [0.85..0.95] of 64 px.
        val distant = layer(0, style).also {
            it.screenBounds = ImagePassRect(0.05f, 0.4f, 0.90f, 0.2f)
            it.screenBoundRects = floatArrayOf(
                0.05f,
                0.4f,
                0.15f,
                0.6f,
                0.85f,
                0.4f,
                0.95f,
                0.6f,
            )
            it.screenBoundRectCount = 2
        }
        val distantRecorder = record(distant)
        assertEquals(2, distantRecorder.draws)
        // Two disjoint cluster scissors followed by one full-scene restore.
        assertEquals(3, distantRecorder.scissors.size)
        val r0 = distantRecorder.scissors[0]
        val r1 = distantRecorder.scissors[1]
        assertEquals(listOf(0, 0, SIZE, SIZE), distantRecorder.scissors[2])
        val disjoint = (r0[0] + r0[2] <= r1[0]) || (r1[0] + r1[2] <= r0[0]) ||
            (r0[1] + r0[3] <= r1[1]) || (r1[1] + r1[3] <= r0[1])
        kotlin.test.assertTrue(disjoint, "emitted cluster scissors must be strictly pairwise disjoint: $r0 vs $r1")
        val combinedArea = r0[2] * r0[3] + r1[2] * r1[3]
        kotlin.test.assertTrue(combinedArea < SIZE * SIZE / 4, "disjoint scissors must skip empty gap area: $combinedArea")

        // Two overlapping draws must merge into one scissor so no pixel blends twice.
        val overlapping = layer(0, style).also {
            it.screenBounds = ImagePassRect(0.25f, 0.25f, 0.30f, 0.30f)
            it.screenBoundRects = floatArrayOf(
                0.25f,
                0.25f,
                0.45f,
                0.45f,
                0.35f,
                0.35f,
                0.55f,
                0.55f,
            )
            it.screenBoundRectCount = 2
        }
        val mergedRecorder = record(overlapping)
        assertEquals(1, mergedRecorder.draws)
        assertEquals(2, mergedRecorder.scissors.size)
    }

    @Test
    fun distantLayersKeepTightPerLayerScissors() {
        val left = layer(0, OutlineStyle(Color(1f, 0f, 0f, 1f), widthPixels = 2f)).also {
            it.screenBounds = ImagePassRect(0.05f, 0.4f, 0.10f, 0.2f)
        }
        val right = layer(1, OutlineStyle(Color(0f, 0f, 1f, 1f), widthPixels = 4f)).also {
            it.screenBounds = ImagePassRect(0.80f, 0.4f, 0.10f, 0.2f)
        }
        val recorder = record(left, right)
        assertEquals(2, recorder.draws)
        assertEquals(3, recorder.scissors.size)
        assertEquals(listOf(0, 0, SIZE, SIZE), recorder.scissors[2])
    }

    private fun layer(layer: Int, style: OutlineStyle) =
        GpuSubPass(target = null, targetLayer = layer, viewProjection = Mat4(), passUniforms = style.packed())

    private fun draws(vararg masks: GpuSubPass): Int = record(*masks).draws

    private fun record(vararg masks: GpuSubPass): CountingRecorder = record(EffectQuality.High, *masks).recorder

    private fun record(quality: EffectQuality, vararg masks: GpuSubPass): Recorded {
        val recorder = CountingRecorder()
        val uniforms = CapturingUniforms()
        val context = FrameContext(
            recorder = recorder,
            passInput = GpuPassInput(viewProjection = Mat4(), cameraEye = Vec3f.ZERO, passUniforms = FloatArray(0), maskPasses = masks.toList()),
            effectQuality = quality,
        )
        OutlineRenderFeature(Pipeline, uniforms).recordCommands(context)
        return Recorded(recorder, uniforms)
    }

    private data class Recorded(val recorder: CountingRecorder, val uniforms: CapturingUniforms)

    private class CountingRecorder : CommandRecorder {
        var draws = 0
        val scissors = mutableListOf<List<Int>>()
        override fun setScissor(x: Int, y: Int, width: Int, height: Int) {
            scissors += listOf(x, y, width, height)
        }
        override fun bindPipeline(pipeline: PipelineHandle) = Unit
        override fun bindMaterial(semantic: BindingSemantic, binding: MaterialBinding) = Unit
        override fun bindVertexBuffer(binding: Int, buffer: BufferHandle) = Unit
        override fun bindIndexBuffer(buffer: BufferHandle) = Unit
        override fun draw(vertexCount: Int, instanceCount: Int) {
            draws++
        }
        override fun drawIndexed(indexCount: Int, instanceCount: Int) = Unit
    }

    private class FrameContext(
        override val recorder: CommandRecorder,
        override val passInput: GpuPassInput,
        override val effectQuality: EffectQuality = EffectQuality.High,
    ) : RenderFrameContext {
        override val frameIndex = 0
        override val groupedDrawCalls: Map<out PipelineHandle, List<PreparedDraw>> = emptyMap()
        override val transparentDrawCalls: List<PreparedDraw> = emptyList()
        override val edgeDrawCalls: List<PreparedDraw> = emptyList()
        override val primaryPipeline: PipelineHandle = Pipeline
        override val viewProjection = Mat4()
        override val cameraEye = Vec3f.ZERO
        override val surfaceWidth = SIZE
        override val surfaceHeight = SIZE
    }

    private object Pipeline : PipelineHandle

    private object Binding : MaterialBinding

    private class CapturingUniforms : UniformBlock {
        private val writer = UniformWriter(OutlineUniformLayout)
        var writes = 0
        var last = FloatArray(OutlineUniformLayout.total)
        override fun binding(frameIndex: Int): MaterialBinding = Binding
        override fun write(frameIndex: Int, fill: UniformWriter.() -> Unit) {
            writes++
            writer.reset().apply(fill)
            last = writer.build().copyOf()
        }
    }

    private companion object {
        const val SIZE = 64
        const val SOFT_QUALITY_OFFSET = 2
        const val WIDTH0_OFFSET = 12
        const val WIDTH1_OFFSET = 13
        const val SOFTNESS0_OFFSET = 14
        const val SOFTNESS1_OFFSET = 15
    }
}
