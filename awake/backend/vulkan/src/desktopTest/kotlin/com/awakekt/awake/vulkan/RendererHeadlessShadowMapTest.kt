/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.passes.OpaqueRenderFeature
import com.awakekt.awake.render.passes2d.UiRenderFeature
import com.awakekt.awake.render.renderer.DEFAULT_SHADOW_CASCADES
import com.awakekt.awake.render.renderer.DrawCall
import com.awakekt.awake.render.renderer.SceneLight
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.renderer.directionalShadowBox
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
import com.awakekt.awake.vulkan.pipeline.VulkanLinePass
import com.awakekt.awake.vulkan.pipeline.VulkanUiPass
import com.awakekt.awake.vulkan.pipeline.createSceneRenderPass
import com.awakekt.awake.vulkan.renderer.Renderer
import com.awakekt.awake.vulkan.swapchain.SwapchainManager
import com.awakekt.awake.vulkan.texture.DepthTarget
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import kotlin.test.Test
import kotlin.test.assertTrue
import com.awakekt.awake.render.material.Material as RenderMaterial
import com.awakekt.awake.render.mesh.Mesh as RenderMesh

/**
 * Real-Vulkan-headless proof that the 3D shadow map reaches the scene's fragment shader.
 *
 * The gate for phase 4 of docs/tasks/2026-08-23-backend-content-split-plan.md, which moves the
 * shadow map out of every material's descriptor set into its own group -- a change to `Material`,
 * so a mistake affects every draw, not only shadowed ones. Nothing covered this before: despite
 * the name, `RendererHeadlessShadowQuadTest` exercises `UiDrawPrimitive.ShadowQuad`, a 2D drop
 * shadow, and never builds a `DepthTarget`.
 *
 * Asserts CONTRAST across a band of pure ground rather than a shadow at a predicted location:
 * where the shadow lands depends on the light's projection, the depth bias and the PCF kernel,
 * none of which this test cares about. A lit ground is one brightness; a shadowed one is two.
 *
 * A `shadowsEnabled` on/off comparison was tried first and cannot work: turning it off skips the
 * pre-pass but leaves the map populated, so both renders sample the same depth and agree
 * exactly. That produced "0 bytes differ" for a long time and read as a broken feature.
 *
 * Uses the real `lit_shadow.wgsl` and `shadow_depth.wgsl` from `awake:asset:shader-pack`, not
 * probe shaders. Phase 4 edits `lit_shadow.wgsl`'s own bindings, so a gate built on a
 * hand-written stand-in with its own bindings could not catch a shader/descriptor mismatch --
 * the specific failure this exists to prevent. Nothing to regenerate: the WGSL is compiled at
 * test time by [packShaderPair], so an edit to it takes effect on the next run.
 */
class RendererHeadlessShadowMapTest {

    @Test
    fun theCasterDarkensTheGroundItStandsOn() {
        val renderer = sharedRenderer()
        val target = renderer.createRenderTarget(TARGET_SIZE, TARGET_SIZE)
        try {
            renderer.shadowsEnabled = true
            val frame = renderer.renderScene(target)

            // A horizontal band of pure ground: below the caster's screen extent, above the
            // frame edge. Brightness across it is uniform when nothing is shadowed and has two
            // levels when something is.
            val band = (BAND_TOP until BAND_BOTTOM).flatMap { y ->
                (BAND_LEFT until BAND_RIGHT).map { x -> frame.redAt(x, y) }
            }
            val lit = band.max()
            val shadowed = band.min()

            assertTrue(
                lit > LIT_FLOOR,
                "The ground band is at most $lit, so the scene did not render lit geometry and " +
                    "the contrast check below would compare two shades of nothing.",
            )
            assertTrue(
                lit - shadowed > MIN_SHADOW_CONTRAST,
                "The ground band runs $shadowed..$lit, a spread of ${lit - shadowed}. A cast " +
                    "shadow darkens part of it well beyond that, so the depth map the pre-pass " +
                    "wrote is not reaching lit_shadow.wgsl's comparison. This exact assertion " +
                    "failed before shadow_depth.wgsl's Uniforms struct was corrected -- it was " +
                    "missing the two point-light arrays, so it read lightMvp at float offset 24 " +
                    "instead of 56.",
            )
        } finally {
            target.destroy()
        }
    }

