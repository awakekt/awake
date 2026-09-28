/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.passes.DEFAULT_SHADOW_DISTANCE
import com.awakekt.awake.render.passes.uniforms.RenderDebugView
import com.awakekt.awake.render.testing.HeadlessRenderSession
import org.junit.AfterClass
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Each shader debug view, on both backends, checked against what it claims to show.
 *
 * Every check also runs against the lit frame and must fail there: a check the lit image passes
 * cannot tell a working view from no view at all.
 */
class SceneDebugViewParityTest {

    @Test
    fun worldNormalsShowTheGroundAndTheCubeFaceInTheirNormalColours() = eachBackend { backend ->
        fun Frame.faces() = count(UP_NORMAL) to count(TOWARD_CAMERA_NORMAL)

        val (up, front) = render(backend, RenderDebugView.WorldNormals).faces()
        val (litUp, litFront) = render(backend, RenderDebugView.Off).faces()

        assertTrue(up > MIN_GROUND_PIXELS, "$backend: only $up pixels read as an up normal")
        assertTrue(front > MIN_FACE_PIXELS, "$backend: only $front pixels read as the cube's +Z normal")
        assertTrue(litUp + litFront == 0, "$backend: the lit frame already has normal colours ($litUp, $litFront)")
    }

    @Test
    fun linearDepthIsViewDistanceOverTheFarPlane() = eachBackend { backend ->
        val depth = render(backend, RenderDebugView.LinearDepth)
        val lit = render(backend, RenderDebugView.Off)
        val rows = groundRows()

        val misses = rows.filter { (row, expected) -> abs(depth.grey(DEPTH_COLUMN, row) - expected) > DEPTH_TOLERANCE }
        val litMatches = rows.count { (row, expected) -> abs(lit.grey(DEPTH_COLUMN, row) - expected) <= DEPTH_TOLERANCE }

        assertTrue(rows.size > MIN_GROUND_ROWS, "$backend: only ${rows.size} ground rows in column $DEPTH_COLUMN")
        assertTrue(misses.isEmpty(), "$backend: rows off their depth ${misses.map { (r, e) -> "$r: ${depth.grey(DEPTH_COLUMN, r)} vs $e" }}")
        assertTrue(litMatches < rows.size / 2, "$backend: the lit frame matches $litMatches of ${rows.size} depth rows")
    }

    @Test
    fun shadowVisibilityTintsEachCascadeAndGreysPastTheShadowDistance() = eachBackend { backend ->
        val frame = render(backend, RenderDebugView.ShadowVisibility)
        val column = groundRows().map { (row, _) -> row to frame.cascadeAt(DEPTH_COLUMN, row) }
        val depthOf = groundRows().associate { (row, value) -> row to value / MAX_CHANNEL * DEBUG_FAR }

        // Bottom of the frame is nearest: cascades must only step outward, then stop.
        val tinted = column.filter { it.second >= 0 }.map { it.second }
        val beyond = column.filter { (row, _) -> depthOf.getValue(row) > DEFAULT_SHADOW_DISTANCE * BLEND_MARGIN }
        val within = column.filter { (row, _) -> depthOf.getValue(row) < DEFAULT_SHADOW_DISTANCE / BLEND_MARGIN }
        assertTrue(tinted.toSet().size >= 2, "$backend: column shows cascades ${tinted.toSet()}")
        assertTrue(tinted.reversed().zipWithNext().all { (near, far) -> far >= near }, "$backend: cascades out of order $column")
        assertTrue(beyond.isNotEmpty() && beyond.all { it.second == NO_CASCADE }, "$backend: past the shadow distance $beyond")
        assertTrue(within.all { it.second >= 0 }, "$backend: inside the shadow distance $within")
        val litFrame = render(backend, RenderDebugView.Off)
        val shadowedGround = frame.darkTintedPixels(litFrame::isGround)
        assertTrue(shadowedGround > MIN_SHADOWED_PIXELS, "$backend: the cube casts no shadow on the ground in the view")

        val unshadowed = render(backend, RenderDebugView.ShadowVisibility, shadowsEnabled = false)
        assertTrue(unshadowed.tintedPixels() == 0, "$backend: tints with shadows off: ${unshadowed.tintedPixels()}")
        assertTrue(litFrame.tintedPixels(litFrame::isGround) == 0, "$backend: the lit ground already has tints")
    }

