/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.vulkan

import io.github.awakelab.awake.asset.shadercompiler.NagaShaderCompiler
import io.github.awakelab.awake.asset.shaderpack.DepthFogRenderFeature
import io.github.awakelab.awake.asset.shaderpack.PackShaderSets
import io.github.awakelab.awake.asset.shaderpack.depthFogShader
import io.github.awakelab.awake.asset.shaders.ShaderStage
import io.github.awakelab.awake.asset.shaders.entryPoint
import io.github.awakelab.awake.core.color.Color
import io.github.awakelab.awake.core.geometry.MeshGeometry
import io.github.awakelab.awake.core.geometry.VertexFormat
import io.github.awakelab.awake.core.math.Lens
import io.github.awakelab.awake.core.math.Mat4
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.render.passes.OpaqueRenderFeature
import io.github.awakelab.awake.render.pipeline.BindingSemantic
import io.github.awakelab.awake.render.pipeline.PipelineVariant
import io.github.awakelab.awake.render.renderer.DepthFogUniformLayout
import io.github.awakelab.awake.render.renderer.DrawCall
import io.github.awakelab.awake.render.renderer.createMaterial
import io.github.awakelab.awake.render.texture.RenderTarget
import io.github.awakelab.awake.vulkan.commands.TransferContext
import io.github.awakelab.awake.vulkan.debug.LineRenderPipeline
import io.github.awakelab.awake.vulkan.device.GraphicsDevice
import io.github.awakelab.awake.vulkan.handles.DescriptorSetLayoutHandle
import io.github.awakelab.awake.vulkan.material.Material
import io.github.awakelab.awake.vulkan.pipeline.DepthOnlyPipeline
import io.github.awakelab.awake.vulkan.pipeline.DepthPrePassFeature
import io.github.awakelab.awake.vulkan.pipeline.PipelineTable
import io.github.awakelab.awake.vulkan.pipeline.RenderPipeline
import io.github.awakelab.awake.vulkan.pipeline.ShaderPair
import io.github.awakelab.awake.vulkan.pipeline.UiShaderPairs
import io.github.awakelab.awake.vulkan.pipeline.VulkanLinePass
import io.github.awakelab.awake.vulkan.pipeline.VulkanUiPass
import io.github.awakelab.awake.vulkan.pipeline.createSceneRenderPass
import io.github.awakelab.awake.vulkan.renderer.Renderer
import io.github.awakelab.awake.vulkan.swapchain.SwapchainManager
import io.github.awakelab.awake.vulkan.texture.DepthTarget
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue
import io.github.awakelab.awake.render.material.Material as RenderMaterial
import io.github.awakelab.awake.render.mesh.Mesh as RenderMesh

/**
 * The first consumer of the scene-depth pass, on real pixels: fog that thickens with distance
 * because it read the depth in front of it.
 *
 * `RendererHeadlessSceneDepthTest` proves the pass can be sampled; this proves the sample is
 * usable. Two white quads at different distances, fogged toward pure red by
 * [DepthFogRenderFeature] drawn after them. The red channel saturates on both, so the assertion
 * reads GREEN, which the fog can only remove: the far quad must be greener-poorer than the near
 * one, and the near one must keep some green rather than being flattened to fog colour.
 *
 * The failures this separates:
 * - fog recorded before the geometry (the sky's paint order) leaves both quads pure white;
 * - a full-screen fog blended by a constant leaves both quads equally fogged;
 * - the far plane fogged too would tint the cleared background, which the last assertion denies.
 */
class RendererHeadlessDepthFogTest {

    @Test
    fun fogThickensWithTheSceneDepthBehindIt() {
        val renderer = buildRenderer()
        val target = renderer.createRenderTarget(TARGET_SIZE, TARGET_SIZE)
        try {
            val pixels = renderer.renderScene(target)

            // Located, not assumed: the further quad is narrower on screen -- see the sibling
            // scene-depth test, which scans for the same reason.
            val row = (0 until TARGET_SIZE).map { pixels.greenAt(it, CENTRE_Y) }
            val lit = row.withIndex().filter { it.value > CLEAR_GREEN }
            val near = lit.filter { it.index < TARGET_SIZE / 2 }.map { it.value }
            val far = lit.filter { it.index >= TARGET_SIZE / 2 }.map { it.value }

            assertTrue(
                near.isNotEmpty() && far.isNotEmpty(),
                "Expected a quad on each side of the frame, got ${near.size} and ${far.size} " +
                    "pixels above the cleared background. Row: $row",
            )
            assertTrue(
                far.max() < near.min(),
                "The far quad (${far.max()}) must keep LESS green than the near one " +
                    "(${near.min()}): fog is mixed by distance, and equal means it was mixed by " +
                    "a constant -- a scene depth that never arrived reads as one flat value.",
            )
            assertTrue(
                near.min() > 0,
                "The near quad is fully fogged. At ${FOG_DENSITY} per unit and ${EYE_Z - NEAR_Z} " +
                    "units away it should keep most of its own colour; losing all of it means " +
                    "the sampled distance is far larger than the scene is.",
            )
            assertTrue(
                pixels.greenAt(CORNER, CORNER) == CLEAR_GREEN,
                "The cleared background was fogged. Nothing rasterised there, so its depth is " +
                    "the far plane -- which the shader excludes rather than unprojecting into a " +
                    "distance that saturates the fog.",
            )
        } finally {
            target.destroy()
            renderer.destroy()
        }
    }

