/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.render.passes.uniforms.skinnedMaterialFloats
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.TextureAnimation
import com.awakekt.awake.render.testing.HeadlessRenderSession
import com.awakekt.awake.render.texture.TextureAsset
import org.junit.AfterClass
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.pow
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A lit, shadowed scene through both backends, compared as a picture.
 *
 * The harness's UI scenarios caught two divergences the day they were written. This is the case
 * they could not reach: WebGPU sampled the shadow map along the wrong V axis for as long as the
 * shader existed, so every caster's shadow fell on the opposite side of the ground from the caster.
 * Vulkan's rendering of this same scene has had coverage the whole time; nothing compared them, and
 * a mirrored shadow is a perfectly plausible-looking picture on its own.
 *
 * Compares where the shadow *is*, not the exact shading. The two backends resolve a PBR fragment to
 * slightly different values, and holding them to that would be a test about floating point rather
 * than about geometry.
 */
class SceneBackendParityTest {

    /**
     * A material without a metallic-roughness map takes its factors as they are: a metallic plane
     * reads clearly differently from a dielectric one. The neutral map used to hold metallic at 0,
     * which multiplied any factor away and drew the two the same.
     */
    @Test
    fun aMetallicFactorTakesEffectWithoutAMapOnBothBackends() {
        BACKEND_ORDER.forEach { backend ->
            val renderer = session(backend).renderer
            val dielectric = renderer.renderTexturedPbrScene(metallic = 0f, roughness = 1f).also { write(backend, it, "pbr-dielectric") }
            val metal = renderer.renderTexturedPbrScene(metallic = 1f, roughness = 1f).also { write(backend, it, "pbr-metal") }
            fun brightness(pixels: ByteArray) = pixels.indices.step(4).sumOf { (pixels[it].toInt() and 0xFF) + (pixels[it + 1].toInt() and 0xFF) + (pixels[it + 2].toInt() and 0xFF) }

            val change = abs(brightness(dielectric) - brightness(metal)).toDouble() / brightness(dielectric)
            assertTrue(change > MIN_METALLIC_CHANGE, "$backend: metallic 1 changed the frame's brightness by ${"%.3f".format(change)}")
        }
    }

    @Test
    fun texturedPbrDrawUsesTheSameCoverageOnBothBackends() {
        val covered = BACKEND_ORDER.associateWith { backend ->
            val session = session(backend)
            val pixels = session.renderer.renderTexturedPbrScene()
            pixels.indices.step(4).count { index ->
                (pixels[index].toInt() and 0xFF) > TEXTURED_RED_THRESHOLD
            }
        }

        val vulkan = covered.getValue(HeadlessUiBackend.Vulkan)
        val webGpu = covered.getValue(HeadlessUiBackend.WebGpu)
        assertTrue(vulkan >= MIN_TEXTURED_PIXELS, "Vulkan textured PBR draw was empty: $vulkan pixels")
        assertTrue(webGpu >= MIN_TEXTURED_PIXELS, "WebGPU textured PBR draw was empty: $webGpu pixels")
        assertTrue(
            kotlin.math.abs(vulkan - webGpu) <= TEXTURED_COVERAGE_TOLERANCE,
            "textured PBR coverage diverged: Vulkan $vulkan px, WebGPU $webGpu px",
        )
    }

    /**
     * A textured skinned mesh draws its texture where its joint puts it: the identity joint shows
     * the orange plane, a zero joint collapses it, so neither the texture nor the palette is ignored.
     */
    @Test
    fun aTexturedSkinnedMeshShowsItsTextureOnBothBackends() {
        val identity = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        BACKEND_ORDER.forEach { backend ->
            val renderer = session(backend).renderer
            fun orange(pixels: ByteArray) = pixels.indices.step(4).count { index ->
                (pixels[index].toInt() and 0xFF).let { red -> red > TEXTURED_RED_THRESHOLD && red > 2 * (pixels[index + 2].toInt() and 0xFF) }
            }

            val shown = orange(renderer.renderTexturedSkinnedScene(identity))
            val collapsed = orange(renderer.renderTexturedSkinnedScene(FloatArray(identity.size)))

            assertTrue(shown >= MIN_TEXTURED_PIXELS, "$backend: the textured skinned plane drew $shown orange pixels")
            assertEquals(0, collapsed, "$backend: a zero joint must collapse the plane")
        }
    }

