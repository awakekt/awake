/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.asset.shaderpack.PackShaderSets
import com.awakekt.awake.asset.shaderpack.skyboxCubemapContentFeature
import com.awakekt.awake.asset.shaderpack.terrainContentFeature
import com.awakekt.awake.asset.shaders.ContentFeatureHost
import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.asset.terrain.clipmap.TerrainClipmapConfig
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.texture.createCubemapAsset
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * A cubemap that attaches after terrain once replaced a 5,130-pixel ground sample in a rendered
 * scene with one flat sky colour. The same area had 1,758 colours with the terrain drawn in front.
 */
class CubemapTerrainParityTest {
    @Test
    fun loadingAndReplacingACubemapKeepsTheTerrainInFront() {
        for (backend in HeadlessUiBackend.entries) {
            openHeadlessScene(backend).use { session ->
                val renderer = session.renderer
                val host = renderer as ContentFeatureHost
                val target = renderer.createRenderTarget(SCENE_SIZE, SCENE_SIZE)
                val pass = ScenePassCompiler.compile(
                    lens = ADJOINING_LENS,
                    drawCalls = emptyList(),
                    environment = EnvironmentUniforms.Default.copy(showSky = true),
                    clipSpace = renderer.clipSpace,
                    aspect = 1f,
                    drawPreparer = (renderer as? GpuDrawPreparationSource)?.gpuDrawPreparer,
                )
                fun capture() = runBlocking {
                    renderer.renderToTexture(target, pass)
                    renderer.readPixels(target).data
                }

                try {
                    val land = Heightmap(FloatArray(17 * 17), 17, 17, Vec3f(1f, 1f, 1f))
                    val layout = TerrainClipmapConfig(ringCount = 2, ringResolution = 16, baseSpacing = 1f)
                    runBlocking { host.attachContentFeature(terrainContentFeature(PackShaderSets.Terrain, land, layout)) }
                    val beforeSky = capture()
                    assertNotEquals(beforeSky.pixel(0, 0), beforeSky.groundPixel(), "$backend: terrain must draw before the sky loads")

                    val day = runBlocking { host.attachContentFeature(solidSky(0, 0, 255)) }
                    val dayFrame = capture()
                    assertEquals(listOf(0, 0, 255), dayFrame.pixel(0, 0), "$backend: day sky")
                    assertEquals(beforeSky.groundPixel(), dayFrame.groundPixel(), "$backend: day sky covered terrain")

                    runBlocking { host.attachContentFeature(solidSky(255, 0, 0)) }
                    day.detach()
                    val nightFrame = capture()
                    assertEquals(listOf(255, 0, 0), nightFrame.pixel(0, 0), "$backend: replacement sky")
                    assertEquals(beforeSky.groundPixel(), nightFrame.groundPixel(), "$backend: replacement sky covered terrain")
                } finally {
                    target.destroy()
                }
            }
        }
    }

    private fun solidSky(r: Int, g: Int, b: Int) =
        skyboxCubemapContentFeature(createCubemapAsset(List(6) { byteArrayOf(r.toByte(), g.toByte(), b.toByte(), 255.toByte()) }, 1))

    private fun ByteArray.groundPixel() = pixel(SCENE_SIZE / 2, SCENE_SIZE / 2)

    private fun ByteArray.pixel(x: Int, y: Int): List<Int> {
        val offset = (y * SCENE_SIZE + x) * 4
        return (0..2).map { this[offset + it].toInt() and 0xFF }
    }
}
