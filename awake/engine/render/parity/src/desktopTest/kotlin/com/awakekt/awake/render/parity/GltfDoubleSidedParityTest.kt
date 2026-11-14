/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.project.runtime.ProjectRenderPlan
import com.awakekt.awake.project.runtime.loadProject
import com.awakekt.awake.project.runtime.runProject
import com.awakekt.awake.render.capture.FramebufferAttachment
import com.awakekt.awake.render.capture.FramebufferAttachmentData
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies glTF `material.doubleSided` end-to-end through [loadProject] -> `GltfAssetResolver` ->
 * `SceneAssetLibrary` -> [ProjectRenderPlan] on both Vulkan and WebGPU.
 *
 * Every scene places two back-facing textured quads in front of a camera at `z = +4`:
 * - left (`x = -1.1`, red): glTF material `0` (`doubleSided: false` or omitted),
 * - right (`x = +1.1`, green): glTF material `1` (`doubleSided: true`).
 *
 * Negative controls recorded via `--rerun` by temporarily removing `buildBackCulled = true` on
 * `PositionNormalColorUv` in `ProjectRenderPlan`:
 * - [oneSidedPrimitiveIsBackCulledWhileDoubleSidedDrawsOnBothBackends]: opaque back-facing red
 *   primitive drew `13020` pixels instead of `0` (`green=13020`, expected `0`).
 * - [maskedMaterialsHonourDoubleSidedTheSameWayOnBothBackends]: `MASK` back-facing red primitive
 *   drew `3100` pixels instead of `0` (`green=3224`, expected `0`).
 * - [omittedSceneCullModeKeepsLegacyUnculledBehaviorAndExplicitCullModeWins]: explicit
 *   `cullMode = "Back"` on green drew `13020` pixels instead of `0` (`red=13020`, expected `0`).
 */
@OptIn(ExperimentalEncodingApi::class)
class GltfDoubleSidedParityTest {
    @Test
    fun oneSidedPrimitiveIsBackCulledWhileDoubleSidedDrawsOnBothBackends() = runBlocking {
        for (backend in HeadlessUiBackend.entries) {
            val counts = renderFlagsScene(
                backend = backend,
                gltf = flagsGltf(leftDoubleSided = false, rightDoubleSided = true, masked = false),
                leftCullMode = "FromMaterial",
                rightCullMode = "FromMaterial",
            )
            assertEquals(
                0,
                counts.red,
                "$backend: doubleSided=false primitive with FromMaterial must be back-culled: $counts",
            )
            assertTrue(
                counts.green > MIN_QUAD_PIXELS,
                "$backend: doubleSided=true primitive with FromMaterial must draw from behind: $counts",
            )
        }
    }

    @Test
    fun maskedMaterialsHonourDoubleSidedTheSameWayOnBothBackends() = runBlocking {
        for (backend in HeadlessUiBackend.entries) {
            val counts = renderFlagsScene(
                backend = backend,
                gltf = flagsGltf(leftDoubleSided = false, rightDoubleSided = true, masked = true),
                leftCullMode = "FromMaterial",
                rightCullMode = "FromMaterial",
            )
            assertEquals(
                0,
                counts.red,
                "$backend: doubleSided=false MASK primitive must be back-culled: $counts",
            )
            assertTrue(
                counts.green in MIN_MASKED_PIXELS..MAX_MASKED_PIXELS,
                "$backend: doubleSided=true MASK primitive must draw opaque half only: $counts",
            )
        }
    }

    @Test
    fun omittedSceneCullModeKeepsLegacyUnculledBehaviorAndExplicitCullModeWins() = runBlocking {
        for (backend in HeadlessUiBackend.entries) {
            val noOpinion = renderFlagsScene(
                backend = backend,
                gltf = flagsGltf(leftDoubleSided = false, rightDoubleSided = true, masked = false),
                leftCullMode = null,
                rightCullMode = null,
            )
            assertTrue(
                noOpinion.red > MIN_QUAD_PIXELS && noOpinion.green > MIN_QUAD_PIXELS,
                "$backend: omitted scene cullMode must keep legacy unculled rendering: $noOpinion",
            )

            val explicitOverride = renderFlagsScene(
                backend = backend,
                gltf = flagsGltf(leftDoubleSided = false, rightDoubleSided = true, masked = false),
                leftCullMode = "None",
                rightCullMode = "Back",
            )
            assertTrue(
                explicitOverride.red > MIN_QUAD_PIXELS,
                "$backend: explicit cullMode=None on doubleSided=false must still draw: $explicitOverride",
            )
            assertEquals(
                0,
                explicitOverride.green,
                "$backend: explicit cullMode=Back on doubleSided=true must cull: $explicitOverride",
            )
        }
    }

