/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.geometry.generate.generate
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.HeadlessSurface
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.particles.ParticleEmitter
import com.awakekt.awake.particles.ParticleMotion
import com.awakekt.awake.particles.ParticleSystem
import com.awakekt.awake.particles.ParticleVisual
import com.awakekt.awake.render.capture.PixelMap
import com.awakekt.awake.render.passes.uniforms.ParticleUniformLayout
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.testing.writePng
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.particles.TransformPlacement
import com.awakekt.awake.scene.rendering.Camera
import com.awakekt.awake.scene.rendering.light.Light
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.rendering.mesh.PbrMaterial
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.showcase.app.EngineShowcaseRenderPlan
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * On Vulkan, a scene readback is the frame the window presents, particles, additive blending,
 * light and shadows included, through the showcase's own render plan. `lit_shadow` does not read
 * a [PbrMaterial]'s colour, so the tint is checked at draw level by `SceneReadbackTest` instead.
 */
class SceneReadbackFrameTest {
    @Test
    fun aReadbackMatchesThePresentedFrame() = runBlocking {
        val game = app {
            window { size(WIDTH, HEIGHT) }
            scene("readback") {
                onReady { populate() }
            }
        }
        val renderer = HeadlessPlanEngine(game, EngineShowcaseRenderPlan).boot(HeadlessSurface(WIDTH, HEIGHT))
        try {
            game.ready(renderer)
            game.update(FRAME, WIDTH.toFloat(), HEIGHT.toFloat())
            val presented = renderer.readPresentedPixels().data.copyOf()
            val captured = game.requireService<SceneAppLifecycleRuntime>().readback(LENS, WIDTH, HEIGHT).data
            PixelMap(WIDTH, HEIGHT, presented.copyOf()).writePng(File(PRESENTED_PATH))
            PixelMap(WIDTH, HEIGHT, captured.copyOf()).writePng(File(CAPTURED_PATH))

            val differing = (0 until WIDTH * HEIGHT).count { pixel ->
                (0 until RGB_CHANNELS).any { channel ->
                    val offset = pixel * RGBA_CHANNELS + channel
                    abs((presented[offset].toInt() and 0xff) - (captured[offset].toInt() and 0xff)) > CHANNEL_TOLERANCE
                }
            }
            assertTrue(differing <= MAX_DIFFERING_PIXELS, "The readback differs from the presented frame in $differing pixels.")
        } finally {
            game.dispose()
            renderer.destroy()
        }
    }

    private fun SceneAppLifecycleRuntime.populate() {
        val cube = renderer.createMesh(generate { cube(size = 1f, colored = true) })
        val lit = renderer.createMaterial(LitShadowUniformLayout)
        world.add(world.create(), Camera(LENS))
        world.add(world.create(), Light(direction = Vec3f(0.4f, 0.8f, 0.4f)))
        world.create().also {
            world.add(it, Transform(position = Vec3f(-1f, 0f, 0f)))
            world.add(it, MeshRenderer(cube, lit))
            world.add(it, PbrMaterial(baseColorFactor = Color(1f, 0.2f, 0.2f, 1f)))
        }
        world.create().also {
            world.add(it, Transform(position = Vec3f(1f, 0f, 0f)))
            world.add(it, MeshRenderer(cube, lit, transparent = true, additive = true))
        }
        val quad = renderer.createMesh(MeshGeometry(QUAD_VERTICES, QUAD_INDICES, format = VertexFormat.PositionUv))
        val white = TextureAsset(ByteArray(RGBA_CHANNELS) { -1 }, 1, 1)
        val spark = renderer.createMaterial(ParticleUniformLayout, texture = white)
        val sparks = ParticleEmitter(
            mesh = quad,
            material = spark,
            origin = Vec3f(0f, 1f, 0f),
            maxParticles = 8,
            spawnRate = 400f,
            lifetime = 10f,
            startAlpha = 1f,
            scale = 0.3f,
            motion = ParticleMotion(baseVelocity = Vec3f.ZERO),
            visual = ParticleVisual(additive = true),
        )
        world.add(world.create(), sparks)
        ParticleSystem(TransformPlacement).update(world, PARTICLE_SPAWN_SECONDS)
    }

    private companion object {
        const val WIDTH = 320
        const val HEIGHT = 180
        const val FRAME = 1f / 60f
        const val PARTICLE_SPAWN_SECONDS = 0.05f
        const val RGB_CHANNELS = 3
        const val RGBA_CHANNELS = 4
        const val CHANNEL_TOLERANCE = 2
        const val MAX_DIFFERING_PIXELS = 0
        val LENS = Lens(eye = Vec3f(0f, 1f, 5f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 100f)
        val QUAD_VERTICES = floatArrayOf(
            -0.5f, -0.5f, 0f, 0f, 1f,
            0.5f, -0.5f, 0f, 1f, 1f,
            0.5f, 0.5f, 0f, 1f, 0f,
            -0.5f, 0.5f, 0f, 0f, 0f,
        )
        val QUAD_INDICES = intArrayOf(0, 1, 2, 2, 3, 0)
        const val PRESENTED_PATH = "build/reports/render-captures/readback-presented.png"
        const val CAPTURED_PATH = "build/reports/render-captures/readback-captured.png"
    }
}