    /** Two white quads the same size, one nearer the camera than the other. */
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
                    DrawCall(near, shared, Mat4().translate(-OFFSET_X, 0f, NEAR_Z)),
                    DrawCall(far, shared, Mat4().translate(OFFSET_X, 0f, FAR_Z)),
                ),
            )
            return runBlocking { readPixels(target) }.data
        } finally {
            nearMesh?.destroy()
            farMesh?.destroy()
            material?.destroy()
        }
    }

    private fun ByteArray.greenAt(x: Int, y: Int): Int =
        this[(y * TARGET_SIZE + x) * BYTES_PER_PIXEL + GREEN].toInt() and 0xFF

    private companion object {
        const val TARGET_SIZE = 128
        const val MAX_FRAMES_IN_FLIGHT = 1
        const val BYTES_PER_PIXEL = 4
        const val GREEN = 1
        const val EYE_Z = 6f
        const val NEAR_Z = 2f
        const val FAR_Z = -6f
        const val OFFSET_X = 1.2f
        const val CENTRE_Y = TARGET_SIZE / 2
        const val CORNER = 2

        /** The scene clears to black, so anything the quads cover is greener than this. */
        const val CLEAR_GREEN = 0

        /** Thick enough that 4 units and 12 units of it land tens of levels apart. */
        const val FOG_DENSITY = 0.12f

        /** Pure red: the fogged colour differs from the quads' white only in green and blue,
         * so one channel answers "how fogged is this pixel" with no unmixing. */
        val FOG_COLOR = Color(r = 1f, g = 0f, b = 0f, a = FOG_DENSITY)

        /** Vulkan's half of the shared definition -- see [depthFogShader]'s `flipDepthV`. */
        val FogShader = depthFogShader(flipDepthV = false)

        /** Position, normal, colour -- `PositionNormalColor`, white. */
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

        /** Raised from the usual 0.1 for the same reason the sibling test raises it: a
         * compressed depth curve puts the two quads a handful of levels apart. */
        const val NEAR_PLANE = 1f

        /** A fresh renderer, destroyed by the caller -- a cached device outlives the class and
         * adds to the GPU contention this module already fights. */
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
            val fogPipeline = fogPipeline(
                graphicsDevice,
                swapchainManager,
                sceneRenderPass,
                pipelineLayoutMaterial,
                sceneDepthTarget,
            )
            val fogBlock = checkNotNull(fogPipeline.uniformBlock) {
                "The fog pipeline was built with a uniform layout, so a block must exist."
            }

            return Renderer(
                graphicsDevice = graphicsDevice,
                swapchainManager = swapchainManager,
                pipelines = PipelineTable(
                    primary = primary,
                    primaryFormat = VertexFormat.PositionNormalColor,
                ),
                // Fog AFTER the opaque feature -- ContentPaint.AfterGeometry, spelled out here
                // because this test builds the list the engine would.
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
                    DepthFogRenderFeature(fogPipeline, fogBlock, FOG_COLOR),
                    io.github.awakelab.awake.render.passes2d.UiRenderFeature(VulkanUiPass()),
                ),
                transferContext = transferContext,
                uiShaderPairs = runBlocking { uiPairs() },
                maxFramesInFlight = MAX_FRAMES_IN_FLIGHT,
                sceneDepthPass = sceneDepthPass,
            )
        }

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

        /** Required by `Renderer`; the UI pass never runs here. */
        private suspend fun uiPairs() = UiShaderPairs(
            quad = packShaderPair("ui_quad"),
            glyph = packShaderPair("ui_glyph"),
            texture = packShaderPair("ui_texture"),
            roundedQuad = packShaderPair("ui_rounded_quad"),
        )

        /**
         * The fog's pipeline, with an empty layout at set 1.
         *
         * Scene depth is set 2 and this pipeline declares no shadow map, so without a filler the
         * scene-depth layout would land at set 1 -- the case `VulkanEngine.extraSetLayouts` fills
         * for real pipelines, including a content feature that declares `samplesSceneDepth`.
         */
        private fun fogPipeline(
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
            NagaShaderCompiler.wgslToSpirv(FogShader.emitWgsl()).let { ShaderPair(it, it) },
            VertexFormat.None,
            vertexEntryPoint = "vertexMain",
            fragmentEntryPoint = "fragmentMain",
            variant = PipelineVariant.Overlay,
            extraDescriptorSetLayouts = listOf(
                emptySetLayout(graphicsDevice),
                DescriptorSetLayoutHandle(sceneDepthTarget.descriptorSetLayout),
            ),
            uniforms = DepthFogUniformLayout,
            framesInFlight = MAX_FRAMES_IN_FLIGHT,
            engineBoundSemantics = setOf(BindingSemantic.SceneDepth),
        )

        /** The same no-binding filler `VulkanEngine.extraSetLayouts` uses. */
        private fun emptySetLayout(graphicsDevice: GraphicsDevice) = DescriptorSetLayoutHandle(
            io.github.awakelab.awake.vulkan.gen.VulkanDescriptors.vkCreateDescriptorSetLayout(
                graphicsDevice.device,
                io.github.awakelab.awake.vulkan.models.info.VkDescriptorSetLayoutCreateInfo(
                    pBindings = emptyArray(),
                ),
            ),
        )
    }
}
