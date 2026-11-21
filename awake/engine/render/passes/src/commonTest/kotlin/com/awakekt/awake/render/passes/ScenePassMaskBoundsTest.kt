/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.BufferHandle
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuResolvedDraw
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.imagepass.ImagePassRect
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A masked draw's world box, as the screen rectangle the outline overlay is scissored to (#1709). */
class ScenePassMaskBoundsTest {
    private val box = floatArrayOf(-0.5f, -0.5f, 0f, 0.5f, 0f, 0f)

    @Test
    fun aBoxProjectsToTextureCoordinatesFromTheTopLeft() {
        val identity = Mat4().apply { identity() }
        // NDC y from -0.5 to 0: the lower half of the middle, or the upper half where the API flips Y.
        assertEquals(ImagePassRect(0.25f, 0.5f, 0.5f, 0.25f), screenBounds(box, 0, identity, ClipSpace.WebGpu))
        assertEquals(ImagePassRect(0.25f, 0.25f, 0.5f, 0.25f), screenBounds(box, 0, identity, ClipSpace.Vulkan))
    }

    @Test
    fun aBoxReachingTheCameraPlaneHasNoBounds() {
        // w = z: the box sits on the camera plane.
        val perspective = Mat4().apply {
            identity()
            data[11] = 1f
            data[15] = 0f
        }
        assertNull(screenBounds(box, 0, perspective, ClipSpace.WebGpu))
    }

    @Test
    fun aMaskLayerCarriesItsDrawsBoundsUnlessOneHasNone() {
        val near = Aabb(Vec3f(-0.25f, -0.25f, -0.25f), Vec3f(0.25f, 0.25f, 0.25f))
        val input = compile(
            RenderDrawCommand(mesh, material, maskLayer = 0, worldBounds = near),
            RenderDrawCommand(mesh, material, maskLayer = 1, worldBounds = near),
            RenderDrawCommand(mesh, material, maskLayer = 1),
        )
        val bounds = assertNotNull(input.maskPasses.single { it.targetLayer == 0 }.screenBounds)
        assertTrue(bounds.x > 0f && bounds.right < 1f && bounds.y > 0f && bounds.bottom < 1f, "a small box in view: $bounds")
        assertNull(input.maskPasses.single { it.targetLayer == 1 }.screenBounds, "a draw with no bounds can be anywhere")
    }

    private fun compile(vararg draws: RenderDrawCommand) = ScenePassCompiler.compile(
        lens = Lens(eye = Vec3f(0f, 1f, 2f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 10f),
        drawCalls = draws.toList(),
        clipSpace = ClipSpace.Vulkan,
        aspect = 1f,
        drawPreparer = GpuDrawPreparer { _, _, _ ->
            GpuResolvedDraw(
                pipeline = object : PipelineHandle {},
                materialBinding = object : MaterialBinding {},
                vertexBuffer = object : BufferHandle {},
                indexBuffer = null,
                elementCount = 3,
            )
        },
        maskLayers = listOf(floatArrayOf(1f, 0f, 0f, 1f, 3f), floatArrayOf(0f, 0f, 1f, 1f, 2f)),
    )

    private val mesh = object : Mesh {
        override val format = VertexFormat.PositionNormalColor
        override val sizeBytes = 0L
        override fun destroy() = Unit
    }
    private val material = object : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }
}