    /** A skinned part's material tints its texture on both backends: white turns red, and stays white untinted. */
    @Test
    fun aSkinnedMeshTakesItsMaterialsTintOnBothBackends() {
        val identity = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        BACKEND_ORDER.forEach { backend ->
            val renderer = session(backend).renderer
            fun red(pixels: ByteArray) = pixels.indices.step(4).count { index ->
                val r = pixels[index].toInt() and 0xFF
                r > TEXTURED_RED_THRESHOLD && r > 2 * (pixels[index + 1].toInt() and 0xFF) && r > 2 * (pixels[index + 2].toInt() and 0xFF)
            }

            val tinted = red(renderer.renderTexturedSkinnedScene(skinnedMaterialFloats(identity, Color(1f, 0f, 0f, 1f), Color.Transparent), SolidWhite))
            val untinted = red(renderer.renderTexturedSkinnedScene(identity, SolidWhite))

            assertTrue(tinted >= MIN_TEXTURED_PIXELS, "$backend: the red-tinted skinned plane drew $tinted red pixels")
            assertEquals(0, untinted, "$backend: an untinted white plane must not read as red")
        }
    }

    /** A skinned part's texture lands the same way up as a prop's: its top row at the same edge. */
    @Test
    fun aTexturedSkinnedMeshHoldsItsTextureTheWayUpAPropDoes() {
        val identity = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        BACKEND_ORDER.forEach { backend ->
            val renderer = session(backend).renderer
            val prop = renderer.renderTexturedPbrScene(texture = RED_OVER_BLUE)
            val skinned = renderer.renderTexturedSkinnedScene(identity, texture = RED_OVER_BLUE)

            assertEquals(prop.redIsAboveBlue(), skinned.redIsAboveBlue(), "$backend: the skinned texture is flipped against the prop's")
        }
    }

    /** Whether the red-dominant pixels sit above (a smaller mean row than) the blue-dominant ones. */
    private fun ByteArray.redIsAboveBlue(): Boolean {
        fun meanRow(dominant: Int, other: Int): Double = (0 until SCENE_SIZE * SCENE_SIZE)
            .filter { channel(it, dominant) > 2 * channel(it, other) + TEXTURED_RED_THRESHOLD }
            .map { it / SCENE_SIZE }
            .average()
        val red = meanRow(RED, BLUE)
        val blue = meanRow(BLUE, RED)
        assertTrue(!red.isNaN() && !blue.isNaN(), "both halves of the texture must show")
        return red < blue
    }

    /**
     * A texture is sRGB: lit only by ambient, mid-grey 128 comes out as
     * encode(neutral(decode(128) x 0.08)), about 15, not the 32 that lighting the raw bytes gives.
     */
    @Test
    fun anAmbientLitTextureKeepsItsShadeOnBothBackends() {
        BACKEND_ORDER.forEach { backend ->
            val pixels = session(backend).renderer.renderTexturedPbrScene(
                texture = TextureAsset(ByteArray(2 * 2 * 4) { if (it % 4 == 3) -1 else MID_GREY.toByte() }, 2, 2),
                sunDirection = Vec3f(0f, -1f, 0f),
            )
            val red = pixels[(SCENE_SIZE / 2 * SCENE_SIZE + SCENE_SIZE / 2) * 4].toInt() and 0xFF
            val shade = if (backend == HeadlessUiBackend.WebGpu) SRGB_TO_LINEAR[red] else red
            assertTrue(kotlin.math.abs(shade - AMBIENT_SHADE) <= SHADE_TOLERANCE, "$backend ambient shade $shade, expected about $AMBIENT_SHADE")
        }
    }

    /** A scene's ambient replaces the shader's: at 0.5, mid-grey 128 comes out as encode(neutral(decode(128) x 0.5)), about 76. */
    @Test
    fun aSceneAmbientSetsTheTexturedShadeOnBothBackends() {
        BACKEND_ORDER.forEach { backend ->
            val pixels = session(backend).renderer.renderTexturedPbrScene(
                texture = TextureAsset(ByteArray(2 * 2 * 4) { if (it % 4 == 3) -1 else MID_GREY.toByte() }, 2, 2),
                sunDirection = Vec3f(0f, -1f, 0f),
                ambient = 0.5f,
            )
            val red = pixels[(SCENE_SIZE / 2 * SCENE_SIZE + SCENE_SIZE / 2) * 4].toInt() and 0xFF
            val shade = if (backend == HeadlessUiBackend.WebGpu) SRGB_TO_LINEAR[red] else red
            assertTrue(kotlin.math.abs(shade - HALF_AMBIENT_SHADE) <= SHADE_TOLERANCE, "$backend shade $shade under ambient 0.5, expected about $HALF_AMBIENT_SHADE")
        }
    }

    /** Time picks the frame; frames play in reading order from the image's top-left and wrap. */
    @Test
    fun aFrameSheetPlaysItsFramesInReadingOrderOnBothBackends() {
        val sheet = TextureAnimation(columns = 2, rows = 2, framesPerSecond = 1f)
        BACKEND_ORDER.forEach { backend ->
            val renderer = session(backend).renderer
            val played = FRAME_TIMES.map { time ->
                renderer.renderTexturedPbrScene(texture = FRAME_SHEET, textureAnimation = sheet, timeSeconds = time)
                    .brightChannelsAt(SCENE_SIZE / 2, SCENE_SIZE / 2)
            }
            assertEquals((FRAME_COLOURS + FRAME_COLOURS.first()).map { it.brightChannels() }, played, "$backend frames")
        }
    }

