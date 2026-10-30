/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.fillMaxSize
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.clip
import com.awakekt.awake.compose.ui.draw.graphicsLayer
import com.awakekt.awake.compose.ui.graphics.CircleShape
import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.compose.ui.graphics.RenderEffect
import com.awakekt.awake.compose.ui.graphics.TileMode
import com.awakekt.awake.compose.ui.graphics.drawscope.GraphicsLayerFrame
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.compose.GraphicsLayerCompositor
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.canvas.CanvasSceneComponents
import com.awakekt.awake.scene.canvas.SceneCanvas
import com.awakekt.awake.scene.canvas.SceneCanvasAppearance
import com.awakekt.awake.scene.canvas.SceneCanvasParticles
import com.awakekt.awake.scene.canvas.SceneCanvasRect
import com.awakekt.awake.scene.canvas.canvasElements
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneColor
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.ui.canvas.CanvasAnchor
import com.awakekt.awake.ui.canvas.CanvasStyle
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Headless Vulkan and WebGPU pixel parity verification for `CanvasParticles` / `SceneCanvasParticles`,
 * composited through `SceneCanvas`, `GraphicsLayerCompositor`, and each backend's UI pass.
 *
 * **Tolerances:**
 * - Cross-backend agreement (`CHANNEL_TOLERANCE = 8`, `MAX_DIFFERING_FRACTION = 0.01f`) matches
 *   [UiBackendParityTest]: coverage (`A`) across all pixels and opaque `RGB` channels agree within 8
 *   levels across >= 99% of the `128 × 128` target.
 * - Solid / fully-lit channels require `>= ON_CHANNEL` (`245`); zeroed / occluded / clipped channels
 *   require `<= OFF_CHANNEL` (`8`); half-faded (`0.5`) and quarter-faded (`0.25`) premultiplied
 *   channels allow `±6` (`HALF_MIN..HALF_MAX = 122..134`, `QUARTER_MIN..QUARTER_MAX = 58..70`).
 *
 * **Scenarios & negative controls:**
 * 1. **Follow & `scale = 2f`:** Moving an element from `(12, 16)` to `(42, 20)` moves its
 *    `localSpace = true` particle from `(26, 24)` to `(56, 28)` (leaving `(26, 24)` unpainted);
 *    rendering at `scale = 2f` places the particle at `(112, 56)` (`16 × 16` px), while the
 *    `scale = 1f` negative control leaves `(112, 56)` completely unpainted (`A = 0`).
 * 2. **Sibling `order`:** An emitter on an `order = 0` element paints over an `order = -1` blue
 *    panel at `(44, 56)` and is occluded by an `order = +1` green panel at `(66, 56)`; the
 *    `order = -2` negative control hides `(44, 56)` behind the blue panel.
 * 3. **Mask clip & `clipped = false` escape:** Inside a `64 × 64` `CircleShape` container, a
 *    `clipped = true` negative control clips away both the circle's outside corner `(6, 6)` and
 *    outside-container `(80, 32)` (`A = 0`), while `clipped = false` paints both (`R >= 245`).
 * 4. **Clean premultiplied fade without dark fringes:** A soft-alpha white particle texture
 *    (`RGBA = 255, 255, 255, 128`, premultiplied once on upload) inside a `CanvasStyle(alpha = 0.5f)`
 *    ancestor over an opaque white `(255, 255, 255)` panel stays pure bright white (`RGB >= 250`),
 *    whereas the double-multiplied straight-alpha negative control darkens white-over-white into a
 *    gray fringe (`RGB ~ 191 <= FRINGE_CEILING`); over black it reads `~64` (`0.25` premultiplied).
 * 5. **Unclipped inside a faded ancestor & offscreen `graphicsLayer` target bound:**
 *    - `clipped = false` inside a `64 × 64` `CircleShape` + `CanvasStyle(alpha = 0.5f)` ancestor
 *      escapes to `(80, 32)` over black while keeping the `0.5f` fade (`RGB in 122..134`, vs `<= 8`
 *      when `clipped = true` and `>= 245` when `alpha = 1f`).
 *    - Inside a `48 × 48` clamped offscreen `graphicsLayer` (`TileMode.Clamp`) at `(16, 16)`,
 *      `clipped = false` stays strictly bounded to the `48 × 48` layer target (`(80, 40)` has
 *      `A = 0`), whereas the unlayered control paints `(80, 40)` (`R >= 245`).
 */
