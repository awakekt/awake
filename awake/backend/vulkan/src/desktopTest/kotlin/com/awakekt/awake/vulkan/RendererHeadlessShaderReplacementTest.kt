/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.asset.shaderdsl.fieldsFrom
import com.awakekt.awake.asset.shaderdsl.fullScreenTriangleCorner
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.plus
import com.awakekt.awake.asset.shaderdsl.shader
import com.awakekt.awake.asset.shaderdsl.times
import com.awakekt.awake.asset.shaderdsl.vec2
import com.awakekt.awake.asset.shaderdsl.vec4
import com.awakekt.awake.asset.shaders.ContentFeatureSource
import com.awakekt.awake.asset.shaders.ShaderStages
import com.awakekt.awake.asset.shaders.aslShaderSet
import com.awakekt.awake.asset.shaders.program
import com.awakekt.awake.asset.shaders.spec
import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.command.UniformBlock
import com.awakekt.awake.render.passes.ContentFeature
import com.awakekt.awake.render.passes.RenderFeature
import com.awakekt.awake.render.passes.RenderFrameContext
import com.awakekt.awake.render.passes.RenderPassSlot
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.pipeline.PipelineVariant
import com.awakekt.awake.render.pipeline.ShaderProgram
import com.awakekt.awake.render.pipeline.ShaderReplacement
import com.awakekt.awake.render.pipeline.ShaderReplacementException
import com.awakekt.awake.render.pipeline.ShaderSource
import com.awakekt.awake.render.renderer.UniformField
import com.awakekt.awake.render.renderer.UniformLayout
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Shaders built at runtime replace a running pipeline's, in place, with two frames in flight: a red
 * square becomes green, then yellow, while a blue one beside it stays blue. Refused replacements
 * change nothing.
 *
 * The device is destroyed in [release], where any validation error fails the class.
 */
class RendererHeadlessShaderReplacementTest {
    @Test
    fun aReplacementRedrawsOnlyThePipelinesRunningTheOldShaders() = withRedAndBlue { fixture ->
        val replacement = replacement(fixture)
        assertEquals(RED to BLUE, fixture.squares())

        fixture.drawInFlight()
        assertEquals(1, runBlocking { replacement.replace(RED_SHADER.program(), GREEN_SHADER.program()) })
        assertEquals(GREEN to BLUE, fixture.squares(), "only the pipeline running the red shaders changes")

        fixture.drawInFlight()
        assertEquals(1, runBlocking { replacement.replace(GREEN_SHADER.program(), YELLOW_SHADER.program()) })
        assertEquals(YELLOW to BLUE, fixture.squares(), "a replacement is what the next one replaces")
        assertEquals(0, runBlocking { replacement.replace(RED_SHADER.program(), GREEN_SHADER.program()) })
    }

    @Test
    fun shadersThatDoNotCompileAreRefusedAndTheOldOnesKeepDrawing() = withRedAndBlue { fixture ->
        val broken = ShaderProgram(
            ShaderSource.InlineText("fn broken( {", "vertexMain"),
            ShaderSource.InlineText("fn broken( {", "fragmentMain"),
            RED_SHADER.program().bindingsByGroup,
        )

        assertFailsWith<ShaderReplacementException> { runBlocking { replacement(fixture).replace(RED_SHADER.program(), broken) } }
        assertEquals(RED to BLUE, fixture.squares())
    }

    @Test
    fun shadersThatBindDifferentlyAreRefusedAndTheOldOnesKeepDrawing() = withRedAndBlue { fixture ->
        assertFailsWith<ShaderReplacementException> {
            runBlocking { replacement(fixture).replace(RED_SHADER.program(), EXTRA_BINDING_SHADER.program()) }
        }
        assertEquals(RED to BLUE, fixture.squares())
    }

    private fun replacement(fixture: HeadlessContentAttachFixture): ShaderReplacement =
        checkNotNull(fixture.renderer.capability(ShaderReplacement))

    private fun withRedAndBlue(test: (HeadlessContentAttachFixture) -> Unit) {
        val fixture = shared()
        val red = runBlocking { fixture.attacher.attachContentFeature(square("red", RED_SHADER)) }
        val blue = runBlocking { fixture.attacher.attachContentFeature(square("blue", BLUE_SHADER)) }
        try {
            test(fixture)
        } finally {
            red.detach()
            blue.detach()
        }
    }

