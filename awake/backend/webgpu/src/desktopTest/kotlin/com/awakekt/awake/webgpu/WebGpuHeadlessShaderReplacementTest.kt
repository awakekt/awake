/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.asset.shaderdsl.a
import com.awakekt.awake.asset.shaderdsl.fieldsFrom
import com.awakekt.awake.asset.shaderdsl.fullScreenTriangleCorner
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.minus
import com.awakekt.awake.asset.shaderdsl.plus
import com.awakekt.awake.asset.shaderdsl.shader
import com.awakekt.awake.asset.shaderdsl.times
import com.awakekt.awake.asset.shaderdsl.vec2
import com.awakekt.awake.asset.shaderdsl.vec4
import com.awakekt.awake.asset.shaderdsl.xy
import com.awakekt.awake.asset.shaderdsl.zw
import com.awakekt.awake.asset.shaders.ContentFeatureAttacher
import com.awakekt.awake.asset.shaders.ContentFeatureSource
import com.awakekt.awake.asset.shaders.EngineShaderSets
import com.awakekt.awake.asset.shaders.ShaderSet
import com.awakekt.awake.asset.shaders.ShaderStage
import com.awakekt.awake.asset.shaders.ShaderStages
import com.awakekt.awake.asset.shaders.aslShaderSet
import com.awakekt.awake.asset.shaders.program
import com.awakekt.awake.asset.shaders.resolveBytes
import com.awakekt.awake.asset.shaders.spec
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.command.UniformBlock
import com.awakekt.awake.render.passes.ContentFeature
import com.awakekt.awake.render.passes.OpaqueRenderFeature
import com.awakekt.awake.render.passes.RenderFeature
import com.awakekt.awake.render.passes.RenderFrameContext
import com.awakekt.awake.render.passes.RenderPassSlot
import com.awakekt.awake.render.passes2d.UiRenderFeature
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.pipeline.PipelineRegistry
import com.awakekt.awake.render.pipeline.PipelineTable
import com.awakekt.awake.render.pipeline.PipelineVariant
import com.awakekt.awake.render.pipeline.PreparedShaderProgram
import com.awakekt.awake.render.pipeline.ShaderProgram
import com.awakekt.awake.render.pipeline.ShaderReplacement
import com.awakekt.awake.render.pipeline.ShaderReplacementException
import com.awakekt.awake.render.pipeline.ShaderSource
import com.awakekt.awake.render.renderer.UniformField
import com.awakekt.awake.render.renderer.UniformLayout
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.webgpu.application.WebGpuContentFeatureGpu
import com.awakekt.awake.webgpu.debug.LineRenderPipeline
import com.awakekt.awake.webgpu.device.GraphicsDevice
import com.awakekt.awake.webgpu.handles.DescriptorSetLayoutHandle
import com.awakekt.awake.webgpu.pipeline.RenderPipeline
import com.awakekt.awake.webgpu.pipeline.UiShaderSources
import com.awakekt.awake.webgpu.pipeline.WebGpuLinePass
import com.awakekt.awake.webgpu.pipeline.WebGpuPipelineFactory
import com.awakekt.awake.webgpu.pipeline.WebGpuShaderReplacement
import com.awakekt.awake.webgpu.pipeline.WebGpuShaderResolver
import com.awakekt.awake.webgpu.pipeline.WebGpuUiPass
import com.awakekt.awake.webgpu.renderer.Renderer
import com.awakekt.awake.webgpu.renderer.activeUiPipelineTargets
import com.awakekt.awake.webgpu.swapchain.SwapchainManager
import io.ygdrasil.webgpu.glfwContextRenderer
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Shaders built at runtime replace a running WebGPU pipeline's, in place: a red square becomes green,
 * then yellow, while a blue one beside it stays blue. Refused replacements change nothing.
 * Negative controls on the original implementation: a missing vertex entry point returned 1
 * instead of throwing, and a second UI swap returned 0 instead of 1 after drawing green pixels.
 */
class WebGpuHeadlessShaderReplacementTest {

    @Test
    fun aReplacementRedrawsOnlyThePipelinesRunningTheOldShaders() = withRedAndBlue { fixture ->
        val replacement = replacement(fixture)
        assertEquals(RED to BLUE, fixture.squares())

        fixture.draw()
        assertEquals(1, runBlocking { replacement.replace(RED_SHADER.program(), GREEN_SHADER.program()) })
        assertEquals(GREEN to BLUE, fixture.squares(), "only the pipeline running the red shaders changes")

        fixture.draw()
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

        assertFailsWith<ShaderReplacementException> {
            runBlocking { replacement(fixture).replace(RED_SHADER.program(), broken) }
        }
        assertEquals(RED to BLUE, fixture.squares())
    }

