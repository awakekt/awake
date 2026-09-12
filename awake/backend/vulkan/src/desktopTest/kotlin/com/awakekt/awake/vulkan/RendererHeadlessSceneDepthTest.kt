/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.asset.shadercompiler.NagaShaderCompiler
import com.awakekt.awake.asset.shaderdsl.AslShaderDefinition
import com.awakekt.awake.asset.shaderdsl.fieldsFrom
import com.awakekt.awake.asset.shaderdsl.fullScreenTriangleCorner
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.plus
import com.awakekt.awake.asset.shaderdsl.sampler
import com.awakekt.awake.asset.shaderdsl.shader
import com.awakekt.awake.asset.shaderdsl.textureDepth2d
import com.awakekt.awake.asset.shaderdsl.textureSampleLevelDepth
import com.awakekt.awake.asset.shaderdsl.times
import com.awakekt.awake.asset.shaderdsl.vec2
import com.awakekt.awake.asset.shaderdsl.vec4
import com.awakekt.awake.asset.shaderdsl.x
import com.awakekt.awake.asset.shaderdsl.y
import com.awakekt.awake.asset.shaderpack.PackShaderSets
import com.awakekt.awake.asset.shaders.ShaderStage
import com.awakekt.awake.asset.shaders.entryPoint
import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.CommandRecorder
import com.awakekt.awake.render.passes.OpaqueRenderFeature
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.RenderFeature
import com.awakekt.awake.render.passes.RenderFrameContext
import com.awakekt.awake.render.passes.RenderPassSlot
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.pipeline.PipelineVariant
import com.awakekt.awake.render.renderer.UniformField
import com.awakekt.awake.render.renderer.UniformLayout
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.vulkan.commands.TransferContext
import com.awakekt.awake.vulkan.debug.LineRenderPipeline
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.handles.DescriptorSetLayoutHandle
import com.awakekt.awake.vulkan.material.Material
import com.awakekt.awake.vulkan.pipeline.DepthOnlyPipeline
import com.awakekt.awake.vulkan.pipeline.DepthPrePassFeature
import com.awakekt.awake.vulkan.pipeline.PipelineTable
import com.awakekt.awake.vulkan.pipeline.RenderPipeline
import com.awakekt.awake.vulkan.pipeline.ShaderPair
import com.awakekt.awake.vulkan.pipeline.UiShaderPairs
import com.awakekt.awake.vulkan.pipeline.VulkanLinePass
import com.awakekt.awake.vulkan.pipeline.VulkanUiPass
import com.awakekt.awake.vulkan.pipeline.createSceneRenderPass
import com.awakekt.awake.vulkan.renderer.Renderer
import com.awakekt.awake.vulkan.swapchain.SwapchainManager
import com.awakekt.awake.vulkan.texture.DepthTarget
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue
import com.awakekt.awake.render.material.Material as RenderMaterial
import com.awakekt.awake.render.mesh.Mesh as RenderMesh

/**
 * The capability, on real pixels: a shader reading the depth of the scene in front of it.
 *
 * What water, soft particles and depth fog all need, and what the engine could not express
 * before -- the one depth pass that existed renders from the light, for shadows. Getting this
 * wrong is invisible in a compile: a pass that never ran, a descriptor bound at the wrong set,
 * or the light's matrix reused for the camera all produce a picture, just not the right one.
 *
 * Two quads at different distances, and a full-screen probe drawn last with depth test off that
 * writes the sampled depth as grey. One assertion covers every failure above: the near quad's
 * grey must be DARKER than the far quad's, and both darker than the cleared far plane.
 *
 * - a pass that never ran leaves the target cleared, so both regions read white and the ordering
 *   assertion fails;
 * - the light's matrix instead of the camera's orders the two quads by distance from the light,
 *   which this scene deliberately places so that ordering differs;
 * - a descriptor at the wrong set samples something that is not depth, which does not vary
 *   between the two regions.
 */
class RendererHeadlessSceneDepthTest {

