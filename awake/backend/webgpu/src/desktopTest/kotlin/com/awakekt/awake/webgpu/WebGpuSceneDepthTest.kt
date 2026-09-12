/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.asset.shaderdsl.AslShaderDefinition
import com.awakekt.awake.asset.shaderdsl.bindingsByGroup
import com.awakekt.awake.asset.shaderdsl.div
import com.awakekt.awake.asset.shaderdsl.inputsFrom
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.minus
import com.awakekt.awake.asset.shaderdsl.plus
import com.awakekt.awake.asset.shaderdsl.sampler
import com.awakekt.awake.asset.shaderdsl.shader
import com.awakekt.awake.asset.shaderdsl.textureDepth2d
import com.awakekt.awake.asset.shaderdsl.textureSampleLevelDepth
import com.awakekt.awake.asset.shaderdsl.times
import com.awakekt.awake.asset.shaderdsl.vec2
import com.awakekt.awake.asset.shaderdsl.vec4
import com.awakekt.awake.asset.shaderdsl.w
import com.awakekt.awake.asset.shaderdsl.x
import com.awakekt.awake.asset.shaderdsl.xy
import com.awakekt.awake.asset.shaderdsl.y
import com.awakekt.awake.asset.shaderpack.PackShaderSets
import com.awakekt.awake.asset.shaders.EngineShaderSets
import com.awakekt.awake.asset.shaders.ShaderStage
import com.awakekt.awake.asset.shaders.entryPoint
import com.awakekt.awake.asset.shaders.resolveBytes
import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.geometry.VertexSemantic
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.passes.OpaqueRenderFeature
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes2d.UiRenderFeature
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.pipeline.PipelineTable
import com.awakekt.awake.webgpu.device.GraphicsDevice
import com.awakekt.awake.webgpu.handles.DescriptorSetLayoutHandle
import com.awakekt.awake.webgpu.pipeline.DepthOnlyPipeline
import com.awakekt.awake.webgpu.pipeline.DepthPrePassFeature
import com.awakekt.awake.webgpu.pipeline.RenderPipeline
import com.awakekt.awake.webgpu.pipeline.WebGpuLinePass
import com.awakekt.awake.webgpu.pipeline.WebGpuUiPass
import com.awakekt.awake.webgpu.renderer.Renderer
import com.awakekt.awake.webgpu.swapchain.SwapchainManager
import com.awakekt.awake.webgpu.texture.DepthTarget
import io.ygdrasil.webgpu.glfwContextRenderer
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * WebGPU's half of the scene-depth capability: a shader reading the depth of the scene in front
 * of it, proven on real pixels.
 *
 * The Vulkan test of the same name proves the capability; this proves THIS backend's wiring,
 * which is genuinely different code -- a bind group per draw carried on `PreparedDraw`, against
 * Vulkan's one descriptor set bound per pass.
 *
 * Shaped differently for that reason too. Vulkan's version paints a full-screen probe over the
 * frame; this backend's `Renderer` takes no feature list, so the primary shader itself samples
 * the depth and writes it as grey, which exercises exactly the path a real consumer would use.
 *
 * Two quads at different camera distances: the near one must read a smaller depth than the far
 * one. That single ordering fails if the pass never ran (both stay background), if the binding
 * never arrived (a constant), or if the light's matrix were used instead of the camera's.
 */
class WebGpuSceneDepthTest {