    @Test
    fun shadersThatBindDifferentlyAreRefusedAndTheOldOnesKeepDrawing() = withRedAndBlue { fixture ->
        assertFailsWith<ShaderReplacementException> {
            runBlocking { replacement(fixture).replace(RED_SHADER.program(), EXTRA_BINDING_SHADER.program()) }
        }
        assertEquals(RED to BLUE, fixture.squares())
    }

    @Test
    fun aMissingVertexEntryPointIsRefusedWithoutChangingThePipeline() = withRedAndBlue { fixture ->
        val green = GREEN_SHADER.program()
        val missingEntry = green.copy(
            vertex = (green.vertex as ShaderSource.InlineText).copy(entryPoint = "missingVertex"),
        )
        assertFailsWith<ShaderReplacementException> {
            runBlocking { replacement(fixture).replace(RED_SHADER.program(), missingEntry) }
        }
        assertEquals(RED to BLUE, fixture.squares(), "driver validation must preserve the working shaders")
    }

    @Test
    fun incorrectBindingMetadataCannotHideAnIncompatibleShader() = withRedAndBlue { fixture ->
        val incompatible = EXTRA_BINDING_SHADER.program().copy(bindingsByGroup = RED_SHADER.program().bindingsByGroup)
        assertFailsWith<ShaderReplacementException> {
            runBlocking { replacement(fixture).replace(RED_SHADER.program(), incompatible) }
        }
        assertEquals(RED to BLUE, fixture.squares())
    }

    @Test
    fun closingAnAbandonedPreparationLeavesTheOldShadersRunning() = withRedAndBlue { fixture ->
        val replacement = replacement(fixture)
        val prepared = runBlocking { replacement.prepare(GREEN_SHADER.program()) }
        prepared.close()
        prepared.close()
        assertFailsWith<ShaderReplacementException> { replacement.swapIn(RED_SHADER.program(), prepared) }
        assertEquals(RED to BLUE, fixture.squares())
    }

    @Test
    fun aPipelineAttachedAfterPreparationRefusesTheWholeSwapUntilPreparedAgain() = withRedAndBlue { fixture ->
        val replacement = replacement(fixture)
        val prepared = runBlocking { replacement.prepare(GREEN_SHADER.program()) }
        val late = runBlocking {
            fixture.attacher.attachContentFeature(square("late-red", RED_SHADER, PipelineVariant.Opaque))
        }
        try {
            prepared.use {
                assertFailsWith<ShaderReplacementException> { replacement.swapIn(RED_SHADER.program(), it) }
            }
            assertEquals(RED to BLUE, fixture.squares(), "a stale preparation must not partially swap earlier pipelines")
            assertEquals(2, runBlocking { replacement.replace(RED_SHADER.program(), GREEN_SHADER.program()) })
            assertEquals(GREEN to BLUE, fixture.squares())
        } finally {
            late.detach()
        }
    }

    @Test
    fun preparingChangesNothingAndSwappingInRedrawsThePipelines() = withRedAndBlue { fixture ->
        val replacement = replacement(fixture)

        val prepared = runBlocking { replacement.prepare(GREEN_SHADER.program()) }
        assertEquals(GREEN_SHADER.program(), prepared.program)
        assertEquals(RED to BLUE, fixture.squares(), "a prepared program is not drawn until it is swapped in")

        fixture.draw()
        assertEquals(1, replacement.swapIn(RED_SHADER.program(), prepared))
        assertEquals(GREEN to BLUE, fixture.squares(), "swapping in redraws only the pipeline running the old shaders")
    }

    @Test
    fun framesKeepDrawingBetweenPrepareAndSwapIn() = withRedAndBlue { fixture ->
        val replacement = replacement(fixture)
        val prepared = runBlocking { replacement.prepare(GREEN_SHADER.program()) }

        repeat(FRAMES * 3) { fixture.draw() }
        assertEquals(RED to BLUE, fixture.squares(), "frames drawn while a program waits keep the old shaders")

        assertEquals(1, replacement.swapIn(RED_SHADER.program(), prepared))
        assertEquals(GREEN to BLUE, fixture.squares())
    }