    /** A run that starts at the sheet's third cell plays cells 2 and 3 and loops inside them, as on every backend. */
    @Test
    fun aFrameSheetRunCanStartAtAnyFrameOnBothBackends() {
        val run = TextureAnimation(columns = 2, rows = 2, frameCount = 2, framesPerSecond = 1f, firstFrame = 2)
        BACKEND_ORDER.forEach { backend ->
            val renderer = session(backend).renderer
            val played = FRAME_TIMES.take(3).map { time ->
                renderer.renderTexturedPbrScene(texture = FRAME_SHEET, textureAnimation = run, timeSeconds = time)
                    .brightChannelsAt(SCENE_SIZE / 2, SCENE_SIZE / 2)
            }
            assertEquals(
                listOf(FRAME_COLOURS[2], FRAME_COLOURS[3], FRAME_COLOURS[2]).map { it.brightChannels() },
                played,
                "$backend run",
            )
        }
    }

    @Test
    fun genericEnvironmentFogAffectsTexturedDrawOnBothBackends() {
        val changes = BACKEND_ORDER.associateWith { backend ->
            val session = session(backend)
            val clear = session.renderer.renderTexturedPbrScene()
            val fogged = session.renderer.renderTexturedPbrScene(
                EnvironmentUniforms.Default.copy(
                    fogColor = Color.Black,
                    fogDensity = FOG_DENSITY,
                    shadowsEnabled = false,
                ),
            )
            redSum(backend, clear) to redSum(backend, fogged)
        }

        changes.forEach { (backend, sums) ->
            assertTrue(sums.first > MIN_FOGGED_RED_SUM, "$backend clear textured draw was empty")
            assertTrue(
                sums.second < sums.first * FOG_REMAINING_FRACTION,
                "$backend generic fog did not reduce the textured draw: clear=${sums.first}, fogged=${sums.second}",
            )
        }
        val clearSums = changes.values.map { it.first }
        val foggedSums = changes.values.map { it.second }
        assertTrue(
            kotlin.math.abs(clearSums[0] - clearSums[1]) <= RED_SUM_TOLERANCE,
            "clear textured environment diverged across backends: $clearSums",
        )
        assertTrue(
            kotlin.math.abs(foggedSums[0] - foggedSums[1]) <= RED_SUM_TOLERANCE,
            "fogged textured environment diverged across backends: $foggedSums",
        )
    }

    @Test
    fun backFaceCullingPreservesFrontFacingSurfacesOnBothBackends() {
        val frontCounts = BACKEND_ORDER.associateWith { backend ->
            val session = session(backend)
            val pixels = session.renderer.renderBackCulledScene(cullBack = true, viewFromAbove = true)
            pixels.indices.step(4).count { index ->
                (pixels[index].toInt() and 0xFF) > 10 ||
                    (pixels[index + 1].toInt() and 0xFF) > 10 ||
                    (pixels[index + 2].toInt() and 0xFF) > 10
            }
        }
        val vulkan = frontCounts.getValue(HeadlessUiBackend.Vulkan)
        val webGpu = frontCounts.getValue(HeadlessUiBackend.WebGpu)
        assertTrue(vulkan > 500, "Vulkan front face was culled or empty: $vulkan px")
        assertTrue(webGpu > 500, "WebGPU front face was culled or empty: $webGpu px")
        assertTrue(
            kotlin.math.abs(vulkan - webGpu) <= 100,
            "front face coverage diverged with CullMode.Back: Vulkan $vulkan px, WebGPU $webGpu px",
        )
    }

    @Test
    fun backFaceCullingDiscardsBackFacingSurfacesOnBothBackends() {
        val backCounts = BACKEND_ORDER.associateWith { backend ->
            val session = session(backend)
            val pixels = session.renderer.renderBackCulledScene(cullBack = true, viewFromAbove = false)
            pixels.indices.step(4).count { index ->
                (pixels[index].toInt() and 0xFF) > 10 ||
                    (pixels[index + 1].toInt() and 0xFF) > 10 ||
                    (pixels[index + 2].toInt() and 0xFF) > 10
            }
        }
        val vulkan = backCounts.getValue(HeadlessUiBackend.Vulkan)
        val webGpu = backCounts.getValue(HeadlessUiBackend.WebGpu)
        assertTrue(vulkan == 0, "Vulkan did not cull back face: $vulkan px drawn")
        assertTrue(webGpu == 0, "WebGPU did not cull back face: $webGpu px drawn")
    }