    /** Frames the GPU may still be running when this returns: two slots in flight, never waited on. */
    private fun HeadlessContentAttachFixture.drawInFlight() = repeat(FRAMES) { draw(LENS) }

    /** The colours of the left and right squares, after enough frames that both slots in flight ran. */
    private fun HeadlessContentAttachFixture.squares(): Pair<Int, Int> {
        drawInFlight()
        val pixels = runBlocking { renderer.readPresentedPixels() }.data
        return rgbAt(pixels, size / 4) to rgbAt(pixels, size * 3 / 4)
    }

    private fun HeadlessContentAttachFixture.rgbAt(pixels: ByteArray, x: Int): Int {
        val i = (size / 2 * size + x) * BYTES_PER_PIXEL
        return (pixels[i].toInt() and 0xFF shl 16) or (pixels[i + 1].toInt() and 0xFF shl 8) or (pixels[i + 2].toInt() and 0xFF)
    }

    companion object {
        private const val FRAMES = 4
        private const val BYTES_PER_PIXEL = 4
        private const val RED = 0xFF0000
        private const val GREEN = 0x00FF00
        private const val BLUE = 0x0000FF
        private const val YELLOW = 0xFFFF00
        private val LENS = Lens(eye = Vec3f(0f, 0f, 5f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 10f)

        private val Tint = UniformField("tint", GpuDataShape.Vec4)
        private val Layout = UniformLayout(Tint)

        /** A square of [rgb], a quarter of the frame wide, centred at ([x], 0) in clip space. */
        private fun solidShader(name: String, rgb: Int, x: Float, extraBinding: Boolean = false): ShaderStages =
            aslShaderSet(
                shader(name) {
                    val group = BindingLayout.Standard.slot(BindingSemantic.Material)
                    val tint = uniformBlock("Uniforms", group = group, binding = 0).fieldsFrom(Layout).value("tint")
                    val scale = if (extraBinding) {
                        uniformBlock("Extra", group = group, binding = 1).fieldsFrom(Layout).value("tint")
                    } else {
                        tint
                    }
                    val out = varyings("VertexOutput")
                    vertex {
                        val corner = fullScreenTriangleCorner()
                        out.position set vec4(corner * 0.25f.lit + vec2(x.lit, 0f.lit), 0f.lit, 1f.lit)
                    }
                    fragment {
                        val color = rgba((rgb shr 16 and 0xFF) / 255f, (rgb shr 8 and 0xFF) / 255f, (rgb and 0xFF) / 255f)
                        colorOutput(color * tint * scale)
                    }
                },
            ).vulkan

        private fun rgba(r: Float, g: Float, b: Float) = vec4(r.lit, g.lit, b.lit, 1f.lit)

        val RED_SHADER = solidShader("red", RED, x = -0.5f)
        val BLUE_SHADER = solidShader("blue", BLUE, x = 0.5f)
        val GREEN_SHADER = solidShader("green", GREEN, x = -0.5f)
        val YELLOW_SHADER = solidShader("yellow", YELLOW, x = -0.5f)
        val EXTRA_BINDING_SHADER = solidShader("extra", GREEN, x = -0.5f, extraBinding = true)

        private fun square(name: String, stages: ShaderStages) = ContentFeatureSource {
            ContentFeature(
                name = name,
                spec = stages.spec(vertexFormat = VertexFormat.None, variant = PipelineVariant.Overlay, uniforms = Layout),
            ) { pipeline, uniforms, _ -> SquareFeature(pipeline, uniforms) }
        }

        private class SquareFeature(private val pipeline: PipelineHandle, private val uniforms: UniformBlock) :
            RenderFeature<RenderFrameContext> {
            override val pass = RenderPassSlot.Scene

            override fun recordCommands(context: RenderFrameContext) {
                uniforms.write(context.frameIndex) { put(Tint, 1f, 1f, 1f, 1f) }
                context.recorder.bindPipeline(pipeline)
                context.recorder.bindMaterial(BindingSemantic.Material, uniforms.binding(context.frameIndex))
                context.recorder.draw(3, 1)
            }

            override fun destroy() = Unit
        }

        private var fixture: HeadlessContentAttachFixture? = null

        private fun shared(): HeadlessContentAttachFixture =
            fixture ?: HeadlessContentAttachFixture.create(framesInFlight = 2, presentable = true).also { fixture = it }

        @AfterClass
        @JvmStatic
        fun release() {
            fixture?.release()
            fixture = null
        }
    }
}