    @Test
    fun shadersThatDoNotCompileAreRefusedAtPrepareAndNothingChanges() = withRedAndBlue { fixture ->
        val broken = ShaderProgram(
            ShaderSource.InlineText("fn broken( {", "vertexMain"),
            ShaderSource.InlineText("fn broken( {", "fragmentMain"),
            RED_SHADER.program().bindingsByGroup,
        )

        assertFailsWith<ShaderReplacementException> { runBlocking { replacement(fixture).prepare(broken) } }
        assertEquals(RED to BLUE, fixture.squares())
    }

    @Test
    fun aProgramThatBindsDifferentlyIsRefusedAtSwapInNotAtPrepare() = withRedAndBlue { fixture ->
        val replacement = replacement(fixture)
        val prepared = runBlocking { replacement.prepare(EXTRA_BINDING_SHADER.program()) }

        prepared.use {
            assertFailsWith<ShaderReplacementException> { replacement.swapIn(RED_SHADER.program(), it) }
        }
        assertEquals(RED to BLUE, fixture.squares(), "a refused swap-in changes nothing")
    }

    @Test
    fun aProgramPreparedByAnotherReplacementIsRefused() = withRedAndBlue { fixture ->
        val foreign = object : PreparedShaderProgram {
            override val program = GREEN_SHADER.program()
        }

        assertFailsWith<ShaderReplacementException> { replacement(fixture).swapIn(RED_SHADER.program(), foreign) }
        assertEquals(RED to BLUE, fixture.squares())
    }

    @Test
    fun uiQuadShaderCanBeReplacedInPlace() {
        val fixture = shared()
        val renderer = fixture.renderer
        val replacement = replacement(fixture)
        val quadTarget = renderer.createRenderTarget(SIZE, SIZE)
        val redQuad = listOf(
            UiDrawPrimitive.Quad(
                x = 0f,
                y = 0f,
                w = SIZE.toFloat(),
                h = SIZE.toFloat(),
                color = Color(1f, 0f, 0f, 1f),
            ),
        )

        try {
            renderer.drawUiToTexture(quadTarget, redQuad, font = null)
            var pixels = runBlocking { renderer.readPixels(quadTarget) }.data
            var centerColor = (pixels[0].toInt() and 0xFF shl 16) or
                (pixels[1].toInt() and 0xFF shl 8) or
                (pixels[2].toInt() and 0xFF)
            assertEquals(RED, centerColor, "initial UI quad renders red")

            val greenQuadShader = greenUiQuadShader().webGpu

            val swapped = runBlocking {
                replacement.replace(
                    EngineShaderSets.UiQuad.webGpu.program(),
                    greenQuadShader.program(),
                )
            }
            assertEquals(1, swapped, "replaced UI quad pipeline")

            renderer.drawUiToTexture(quadTarget, redQuad, font = null)
            pixels = runBlocking { renderer.readPixels(quadTarget) }.data
            centerColor = (pixels[0].toInt() and 0xFF shl 16) or
                (pixels[1].toInt() and 0xFF shl 8) or
                (pixels[2].toInt() and 0xFF)
            assertEquals(GREEN, centerColor, "swapped UI quad pipeline renders green")
            assertEquals(
                1,
                runBlocking { replacement.replace(greenQuadShader.program(), EngineShaderSets.UiQuad.webGpu.program()) },
                "the running green program is found by a second UI replacement",
            )
            renderer.drawUiToTexture(quadTarget, redQuad, font = null)
            pixels = runBlocking { renderer.readPixels(quadTarget) }.data
            centerColor = (pixels[0].toInt() and 0xFF shl 16) or
                (pixels[1].toInt() and 0xFF shl 8) or
                (pixels[2].toInt() and 0xFF)
            assertEquals(RED, centerColor, "a second UI replacement restores red pixels")
        } finally {
            quadTarget.destroy()
        }
    }

    private fun replacement(fixture: WebGpuContentAttachFixture): ShaderReplacement =
        checkNotNull(fixture.renderer.capability(ShaderReplacement))