    private suspend fun renderFlagsScene(
        backend: HeadlessUiBackend,
        gltf: ByteArray,
        leftCullMode: String?,
        rightCullMode: String?,
    ): ColorCounts {
        val sceneJson = buildSceneJson(leftCullMode, rightCullMode)
        val source = memorySource(
            "awake.project.json" to
                """{"formatVersion":1,"id":"com.example.doublesided","name":"DoubleSided","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""
                    .encodeToByteArray(),
            "scenes/main.scene.json" to sceneJson.encodeToByteArray(),
            "assets/models/flags.gltf" to gltf,
        )
        val project = loadProject(source)
        val game = app { scene("play") { runProject(project) } }
        return openRuntimeSession(backend, game, WIDTH, HEIGHT, ProjectRenderPlan).use { session ->
            try {
                game.ready(session.renderer)
                repeat(3) { game.update(STEP, WIDTH.toFloat(), HEIGHT.toFloat()) }
                val runtime = game.requireService<SceneAppLifecycleRuntime>()
                val frame = runtime.readbackAttachment(WIDTH, HEIGHT, FramebufferAttachment.Color0)
                assertTrue(frame.available, frame.reason)
                countColors(frame)
            } finally {
                session.renderer.waitIdle()
                game.dispose()
                project.close()
            }
        }
    }

    private fun buildSceneJson(leftCullMode: String?, rightCullMode: String?): String {
        fun cullLine(mode: String?) = if (mode == null) "" else """, "cullMode": "$mode""""
        return """
            {
              "version": 1,
              "name": "double-sided",
              "nodes": [
                {
                  "name": "camera",
                  "components": [
                    {
                      "component": "camera",
                      "eye": { "x": 0.0, "y": 0.0, "z": 4.0 },
                      "center": { "x": 0.0, "y": 0.0, "z": 0.0 },
                      "up": { "x": 0.0, "y": 1.0, "z": 0.0 },
                      "fovYDegrees": 45.0,
                      "near": 0.1,
                      "far": 50.0,
                      "primary": true
                    }
                  ]
                },
                {
                  "name": "ambient",
                  "components": [
                    {
                      "component": "ambient_light",
                      "intensity": 1.0,
                      "color": { "r": 1.0, "g": 1.0, "b": 1.0, "a": 1.0 }
                    }
                  ]
                },
                {
                  "name": "left",
                  "transform": { "position": { "x": -1.1, "y": 0.0, "z": 0.0 } },
                  "components": [
                    {
                      "component": "mesh_renderer",
                      "mesh": "gltf-primitive:assets/models/flags.gltf#0",
                      "material": "gltf-material:assets/models/flags.gltf#0"${cullLine(leftCullMode)}
                    }
                  ]
                },
                {
                  "name": "right",
                  "transform": { "position": { "x": 1.1, "y": 0.0, "z": 0.0 } },
                  "components": [
                    {
                      "component": "mesh_renderer",
                      "mesh": "gltf-primitive:assets/models/flags.gltf#1",
                      "material": "gltf-material:assets/models/flags.gltf#1"${cullLine(rightCullMode)}
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
    }

    /**
     * One glTF with two back-facing textured quads in the XY plane: clockwise as viewed from `+Z`
     * (indices `0, 2, 1` and `0, 3, 2`), so `+Z` camera sees the back face of every primitive.
     */
    private fun flagsGltf(leftDoubleSided: Boolean, rightDoubleSided: Boolean, masked: Boolean): ByteArray {
        val quad = quadBuffer()
        val redPng = solidOrHalfMaskedPng(r = 255, g = 20, b = 20, halfClear = masked)
        val greenPng = solidOrHalfMaskedPng(r = 20, g = 255, b = 20, halfClear = masked)
        val alphaField = if (masked) """, "alphaMode": "MASK"""" else ""
        val leftFlag = if (leftDoubleSided) """, "doubleSided": true""" else ""
        val rightFlag = if (rightDoubleSided) """, "doubleSided": true""" else ""

        return """
            {
              "asset": { "version": "2.0" },
              "buffers": [ { "uri": "data:application/octet-stream;base64,${Base64.encode(quad.bytes)}", "byteLength": ${quad.bytes.size} } ],
              "bufferViews": [
                { "buffer": 0, "byteOffset": 0, "byteLength": ${quad.posSize} },
                { "buffer": 0, "byteOffset": ${quad.posSize}, "byteLength": ${quad.normSize} },
                { "buffer": 0, "byteOffset": ${quad.posSize + quad.normSize}, "byteLength": ${quad.uvSize} },
                { "buffer": 0, "byteOffset": ${quad.posSize + quad.normSize + quad.uvSize}, "byteLength": ${quad.idxSize} }
              ],
              "accessors": [
                { "bufferView": 0, "componentType": 5126, "count": 4, "type": "VEC3" },
                { "bufferView": 1, "componentType": 5126, "count": 4, "type": "VEC3" },
                { "bufferView": 2, "componentType": 5126, "count": 4, "type": "VEC2" },
                { "bufferView": 3, "componentType": 5123, "count": 6, "type": "SCALAR" }
              ],
              "materials": [
                { "pbrMetallicRoughness": { "baseColorTexture": { "index": 0 } } $alphaField $leftFlag },
                { "pbrMetallicRoughness": { "baseColorTexture": { "index": 1 } } $alphaField $rightFlag }
              ],
              "textures": [ { "source": 0 }, { "source": 1 } ],
              "images": [
                { "uri": "data:image/png;base64,${Base64.encode(redPng)}" },
                { "uri": "data:image/png;base64,${Base64.encode(greenPng)}" }
              ],
              "meshes": [
                {
                  "primitives": [
                    { "attributes": { "POSITION": 0, "NORMAL": 1, "TEXCOORD_0": 2 }, "indices": 3, "material": 0 },
                    { "attributes": { "POSITION": 0, "NORMAL": 1, "TEXCOORD_0": 2 }, "indices": 3, "material": 1 }
                  ]
                }
              ],
              "nodes": [ { "mesh": 0 } ],
              "scenes": [ { "nodes": [0] } ],
              "scene": 0
            }
        """.trimIndent().encodeToByteArray()
    }

    private fun quadBuffer(): QuadBuffer {
        val h = 0.8f
        val posBytes = floatsToLe(floatArrayOf(-h, -h, 0f, h, -h, 0f, h, h, 0f, -h, h, 0f))
        val normBytes = floatsToLe(floatArrayOf(0f, 0f, -1f, 0f, 0f, -1f, 0f, 0f, -1f, 0f, 0f, -1f))
        val uvBytes = floatsToLe(floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f))
        val idxBytes = shortsToLe(shortArrayOf(0, 2, 1, 0, 3, 2))
        return QuadBuffer(posBytes + normBytes + uvBytes + idxBytes, posBytes.size, normBytes.size, uvBytes.size, idxBytes.size)
    }

    private class QuadBuffer(
        val bytes: ByteArray,
        val posSize: Int,
        val normSize: Int,
        val uvSize: Int,
        val idxSize: Int,
    )

    private fun solidOrHalfMaskedPng(r: Int, g: Int, b: Int, halfClear: Boolean): ByteArray =
        ByteArrayOutputStream().also { out ->
            val width = if (halfClear) 2 else 1
            val image = BufferedImage(width, 1, BufferedImage.TYPE_INT_ARGB)
            image.setRGB(0, 0, (0xFF shl 24) or (r shl 16) or (g shl 8) or b)
            if (halfClear) image.setRGB(1, 0, 0x00000000)
            ImageIO.write(image, "png", out)
        }.toByteArray()

    private fun countColors(image: FramebufferAttachmentData): ColorCounts {
        var red = 0
        var green = 0
        for (pixel in 0 until image.width * image.height) {
            val offset = pixel * image.channels
            val r = image.values[offset]
            val g = image.values[offset + 1]
            val b = image.values[offset + 2]
            if (r > g + DOMINANCE && r > b + DOMINANCE) red++
            if (g > r + DOMINANCE && g > b + DOMINANCE) green++
        }
        return ColorCounts(red = red, green = green)
    }

    private fun memorySource(vararg entries: Pair<String, ByteArray>): AssetSource {
        val map = entries.toMap()
        return object : AssetSource {
            override suspend fun read(path: AssetPath): Result<ByteArray> =
                map[path.value]?.let { Result.success(it) }
                    ?: Result.failure(IllegalArgumentException("Missing asset: ${path.value}"))
        }
    }

    private fun floatsToLe(values: FloatArray): ByteArray = ByteArray(values.size * 4).also { bytes ->
        values.forEachIndexed { index, value ->
            val bits = value.toRawBits()
            for (b in 0 until 4) bytes[index * 4 + b] = (bits ushr (b * 8)).toByte()
        }
    }

    private fun shortsToLe(values: ShortArray): ByteArray = ByteArray(values.size * 2).also { bytes ->
        values.forEachIndexed { index, value ->
            val v = value.toInt()
            bytes[index * 2] = (v and 0xFF).toByte()
            bytes[index * 2 + 1] = ((v ushr 8) and 0xFF).toByte()
        }
    }

    private data class ColorCounts(val red: Int, val green: Int)

    private companion object {
        const val WIDTH = 256
        const val HEIGHT = 256
        const val STEP = 1f / 60f
        const val DOMINANCE = 0.25f
        const val MIN_QUAD_PIXELS = 10_000
        const val MIN_MASKED_PIXELS = 2_000
        const val MAX_MASKED_PIXELS = 4_000
    }
}