    @Test
    fun aShaderSamplesTheDepthOfTheSceneInFrontOfIt() = withSceneDepthRenderer { renderer ->
        val target = renderer.createRenderTarget(SIZE, SIZE)
        var nearMesh: com.awakekt.awake.render.mesh.Mesh? = null
        var farMesh: com.awakekt.awake.render.mesh.Mesh? = null
        var material: com.awakekt.awake.render.material.Material? = null
        try {
            val near = renderer.createMesh(quad()).also { nearMesh = it }
            val far = renderer.createMesh(quad()).also { farMesh = it }
            val shared = renderer.createMaterial().also { material = it }
            renderer.renderToTexture(
                target,
                Lens(
                    eye = Vec3f(0f, 0f, EYE_Z),
                    center = Vec3f(0f, 0f, 0f),
                    fovYRadians = 1f,
                    near = NEAR_PLANE,
                    far = 50f,
                ),
                listOf(
                    RenderDrawCommand(near, shared, Mat4().translate(-OFFSET_X, 0f, NEAR_Z)),
                    RenderDrawCommand(far, shared, Mat4().translate(OFFSET_X, 0f, FAR_Z)),
                ),
            )
            val pixels = runBlocking { renderer.readPixels(target) }.data

            // Located by scanning, not by fixed points: the further quad is narrower on screen,
            // so a mirrored sample point misses it. Background is the black clear, so anything
            // the quads wrote is brighter than it.
            val row = (0 until SIZE).map { pixels.greyAt(it, SIZE / 2) }
            val drawn = row.withIndex().filter { it.value > BACKGROUND }
            val leftBand = drawn.filter { it.index < SIZE / 2 }.map { it.value }
            val rightBand = drawn.filter { it.index >= SIZE / 2 }.map { it.value }

            assertTrue(
                leftBand.isNotEmpty() && rightBand.isNotEmpty(),
                "Expected a quad on each side of the frame, got ${leftBand.size} and " +
                    "${rightBand.size} lit pixels. All background means the draws never sampled " +
                    "anything -- the scene-depth pass did not run, or its bind group never " +
                    "reached them. Row: $row",
            )
            assertTrue(
                leftBand.max() < rightBand.max(),
                "The near quad (left, ${leftBand.max()}) must sample a smaller depth than the " +
                    "far quad (right, ${rightBand.max()}). Equal means the shader is reading a " +
                    "constant rather than per-pixel depth.",
            )
        } finally {
            target.destroy()
            nearMesh?.destroy()
            farMesh?.destroy()
            material?.destroy()
        }
    }

    private fun ByteArray.greyAt(x: Int, y: Int): Int =
        this[(y * SIZE + x) * BYTES_PER_PIXEL].toInt() and 0xFF

    /** The fixture, with a scene-depth pass wired the way `WebGpuEngine` wires one. */
    private fun withSceneDepthRenderer(block: (Renderer) -> Unit) = runBlocking {
        val context = glfwContextRenderer(
            width = 1,
            height = 1,
            title = "awake-scene-depth",
            onUncapturedError = { error -> println("WGPU UNCAPTURED: $error") },
        )
        val graphicsDevice = GraphicsDevice()
        graphicsDevice.create(context.wgpuContext)
        val swapchainManager = SwapchainManager(graphicsDevice, MAX_FRAMES_IN_FLIGHT)
        swapchainManager.create()

        val sceneDepthPass = sceneDepthPass(graphicsDevice)
        val primary = RenderPipeline(
            graphicsDevice,
            swapchainManager,
            DescriptorSetLayoutHandle(0),
            ProbeShader.emitWgsl().encodeToByteArray(),
            ByteArray(0),
            VertexFormat.PositionNormalColor,
            "vertexMain",
            "fragmentMain",
            bindingsByGroup = ProbeShader.bindingsByGroup(),
            bindingsMetadataAvailable = true,
        )
        val lineRenderPipeline = com.awakekt.awake.webgpu.debug.LineRenderPipeline(
            graphicsDevice,
            swapchainManager,
            EngineShaderSets.DebugLine.webGpu.wgslBytes(),
        )
        val renderer = Renderer(
            graphicsDevice = graphicsDevice,
            swapchainManager = swapchainManager,
            pipelines = PipelineTable(
                primary = primary,
                primaryFormat = VertexFormat.PositionNormalColor,
            ),
            lineRenderPipeline = lineRenderPipeline,
            uiShaderSources = uiSources(),
            maxFramesInFlight = MAX_FRAMES_IN_FLIGHT,
            // The same feature list WebGpuEngine builds. Not optional any more: rendering to a
            // texture records features exactly as the on-screen path does, so a renderer with an
            // empty list draws nothing -- which is what a capture of a featureless renderer
            // always meant, it just used to draw anyway through a second, hand-written path.
            renderFeatures = listOf(
                OpaqueRenderFeature(WebGpuLinePass(lineRenderPipeline)),
                UiRenderFeature(WebGpuUiPass()),
            ),
            sceneDepthPass = sceneDepthPass,
        )
        try {
            block(renderer)
        } finally {
            renderer.destroy()
            graphicsDevice.destroy()
        }
    }

