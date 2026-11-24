/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.mesh.GpuDynamicMeshes
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.renderer.GpuResourceStats
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.testing.HeadlessRenderSession
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.TextureAsset
import org.junit.AfterClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Cross-backend GPU resource leak gate across both Vulkan and WebGPU:
 * creating, drawing, and destroying static meshes, shared-vertex meshes, dynamic meshes,
 * textured/PBR/render-target materials, and render targets must return every live counter and
 * byte estimate to its exact baseline across repeated cycles, and double-destroying any resource
 * must be a safe no-op.
 */
class GpuResourceLeakParityTest {
    @Test
    fun createDrawDestroyCyclesReturnToExactBaselineOnBothBackends() {
        BACKEND_ORDER.forEach { backend ->
            val renderer = session(backend).renderer
            // Seed cached neutral PBR textures once before capturing the steady-state baseline.
            renderer.createMaterial(layout = LitShadowUniformLayout, texture = solidTexture(10, 20, 30)).destroy()

            val baseline = assertNotNull(renderer.gpuResourceStats, "$backend: gpuResourceStats must be reported")
            assertEquals(0, baseline.liveMeshes, "$backend: no live meshes before cycle")
            assertEquals(0, baseline.liveMaterials, "$backend: no live materials before cycle")
            assertEquals(0, baseline.liveRenderTargets, "$backend: no live render targets before cycle")
            assertEquals(0L, baseline.liveMeshBytes, "$backend: zero mesh bytes before cycle")

            repeat(CYCLES) { cycle -> runResourceCycle(backend, renderer, baseline, cycle) }
        }
    }

    private fun runResourceCycle(
        backend: HeadlessUiBackend,
        renderer: Renderer,
        baseline: GpuResourceStats,
        cycle: Int,
    ) {
        val target = renderer.createRenderTarget(TARGET_SIZE, TARGET_SIZE)
        val baseGeometry = plane(GROUND_HALF, y = 0f)
        val baseMesh = renderer.createMesh(baseGeometry)
        val lodMesh = renderer.createMeshSharingVertices(baseMesh, baseGeometry)
        val dynamicMesh = renderer.capability(GpuDynamicMeshes)?.createDynamicMesh(
            VertexFormat.PositionNormalColor,
            vertexCapacity = 3,
            indices = intArrayOf(0, 1, 2),
        )
        val texturedMaterial = renderer.createMaterial(
            layout = LitShadowUniformLayout,
            texture = solidTexture(cycle * 30, 80, 160),
            pbrTextures = PbrTextureSet(
                metallicRoughness = solidTexture(40, 120, 200),
                normal = solidTexture(128, 128, 255),
            ),
        )
        val afterTextured = assertNotNull(renderer.gpuResourceStats)
        assertEquals(baseline.liveTextures + 3, afterTextured.liveTextures, "$backend cycle $cycle: base + 2 custom PBR maps")
        assertTrue(afterTextured.estimatedTextureBytes > baseline.estimatedTextureBytes, "$backend cycle $cycle: texture bytes grew")

        val untexturedMaterial = renderer.createMaterial(LitShadowUniformLayout)
        val previewMaterial = renderer.createMaterial(layout = LitShadowUniformLayout, renderTarget = target)

        val active = assertNotNull(renderer.gpuResourceStats)
        assertEquals(if (dynamicMesh != null) 3 else 2, active.liveMeshes, "$backend cycle $cycle: liveMeshes")
        assertEquals(3, active.liveMaterials, "$backend cycle $cycle: liveMaterials")
        assertEquals(1, active.liveRenderTargets, "$backend cycle $cycle: liveRenderTargets")
        assertTrue(active.liveMeshBytes > 0L, "$backend cycle $cycle: liveMeshBytes > 0")

        renderer.renderToTexture(target, renderer.compileShadowScene(listOf(RenderDrawCommand(lodMesh, texturedMaterial))))

        // Destroy everything twice to verify idempotent release and zero counter underflow.
        listOf(previewMaterial, untexturedMaterial, texturedMaterial).forEach { it.destroy(); it.destroy() }
        dynamicMesh?.let { it.destroy(); it.destroy() }
        lodMesh.destroy()
        lodMesh.destroy()
        baseMesh.destroy()
        baseMesh.destroy()
        target.destroy()

        val after = assertNotNull(renderer.gpuResourceStats)
        assertEquals(0, after.liveMeshes, "$backend cycle $cycle: liveMeshes leaked")
        assertEquals(0, after.liveMaterials, "$backend cycle $cycle: liveMaterials leaked")
        assertEquals(0, after.liveRenderTargets, "$backend cycle $cycle: liveRenderTargets leaked")
        assertEquals(0L, after.liveMeshBytes, "$backend cycle $cycle: liveMeshBytes leaked")
        assertEquals(baseline.liveTextures, after.liveTextures, "$backend cycle $cycle: liveTextures leaked")
        assertEquals(baseline.estimatedTextureBytes, after.estimatedTextureBytes, "$backend cycle $cycle: texture bytes leaked")
    }