class CanvasParticlesParityTest {
    private class CapturedUi(
        val name: String,
        val primitives: List<UiDrawPrimitive>,
        val layers: List<GraphicsLayerFrame>,
    )

    @Test
    fun canvasParticlesRenderConsistentlyWithNegativeControlsOnVulkanAndWebGpu() {
        val scenarios = buildAllScenarios()
        // Open Vulkan once before WebGPU so libwgpu_native's bundled Vulkan loader never collides.
        val capturesByBackend = BACKEND_ORDER.associateWith { backend ->
            openHeadlessUi(backend, SIZE).use { session ->
                val compositor = GraphicsLayerCompositor()
                val target = session.renderer.createRenderTarget(SIZE, SIZE)
                try {
                    scenarios.associate { scenario ->
                        val pixels = renderScenario(session.renderer, compositor, target, scenario)
                        writeReportPng(scenario.name, backend, pixels)
                        scenario.name to pixels
                    }
                } finally {
                    target.destroy()
                    compositor.dispose()
                }
            }
        }

        BACKEND_ORDER.forEach { backend ->
            val frames = capturesByBackend.getValue(backend)
            assertFollowAndScale(backend, frames)
            assertSiblingOrder(backend, frames)
            assertMaskClipAndEscape(backend, frames)
            assertPremultipliedFadeNoDarkFringe(backend, frames)
            assertFadedUnclippedAndLayerBound(backend, frames)
        }

        val vulkan = capturesByBackend.getValue(HeadlessUiBackend.Vulkan)
        val webGpu = capturesByBackend.getValue(HeadlessUiBackend.WebGpu)
        val divergences = scenarios.mapNotNull { scenario ->
            disagreement(vulkan.getValue(scenario.name), webGpu.getValue(scenario.name))
                ?.let { "${scenario.name}: $it" }
        }
        assertTrue(
            divergences.isEmpty(),
            "Vulkan and WebGPU disagreed on canvas particle scenarios:\n${divergences.joinToString("\n")}",
        )
    }

    private fun buildAllScenarios(): List<CapturedUi> =
        buildFollowAndScaleScenarios() +
            buildSiblingOrderScenarios() +
            buildClipEscapeScenarios() +
            buildPremultipliedFadeScenarios() +
            buildFadedUnclippedAndLayerScenarios()

    private fun buildFollowAndScaleScenarios(): List<CapturedUi> {
        fun worldMovedTo(x: Float, y: Float) = loadCanvas(
            listOf(
                SceneNode(
                    name = "Badge",
                    components = listOf(
                        SceneCanvasRect(anchor = CanvasAnchor.TopLeft, offsetX = 12f, offsetY = 16f, width = 20f, height = 16f),
                        SceneCanvasParticles(
                            anchor = CanvasAnchor.Center,
                            offsetX = 4f,
                            spawnRate = 0f,
                            speed = 0f,
                            size = 8f,
                            tint = SceneColor(1f, 0.25f, 0f, 1f),
                        ),
                    ),
                ),
            ),
        ).also { world ->
            val badge = world.canvasElements().getValue("Badge")
            checkNotNull(badge.particles).emit(1)
            badge.offsetX = x
            badge.offsetY = y
        }
        val movedFor1x = worldMovedTo(42f, 20f)
        val movedFor2x = worldMovedTo(42f, 20f)
        return listOf(
            captureUi("follow-moved-1x") { SceneCanvas(movedFor1x, scale = 1f) },
            captureUi("follow-moved-2x") { SceneCanvas(movedFor2x, scale = 2f) },
        )
    }