    @Test
    fun albedoIgnoresLightAndShadow() = eachBackend { backend ->
        val albedo = render(backend, RenderDebugView.Albedo)
        val lit = render(backend, RenderDebugView.Off)
        val ground = groundRows().map { (row, _) -> DEPTH_COLUMN to row } + shadowedGround(lit)

        assertTrue(ground.all { (x, y) -> albedo.isColour(x, y, WHITE) }, "$backend: albedo ground is not white everywhere")
        assertTrue(albedo.count(RED) > MIN_FACE_PIXELS, "$backend: the red cube is missing from albedo")
        assertTrue(!ground.all { (x, y) -> lit.isColour(x, y, WHITE) }, "$backend: the lit ground is already flat white")
        assertTrue(lit.count(RED) == 0, "$backend: the lit cube is already flat red")
    }

    /** Ground pixels the lit frame shows in the cube's shadow, for albedo to prove it ignores. */
    private fun shadowedGround(lit: Frame): List<Pair<Int, Int>> {
        val shadowed = (0 until SCENE_SIZE).flatMap { y -> (0 until SCENE_SIZE).map { x -> x to y } }
            .filter { (x, y) -> lit.isGround(x, y) && lit.grey(x, y) < SHADOWED_GREY }
        assertTrue(shadowed.size > MIN_SHADOWED_PIXELS, "the lit frame shows no shadow to test albedo against: ${shadowed.size}")
        return shadowed
    }

    private fun eachBackend(check: (HeadlessUiBackend) -> Unit) = BACKEND_ORDER.forEach(check)

    private fun render(backend: HeadlessUiBackend, view: RenderDebugView, shadowsEnabled: Boolean = true): Frame {
        val pixels = session(backend).renderer.renderDebugViewScene(view, shadowsEnabled)
        write(backend, view, shadowsEnabled, pixels)
        return Frame(pixels, backend)
    }

    /**
     * The ground rows of [DEPTH_COLUMN] and each one's expected [RenderDebugView.LinearDepth] grey,
     * from the pixel centre's ray against y = 0. Rows whose ray reaches past the far plane, or that
     * the cube covers, are left out.
     */
    private fun groundRows(): List<Pair<Int, Float>> {
        val lens = debugViewLens()
        val forward = (lens.center - lens.eye).normalized()
        val right = forward.cross(lens.up).normalized()
        val up = right.cross(forward)
        val half = tan(lens.fovYRadians / 2f)
        val x = (2f * (DEPTH_COLUMN + 0.5f) / SCENE_SIZE - 1f) * half
        return (0 until SCENE_SIZE).mapNotNull { row ->
            val y = (1f - 2f * (row + 0.5f) / SCENE_SIZE) * half
            val rayY = forward.y + right.y * x + up.y * y
            // dot(ray, forward) is 1, so the ray's parameter at the ground is its view depth.
            val depth = if (rayY < 0f) lens.eye.y / -rayY else Float.MAX_VALUE
            if (depth < lens.far * FAR_MARGIN) row to depth / lens.far * MAX_CHANNEL else null
        }
    }

    private class Frame(private val pixels: ByteArray, private val backend: HeadlessUiBackend) {
        /** The shader's output value: WebGPU's target is sRGB, so its stored bytes are decoded. */
        fun channel(x: Int, y: Int, c: Int): Float {
            val stored = pixels[(y * SCENE_SIZE + x) * 4 + c].toInt() and 0xFF
            return if (backend == HeadlessUiBackend.WebGpu) SRGB_TO_LINEAR[stored] else stored.toFloat()
        }

        fun grey(x: Int, y: Int) = (channel(x, y, 0) + channel(x, y, 1) + channel(x, y, 2)) / 3f

        fun isGrey(x: Int, y: Int) = (0..2).all { abs(channel(x, y, it) - grey(x, y)) <= COLOUR_TOLERANCE }

        /** Ground in a lit frame: grey rather than the red cube, and drawn rather than cleared. */
        fun isGround(x: Int, y: Int) = isGrey(x, y) && grey(x, y) > BACKGROUND_PEAK

        fun isColour(x: Int, y: Int, rgb: Vec3f) =
            abs(channel(x, y, 0) - rgb.x * MAX_CHANNEL) <= COLOUR_TOLERANCE &&
                abs(channel(x, y, 1) - rgb.y * MAX_CHANNEL) <= COLOUR_TOLERANCE &&
                abs(channel(x, y, 2) - rgb.z * MAX_CHANNEL) <= COLOUR_TOLERANCE

