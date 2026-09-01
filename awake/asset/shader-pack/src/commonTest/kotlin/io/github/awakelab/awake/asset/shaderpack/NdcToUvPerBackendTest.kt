/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.shaderpack

import io.github.awakelab.awake.asset.shaderdsl.AslShaderDefinition
import io.github.awakelab.awake.asset.shaders.ShaderSet
import io.github.awakelab.awake.asset.shaders.ShaderStage
import io.github.awakelab.awake.asset.shaders.ShaderStages
import io.github.awakelab.awake.asset.shaders.aslShaderSet
import io.github.awakelab.awake.core.math.ClipSpace
import io.github.awakelab.awake.render.pipeline.ShaderSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every shader that turns an NDC coordinate into a texture coordinate does it per backend.
 *
 * A shadow map or a depth target is rasterised through a projection built for the active
 * `ClipSpace`, so on Vulkan clip-space Y already runs the way a texture's rows do and on WebGPU it
 * runs the other way. One formula for both mirrors the lookup about the image's centre. That is not
 * hypothetical: `lit_shadow` shipped that way and put every caster's shadow on the far side of the
 * scene from the caster, while `depth_fog` next door had already been fixed with a hand-passed
 * boolean nobody generalised.
 *
 * Asserted on emitted source because the alternative is a lit, shadowed scene rendered on both
 * backends, and the source is where the decision is made.
 */
class NdcToUvPerBackendTest {

    private fun wgsl(stages: ShaderStages): String {
        val source = checkNotNull(stages[ShaderStage.VERTEX]) { "the shader declares no vertex stage" }
        return (source as ShaderSource.InlineText).sourceCode
    }

    /** How each backend turns NDC Y into a V, as the emitter spells it. */
    private fun flippedV(ndc: String) = "(1.0 - $ndc.y) * 0.5"
    private fun directV(ndc: String) = "($ndc.y + 1.0) * 0.5"

    /**
     * The two shaders that sample an image by screen or light-space position, and the NDC value
     * each one converts. Anything added here must be built through `aslShaderSet { clipSpace -> }`.
     */
    private val screenSamplers = listOf(
        Triple("lit_shadow", PackShaderSets.LitShadow, "offsetNdcXy"),
        Triple("depth_fog", aslShaderSet(::depthFogShader), "ndc"),
    )

    @Test
    fun eachBackendGetsItsOwnConversion() {
        // The premise: this test is only meaningful while the two clip spaces differ in Y.
        assertTrue(ClipSpace.Vulkan.flipY && !ClipSpace.WebGpu.flipY, "the clip-space conventions moved")

        screenSamplers.forEach { (name, set, ndc) ->
            val vulkan = wgsl(set.vulkan)
            val webGpu = wgsl(set.webGpu)
            assertTrue(
                directV(ndc) in vulkan,
                "$name's Vulkan source should take NDC Y as it comes, its projection having " +
                    "flipped it already",
            )
            assertTrue(
                flippedV(ndc) in webGpu,
                "$name's WebGPU source does not flip V, so it samples the image mirrored",
            )
        }
    }

    /**
     * Only the conversion differs between a shader's two backends.
     *
     * Emitting one shader twice is a place for the backends to drift apart in ways nobody intended,
     * so the difference is held to the lines it exists for.
     */
    @Test
    fun nothingElseDivergedBetweenTheBackends() {
        screenSamplers.forEach { (name, set, ndc) ->
            val vulkan = wgsl(set.vulkan).lines()
            val webGpu = wgsl(set.webGpu).lines()

            assertEquals(vulkan.size, webGpu.size, "$name's two sources are different lengths")
            val differing = vulkan.indices.filter { vulkan[it] != webGpu[it] }
            assertTrue(differing.isNotEmpty(), "$name emitted the same source for both backends")
            assertTrue(
                differing.all { directV(ndc) in vulkan[it] },
                "$name differs outside its NDC conversion: " + differing.map { vulkan[it].trim() },
            )
        }
    }

    /**
     * A shader that never asks for its clip space gets one source, not two.
     *
     * The builder takes a function of `ClipSpace` for every shader, so this pins that the cost of
     * that uniformity is nothing: a definition that ignores the argument emits identical halves.
     */
    @Test
    fun aClipSpaceAgnosticShaderIsUnaffected() {
        val agnostic: (ClipSpace) -> AslShaderDefinition = { ShadowDepthShader }

        val set: ShaderSet = aslShaderSet(agnostic)

        assertEquals(wgsl(set.vulkan), wgsl(set.webGpu), "a shader that ignores its clip space diverged")
    }
}
