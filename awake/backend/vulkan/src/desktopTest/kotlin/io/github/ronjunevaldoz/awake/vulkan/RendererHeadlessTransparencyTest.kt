// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.vulkan

import io.github.ronjunevaldoz.awake.render.pipeline.PipelineVariant
import io.github.ronjunevaldoz.awake.core.geometry.MeshGeometry
import io.github.ronjunevaldoz.awake.core.geometry.VertexFormat
import io.github.ronjunevaldoz.awake.core.host.readResourceBytes
import io.github.ronjunevaldoz.awake.core.math.Lens
import io.github.ronjunevaldoz.awake.core.math.Vec3f
import io.github.ronjunevaldoz.awake.render.passes.OpaqueRenderFeature
import io.github.ronjunevaldoz.awake.render.passes2d.UiRenderFeature
import io.github.ronjunevaldoz.awake.render.renderer.DrawCall
import io.github.ronjunevaldoz.awake.render.texture.RenderTarget
import io.github.ronjunevaldoz.awake.vulkan.commands.TransferContext
import io.github.ronjunevaldoz.awake.vulkan.debug.LineRenderPipeline
import io.github.ronjunevaldoz.awake.vulkan.device.GraphicsDevice
import io.github.ronjunevaldoz.awake.vulkan.material.Material
import io.github.ronjunevaldoz.awake.vulkan.pipeline.PipelineTable
import io.github.ronjunevaldoz.awake.vulkan.pipeline.RenderPipeline
import io.github.ronjunevaldoz.awake.vulkan.pipeline.ShaderPair
import io.github.ronjunevaldoz.awake.vulkan.pipeline.UiShaderPairs
import io.github.ronjunevaldoz.awake.vulkan.pipeline.VulkanLinePass
import io.github.ronjunevaldoz.awake.vulkan.pipeline.VulkanUiPass
import io.github.ronjunevaldoz.awake.vulkan.pipeline.createSceneRenderPass
import io.github.ronjunevaldoz.awake.vulkan.renderer.Renderer
import io.github.ronjunevaldoz.awake.vulkan.swapchain.SwapchainManager
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue
import io.github.ronjunevaldoz.awake.render.material.Material as RenderMaterial
import io.github.ronjunevaldoz.awake.render.mesh.Mesh as RenderMesh

/**
 * Proves a `DrawCall.transparent` draw actually resolves to the alpha-blended pipeline and
 * blends against what is already in the colour buffer -- the one property nothing else checks.
 *
 * The whole transparency path (`MeshRenderer.transparent` -> `DrawCall.transparent` ->
 * `PipelineTable.transparentByFormat` -> `Renderer.pipelineFor`) was built without any scene
 * setting the flag, so every piece of it was unexercised. A wrong pipeline choice here is
 * invisible in every other test: the draw still renders, in the right place, at the right size
 * -- only unblended.
 *
 * Uses `transparent_probe.wgsl` rather than `triangle.wgsl` for a specific reason: that shader
 * returns a hardcoded alpha of 1.0, and straight-alpha blending at alpha 1 is arithmetically a
 * no-op, so a blend assertion written against it would pass identically whether the transparent
 * pipeline was selected or never built at all. The probe takes its alpha from `uv.x`, so one
 * shader covers both the opaque backdrop (uv.x = 1) and the half-transparent overlay
 * (uv.x = 0.5) in a single frame.
 *
 * Vulkan only. WebGPU has no headless render harness, so its own transparent pipeline -- added
 * alongside this test -- stays visually verified rather than covered here.
 *
 * The overlap pixel is asserted against the exact arithmetic midpoint: a 0.5-alpha red over
 * opaque blue measures (128, 0, 128), so the blend lands in the same space the vertex colours
 * were written in, with no transfer curve applied. Dropping the transparent pipeline from the
 * table instead measures (255, 0, 0) -- pure overlay, no backdrop -- which is what this test
 * was falsified against before being committed.
 */
class RendererHeadlessTransparencyTest {

    @Test
    fun transparentDrawBlendsAgainstTheOpaqueDrawBehindIt() = withHeadlessRenderer { renderer ->
        val target = renderer.createRenderTarget(TARGET_SIZE, TARGET_SIZE)
        val pixels = renderer.renderQuads(target, overlayZ = NEAR_Z)

        val overlap = pixels.pixelAt(CENTER, CENTER)
        val backdropOnly = pixels.pixelAt(BACKDROP_ONLY_X, CENTER)

        assertTrue(
            backdropOnly.blue > STRONG && backdropOnly.red < FAINT,
            "sanity: the backdrop-only sample should be the blue quad alone, was $backdropOnly. " +
                    "If this fails the sampled coordinates no longer match the quad sizes, and the " +
                    "blend assertion below would be reading the wrong pixel.",
        )
        assertTrue(
            overlap.isHalfway(RED_OVER_BLUE),
            "a transparent draw must blend with the opaque quad behind it, so the overlap pixel " +
                    "should be the even mix $RED_OVER_BLUE, but was $overlap. A pure red " +
                    "(255, 0, 0) means the draw resolved to the opaque pipeline instead of " +
                    "PipelineTable.transparentByFormat; anything else means the blend factors " +
                    "are no longer straight src-alpha.",
        )
    }