    @Test
    fun aShaderSamplesTheDepthOfTheSceneInFrontOfIt() {
        val renderer = buildRenderer()
        val target = renderer.createRenderTarget(TARGET_SIZE, TARGET_SIZE)
        try {
            val pixels = renderer.renderScene(target)

            // Located, not assumed: the further quad is narrower on screen, so a sample point
            // mirrored from the near one misses it. Scanning also means the assertion still
            // means what it says if the framing ever changes.
            val row = (0 until TARGET_SIZE).map { pixels.greyAt(it, CENTRE_Y) }
            val drawn = row.withIndex().filter { it.value < CLEARED_DEPTH }
            val leftBand = drawn.filter { it.index < TARGET_SIZE / 2 }.map { it.value }
            val rightBand = drawn.filter { it.index >= TARGET_SIZE / 2 }.map { it.value }

            assertTrue(
                leftBand.isNotEmpty() && rightBand.isNotEmpty(),
                "Expected a band of sampled depth on each side of the frame, got " +
                    "${leftBand.size} and ${rightBand.size} pixels. Everything reading the " +
                    "cleared far plane means the scene-depth pass rasterised no geometry -- it " +
                    "ran with no draw calls, or not at all. Row: $row",
            )
            val near = leftBand.min()
            val far = rightBand.min()
            assertTrue(
                near < far,
                "The near quad (left, $near) must sample a smaller depth than the far quad " +
                    "(right, $far). Equal means the probe is reading a constant rather than " +
                    "per-pixel depth; reversed means it is reading the light's depth, not the " +
                    "camera's.",
            )
            assertTrue(
                near > 0,
                "The near band reads pure black -- the near plane exactly, which this scene " +
                    "never touches. The probe is sampling something that is not this depth.",
            )
        } finally {
            target.destroy()
            renderer.destroy()
        }
    }