        fun count(rgb: Vec3f) = all().count { (x, y) -> isColour(x, y, rgb) }

        /** The cascade whose tint this pixel carries, [NO_CASCADE] for grey, [BACKGROUND] for nothing drawn. */
        fun cascadeAt(x: Int, y: Int): Int {
            val rgb = Vec3f(channel(x, y, 0), channel(x, y, 1), channel(x, y, 2))
            val peak = maxOf(rgb.x, rgb.y, rgb.z)
            if (peak < BACKGROUND_PEAK) return BACKGROUND
            val hue = rgb * (1f / peak)
            return (listOf(GREY) + CASCADE_TINTS).withIndex().minBy { (_, tint) -> (tint - hue).length3() }.index - 1
        }

        fun tintedPixels(where: (Int, Int) -> Boolean = { _, _ -> true }) = all().count { (x, y) -> where(x, y) && cascadeAt(x, y) >= 0 }

        /** Tinted pixels, among those [where] admits, at the shadowed floor of their tint's brightness. */
        fun darkTintedPixels(where: (Int, Int) -> Boolean) = all().count { (x, y) ->
            where(x, y) && cascadeAt(x, y) >= 0 && maxOf(channel(x, y, 0), channel(x, y, 1), channel(x, y, 2)) < SHADOWED_TINT_PEAK
        }

        private fun all() = (0 until SCENE_SIZE).flatMap { y -> (0 until SCENE_SIZE).map { x -> x to y } }
    }

    private fun write(backend: HeadlessUiBackend, view: RenderDebugView, shadowsEnabled: Boolean, pixels: ByteArray) {
        val image = BufferedImage(SCENE_SIZE, SCENE_SIZE, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until SCENE_SIZE) {
            for (x in 0 until SCENE_SIZE) {
                val offset = (y * SCENE_SIZE + x) * 4
                fun channel(index: Int) = pixels[offset + index].toInt() and 0xFF
                image.setRGB(x, y, (0xFF shl 24) or (channel(0) shl 16) or (channel(1) shl 8) or channel(2))
            }
        }
        val name = "debug-${view.name.lowercase()}${if (shadowsEnabled) "" else "-noshadow"}-${backend.name.lowercase()}.png"
        ImageIO.write(image, "png", File(File(REPORT_DIR).apply { mkdirs() }, name))
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
        const val MAX_CHANNEL = 255f

        /** Left of the cube and its shadow, so the column is bare ground from near to far. */
        const val DEPTH_COLUMN = 16
        const val FAR_MARGIN = 0.98f
        const val DEPTH_TOLERANCE = 3f
        const val COLOUR_TOLERANCE = 6f
        const val MIN_GROUND_ROWS = 60
        const val MIN_GROUND_PIXELS = 4000
        const val MIN_FACE_PIXELS = 40
        const val MIN_SHADOWED_PIXELS = 20

        /** Cascades blend across a split; stay this factor clear of the shadow distance. */
        const val BLEND_MARGIN = 1.25f
        const val NO_CASCADE = -1
        const val BACKGROUND = -2
        const val BACKGROUND_PEAK = 8f
        const val SHADOWED_TINT_PEAK = 150f
        /** Lit ground reads about 116 and ground in the cube's shadow about 78. */
        const val SHADOWED_GREY = 100f

        val UP_NORMAL = Vec3f(0.5f, 1f, 0.5f)
        val TOWARD_CAMERA_NORMAL = Vec3f(0.5f, 0.5f, 1f)
        val WHITE = Vec3f(1f, 1f, 1f)
        val RED = Vec3f(1f, 0f, 0f)
        val GREY = Vec3f(1f, 1f, 1f)

        /** `debugViewColor`'s cascade tints, normalised to their brightest channel. */
        val CASCADE_TINTS = listOf(
            Vec3f(1f, 0.3f, 0.3f),
            Vec3f(0.3f, 1f, 0.3f),
            Vec3f(0.3f, 0.45f, 1f),
            Vec3f(1f, 1f, 0.3f),
        )

        val SRGB_TO_LINEAR = FloatArray(256) { srgb ->
            val c = srgb / 255.0
            val lin = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            (lin * 255.0).toFloat()
        }
    }
}