    /** A textured ground receives the sun's shadow where an untextured one does, on both backends. */
    @Test
    fun aTexturedSurfaceReceivesTheSameShadow() {
        BACKEND_ORDER.forEach { backend ->
            val renderer = session(backend).renderer
            val untextured = assertNotNull(renderer.renderShadowScene().shadowCentroid(), "$backend lit ground")
            val textured = assertNotNull(
                // Relative: the textured ground's shading varies across it by more than a fixed margin.
                renderer.renderShadowScene(texturedGround = true).also { write(backend, it, "textured-shadow-scene") }
                    .shadowCentroid(::shadowLevel),
                "$backend textured ground shows no shadow",
            )
            val drift = maxOf(kotlin.math.abs(untextured.first - textured.first), kotlin.math.abs(untextured.second - textured.second))
            assertTrue(drift <= CENTROID_TOLERANCE, "$backend: textured shadow at $textured, untextured at $untextured")
        }
    }

    /** A textured caster throws the shadow an untextured one does, on both backends. */
    @Test
    fun aTexturedCasterCastsTheSameShadow() {
        BACKEND_ORDER.forEach { backend ->
            val renderer = session(backend).renderer
            val untextured = assertNotNull(renderer.renderShadowScene().shadowCentroid(), "$backend lit ground")
            val textured = assertNotNull(
                renderer.renderShadowScene(texturedCaster = true).also { write(backend, it, "textured-caster-scene") }
                    .shadowCentroid(),
                "$backend textured caster casts no shadow",
            )
            val drift = maxOf(kotlin.math.abs(untextured.first - textured.first), kotlin.math.abs(untextured.second - textured.second))
            assertTrue(drift <= CENTROID_TOLERANCE, "$backend: textured caster's shadow at $textured, untextured at $untextured")
        }
    }

    /**
     * A textured skinned caster throws its shadow where a static one does, posed by its own joint
     * palette and placed by its model matrix. A zero joint collapses it, and it casts nothing.
     */
    @Test
    fun aTexturedSkinnedCasterCastsItsShadow() {
        val identity = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        BACKEND_ORDER.forEach { backend ->
            val renderer = session(backend).renderer
            val static = assertNotNull(renderer.renderShadowScene(texturedCaster = true).shadowCentroid(), "$backend static caster")
            val skinned = assertNotNull(
                renderer.renderSkinnedShadowScene(identity).also { write(backend, it, "skinned-caster-scene") }.shadowCentroid(),
                "$backend skinned caster casts no shadow",
            )
            val drift = maxOf(kotlin.math.abs(static.first - skinned.first), kotlin.math.abs(static.second - skinned.second))
            assertTrue(drift <= CENTROID_TOLERANCE, "$backend: skinned caster's shadow at $skinned, static at $static")
            assertNull(renderer.renderSkinnedShadowScene(FloatArray(identity.size)).shadowCentroid(), "$backend: a zero joint casts nothing")
        }
    }

    /** Copies folded into one instanced draw render, and cast shadows, as separate draws do. */
    @Test
    fun repeatedPropsDrawnAsInstancesMatchSeparateDraws() {
        BACKEND_ORDER.forEach { backend ->
            val renderer = session(backend).renderer
            val (separate, separateDraws) = renderer.renderRepeatedPropsScene(shareMaterial = false)
            val (instanced, instancedDraws) = renderer.renderRepeatedPropsScene(shareMaterial = true)
            write(backend, separate, "repeated-props-separate")
            write(backend, instanced, "repeated-props-instanced")

            assertEquals(6, separateDraws, "$backend: the ground and five posts")
            assertEquals(2, instancedDraws, "$backend did not fold the posts into one instanced draw")
            val differing = separate.indices.count { kotlin.math.abs((separate[it].toInt() and 0xFF) - (instanced[it].toInt() and 0xFF)) > 2 }
            assertEquals(0, differing, "$backend: instanced posts differ from separate ones in $differing channels")
        }
    }

    /** A render submitted without waiting reads back as a waited one does, on both backends. */
    @Test
    fun aSubmittedRenderReadsBackAsAWaitedOneDoes() {
        BACKEND_ORDER.forEach { backend ->
            val renderer = session(backend).renderer
            val (waited, _) = renderer.renderRepeatedPropsScene(shareMaterial = true)
            repeat(3) { pass ->
                val (submitted, _) = renderer.renderRepeatedPropsScene(shareMaterial = true, submit = true)
                assertTrue(waited.contentEquals(submitted), "$backend: submitted render $pass differs from the waited one")
            }
        }
    }

