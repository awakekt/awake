/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.testing.HeadlessRenderSession
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A mesh that draws another's vertex buffer, as a model's levels of detail do, renders exactly as
 * the same triangles uploaded on their own, on both backends, after the mesh it shares with is
 * destroyed.
 */
class SharedVerticesParityTest {
    @Test
    fun aLevelDrawingItsFullMeshsVerticesRendersAsItsOwnUploadDoes() {
        BACKEND_ORDER.forEach { backend ->
            val renderer = session(backend).renderer
            val separate = renderer.renderSharedVerticesScene(shareVertices = false)
            val shared = renderer.renderSharedVerticesScene(shareVertices = true)
            val ground = renderer.renderGroundOnly()

            assertEquals(0, separate.differingChannels(shared), "$backend: the shared-vertex triangle draws differently")
            assertTrue(shared.differingChannels(ground) > MIN_TRIANGLE_CHANNELS, "$backend: the triangle drew nothing, so this compares nothing")
        }
    }

    /** The same scene without the triangle: the control that shows the triangle drew at all. */
    private fun Renderer.renderGroundOnly(): ByteArray {
        val target = createRenderTarget(SCENE_SIZE, SCENE_SIZE)
        val ground = createMesh(plane(GROUND_HALF, y = 0f))
        val material = createMaterial(LitShadowUniformLayout)
        return try {
            renderToTexture(target, compileShadowScene(listOf(RenderDrawCommand(ground, material))))
            runBlocking { readPixels(target) }.data
        } finally {
            ground.destroy()
            material.destroy()
            target.destroy()
        }
    }

    private fun ByteArray.differingChannels(other: ByteArray): Int =
        indices.count { abs((this[it].toInt() and 0xFF) - (other[it].toInt() and 0xFF)) > CHANNEL_TOLERANCE }

    private companion object {
        /** Vulkan first, for the loader reason [UiBackendParityTest] documents. */
        val BACKEND_ORDER = listOf(HeadlessUiBackend.Vulkan, HeadlessUiBackend.WebGpu)
        private val sessions = mutableMapOf<HeadlessUiBackend, HeadlessRenderSession>()

        fun session(backend: HeadlessUiBackend): HeadlessRenderSession = sessions.getOrPut(backend) { openHeadlessScene(backend) }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            sessions.remove(HeadlessUiBackend.Vulkan)?.close()
        }

        const val CHANNEL_TOLERANCE = 2

        /** A large orange triangle over the ground covers far more channels than this. */
        const val MIN_TRIANGLE_CHANNELS = 300
    }
}