    private fun withRedAndBlue(test: (WebGpuContentAttachFixture) -> Unit) {
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

    private class WebGpuContentAttachFixture(
        val renderer: Renderer,
        val attacher: ContentFeatureAttacher<RenderPipeline>,
        private val graphicsDevice: GraphicsDevice,
        private val offscreenTarget: RenderTarget,
        private val cleanup: () -> Unit,
    ) {
        fun draw() {
            renderer.renderSceneToTexture(
                offscreenTarget,
                LENS,
                emptyList(),
            )
        }

        fun squares(): Pair<Int, Int> {
            draw()
            val pixels = runBlocking { renderer.readPixels(offscreenTarget) }.data
            return rgbAt(pixels, SIZE / 4) to rgbAt(pixels, SIZE * 3 / 4)
        }

        private fun rgbAt(pixels: ByteArray, x: Int): Int {
            val i = (SIZE / 2 * SIZE + x) * BYTES_PER_PIXEL
            return (pixels[i].toInt() and 0xFF shl 16) or
                (pixels[i + 1].toInt() and 0xFF shl 8) or
                (pixels[i + 2].toInt() and 0xFF)
        }

        fun release() {
            offscreenTarget.destroy()
            renderer.destroy()
            cleanup()
            graphicsDevice.destroy()
        }

        companion object {
            fun create(): WebGpuContentAttachFixture = runBlocking {
                val context = glfwContextRenderer(
                    width = 1,
                    height = 1,
                    title = "awake-shader-replacement",
                    onUncapturedError = { error -> println("WGPU UNCAPTURED: $error") },
                )
                val graphicsDevice = GraphicsDevice().apply { create(context.wgpuContext) }
                val swapchainManager = SwapchainManager(graphicsDevice, MAX_FRAMES_IN_FLIGHT).apply { create() }
                val registry = PipelineRegistry(
                    WebGpuPipelineFactory(graphicsDevice, swapchainManager, WebGpuShaderResolver()),
                )
                val gpu = WebGpuContentFeatureGpu(graphicsDevice, registry)
                val attacher = ContentFeatureAttacher(gpu)
                val linePipeline = LineRenderPipeline(
                    graphicsDevice,
                    swapchainManager,
                    engineWgsl(EngineShaderSets.DebugLine),
                )
                val primary = RenderPipeline(
                    graphicsDevice,
                    swapchainManager,
                    DescriptorSetLayoutHandle(0),
                    EngineShaderSets.DebugLine.webGpu.wgslBytes(),
                    ByteArray(0),
                    VertexFormat.None,
                    bindingsByGroup = emptyMap(),
                    bindingsMetadataAvailable = true,
                )
                val renderer = buildTestRenderer(
                    graphicsDevice,
                    swapchainManager,
                    primary,
                    linePipeline,
                    attacher,
                    registry,
                )
                val target = renderer.createRenderTarget(SIZE, SIZE)
                WebGpuContentAttachFixture(renderer, attacher, graphicsDevice, target) {
                    registry.destroyAll { it.destroy() }
                    attacher.releaseAll()
                    primary.destroy()
                    linePipeline.destroy()
                    swapchainManager.destroy()
                }
            }

            @Suppress("LongParameterList")
            private suspend fun buildTestRenderer(
                device: GraphicsDevice,
                swapchain: SwapchainManager,
                primary: RenderPipeline,
                linePipeline: LineRenderPipeline,
                attacher: ContentFeatureAttacher<RenderPipeline>,
                registry: PipelineRegistry<RenderPipeline>,
            ): Renderer = Renderer(
                graphicsDevice = device,
                swapchainManager = swapchain,
                pipelines = PipelineTable(primary = primary, primaryFormat = VertexFormat.None),
                lineRenderPipeline = linePipeline,
                uiShaderSources = UiShaderSources(
                    quad = engineWgsl(EngineShaderSets.UiQuad),
                    glyph = engineWgsl(EngineShaderSets.UiGlyph),
                    texture = engineWgsl(EngineShaderSets.UiTexture),
                    roundedQuad = engineWgsl(EngineShaderSets.UiRoundedQuad),
                    targetComposite = engineWgsl(EngineShaderSets.UiTargetComposite),
                ),
                maxFramesInFlight = MAX_FRAMES_IN_FLIGHT,
                renderFeatures = listOf(
                    attacher.beforeGeometry,
                    OpaqueRenderFeature(WebGpuLinePass(linePipeline)),
                    attacher.afterGeometry,
                    UiRenderFeature(WebGpuUiPass()),
                ),
            ).also { renderer ->
                renderer.contentFeatureHost = attacher
                renderer.shaderReplacement = WebGpuShaderReplacement(
                    registry = registry,
                    device = device.wgpuContext.device,
                    uiTargets = { renderer.activeUiPipelineTargets() },
                    onSwap = { oldPipeline -> renderer.bufferPools.invalidatePipeline(oldPipeline) },
                )
            }
        }
    }

    companion object {
        private const val FRAMES = 2
        private const val SIZE = 64
        private const val MAX_FRAMES_IN_FLIGHT = 1
        private const val BYTES_PER_PIXEL = 4
        private const val RED = 0xFF0000
        private const val GREEN = 0x00FF00
        private const val BLUE = 0x0000FF
        private const val YELLOW = 0xFFFF00
        private val LENS = Lens(eye = Vec3f(0f, 0f, 5f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 10f)

        private val Tint = UniformField("tint", GpuDataShape.Vec4)
        private val Layout = UniformLayout(Tint)

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
            ).webGpu

        private fun rgba(r: Float, g: Float, b: Float) = vec4(r.lit, g.lit, b.lit, 1f.lit)

        val RED_SHADER = solidShader("red", RED, x = -0.5f)
        val BLUE_SHADER = solidShader("blue", BLUE, x = 0.5f)
        val GREEN_SHADER = solidShader("green", GREEN, x = -0.5f)
        val YELLOW_SHADER = solidShader("yellow", YELLOW, x = -0.5f)
        val EXTRA_BINDING_SHADER = solidShader("extra", GREEN, x = -0.5f, extraBinding = true)

        private fun greenUiQuadShader(): ShaderSet = aslShaderSet(
            shader("green_ui_quad") {
                val uniforms = uniformBlock("Uniforms", group = 0, binding = 0)
                val screenToNdc by uniforms.field(GpuDataShape.Vec4)
                val out = varyings("VertexOutput")
                val color by out.varying(GpuDataShape.Vec4, location = 0)

                vertex {
                    val inPos by input(GpuDataShape.Vec2, location = 0)
                    val inColor by input(GpuDataShape.Vec4, location = 1)
                    val inTransform by input(GpuDataShape.Vec4, location = 2)

                    val scale = inTransform.xy
                    val pivot = inTransform.zw
                    val scaledPos = pivot + (inPos - pivot) * scale
                    val ndc = scaledPos * screenToNdc.xy + screenToNdc.zw
                    out.position set vec4(ndc, 0f.lit, 1f.lit)
                    color set inColor
                }

                fragment {
                    val a = let("a", color.a)
                    colorOutput(vec4(0f.lit, 1f.lit, 0f.lit, 1f.lit) * a)
                }
            },
        )

        private fun square(name: String, stages: ShaderStages, variant: PipelineVariant = PipelineVariant.Overlay) = ContentFeatureSource {
            ContentFeature(
                name = name,
                spec = stages.spec(vertexFormat = VertexFormat.None, variant = variant, uniforms = Layout),
            ) { pipeline, uniforms, _ -> SquareFeature(pipeline, uniforms) }
        }

        private class SquareFeature(
            private val pipeline: PipelineHandle,
            private val uniforms: UniformBlock,
        ) : RenderFeature<RenderFrameContext> {
            override val pass = RenderPassSlot.Scene

            override fun recordCommands(context: RenderFrameContext) {
                uniforms.write(context.frameIndex) { put(Tint, 1f, 1f, 1f, 1f) }
                context.recorder.bindPipeline(pipeline)
                context.recorder.bindMaterial(BindingSemantic.Material, uniforms.binding(context.frameIndex))
                context.recorder.draw(3, 1)
            }

            override fun destroy() = Unit
        }

        private var fixture: WebGpuContentAttachFixture? = null

        private fun shared(): WebGpuContentAttachFixture =
            fixture ?: WebGpuContentAttachFixture.create().also { fixture = it }

        @AfterClass
        @JvmStatic
        fun release() {
            fixture?.release()
            fixture = null
        }
    }
}

private suspend fun engineWgsl(set: ShaderSet): ByteArray =
    checkNotNull(set.webGpu[ShaderStage.VERTEX]) {
        "Every engine shader set declares a WebGPU vertex stage."
    }.resolveBytes()

private suspend fun ShaderStages.wgslBytes(): ByteArray =
    checkNotNull(this[ShaderStage.VERTEX]) {
        "Every WebGPU stage declares a vertex stage."
    }.resolveBytes()