    /**
     * Depth *test* stays on for a transparent draw -- only depth *write* is disabled. A
     * transparent surface behind opaque geometry is still hidden by it, which is what stops
     * `depthWriteEnabled = false` from turning into "transparent draws ignore depth entirely".
     */
    @Test
    fun transparentDrawBehindOpaqueGeometryIsStillDepthTestedAway() =
        withHeadlessRenderer { renderer ->
            val target = renderer.createRenderTarget(TARGET_SIZE, TARGET_SIZE)
            val pixels = renderer.renderQuads(target, overlayZ = BEHIND_Z)

            val overlap = pixels.pixelAt(CENTER, CENTER)
            assertTrue(
                overlap.red < FAINT,
                "the transparent quad sits behind the opaque one, so depth test should reject it " +
                        "entirely and leave the backdrop untouched, but the overlap pixel was " +
                        "$overlap -- red present means depth testing was disabled along with " +
                        "depth writing.",
            )
        }

    /** Renders the blue opaque backdrop, then the half-transparent red overlay at [overlayZ]. */
    private fun Renderer.renderQuads(
        target: RenderTarget,
        overlayZ: Float,
    ): ByteArray {
        var backdropMesh: RenderMesh? = null
        var overlayMesh: RenderMesh? = null
        var material: RenderMaterial? = null
        try {
            val backdrop = createMesh(quad(BACKDROP_HALF, FAR_Z, BLUE, alpha = 1f))
                .also { backdropMesh = it }
            val overlay = createMesh(quad(OVERLAY_HALF, overlayZ, RED, alpha = 0.5f))
                .also { overlayMesh = it }
            val sharedMaterial = createMaterial().also { material = it }
            renderToTexture(
                target,
                Lens(
                    eye = Vec3f(0f, 0f, EYE_Z),
                    center = Vec3f(0f, 0f, 0f),
                    fovYRadians = 1f,
                    near = 0.1f,
                    far = 10f,
                ),
                listOf(
                    DrawCall(backdrop, sharedMaterial),
                    DrawCall(overlay, sharedMaterial, transparent = true),
                ),
            )
            return runBlocking { readPixels(target) }.data
        } finally {
            backdropMesh?.destroy()
            overlayMesh?.destroy()
            material?.destroy()
        }
    }

    private data class Pixel(val red: Int, val green: Int, val blue: Int) {
        /** Every channel within [CHANNEL_TOLERANCE] of [expected] -- the slack absorbs the
         * rounding of a half-alpha blend into 8-bit channels, nothing wider. */
        fun isHalfway(expected: Pixel) =
            kotlin.math.abs(red - expected.red) <= CHANNEL_TOLERANCE &&
                    kotlin.math.abs(green - expected.green) <= CHANNEL_TOLERANCE &&
                    kotlin.math.abs(blue - expected.blue) <= CHANNEL_TOLERANCE
    }

    private fun ByteArray.pixelAt(x: Int, y: Int): Pixel {
        val offset = (y * TARGET_SIZE + x) * BYTES_PER_PIXEL
        return Pixel(
            this[offset].toInt() and 0xFF,
            this[offset + 1].toInt() and 0xFF,
            this[offset + 2].toInt() and 0xFF,
        )
    }

    /**
     * A camera-facing square of side `2 * halfSize` at [z], as `VertexFormat.PositionColorUv`
     * (position vec3, colour vec3, uv vec2). [alpha] rides in `uv.x`, which is what
     * `transparent_probe.wgsl` returns as the fragment's alpha.
     */
    private fun quad(halfSize: Float, z: Float, color: Triple<Float, Float, Float>, alpha: Float) =
        MeshGeometry(
            floatArrayOf(
                -halfSize, -halfSize, z, color.first, color.second, color.third, alpha, 0f,
                halfSize, -halfSize, z, color.first, color.second, color.third, alpha, 0f,
                halfSize, halfSize, z, color.first, color.second, color.third, alpha, 1f,
                -halfSize, halfSize, z, color.first, color.second, color.third, alpha, 1f,
            ),
            intArrayOf(0, 1, 2, 2, 3, 0),
        )