    @Test
    fun rendererDestroyDrainsBackendOwnedResourcesToZero() {
        val fresh = openHeadlessScene(HeadlessUiBackend.Vulkan)
        val renderer = fresh.renderer
        val target = renderer.createRenderTarget(TARGET_SIZE, TARGET_SIZE)
        val mesh = renderer.createMesh(plane(GROUND_HALF, y = 0f))
        val material = renderer.createMaterial(
            layout = LitShadowUniformLayout,
            texture = solidTexture(200, 100, 50),
        )
        renderer.renderToTexture(target, renderer.compileShadowScene(listOf(RenderDrawCommand(mesh, material))))

        mesh.destroy()
        material.destroy()
        target.destroy()
        fresh.close()

        val afterShutdown = assertNotNull(renderer.gpuResourceStats)
        assertEquals(0, afterShutdown.liveMeshes, "meshes after destroy")
        assertEquals(0, afterShutdown.liveMaterials, "materials after destroy")
        assertEquals(0, afterShutdown.liveTextures, "textures after destroy")
        assertEquals(0, afterShutdown.liveRenderTargets, "render targets after destroy")
        assertEquals(0, afterShutdown.livePooledBuffers, "pooled buffers after destroy")
        assertEquals(0L, afterShutdown.liveMeshBytes, "mesh bytes after destroy")
        assertEquals(0L, afterShutdown.estimatedTextureBytes, "texture bytes after destroy")
        assertEquals(0, afterShutdown.totalLiveResources, "total live resources after destroy")
    }

    private fun solidTexture(r: Int, g: Int, b: Int): TextureAsset {
        val pixels = ByteArray(TEX_SIDE * TEX_SIDE * RGBA_BYTES)
        for (i in 0 until TEX_SIDE * TEX_SIDE) {
            pixels[i * RGBA_BYTES] = r.toByte()
            pixels[i * RGBA_BYTES + 1] = g.toByte()
            pixels[i * RGBA_BYTES + 2] = b.toByte()
            pixels[i * RGBA_BYTES + 3] = ALPHA_OPAQUE
        }
        return TextureAsset(pixels, TEX_SIDE, TEX_SIDE)
    }

    private companion object {
        val BACKEND_ORDER = listOf(HeadlessUiBackend.Vulkan, HeadlessUiBackend.WebGpu)
        private val sessions = mutableMapOf<HeadlessUiBackend, HeadlessRenderSession>()

        fun session(backend: HeadlessUiBackend): HeadlessRenderSession =
            sessions.getOrPut(backend) { openHeadlessScene(backend) }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            sessions.remove(HeadlessUiBackend.Vulkan)?.close()
        }

        const val CYCLES = 4
        const val TARGET_SIZE = 32
        const val TEX_SIDE = 8
        const val RGBA_BYTES = 4
        const val ALPHA_OPAQUE: Byte = -1
    }
}