    private fun buildSiblingOrderScenarios(): List<CapturedUi> {
        fun orderWorld(emitterOrder: Int) = loadCanvas(
            listOf(
                SceneNode(
                    name = "Back",
                    components = listOf(
                        SceneCanvasRect(anchor = CanvasAnchor.TopLeft, offsetX = 16f, offsetY = 16f, width = 64f, height = 64f, order = -1),
                        SceneCanvasAppearance(background = "#0000FFFF"),
                    ),
                ),
                SceneNode(
                    name = "MidEmitter",
                    components = listOf(
                        SceneCanvasRect(anchor = CanvasAnchor.TopLeft, offsetX = 32f, offsetY = 32f, width = 48f, height = 48f, order = emitterOrder),
                        SceneCanvasParticles(
                            anchor = CanvasAnchor.Center,
                            spawnRate = 0f,
                            speed = 0f,
                            size = 40f,
                            tint = SceneColor(1f, 0f, 0f, 1f),
                        ),
                    ),
                ),
                SceneNode(
                    name = "Front",
                    components = listOf(
                        SceneCanvasRect(anchor = CanvasAnchor.TopLeft, offsetX = 56f, offsetY = 16f, width = 56f, height = 80f, order = 1),
                        SceneCanvasAppearance(background = "#00FF00FF"),
                    ),
                ),
            ),
        ).also { world ->
            checkNotNull(world.canvasElements().getValue("MidEmitter").particles).emit(1)
        }
        return listOf(
            captureUi("sibling-order-0") { SceneCanvas(orderWorld(emitterOrder = 0)) },
            captureUi("sibling-order-neg2-control") { SceneCanvas(orderWorld(emitterOrder = -2)) },
        )
    }

    private fun buildClipEscapeScenarios(): List<CapturedUi> {
        fun clipWorld(clipped: Boolean) = loadCanvas(
            listOf(
                SceneNode(
                    name = "Slot",
                    components = listOf(
                        SceneCanvasRect(anchor = CanvasAnchor.TopLeft, offsetX = 0f, offsetY = 0f, width = 64f, height = 64f),
                        SceneCanvasParticles(
                            anchor = CanvasAnchor.Center,
                            offsetX = 16f,
                            clipped = clipped,
                            spawnRate = 0f,
                            speed = 0f,
                            size = 88f,
                            tint = SceneColor(1f, 0f, 0f, 1f),
                        ),
                    ),
                ),
            ),
        ).also { world ->
            checkNotNull(world.canvasElements().getValue("Slot").particles).emit(1)
        }
        return listOf(
            captureUi("mask-clipped-control") {
                Box(Modifier.size(64.dp, 64.dp).clip(CircleShape)) { SceneCanvas(clipWorld(clipped = true)) }
            },
            captureUi("mask-unclipped") {
                Box(Modifier.size(64.dp, 64.dp).clip(CircleShape)) { SceneCanvas(clipWorld(clipped = false)) }
            },
        )
    }