    private fun ByteArray.redAt(x: Int, y: Int): Int =
        this[(y * TARGET_SIZE + x) * BYTES_PER_PIXEL].toInt() and 0xFF

    /** A ground plane with a smaller quad hovering above it, lit from overhead and to one side. */
    private fun Renderer.renderScene(target: RenderTarget): ByteArray {
        var groundMesh: RenderMesh? = null
        var casterMesh: RenderMesh? = null
        var material: RenderMaterial? = null
        try {
            val ground = createMesh(plane(GROUND_HALF, y = 0f)).also { groundMesh = it }
            val caster = createMesh(plane(CASTER_HALF, y = CASTER_Y, r = 1f, g = 0f, b = 0f)).also { casterMesh = it }
            val shared = createMaterial(LitShadowUniformLayout).also { material = it }
            renderToTexture(
                target,
                Lens(
                    eye = Vec3f(0f, EYE_Y, EYE_Z),
                    center = Vec3f(0f, 0f, 0f),
                    fovYRadians = 1f,
                    near = 0.1f,
                    far = 50f,
                ),
                listOf(DrawCall(ground, shared), DrawCall(caster, shared)),
                // viewProjection is supplied, not derived: the renderer renders depth from
                // whatever matrix the light carries and never builds one. `RenderSystem` fills
                // this in for a real scene; a direct `renderToTexture` caller does it here.
                SceneLight(direction = Vec3f(LIGHT_X, 1f, 0f), color = Vec3f(1f, 1f, 1f)).let {
                    it.copy(viewProjection = directionalShadowBox(it.direction, clipSpace).viewProjection)
                },
            )
            return runBlocking { readPixels(target) }.data
        } finally {
            groundMesh?.destroy()
            casterMesh?.destroy()
            material?.destroy()
        }
    }

    /** A horizontal quad at [y], facing up. `VertexFormat.PositionNormalColor`: 9 floats/vertex. */
    private fun plane(half: Float, y: Float, r: Float = 1f, g: Float = 1f, b: Float = 1f) = MeshGeometry(
        floatArrayOf(
            -half, y, -half, 0f, 1f, 0f, r, g, b,
            half, y, -half, 0f, 1f, 0f, r, g, b,
            half, y, half, 0f, 1f, 0f, r, g, b,
            -half, y, half, 0f, 1f, 0f, r, g, b,
        ),
        intArrayOf(0, 1, 2, 2, 3, 0),
        // Explicit: MeshGeometry defaults to PositionColorUv (8 floats/vertex), which would read
        // these 9-float vertices at the wrong stride and put the geometry nowhere visible.
        VertexFormat.PositionNormalColor,
    )