    /** Two quads the same size, one nearer the camera than the other. */
    private fun Renderer.renderScene(target: RenderTarget): ByteArray {
        var nearMesh: RenderMesh? = null
        var farMesh: RenderMesh? = null
        var material: RenderMaterial? = null
        try {
            val near = createMesh(quad()).also { nearMesh = it }
            val far = createMesh(quad()).also { farMesh = it }
            val shared = createMaterial().also { material = it }
            renderToTexture(
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
            return runBlocking { readPixels(target) }.data
        } finally {
            nearMesh?.destroy()
            farMesh?.destroy()
            material?.destroy()
        }
    }

    private fun ByteArray.greyAt(x: Int, y: Int): Int =
        this[(y * TARGET_SIZE + x) * BYTES_PER_PIXEL].toInt() and 0xFF

    /** Draws the probe over everything already in the frame. Last in the feature list and
     * depth-test-off, so what lands is the sampled depth rather than the quads themselves. */
    private class SceneDepthProbeFeature(
        private val pipeline: RenderPipeline,
    ) : RenderFeature<RenderFrameContext> {
        override val pass = RenderPassSlot.Scene

        override fun recordCommands(context: RenderFrameContext) {
            val block = checkNotNull(pipeline.uniformBlock) {
                "The probe pipeline was built with non-null uniforms, so a block must exist."
            }
            block.write(context.frameIndex) { put(floatArrayOf(1f), SCALE_FIELD) }
            val recorder: CommandRecorder = context.recorder
            recorder.bindPipeline(pipeline)
            recorder.bindMaterial(BindingSemantic.Material, block.binding(context.frameIndex))
            recorder.draw(FULLSCREEN_TRIANGLE_VERTICES, 1)
        }

        override fun destroy() = pipeline.destroy()
    }

    private companion object {
        val SCENE_DEPTH_GROUP = BindingLayout.Standard.slot(BindingSemantic.SceneDepth)

        /**
         * Writes the depth it samples straight out as grey.
         *
         * Vertex-less, and the v coordinate is NOT flipped: the depth target was rasterised by
         * the same camera matrix into the same clip space, so probe and depth agree without one.
         */
        val SCALE_FIELD = UniformField("scale", GpuDataShape.Float)

        /**
         * The probe's own set 0.
         *
         * Not decoration: a pipeline built without `uniforms` gets the shared material layout at
         * set 0, whose glTF bindings this shader never declares -- and MoltenVK then refuses the
         * pipeline outright ("argument buffer resource base type could not be determined"),
         * because it cannot type a resource no shader stage names.
         */
        val PROBE_LAYOUT = UniformLayout(SCALE_FIELD)

        val ProbeShader: AslShaderDefinition = shader("scene_depth_probe") {
            val u = uniformBlock("Uniforms", group = 0, binding = 0)
            val scale = u.fieldsFrom(PROBE_LAYOUT).value("scale")
            val sceneDepth by textureDepth2d(group = SCENE_DEPTH_GROUP, binding = 0)
            val sceneDepthSampler by sampler(group = SCENE_DEPTH_GROUP, binding = 1)

            val out = varyings("VertexOutput")
            val uv by out.varying(GpuDataShape.Vec2, location = 0)

            vertex {
                val corner = fullScreenTriangleCorner()
                out.position set vec4(corner, 0f.lit, 1f.lit)
                uv set vec2((corner.x + 1f.lit) * 0.5f.lit, (corner.y + 1f.lit) * 0.5f.lit)
            }

            fragment {
                val depth = textureSampleLevelDepth(sceneDepth, sceneDepthSampler, uv, 0.lit) * scale
                colorOutput(vec4(depth, depth, depth, 1f.lit))
            }
        }

        /** Position, normal, colour -- `PositionNormalColor`, what the depth shader declares. */
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

        const val TARGET_SIZE = 128
        const val MAX_FRAMES_IN_FLIGHT = 1
        const val BYTES_PER_PIXEL = 4
        const val FULLSCREEN_TRIANGLE_VERTICES = 3
        const val EYE_Z = 6f

        /** Well apart in Z so the sampled depths differ by far more than quantisation. */
        const val NEAR_Z = 2f
        const val FAR_Z = -6f
        const val OFFSET_X = 1.2f

        const val CLEARED_DEPTH = 255

        /** Raised from the usual 0.1 so the depth curve is not so compressed that the two quads
         * land four grey levels apart; at 1.0 they differ by tens. */
        const val NEAR_PLANE = 1f
        const val CENTRE_Y = TARGET_SIZE / 2

        /** A fresh renderer, destroyed by the caller. Not cached like the older probes here: one
         * test needs it once, and a cached device outlives the class and adds to the GPU
         * contention this module already fights. */
        fun buildRenderer(): Renderer {
            val graphicsDevice = GraphicsDevice()
            graphicsDevice.createHeadless()
            val swapchainManager = SwapchainManager(graphicsDevice, MAX_FRAMES_IN_FLIGHT)
            swapchainManager.createHeadless(TARGET_SIZE, TARGET_SIZE)
            val pipelineLayoutMaterial = Material(graphicsDevice)
            val sceneRenderPass = createSceneRenderPass(graphicsDevice, swapchainManager)
            val transferContext = TransferContext(graphicsDevice)

            val sceneDepthTarget = DepthTarget(graphicsDevice)
            val sceneDepthPass = sceneDepthPass(graphicsDevice, pipelineLayoutMaterial, sceneDepthTarget)

            val primary = RenderPipeline(
                graphicsDevice,
                swapchainManager,
                sceneRenderPass,
                pipelineLayoutMaterial.descriptorSetLayout,
                runBlocking { packShaderPair("triangle") },
                VertexFormat.PositionNormalColor,
                vertexEntryPoint = "vertexMain",
                fragmentEntryPoint = "fragmentMain",
            )
            val probe = probePipeline(
                graphicsDevice,
                swapchainManager,
                sceneRenderPass,
                pipelineLayoutMaterial,
                sceneDepthTarget,
            )

            val renderer = Renderer(
                graphicsDevice = graphicsDevice,
                swapchainManager = swapchainManager,
                pipelines = PipelineTable(
                    primary = primary,
                    primaryFormat = VertexFormat.PositionNormalColor,
                ),
                // The probe records LAST, after the opaque feature has drawn the quads, so its
                // depth-test-off fill lands on top of them.
                renderFeatures = listOf(
                    OpaqueRenderFeature(
                        VulkanLinePass(
                            LineRenderPipeline(
                                graphicsDevice,
                                swapchainManager,
                                sceneRenderPass,
                                runBlocking { packShaderPair("debug_line") },
                                MAX_FRAMES_IN_FLIGHT,
                            ),
                        ),
                    ),
                    SceneDepthProbeFeature(probe),
                    UiRenderFeatureOf(),
                ),
                transferContext = transferContext,
                uiShaderPairs = runBlocking { uiPairs() },
                maxFramesInFlight = MAX_FRAMES_IN_FLIGHT,
                sceneDepthPass = sceneDepthPass,
            )
            return renderer
        }

        private fun UiRenderFeatureOf() =
            com.awakekt.awake.render.passes2d.UiRenderFeature(VulkanUiPass())

        /**
         * The probe's pipeline, with an empty layout at set 1.
         *
         * Scene depth is set 2 and this pipeline declares no shadow map, so without a filler the
         * scene-depth layout would land at set 1 and the shader would read the wrong group --
         * the case `VulkanEngine.extraSetLayouts` fills for real pipelines.
         */
        private fun probePipeline(
            graphicsDevice: GraphicsDevice,
            swapchainManager: SwapchainManager,
            sceneRenderPass: Long,
            pipelineLayoutMaterial: Material,
            sceneDepthTarget: DepthTarget,
        ): RenderPipeline = RenderPipeline(
            graphicsDevice,
            swapchainManager,
            sceneRenderPass,
            pipelineLayoutMaterial.descriptorSetLayout,
            probeShaderPair(),
            VertexFormat.None,
            vertexEntryPoint = "vertexMain",
            fragmentEntryPoint = "fragmentMain",
            variant = PipelineVariant.Background,
            extraDescriptorSetLayouts = listOf(
                emptySetLayout(graphicsDevice),
                DescriptorSetLayoutHandle(sceneDepthTarget.descriptorSetLayout),
            ),
            uniforms = PROBE_LAYOUT,
            framesInFlight = MAX_FRAMES_IN_FLIGHT,
            engineBoundSemantics = setOf(BindingSemantic.SceneDepth),
        )

        /** Required by `Renderer`; the UI pass never runs here. */
        private suspend fun uiPairs() = UiShaderPairs(
            quad = packShaderPair("ui_quad"),
            glyph = packShaderPair("ui_glyph"),
            texture = packShaderPair("ui_texture"),
            roundedQuad = packShaderPair("ui_rounded_quad"),
        )

        /** The camera-space depth pass, built the way `VulkanEngine.buildSceneDepthFeature`
         * builds it -- same target, same depth-only pipeline, same shader set. */
        private fun sceneDepthPass(
            graphicsDevice: GraphicsDevice,
            pipelineLayoutMaterial: Material,
            sceneDepthTarget: DepthTarget,
        ) = DepthPrePassFeature(
            sceneDepthTarget,
            DepthOnlyPipeline(
                graphicsDevice,
                sceneDepthTarget.renderPass,
                pipelineLayoutMaterial.descriptorSetLayout,
                runBlocking { packShaderPair(PackShaderSets.SceneDepth) },
                VertexFormat.PositionNormalColor,
                sceneDepthTarget.size,
                PackShaderSets.SceneDepth.vulkan.entryPoint(ShaderStage.VERTEX),
                PackShaderSets.SceneDepth.vulkan.entryPoint(ShaderStage.FRAGMENT),
            ),
        )

        /** The same no-binding filler `VulkanEngine.extraSetLayouts` uses for a slot a pipeline
         * must occupy but does not read. */
        private fun emptySetLayout(graphicsDevice: GraphicsDevice) = DescriptorSetLayoutHandle(
            com.awakekt.awake.vulkan.gen.VulkanDescriptors.vkCreateDescriptorSetLayout(
                graphicsDevice.device,
                com.awakekt.awake.vulkan.models.info.VkDescriptorSetLayoutCreateInfo(
                    pBindings = emptyArray(),
                ),
            ),
        )

        private fun probeShaderPair(): ShaderPair =
            NagaShaderCompiler.wgslToSpirv(ProbeShader.emitWgsl()).let { ShaderPair(it, it) }
    }
}