    /**
     * A masked caster's clear texels neither draw nor cast, static or skinned; opaque, the same card
     * draws and casts whole.
     */
    @Test
    fun aMaskedCasterCutsOutItsClearTexels() {
        val failures = BACKEND_ORDER.flatMap { backend ->
            val renderer = session(backend).renderer
            listOf(false, true).flatMap { skinned ->
                val caster = if (skinned) "skinned" else "static"
                val opaque = renderer.renderHalfClearCasterScene(masked = false, skinned).also { write(backend, it, "half-clear-opaque-$caster") }
                val masked = renderer.renderHalfClearCasterScene(masked = true, skinned).also { write(backend, it, "half-clear-masked-$caster") }

                val card = opaque.shadowedGroundPixels()
                val cutOut = masked.shadowedGroundPixels()
                // Where the opaque card drew its clear half black, masked shows the lit ground. Black
                // only: a caster that lost its whole shadow also brightens the ground it shadowed.
                val clearHalf = opaque.blackPixelsLitIn(masked)
                listOfNotNull(
                    "$backend $caster: masked shadow $cutOut px, the whole card's $card px".takeIf { card == 0 || cutOut !in card / 4..card * 3 / 4 },
                    "$backend $caster: masking showed the ground through $clearHalf card pixels".takeIf { clearHalf <= MIN_CLEAR_HALF_PIXELS },
                )
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    /** An additive quad brightens the ground under it; an alpha-blended one covers it. */
    @Test
    fun anAdditiveQuadAddsToTheGroundInsteadOfCoveringIt() {
        BACKEND_ORDER.forEach { backend ->
            val renderer = session(backend).renderer
            val ground = renderer.renderGlowScene(GlowBlend.None)
            val covered = renderer.renderGlowScene(GlowBlend.Alpha).also { write(backend, it, "glow-alpha") }
            val added = renderer.renderGlowScene(GlowBlend.Additive).also { write(backend, it, "glow-additive") }

            // Under the quad: where the alpha-blended red replaced the ground's green.
            val quad = (0 until SCENE_SIZE * SCENE_SIZE).filter { ground.channel(it, GREEN) - covered.channel(it, GREEN) > COVERED_GREEN }
            assertTrue(quad.size > MIN_QUAD_PIXELS, "$backend: the red quad covered only ${quad.size} px")
            val keptGreen = quad.count { added.channel(it, GREEN) >= ground.channel(it, GREEN) - CHANNEL_TOLERANCE }
            val addedRed = quad.count { added.channel(it, RED) > ground.channel(it, RED) }
            assertTrue(keptGreen == quad.size, "$backend: additive kept the ground's green in $keptGreen of ${quad.size} px")
            assertTrue(addedRed == quad.size, "$backend: additive raised red in $addedRed of ${quad.size} px")
        }
    }

    /**
     * An additive effect sprite adds its glow and nothing else: where its texture is black the
     * ground shows as it does without it, though the sun glints off it, and in fog too. A white
     * sprite shows the same quad does draw.
     */
    @Test
    fun anAdditiveSpriteAddsNothingWhereItsTextureIsBlackOnBothBackends() {
        val failures = BACKEND_ORDER.flatMap { backend ->
            val renderer = session(backend).renderer
            listOf(0f, FOG_DENSITY).flatMap { fog ->
                val ground = renderer.renderEffectSpriteScene(null, fog)
                val glowing = renderer.renderEffectSpriteScene(SolidWhite, fog)
                val black = renderer.renderEffectSpriteScene(SolidBlack, fog).also { write(backend, it, "effect-sprite-black-fog$fog") }
                val pixels = 0 until SCENE_SIZE * SCENE_SIZE

                val glow = pixels.count { pixel -> (0..2).any { glowing.channel(pixel, it) > ground.channel(pixel, it) } }
                val added = pixels.map { pixel -> (0..2).maxOf { black.channel(pixel, it) - ground.channel(pixel, it) } }
                val brightened = added.count { it > CHANNEL_TOLERANCE }
                listOfNotNull(
                    "$backend, fog $fog: the white sprite added to only $glow px".takeIf { glow <= MIN_QUAD_PIXELS },
                    "$backend, fog $fog: the black sprite brightened $brightened px, by up to ${added.max()}".takeIf { brightened > 0 },
                )
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    /**
     * A flat sprite lies in the ground plane: seen from above it covers pixels, seen edge-on none.
     * The camera-facing sprite drawn beside it from the same material shows from both views, so
     * each draw keeps its own quad axes rather than sharing the last draw's.
     */
    @Test
    fun aFlatSpriteLiesInItsPlaneBesideACameraFacingOneOnBothBackends() {
        BACKEND_ORDER.forEach { backend ->
            val renderer = session(backend).renderer
            val above = renderer.renderSpriteScene(SpriteView.Above).also { write(backend, it, "sprites-above") }
            val edgeOn = renderer.renderSpriteScene(SpriteView.EdgeOn).also { write(backend, it, "sprites-edge-on") }
            val flatHalf = 0 until SCENE_SIZE / 2
            val facingHalf = SCENE_SIZE / 2 until SCENE_SIZE

            assertTrue(above.redPixels(flatHalf) >= MIN_SPRITE_PIXELS, "$backend: from above the flat sprite drew ${above.redPixels(flatHalf)} px")
            assertTrue(edgeOn.redPixels(flatHalf) <= MAX_EDGE_ON_PIXELS, "$backend: edge-on the flat sprite drew ${edgeOn.redPixels(flatHalf)} px")
            assertTrue(above.redPixels(facingHalf) >= MIN_SPRITE_PIXELS, "$backend: from above the camera-facing sprite drew ${above.redPixels(facingHalf)} px")
            assertTrue(edgeOn.redPixels(facingHalf) >= MIN_SPRITE_PIXELS, "$backend: edge-on the camera-facing sprite drew ${edgeOn.redPixels(facingHalf)} px")
        }
    }

    /**
     * Seen from the side, a camera-facing sprite is as tall as it is wide and keeps its texture's left
     * on the left. Its size used to read as a stretch along world up: twice as tall, and mirrored.
     */
    @Test
    fun aCameraFacingSpriteSeenFromTheSideKeepsItsShapeOnBothBackends() {
        BACKEND_ORDER.forEach { backend ->
            val pixels = session(backend).renderer.renderFacingSpriteFromTheSide().also { write(backend, it, "facing-sprite-side") }
            fun isRed(x: Int, y: Int) = pixels.channel(y * SCENE_SIZE + x, RED) > TEXTURED_RED_THRESHOLD + 2 * pixels.channel(y * SCENE_SIZE + x, BLUE)
            fun isBlue(x: Int, y: Int) = pixels.channel(y * SCENE_SIZE + x, BLUE) > TEXTURED_RED_THRESHOLD + 2 * pixels.channel(y * SCENE_SIZE + x, RED)
            val all = (0 until SCENE_SIZE).flatMap { y -> (0 until SCENE_SIZE).map { x -> x to y } }
            // Every lit pixel for the outline: the texture wraps, so its edges blend red into blue.
            val drawn = all.filter { (x, y) -> maxOf(pixels.channel(y * SCENE_SIZE + x, RED), pixels.channel(y * SCENE_SIZE + x, BLUE)) > TEXTURED_RED_THRESHOLD }
            assertTrue(drawn.isNotEmpty(), "$backend drew no sprite -- see $REPORT_DIR")
            val width = drawn.maxOf { it.first } - drawn.minOf { it.first } + 1
            val height = drawn.maxOf { it.second } - drawn.minOf { it.second } + 1
            val redX = all.filter { (x, y) -> isRed(x, y) }.map { it.first }.average()
            val blueX = all.filter { (x, y) -> isBlue(x, y) }.map { it.first }.average()

            assertTrue(abs(width - height) <= maxOf(width, height) / 10, "$backend: the sprite is $width x $height px, not square")
            assertTrue(redX < blueX, "$backend: the texture's left (red, x=$redX) must stay left of its right (blue, x=$blueX)")
        }
    }

    /** A particle sprite between the sun and the ground darkens the ground on both backends. */
    @Test
    fun aParticleCastsAShadowOnBothBackends() {
        BACKEND_ORDER.forEach { backend ->
            val renderer = session(backend).renderer
            val bare = renderer.renderParticleShadowScene(withSprite = false).also { write(backend, it, "particle-shadow-off") }
            val cast = renderer.renderParticleShadowScene(withSprite = true).also { write(backend, it, "particle-shadow-on") }

            assertNull(bare.shadowCentroid(), "$backend: the bare ground must hold no shadow")
            assertNotNull(cast.shadowCentroid(), "$backend: the particle cast no shadow -- see $REPORT_DIR")
        }
    }

    /** Red-dominant pixels in [columns]. */
    private fun ByteArray.redPixels(columns: IntRange): Int = (0 until SCENE_SIZE).sumOf { y ->
        columns.count { x ->
            val pixel = y * SCENE_SIZE + x
            channel(pixel, RED) > TEXTURED_RED_THRESHOLD + 2 * maxOf(channel(pixel, GREEN), channel(pixel, BLUE))
        }
    }

    @Test
    fun theShadowLandsInTheSamePlaceOnBothBackends() {
        val centroids = BACKEND_ORDER.associateWith { backend ->
            val session = session(backend)
            val pixels = session.renderer.renderShadowScene().also { write(backend, it) }
            pixels.shadowCentroid()
                ?: error("$backend drew no shadow at all -- see $REPORT_DIR")
        }

        val vulkan = centroids.getValue(HeadlessUiBackend.Vulkan)
        val webGpu = centroids.getValue(HeadlessUiBackend.WebGpu)
        val drift = maxOf(kotlin.math.abs(vulkan.first - webGpu.first), kotlin.math.abs(vulkan.second - webGpu.second))

        assertTrue(
            drift <= CENTROID_TOLERANCE,
            "the backends put the shadow in different places -- see $REPORT_DIR. " +
                "Vulkan centroid $vulkan, WebGPU centroid $webGpu, ${drift}px apart",
        )
    }

    /**
     * The middle of the shadowed region of the ground, or null if nothing is shadowed.
     *
     * A centroid rather than a side-of-the-frame reading. The first version of this test compared
     * brightness left and right of centre and passed with the shadow lookup deliberately broken:
     * mirroring the map's V axis moves the shadow along world Z, toward or away from the camera,
     * which a left/right reading cannot see. Both axes, or the test only covers half the bug.
     *
     * The lit level is measured per capture rather than fixed, because the two backends store
     * colour differently -- Vulkan's offscreen target is UNORM and WebGPU's takes its sRGB format
     * from the surface -- so the same ground reads 116 on one and 180 on the other. The shadow is
     * the same 581 pixels either way; only the numbers written into them differ.
     */
    /** [below] maps the ground's lit level to the level a shadowed pixel falls below. */
    /** The ground's shadowed pixels, measured as [shadowCentroid] finds them. */
    private fun ByteArray.shadowedGroundPixels(): Int {
        val ground = (GROUND_TOP..GROUND_BOTTOM).flatMap { y -> (0 until SCENE_SIZE).map { x -> x to y } }
            .filter { (x, y) -> luminanceAt(x, y) > 0 && isGreyAt(x, y) }
        val lit = ground.groupingBy { (x, y) -> luminanceAt(x, y) }.eachCount().maxByOrNull { it.value }?.key ?: return 0
        return ground.count { (x, y) -> luminanceAt(x, y) < lit - SHADOW_MARGIN }
    }

    /** Pixels black here, a card's clear half drawn whole, that [other] shows as lit ground. */
    private fun ByteArray.blackPixelsLitIn(other: ByteArray): Int = (0 until SCENE_SIZE).sumOf { y ->
        (0 until SCENE_SIZE).count { x -> luminanceAt(x, y) <= BLACK_LEVEL && other.luminanceAt(x, y) - luminanceAt(x, y) > CARD_TO_GROUND }
    }

    private fun ByteArray.channel(pixel: Int, channel: Int): Int = this[pixel * 4 + channel].toInt() and 0xFF

    private fun ByteArray.shadowCentroid(below: (lit: Int) -> Int = { it - SHADOW_MARGIN }): Pair<Int, Int>? {
        val ground = (GROUND_TOP..GROUND_BOTTOM).flatMap { y ->
            (0 until SCENE_SIZE).map { x -> x to y }
        }.filter { (x, y) -> luminanceAt(x, y) > 0 && isGreyAt(x, y) }
        // The ground is most of the frame, so its lit value is the most common one in it.
        val lit = ground.groupingBy { (x, y) -> luminanceAt(x, y) }.eachCount().maxByOrNull { it.value }?.key
        val shadowed = ground.filter { (x, y) -> lit != null && luminanceAt(x, y) < below(lit) }
        return shadowed
            .takeIf { it.isNotEmpty() }
            ?.let { it.sumOf { (x, _) -> x } / it.size to it.sumOf { (_, y) -> y } / it.size }
    }

    /** The channels at least half as bright as the brightest: a colour's hue, whatever the lighting. */
    private fun ByteArray.brightChannelsAt(x: Int, y: Int): Set<Int> =
        copyOfRange((y * SCENE_SIZE + x) * 4, (y * SCENE_SIZE + x) * 4 + 3).brightChannels()

    private fun ByteArray.brightChannels(): Set<Int> {
        val channels = take(3).map { it.toInt() and 0xFF }
        return channels.indices.filter { channels[it] * 2 >= channels.max() }.toSet()
    }

    private fun shadowLevel(lit: Int): Int = lit * 3 / 4

    /** Ground and its shadow are grey; the red caster is not, whatever its brightness. */
    private fun ByteArray.isGreyAt(x: Int, y: Int): Boolean {
        val offset = (y * SCENE_SIZE + x) * 4
        val channels = (0..2).map { this[offset + it].toInt() and 0xFF }
        return channels.max() - channels.min() <= GREY_SPREAD
    }

    private fun redSum(backend: HeadlessUiBackend, pixels: ByteArray): Int = pixels.asSequence()
        .filterIndexed { index, _ -> index % 4 == 0 }
        .sumOf { byte ->
            val v = byte.toInt() and 0xFF
            if (backend == HeadlessUiBackend.WebGpu) SRGB_TO_LINEAR[v] else v
        }

    private fun write(backend: HeadlessUiBackend, pixels: ByteArray, name: String = "shadow-scene") {
        val image = BufferedImage(SCENE_SIZE, SCENE_SIZE, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until SCENE_SIZE) {
            for (x in 0 until SCENE_SIZE) {
                val offset = (y * SCENE_SIZE + x) * 4
                fun channel(index: Int) = pixels[offset + index].toInt() and 0xFF
                image.setRGB(
                    x,
                    y,
                    (channel(3) shl 24) or (channel(0) shl 16) or (channel(1) shl 8) or channel(2),
                )
            }
        }
        val out = File(REPORT_DIR).apply { mkdirs() }
        ImageIO.write(image, "png", File(out, "$name-${backend.name.lowercase()}.png"))
    }

    private companion object {
        /** Vulkan first, for the loader reason [UiBackendParityTest] documents. */
        val BACKEND_ORDER = listOf(HeadlessUiBackend.Vulkan, HeadlessUiBackend.WebGpu)
        private val sessions = mutableMapOf<HeadlessUiBackend, HeadlessRenderSession>()

        fun session(backend: HeadlessUiBackend): HeadlessRenderSession =
            sessions.getOrPut(backend) { openHeadlessScene(backend) }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            sessions.remove(HeadlessUiBackend.Vulkan)?.close()
        }

        const val REPORT_DIR = "build/reports/render-parity"

        /** The band the ground occupies in this framing, measured off the captures. */
        const val GROUND_TOP = 45
        const val GROUND_BOTTOM = 110

        /** How far below the lit level a pixel must fall to count as shadowed. */
        const val SHADOW_MARGIN = 10
        const val CARD_TO_GROUND = 60

        /** A card's black clear half: it reads up to 22 through WebGPU's sRGB target, the shadowed ground 59 and up. */
        const val BLACK_LEVEL = 30
        const val MIN_CLEAR_HALF_PIXELS = 20
        /** A metal loses its diffuse light; anything under a tenth is the factor being ignored. */
        const val MIN_METALLIC_CHANGE = 0.1
        const val RED = 0
        const val GREEN = 1
        const val BLUE = 2

        /** Top row red, bottom row blue. */
        val RED_OVER_BLUE = com.awakekt.awake.render.texture.TextureAsset(
            data = byteArrayOf(
                -1, 0, 0, -1, -1, 0, 0, -1,
                0, 0, -1, -1, 0, 0, -1, -1,
            ),
            width = 2,
            height = 2,
        )
        const val MIN_SPRITE_PIXELS = 200
        const val MAX_EDGE_ON_PIXELS = 4
        const val COVERED_GREEN = 40
        const val MIN_QUAD_PIXELS = 50
        const val CHANNEL_TOLERANCE = 2

        /** Antialiasing and PBR rounding move a centroid by a pixel; a mirrored lookup moves it
         * across the ground. */
        const val CENTROID_TOLERANCE = 3

        const val MID_GREY = 128
        const val GREY_SPREAD = 16

        /** 255 x neutral((128 / 255)^2.2 x 0.08)^(1 / 2.2), where neutral is Khronos PBR Neutral. */
        const val AMBIENT_SHADE = 15
        const val SHADE_TOLERANCE = 6

        /** 255 x neutral((128 / 255)^2.2 x 0.5)^(1 / 2.2). */
        const val HALF_AMBIENT_SHADE = 76

        /** Red, green, blue, yellow: frames 0 to 3 of [FRAME_SHEET]. */
        val FRAME_COLOURS = listOf(
            byteArrayOf(-1, 0, 0),
            byteArrayOf(0, -1, 0),
            byteArrayOf(0, 0, -1),
            byteArrayOf(-1, -1, 0),
        )

        /** A 2 x 2 frame sheet of 2 x 2 texels per frame. Data row 0 is the image's bottom row. */
        val FRAME_SHEET = TextureAsset(
            data = ByteArray(4 * 4 * 4) { index ->
                val texel = index / 4
                val frame = (if (texel / 4 < 2) 2 else 0) + (if (texel % 4 < 2) 0 else 1)
                if (index % 4 == 3) -1 else FRAME_COLOURS[frame][index % 4]
            },
            width = 4,
            height = 4,
        )

        /** Mid-frame at one frame a second: frames 0 to 3, then frame 0 again. */
        val FRAME_TIMES = listOf(0.5f, 1.5f, 2.5f, 3.5f, 4.5f)

        const val TEXTURED_RED_THRESHOLD = 20
        const val MIN_TEXTURED_PIXELS = 200
        const val TEXTURED_COVERAGE_TOLERANCE = 160

        /** A dense fog control must visibly change the red channel without erasing the draw. */
        const val FOG_DENSITY = 0.25f
        const val FOG_REMAINING_FRACTION = 0.85f
        const val MIN_FOGGED_RED_SUM = 10_000
        const val RED_SUM_TOLERANCE = 35_000

        private val SRGB_TO_LINEAR = IntArray(256) { srgb ->
            val c = srgb / 255.0
            val lin = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            (lin * 255.0 + 0.5).toInt().coerceIn(0, 255)
        }
    }
}