    /**
     * The shared headless renderer, built once per JVM.
     *
     * Not torn down between tests, and deliberately so: `RendererHeadlessUiGlyphBaselineTest`'s
     * own fixture documents that destroying and recreating a validation-enabled headless
     * instance twice in one JVM is flaky on MoltenVK, and this test hit exactly that -- the
     * second test's `vkCreateInstance` failed with `VK_ERROR_EXTENSION_NOT_PRESENT`. That
     * fixture can't be reused directly here: its `PipelineTable` has no
     * [PipelineTable.transparentByFormat] entry, which is the thing under test.
     */
    private fun withHeadlessRenderer(block: (Renderer) -> Unit) = block(sharedRenderer())

    private companion object {
        private var cachedRenderer: Renderer? = null

        /**
         * The primary pipeline plus the alpha-blended companion registered under
         * [VertexFormat.PositionColorUv] -- the entry `Renderer.pipelineFor` looks up for a
         * transparent draw. Both are built from `transparent_probe.wgsl`, so the only
         * difference between them is the [PipelineVariant].
         */
        fun sharedRenderer(): Renderer {
            cachedRenderer?.let { return it }

            val graphicsDevice = GraphicsDevice()
            graphicsDevice.createHeadless()
            val swapchainManager = SwapchainManager(graphicsDevice, MAX_FRAMES_IN_FLIGHT)
            swapchainManager.createHeadless(TARGET_SIZE, TARGET_SIZE)
            val pipelineLayoutMaterial = Material(graphicsDevice)
            val sceneRenderPass = createSceneRenderPass(graphicsDevice, swapchainManager)
            val probeShaders = runBlocking { pair("transparent_probe") }
            fun buildPipeline(variant: PipelineVariant) = RenderPipeline(
                graphicsDevice,
                swapchainManager,
                sceneRenderPass,
                pipelineLayoutMaterial.descriptorSetLayout,
                probeShaders,
                VertexFormat.PositionColorUv,
                vertexEntryPoint = "vertexMain",
                fragmentEntryPoint = "fragmentMain",
                variant = variant,
            )
            val lineRenderPipeline = LineRenderPipeline(
                graphicsDevice,
                swapchainManager,
                sceneRenderPass,
                runBlocking { pair("debug_line") },
                MAX_FRAMES_IN_FLIGHT,
            )
            return Renderer(
                graphicsDevice = graphicsDevice,
                swapchainManager = swapchainManager,
                pipelines = PipelineTable(
                    primary = buildPipeline(PipelineVariant.Opaque),
                    transparentByFormat = mapOf(
                        VertexFormat.PositionColorUv to buildPipeline(PipelineVariant.AlphaBlended),
                    ),
                ),
                renderFeatures = listOf(
                    OpaqueRenderFeature(VulkanLinePass(lineRenderPipeline)),
                    UiRenderFeature(VulkanUiPass()),
                ),
                transferContext = TransferContext(graphicsDevice),
                uiShaderPairs = runBlocking {
                    UiShaderPairs(
                        quad = pair("ui_quad"),
                        glyph = pair("ui_glyph"),
                        texture = pair("ui_texture"),
                        roundedQuad = pair("ui_rounded_quad"),
                    )
                },
                maxFramesInFlight = MAX_FRAMES_IN_FLIGHT,
            ).also { cachedRenderer = it }
        }

        private suspend fun pair(name: String) = ShaderPair(
            readResourceBytes("assets/shader/vulkan/$name.vert.spv"),
            readResourceBytes("assets/shader/vulkan/$name.frag.spv"),
        )

        const val TARGET_SIZE = 128
        const val MAX_FRAMES_IN_FLIGHT = 1
        const val BYTES_PER_PIXEL = 4
        const val CENTER = TARGET_SIZE / 2

        /** Inside the backdrop quad's screen extent but outside the overlay's. */
        const val BACKDROP_ONLY_X = CENTER + 44

        const val EYE_Z = 3f
        const val FAR_Z = -0.5f
        const val NEAR_Z = 0.5f
        const val BEHIND_Z = -1.5f
        const val BACKDROP_HALF = 1.5f
        const val OVERLAY_HALF = 0.8f

        /** A channel this high can only come from a quad that actually painted. */
        const val STRONG = 128

        /** Above this a channel is a real contribution, not clear-colour or rounding. */
        const val FAINT = 24

        const val CHANNEL_TOLERANCE = 2

        /** Half-alpha red composited over opaque blue, measured on MoltenVK. */
        val RED_OVER_BLUE = Pixel(128, 0, 128)

        val BLUE = Triple(0f, 0f, 1f)
        val RED = Triple(1f, 0f, 0f)
    }
}