    private companion object {
        private var cachedRenderer: Renderer? = null

        /** The device the cached renderer was built on. `Renderer.destroy` frees what the
         * renderer built, not the device underneath it, so without this the headless device and
         * its instance outlive this class and add to the GPU contention every later suite in the
         * same JVM fights. */
        private var cachedDevice: GraphicsDevice? = null

        /** Frees what neither the renderer nor the device frees itself -- the render pass,
         * pipelines, descriptor set layout and transfer context this fixture built by hand.
         * Captured as a lambda where those locals are still in scope, which is smaller than
         * threading each one back out through a holder. */
        private var cachedCleanup: (() -> Unit)? = null

        @AfterClass
        @JvmStatic
        fun releaseSharedRenderer() {
            cachedRenderer?.destroy()
            cachedRenderer = null
            cachedCleanup?.invoke()
            cachedCleanup = null
            cachedDevice?.destroy()
            cachedDevice = null
        }

        const val TARGET_SIZE = 128
        const val MAX_FRAMES_IN_FLIGHT = 1
        const val BYTES_PER_PIXEL = 4

        /** Well above 8-bit dither: a real shadow covers far more of a 128x128 frame than this. */
        /** Ground-only band: below the caster's screen extent, inside the frame. */
        const val BAND_TOP = 70
        const val BAND_BOTTOM = 100
        const val BAND_LEFT = 12
        const val BAND_RIGHT = 116

        /** Lit ground reads far above this; a frame that drew nothing does not. */
        const val LIT_FLOOR = 60

        /** A real cast shadow is a large step, not 8-bit dither across a gradient. */
        const val MIN_SHADOW_CONTRAST = 25

        const val GROUND_HALF = 6f
        const val CASTER_HALF = 1.5f
        const val CASTER_Y = 2f
        const val EYE_Y = 6f
        const val EYE_Z = 9f

        /** Off-axis so the caster's shadow lands beside it rather than directly underneath, where
         * the caster itself would hide the difference from this camera. */
        const val LIGHT_X = 2f

        fun sharedRenderer(): Renderer {
            cachedRenderer?.let { return it }

            val graphicsDevice = GraphicsDevice().also { cachedDevice = it }
            graphicsDevice.createHeadless()
            val swapchainManager = SwapchainManager(graphicsDevice, MAX_FRAMES_IN_FLIGHT)
            swapchainManager.createHeadless(TARGET_SIZE, TARGET_SIZE)
            // Layered and arrayed like VulkanEngine builds it: lit_shadow declares
            // texture_depth_2d_array now, and a 2D view against that declaration is a validation
            // error rather than a wrong picture.
            val depthTarget = DepthTarget(graphicsDevice, layers = DEFAULT_SHADOW_CASCADES, arrayed = true, comparison = true)
            val descriptorSetLayout = Material.createDescriptorSetLayout(graphicsDevice)
            val sceneRenderPass = createSceneRenderPass(graphicsDevice, swapchainManager)
            val depthPrePass = depthPrePass(graphicsDevice, depthTarget, descriptorSetLayout)
            val primary = RenderPipeline(
                graphicsDevice,
                swapchainManager,
                sceneRenderPass,
                descriptorSetLayout,
                runBlocking { packShaderPair("lit_shadow") },
                VertexFormat.PositionNormalColor,
                vertexEntryPoint = "vertexMain",
                fragmentEntryPoint = "fragmentMain",
                // Set 1: the shadow map's own descriptor set. It used to ride inside the
                // material's set 0, which is what phase 4 removed.
                extraDescriptorSetLayouts = listOf(DescriptorSetLayoutHandle(depthTarget.descriptorSetLayout)),
            )
            val transferContext = TransferContext(graphicsDevice)
            cachedCleanup = headlessCleanup(
                graphicsDevice,
                transferContext,
                sceneRenderPass,
                descriptorSetLayout,
                primary,
            )
            return Renderer(
                graphicsDevice = graphicsDevice,
                swapchainManager = swapchainManager,
                pipelines = PipelineTable(
                    primary = primary,
                    primaryFormat = VertexFormat.PositionNormalColor,
                ),
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
                    UiRenderFeature(VulkanUiPass()),
                ),
                depthPrePass = depthPrePass,
                transferContext = transferContext,
                uiShaderPairs = runBlocking { defaultUiShaderPairs() },
                maxFramesInFlight = MAX_FRAMES_IN_FLIGHT,
            ).also { cachedRenderer = it }
        }

        /** The depth pre-pass: its own render pass, its own pipeline, the scene's descriptor
         * layout (shadow_depth binds the same per-draw uniform buffer and reads only lightMvp). */
        private fun depthPrePass(
            graphicsDevice: GraphicsDevice,
            depthTarget: DepthTarget,
            descriptorSetLayout: DescriptorSetLayoutHandle,
        ) = DepthPrePassFeature(
            depthTarget,
            DepthOnlyPipeline(
                graphicsDevice,
                depthTarget.renderPass,
                descriptorSetLayout,
                runBlocking { packShaderPair("shadow_depth") },
                VertexFormat.PositionNormalColor,
                depthTarget.size,
                vertexEntryPoint = "vertexMain",
                fragmentEntryPoint = "fragmentMain",
                cascadeCount = depthTarget.layers,
            ),
        )
    }
}