    /** The camera-space depth pass, wired the way `WebGpuEngine` wires one. */
    private suspend fun sceneDepthPass(graphicsDevice: GraphicsDevice) = DepthPrePassFeature(
        depthTarget = DepthTarget(graphicsDevice),
        depthOnlyPipeline = DepthOnlyPipeline(
            graphicsDevice = graphicsDevice,
            shaderCode = PackShaderSets.SceneDepth.webGpu.wgslBytes(),
            vertexFormat = VertexFormat.PositionNormalColor,
            vertexEntryPoint = PackShaderSets.SceneDepth.webGpu.entryPoint(ShaderStage.VERTEX),
            fragmentEntryPoint = PackShaderSets.SceneDepth.webGpu.entryPoint(ShaderStage.FRAGMENT),
            bindingsByGroup = PackShaderSets.SceneDepth.webGpu.bindingsByGroup,
            bindingsMetadataAvailable = PackShaderSets.SceneDepth.webGpu.bindingsMetadataAvailable,
        ),
    )

    private companion object {
        val SCENE_DEPTH_GROUP = BindingLayout.Standard.slot(BindingSemantic.SceneDepth)

        /**
         * MVP in, sampled scene depth out as grey.
         *
         * The screen UV comes from this fragment's own clip position rather than a vertex
         * attribute, so it names the same pixel the depth pass rasterised.
         */
        val ProbeShader: AslShaderDefinition = shader("webgpu_scene_depth_probe") {
            val u = uniformBlock("Uniforms", group = 0, binding = 0)
            val mvp by u.field(GpuDataShape.Mat4)
            val sceneDepth by textureDepth2d(group = SCENE_DEPTH_GROUP, binding = 0)
            val sceneDepthSampler by sampler(group = SCENE_DEPTH_GROUP, binding = 1)

            val out = varyings("VertexOutput")
            val clip by out.varying(GpuDataShape.Vec4, location = 0)

            vertex {
                val ins = inputsFrom(VertexFormat.PositionNormalColor)
                val projected = mvp * vec4(ins.input(VertexSemantic.Position), 1f.lit)
                out.position set projected
                clip set projected
            }

            fragment {
                val ndc = clip.xy / clip.w
                val uv = vec2((ndc.x + 1f.lit) * 0.5f.lit, (1f.lit - ndc.y) * 0.5f.lit)
                val depth = textureSampleLevelDepth(sceneDepth, sceneDepthSampler, uv, 0.lit)
                colorOutput(vec4(depth, depth, depth, 1f.lit))
            }
        }

        /** Required by `Renderer`; the UI pass never runs here. */
        suspend fun uiSources() = com.awakekt.awake.webgpu.pipeline.UiShaderSources(
            quad = EngineShaderSets.UiQuad.webGpu.wgslBytes(),
            glyph = EngineShaderSets.UiGlyph.webGpu.wgslBytes(),
            texture = EngineShaderSets.UiTexture.webGpu.wgslBytes(),
            roundedQuad = EngineShaderSets.UiRoundedQuad.webGpu.wgslBytes(),
            targetComposite = EngineShaderSets.UiTargetComposite.webGpu.wgslBytes(),
        )

        suspend fun com.awakekt.awake.asset.shaders.ShaderStages.wgslBytes(): ByteArray =
            checkNotNull(this[ShaderStage.VERTEX]) { "No WebGPU vertex stage." }.resolveBytes()

        /** Position, normal, colour -- the layout the depth shader declares. */
        fun quad() = MeshGeometry(
            floatArrayOf(
                -0.5f, -0.5f, 0f, 0f, 0f, 1f, 1f, 1f, 1f,
                0.5f, -0.5f, 0f, 0f, 0f, 1f, 1f, 1f, 1f,
                0.5f, 0.5f, 0f, 0f, 0f, 1f, 1f, 1f, 1f,
                -0.5f, 0.5f, 0f, 0f, 0f, 1f, 1f, 1f, 1f,
            ),
            intArrayOf(0, 1, 2, 2, 3, 0),
            VertexFormat.PositionNormalColor,
        )

        const val SIZE = 64
        const val MAX_FRAMES_IN_FLIGHT = 1
        const val BYTES_PER_PIXEL = 4
        const val BACKGROUND = 8
        const val EYE_Z = 6f
        const val NEAR_PLANE = 1f
        const val NEAR_Z = 2f
        const val FAR_Z = -6f
        const val OFFSET_X = 1.2f
    }
}