    private fun buildPremultipliedFadeScenarios(): List<CapturedUi> {
        val softWhiteImages = mapOf("fx/soft_white.png" to solidBitmap(255, 255, 255, HALF_ALPHA_BYTE))
        fun fadeWorld(backdropHex: String) = loadCanvas(
            listOf(
                SceneNode(
                    name = "Backdrop",
                    components = listOf(
                        SceneCanvasRect(anchor = CanvasAnchor.TopLeft, offsetX = 0f, offsetY = 0f, width = SIZE.toFloat(), height = SIZE.toFloat(), order = -1),
                        SceneCanvasAppearance(background = backdropHex),
                    ),
                ),
                SceneNode(
                    name = "FadedHud",
                    components = listOf(
                        SceneCanvasRect(anchor = CanvasAnchor.TopLeft, offsetX = 24f, offsetY = 24f, width = 64f, height = 64f),
                        SceneCanvasAppearance(style = CanvasStyle(alpha = 0.5f)),
                    ),
                    children = listOf(
                        SceneNode(
                            name = "SoftGlow",
                            components = listOf(
                                SceneCanvasRect(anchor = CanvasAnchor.TopLeft, offsetX = 0f, offsetY = 0f, width = 64f, height = 64f),
                                SceneCanvasParticles(
                                    anchor = CanvasAnchor.Center,
                                    texture = "fx/soft_white.png",
                                    spawnRate = 0f,
                                    speed = 0f,
                                    size = 40f,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ).also { world ->
            checkNotNull(world.canvasElements().getValue("SoftGlow").particles).emit(1)
        }

        // Negative control: double-multiplied RGB (128, 128, 128, 128) drawn with straight-alpha
        // premultiplied = false over opaque white produces a ~191 gray fringe instead of 255 white.
        val doubleMultipliedControl = CapturedUi(
            name = "fade-fringe-negative-control",
            primitives = listOf(
                UiDrawPrimitive.Quad(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), Color.White),
                UiDrawPrimitive.Texture(
                    x = 36f,
                    y = 36f,
                    w = 40f,
                    h = 40f,
                    material = solidBitmap(HALF_ALPHA_BYTE, HALF_ALPHA_BYTE, HALF_ALPHA_BYTE, HALF_ALPHA_BYTE),
                    premultiplied = false,
                ),
            ),
            layers = emptyList(),
        )
        return listOf(
            captureUi("fade-over-white") { SceneCanvas(fadeWorld("#FFFFFFFF"), images = softWhiteImages) },
            captureUi("fade-over-black") { SceneCanvas(fadeWorld("#000000FF"), images = softWhiteImages) },
            doubleMultipliedControl,
        )
    }

    private fun buildFadedUnclippedAndLayerScenarios(): List<CapturedUi> {
        fun fadedEscapeWorld(clipped: Boolean, alpha: Float) = loadCanvas(
            listOf(
                SceneNode(
                    name = "FadedParent",
                    components = listOf(
                        SceneCanvasRect(anchor = CanvasAnchor.TopLeft, offsetX = 0f, offsetY = 0f, width = 64f, height = 64f),
                        SceneCanvasAppearance(style = CanvasStyle(alpha = alpha)),
                    ),
                    children = listOf(
                        SceneNode(
                            name = "BurstChild",
                            components = listOf(
                                SceneCanvasRect(anchor = CanvasAnchor.TopLeft, offsetX = 0f, offsetY = 0f, width = 64f, height = 64f),
                                SceneCanvasParticles(
                                    anchor = CanvasAnchor.Center,
                                    offsetX = 48f,
                                    clipped = clipped,
                                    spawnRate = 0f,
                                    speed = 0f,
                                    size = 24f,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ).also { world ->
            checkNotNull(world.canvasElements().getValue("BurstChild").particles).emit(1)
        }

        fun layerWorld() = loadCanvas(
            listOf(
                SceneNode(
                    name = "LayerBox",
                    components = listOf(
                        SceneCanvasRect(anchor = CanvasAnchor.TopLeft, offsetX = 0f, offsetY = 0f, width = 48f, height = 48f),
                        SceneCanvasParticles(
                            anchor = CanvasAnchor.Center,
                            clipped = false,
                            spawnRate = 0f,
                            speed = 0f,
                            size = 96f,
                            tint = SceneColor(1f, 0f, 0f, 1f),
                        ),
                    ),
                ),
            ),
        ).also { world ->
            checkNotNull(world.canvasElements().getValue("LayerBox").particles).emit(1)
        }

        val clampedBlur = RenderEffect.createBlurEffect(1f, 1f, TileMode.Clamp)
        return listOf(
            captureUi("faded-unclipped") {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    Box(Modifier.size(64.dp, 64.dp).clip(CircleShape)) {
                        SceneCanvas(fadedEscapeWorld(clipped = false, alpha = 0.5f))
                    }
                }
            },
            captureUi("faded-clipped-control") {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    Box(Modifier.size(64.dp, 64.dp).clip(CircleShape)) {
                        SceneCanvas(fadedEscapeWorld(clipped = true, alpha = 0.5f))
                    }
                }
            },
            captureUi("unfaded-unclipped-control") {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    Box(Modifier.size(64.dp, 64.dp).clip(CircleShape)) {
                        SceneCanvas(fadedEscapeWorld(clipped = false, alpha = 1f))
                    }
                }
            },
            captureUi("clamped-layer-bound") {
                Box(Modifier.padding(start = 16.dp, top = 16.dp)) {
                    Box(Modifier.size(48.dp, 48.dp).graphicsLayer(renderEffect = clampedBlur)) {
                        SceneCanvas(layerWorld())
                    }
                }
            },
            captureUi("unlayered-control") {
                Box(Modifier.padding(start = 16.dp, top = 16.dp)) {
                    Box(Modifier.size(48.dp, 48.dp)) {
                        SceneCanvas(layerWorld())
                    }
                }
            },
        )
    }

    private fun assertFollowAndScale(backend: HeadlessUiBackend, frames: Map<String, ByteArray>) {
        val at1x = frames.getValue("follow-moved-1x")
        val at2x = frames.getValue("follow-moved-2x")
        // Initial pre-move spot (26, 24) is unpainted; moved spot (56, 28) is painted at 1x.
        assertEquals(0, at1x.alphaAt(26, 24), "$backend 1x pre-move position must be empty")
        assertTrue(at1x.redAt(56, 28) >= ON_CHANNEL, "$backend 1x moved particle at (56, 28): ${at1x.rgbaAt(56, 28)}")
        // Negative control: 2x coordinate (112, 56) is unpainted at 1x, and painted at 2x!
        assertEquals(0, at1x.alphaAt(112, 56), "$backend 1x negative control at (112, 56) must be empty")
        assertEquals(0, at2x.alphaAt(56, 28), "$backend 2x must move particle off 1x spot (56, 28)")
        assertTrue(at2x.redAt(112, 56) >= ON_CHANNEL, "$backend 2x scaled particle at (112, 56): ${at2x.rgbaAt(112, 56)}")
    }

    private fun assertSiblingOrder(backend: HeadlessUiBackend, frames: Map<String, ByteArray>) {
        val order0 = frames.getValue("sibling-order-0")
        val orderNeg2 = frames.getValue("sibling-order-neg2-control")
        // At (44, 56): order=0 red particle paints over order=-1 blue panel; order=-2 control stays blue.
        assertTrue(
            order0.redAt(44, 56) >= ON_CHANNEL && order0.blueAt(44, 56) <= OFF_CHANNEL,
            "$backend order=0 particle over order=-1 panel at (44, 56): ${order0.rgbaAt(44, 56)}",
        )
        assertTrue(
            orderNeg2.blueAt(44, 56) >= ON_CHANNEL && orderNeg2.redAt(44, 56) <= OFF_CHANNEL,
            "$backend order=-2 negative control behind order=-1 panel at (44, 56): ${orderNeg2.rgbaAt(44, 56)}",
        )
        // At (66, 56): order=+1 green panel occludes order=0 red particle.
        assertTrue(
            order0.greenAt(66, 56) >= ON_CHANNEL && order0.redAt(66, 56) <= OFF_CHANNEL,
            "$backend order=+1 panel over order=0 particle at (66, 56): ${order0.rgbaAt(66, 56)}",
        )
    }

    private fun assertMaskClipAndEscape(backend: HeadlessUiBackend, frames: Map<String, ByteArray>) {
        val clipped = frames.getValue("mask-clipped-control")
        val unclipped = frames.getValue("mask-unclipped")
        assertTrue(clipped.redAt(32, 32) >= ON_CHANNEL, "$backend clipped interior (32, 32)")
        assertEquals(0, clipped.alphaAt(6, 6), "$backend clipped circle corner (6, 6) must be masked")
        assertEquals(0, clipped.alphaAt(80, 32), "$backend clipped outside (80, 32) must be masked")
        assertTrue(unclipped.redAt(32, 32) >= ON_CHANNEL, "$backend unclipped interior (32, 32)")
        assertTrue(unclipped.redAt(6, 6) >= ON_CHANNEL, "$backend unclipped escapes circle corner (6, 6)")
        assertTrue(unclipped.redAt(80, 32) >= ON_CHANNEL, "$backend unclipped escapes container bounds (80, 32)")
    }

    private fun assertPremultipliedFadeNoDarkFringe(backend: HeadlessUiBackend, frames: Map<String, ByteArray>) {
        val overWhite = frames.getValue("fade-over-white")
        val overBlack = frames.getValue("fade-over-black")
        val fringeControl = frames.getValue("fade-fringe-negative-control")
        val whiteSample = overWhite.rgbaAt(56, 56)
        assertTrue(
            whiteSample[0] >= PURE_WHITE_MIN && whiteSample[1] >= PURE_WHITE_MIN && whiteSample[2] >= PURE_WHITE_MIN,
            "$backend faded soft-alpha white particle over white must stay fringe-free (>= $PURE_WHITE_MIN), got $whiteSample",
        )
        val fringeSample = fringeControl.rgbaAt(56, 56)
        assertTrue(
            fringeSample[0] <= FRINGE_CEILING,
            "$backend double-multiplied negative control must show gray fringe (<= $FRINGE_CEILING), got $fringeSample",
        )
        val blackSample = overBlack.rgbaAt(56, 56)
        assertTrue(
            blackSample[0] in QUARTER_MIN..QUARTER_MAX,
            "$backend 0.5 texel * 0.5 ancestor over black expected $QUARTER_MIN..$QUARTER_MAX, got $blackSample",
        )
    }

    private fun assertFadedUnclippedAndLayerBound(backend: HeadlessUiBackend, frames: Map<String, ByteArray>) {
        val fadedUnclipped = frames.getValue("faded-unclipped")
        val fadedClipped = frames.getValue("faded-clipped-control")
        val unfadedUnclipped = frames.getValue("unfaded-unclipped-control")
        assertTrue(
            fadedUnclipped.redAt(80, 32) in HALF_MIN..HALF_MAX,
            "$backend unclipped inside 0.5 faded ancestor at (80, 32) expected $HALF_MIN..$HALF_MAX, got ${fadedUnclipped.rgbaAt(80, 32)}",
        )
        assertTrue(
            fadedClipped.redAt(80, 32) <= OFF_CHANNEL,
            "$backend clipped control at (80, 32) must stay black, got ${fadedClipped.rgbaAt(80, 32)}",
        )
        assertTrue(
            unfadedUnclipped.redAt(80, 32) >= ON_CHANNEL,
            "$backend unfaded control at (80, 32) must be full white, got ${unfadedUnclipped.rgbaAt(80, 32)}",
        )

        val clampedBound = frames.getValue("clamped-layer-bound")
        val unlayeredControl = frames.getValue("unlayered-control")
        assertTrue(clampedBound.redAt(40, 40) >= ON_CHANNEL, "$backend inside clamped graphicsLayer (40, 40)")
        assertEquals(0, clampedBound.alphaAt(80, 40), "$backend clipped=false must not escape offscreen graphicsLayer target at (80, 40)")
        assertTrue(unlayeredControl.redAt(80, 40) >= ON_CHANNEL, "$backend unlayered control paints (80, 40)")
    }

    private fun captureUi(name: String, content: context(Composer) () -> Unit): CapturedUi {
        val output = ComposeHost().frame(FrameInput(SIZE, SIZE, deltaSeconds = 0f), content)
        return CapturedUi(
            name = name,
            primitives = output.primitives.toList(),
            layers = output.graphicsLayers.map { it.copy(primitives = it.primitives.toList()) },
        )
    }

    private fun loadCanvas(nodes: List<SceneNode>): World =
        World().also { world -> nodes.forEach { world.loadCanvasNode(it, parent = null) } }

    private fun World.loadCanvasNode(node: SceneNode, parent: Entity?): Entity {
        val entity = create()
        add(entity, Transform(parent = parent))
        node.name?.let { add(entity, Name(it)) }
        val context = object : SceneResolutionContext {
            override val world: World = this@loadCanvasNode
            override fun deferNodeLink(targetNodeName: String, onResolved: (target: Entity) -> Unit) = Unit
            override fun recordRequest(request: Any) = Unit
        }
        for (component in node.components) CANVAS_REGISTRY.resolve(this, entity, component, context)
        for (child in node.children) loadCanvasNode(child, entity)
        return entity
    }

    private fun renderScenario(
        renderer: Renderer,
        compositor: GraphicsLayerCompositor,
        target: com.awakekt.awake.render.texture.RenderTarget,
        scenario: CapturedUi,
    ): ByteArray {
        val resolved = compositor.composite(
            renderer = renderer,
            primitives = scenario.primitives,
            layers = scenario.layers,
            font = UiFonts.default(),
            viewportWidth = SIZE,
            viewportHeight = SIZE,
        )
        renderer.drawUiToTexture(target, resolved, font = null)
        return runBlocking { renderer.readPixels(target) }.data
    }

    private fun disagreement(vulkan: ByteArray, webGpu: ByteArray): String? {
        var coverageDiffs = 0
        var opaqueColourDiffs = 0
        for (offset in vulkan.indices step 4) {
            val va = vulkan[offset + 3].toInt() and BYTE_MASK
            val wa = webGpu[offset + 3].toInt() and BYTE_MASK
            if (abs(va - wa) > CHANNEL_TOLERANCE) coverageDiffs++
            if (va == OPAQUE && wa == OPAQUE) {
                val differs = (0..2).any { c ->
                    abs((vulkan[offset + c].toInt() and BYTE_MASK) - (webGpu[offset + c].toInt() and BYTE_MASK)) > CHANNEL_TOLERANCE
                }
                if (differs) opaqueColourDiffs++
            }
        }
        val total = vulkan.size / 4
        return when {
            coverageDiffs.toFloat() / total > MAX_DIFFERING_FRACTION -> "$coverageDiffs px differ in alpha"
            opaqueColourDiffs.toFloat() / total > MAX_DIFFERING_FRACTION -> "$opaqueColourDiffs opaque px differ in RGB"
            else -> null
        }
    }

    private fun solidBitmap(r: Int, g: Int, b: Int, a: Int): ImageBitmap {
        val bytes = ByteArray(TEX_SIDE * TEX_SIDE * 4)
        for (i in bytes.indices step 4) {
            bytes[i] = r.toByte()
            bytes[i + 1] = g.toByte()
            bytes[i + 2] = b.toByte()
            bytes[i + 3] = a.toByte()
        }
        return ImageBitmap(TEX_SIDE, TEX_SIDE, bytes)
    }

    private fun writeReportPng(name: String, backend: HeadlessUiBackend, pixels: ByteArray) {
        val image = BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                val rgba = pixels.rgbaAt(x, y)
                image.setRGB(x, y, (rgba[3] shl 24) or (rgba[0] shl 16) or (rgba[1] shl 8) or rgba[2])
            }
        }
        val dir = File(REPORT_DIR).apply { mkdirs() }
        ImageIO.write(image, "png", File(dir, "canvas-particles-$name-${backend.name.lowercase()}.png"))
    }

    private fun ByteArray.rgbaAt(x: Int, y: Int): List<Int> {
        val base = (y * SIZE + x) * 4
        return listOf(
            this[base].toInt() and BYTE_MASK,
            this[base + 1].toInt() and BYTE_MASK,
            this[base + 2].toInt() and BYTE_MASK,
            this[base + 3].toInt() and BYTE_MASK,
        )
    }

    private fun ByteArray.redAt(x: Int, y: Int): Int = rgbaAt(x, y)[0]
    private fun ByteArray.greenAt(x: Int, y: Int): Int = rgbaAt(x, y)[1]
    private fun ByteArray.blueAt(x: Int, y: Int): Int = rgbaAt(x, y)[2]
    private fun ByteArray.alphaAt(x: Int, y: Int): Int = rgbaAt(x, y)[3]

    private companion object {
        val CANVAS_REGISTRY = SceneComponentRegistry(bindings = CanvasSceneComponents.bindings)
        val BACKEND_ORDER = listOf(HeadlessUiBackend.Vulkan, HeadlessUiBackend.WebGpu)
        const val REPORT_DIR = "build/reports/render-parity"
        const val SIZE = 128
        const val TEX_SIDE = 4
        const val BYTE_MASK = 0xFF
        const val OPAQUE = 255
        const val HALF_ALPHA_BYTE = 128
        const val ON_CHANNEL = 245
        const val PURE_WHITE_MIN = 250
        const val FRINGE_CEILING = 200
        const val OFF_CHANNEL = 8
        const val HALF_MIN = 122
        const val HALF_MAX = 134
        const val QUARTER_MIN = 58
        const val QUARTER_MAX = 70
        const val CHANNEL_TOLERANCE = 8
        const val MAX_DIFFERING_FRACTION = 0.01f
    }
}
