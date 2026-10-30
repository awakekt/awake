/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase

import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.engine.bootstrap.dsl.appSpec
import com.awakekt.awake.engine.platform.HeadlessSurface
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.showcase.app.EngineShowcaseRenderPlan
import com.awakekt.awake.vulkan.application.VulkanEngine
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.ui.DynamicMesh
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Replays #1118's 190 textured primitives without loading a consumer's assets. Both the old
 * oversized allocation and the corrected capacity exercise the actual Android JNI writes.
 * The raw-write cases submit no draw, so frame-slot writes need no fence. The texture case uses
 * the showcase's real render plan and verifies all 190 image centres in GPU readback; replacing
 * its draws with an empty list measured 0 correct centres instead of 190 on API 36.
 * These are isolated coverage, not proof that the original consumer crash has been reproduced.
 */
class VulkanBufferWriteDeviceTest {
    @Test
    fun singleQuadAllocations() = replay(initialQuads = 1)

    @Test
    fun legacyOversizedAllocations() = replay(initialQuads = 256)

    @Test
    fun aScreenOfTexturedPrimitivesSurvivesUploadsAndDraws() = runBlocking {
        val engine = TextureReplayEngine()
        val renderer = engine.boot()
        val materials = (0 until PRIMITIVES).map { index ->
            val rgba = if (index % 2 == 0) byteArrayOf(-1, 0, 0, -1) else byteArrayOf(0, -1, 0, -1)
            renderer.createMaterial(texture = TextureAsset(rgba, 1, 1))
        }
        val target = renderer.createRenderTarget(WIDTH, HEIGHT)
        try {
            repeat(UPDATES) { update ->
                val primitives = materials.mapIndexed { index, material ->
                    UiDrawPrimitive.Texture(
                        x = (index % COLUMNS * CELL + update % 2).toFloat(),
                        y = (index / COLUMNS * CELL).toFloat(),
                        w = (CELL - 1).toFloat(),
                        h = (CELL - 1).toFloat(),
                        material = material,
                    )
                }
                renderer.drawUiToTexture(target, primitives)
            }
            val pixels = renderer.readPixels(target).data
            val correct = materials.indices.count { index ->
                val offset = ((index / COLUMNS * CELL + CELL / 2) * WIDTH + index % COLUMNS * CELL + CELL / 2) * 4
                val channel = index % 2
                (pixels[offset + channel].toInt() and 0xff) >= 250 &&
                    (pixels[offset + 1 - channel].toInt() and 0xff) <= 5
            }
            assertEquals(PRIMITIVES, correct, "every red/green image must reach the readback")
        } finally {
            target.destroy()
            materials.forEach { it.destroy() }
            renderer.destroy()
        }
    }

    private fun replay(initialQuads: Int) {
        val device = GraphicsDevice()
        device.createHeadless()
        val meshes = mutableListOf<DynamicMesh>()
        try {
            repeat(PRIMITIVES) {
                meshes += DynamicMesh(device, initialQuads, DynamicMesh.GLYPH_FLOATS_PER_VERTEX, FRAMES_IN_FLIGHT)
            }
            repeat(UPDATES) { update ->
                meshes.forEachIndexed { primitive, mesh ->
                    // Fresh arrays bypass the retained-geometry shortcut on every update.
                    val vertices = FloatArray(4 * DynamicMesh.GLYPH_FLOATS_PER_VERTEX) { (it + primitive + update).toFloat() }
                    val indices = intArrayOf(0, 1, 2, 2, 3, 0)
                    mesh.update(update % FRAMES_IN_FLIGHT, vertices, indices)
                    assertEquals(6, mesh.drawIndexCount)
                }
            }
        } finally {
            meshes.forEach(DynamicMesh::destroy)
            device.destroy()
        }
    }

    private companion object {
        const val PRIMITIVES = 190
        const val FRAMES_IN_FLIGHT = 2
        const val UPDATES = 8
        const val COLUMNS = 19
        const val CELL = 8
        const val WIDTH = COLUMNS * CELL
        const val HEIGHT = 10 * CELL
    }

    private class TextureReplayEngine : VulkanEngine(appSpec { window { size(WIDTH, HEIGHT) } }.createLifecycle(), EngineShowcaseRenderPlan) {
        suspend fun boot() = createBackendResources(HeadlessSurface(WIDTH, HEIGHT)).renderer
    }
}
