/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.passes.uniforms.pbrMaterialFloats
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.TextureAsset
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A textured material with no PBR maps draws, with neutral textures in their place, as on Vulkan. glTF
 * materials that carry only a base-colour texture come through as `pbrTextures = null`, and the textured
 * pipeline's declaration binds all four maps.
 */
class WebGpuTexturedMaterialTest {
    @Test
    fun aBaseColourOnlyMaterialDrawsItsTexture() = webGpuHeadlessScene().use { scene ->
        val renderer = scene.renderer
        val target = renderer.createRenderTarget(SIZE, SIZE)
        val mesh = renderer.createMesh(plane())
        val material = renderer.createMaterial(texture = RED, uniformFloatCount = MaterialUniformLayouts.PbrTextured.total, pbrTextures = null)
        try {
            val draw = RenderDrawCommand(
                mesh = mesh,
                material = material,
                extraUniformFloats = pbrMaterialFloats(metallic = 0f, roughness = 1f, baseColorFactor = Color.White, emissiveFactor = Color.Transparent),
            )
            renderer.renderToTexture(
                target,
                ScenePassCompiler.compile(
                    lens = Lens(eye = Vec3f(0f, EYE_Y, EYE_Z), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 50f),
                    drawCalls = listOf(draw),
                    light = SceneLight(direction = Vec3f(0f, 1f, 0f), color = Vec3f(1f, 1f, 1f), ambient = null),
                    environment = EnvironmentUniforms.Default.copy(shadowsEnabled = false),
                    clipSpace = renderer.clipSpace,
                    aspect = 1f,
                    drawPreparer = (renderer as? GpuDrawPreparationSource)?.gpuDrawPreparer,
                ),
            )
            val pixels = runBlocking { renderer.readPixels(target) }.data
            val offset = (SIZE / 2 * SIZE + SIZE / 2) * 4
            val channels = (0..2).map { pixels[offset + it].toInt() and 0xFF }
            assertEquals(setOf(0), channels.indices.filter { channels[it] * 2 >= channels.max() }.toSet(), "the centre shows the red texture, got $channels")
        } finally {
            mesh.destroy()
            material.destroy()
            target.destroy()
        }
    }

    private fun plane() = MeshGeometry(
        floatArrayOf(
            -HALF, 0f, -HALF, 0f, 1f, 0f, 1f, 1f, 1f, 0f, 0f,
            HALF, 0f, -HALF, 0f, 1f, 0f, 1f, 1f, 1f, 1f, 0f,
            HALF, 0f, HALF, 0f, 1f, 0f, 1f, 1f, 1f, 1f, 1f,
            -HALF, 0f, HALF, 0f, 1f, 0f, 1f, 1f, 1f, 0f, 1f,
        ),
        intArrayOf(0, 1, 2, 2, 3, 0),
        VertexFormat.PositionNormalColorUv,
    )

    private companion object {
        const val SIZE = 64
        const val HALF = 6f
        const val EYE_Y = 6f
        const val EYE_Z = 9f
        val RED = TextureAsset(data = byteArrayOf(-1, 0, 0, -1), width = 1, height = 1)
    }
}
