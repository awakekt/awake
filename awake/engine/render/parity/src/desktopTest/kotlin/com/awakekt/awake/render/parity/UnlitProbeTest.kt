/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.engine.platform.HeadlessSurface
import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.passes.uniforms.pbrMaterialFloats
import com.awakekt.awake.render.passes.uniforms.skinnedMaterialFloats
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.SkinnedUniformLayout
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.testing.HeadlessRenderSession
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.showcase.app.EngineShowcaseRenderPlan
import com.awakekt.awake.vulkan.application.VulkanEngine
import com.awakekt.awake.webgpu.webGpuHeadlessPlan
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test

class UnlitProbeTest {
    @Test fun probe() = runBlocking {
        val lines = mutableListOf<String>()
        var bad = false
        for (backend in listOf(HeadlessUiBackend.Vulkan, HeadlessUiBackend.WebGpu)) {
            for (full in listOf(false, true)) {
                runCatching {
                    open(backend, full).use { s ->
                        val presents = listOf(false) + if (backend == HeadlessUiBackend.Vulkan) listOf(true) else emptyList()
                        for (present in presents) for (texel in listOf(intArrayOf(0xDD, 0xAD, 0x98), intArrayOf(0x60, 0x40, 0x20))) {
                            val tex = solid(texel)
                            val st = draw(s.renderer, staticQuad(), s.renderer.createMaterial(texture = tex, uniformFloatCount = MaterialUniformLayouts.PbrTextured.total, pbrTextures = PbrTextureSet()),
                                pbrMaterialFloats(0f, 1f, Color.White, Color.Transparent, unlit = true), present)
                            val sk = draw(s.renderer, skinQuad(), s.renderer.createMaterial(SkinnedUniformLayout, texture = tex),
                                skinnedMaterialFloats(ID, Color.White, Color.Transparent, unlit = true), present)
                            if ((0..2).any { kotlin.math.abs(st[it] - texel[it]) > 2 || kotlin.math.abs(sk[it] - texel[it]) > 2 }) bad = true
                            lines += "PROBE $backend full=$full present=$present texel=${texel.toList()} static=${st.toList()} skinned=${sk.toList()}"
                        }
                    }
                }.onFailure { lines += "PROBE $backend full=$full error=$it"; bad = true }
            }
        }
        println(lines.joinToString("\n"))
        kotlin.test.assertFalse(bad, lines.joinToString("\n"))
    }

    private suspend fun draw(r: Renderer, geo: MeshGeometry, material: com.awakekt.awake.render.material.Material, extras: FloatArray, present: Boolean): IntArray {
        val target = r.createRenderTarget(64, 64)
        val mesh = r.createMesh(geo)
        try {
            val input = ScenePassCompiler.compile(
                lens = Lens(eye = Vec3f(0f, EYE_Y, EYE_Z), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 50f),
                drawCalls = listOf(RenderDrawCommand(mesh, material, extraUniformFloats = extras)),
                light = SceneLight(direction = Vec3f(0f, 1f, 0f), color = Vec3f(1f, 1f, 1f)),
                environment = EnvironmentUniforms.Default.copy(shadowsEnabled = false, exposure = 1f),
                clipSpace = r.clipSpace, aspect = 1f,
                drawPreparer = (r as GpuDrawPreparationSource).gpuDrawPreparer,
            )
            val p = if (present) {
                r.draw(input); r.readPresentedPixels().data
            } else {
                r.renderToTexture(target, input); r.readPixels(target).data
            }
            val b = (32 * 64 + 32) * 4
            return intArrayOf(p[b].toInt() and 255, p[b + 1].toInt() and 255, p[b + 2].toInt() and 255)
        } finally {
            r.waitIdle(); mesh.destroy(); material.destroy(); target.destroy()
        }
    }

    private suspend fun open(backend: HeadlessUiBackend, full: Boolean): HeadlessRenderSession {
        val plan = if (full) EngineShowcaseRenderPlan else EngineShowcaseRenderPlan.copy(contentFeatures = emptyList())
        if (backend == HeadlessUiBackend.WebGpu) return webGpuHeadlessPlan(plan, surface = HeadlessSurface(64, 64))
        val ready = CompletableDeferred<Renderer>()
        val app = AppSpecBuilder().apply { ready { ready.complete(it) } }.build().createLifecycle()
        val engine = VulkanEngine(app, plan)
        engine.create(HeadlessSurface(64, 64))
        val renderer = withTimeout(30_000) { ready.await() }
        return object : HeadlessRenderSession { override val renderer = renderer; override fun close() = engine.dispose() }
    }

    companion object {
        val ID = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        fun solid(c: IntArray) = TextureAsset(ByteArray(16) { if (it % 4 == 3) -1 else c[it % 4].toByte() }, 2, 2)
        val H = GROUND_HALF
        fun skinQuad() = MeshGeometry(floatArrayOf(
            -H, 0f, -H, 0f, 1f, 0f, 1f, 1f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f,
            H, 0f, -H, 0f, 1f, 0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f,
            H, 0f, H, 0f, 1f, 0f, 1f, 1f, 1f, 1f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f,
            -H, 0f, H, 0f, 1f, 0f, 1f, 1f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f,
        ), intArrayOf(0, 1, 2, 2, 3, 0), VertexFormat.PositionNormalColorUvSkin)
        fun staticQuad() = MeshGeometry(floatArrayOf(
            -H, 0f, -H, 0f, 1f, 0f, 1f, 1f, 1f, 0f, 0f,
            H, 0f, -H, 0f, 1f, 0f, 1f, 1f, 1f, 1f, 0f,
            H, 0f, H, 0f, 1f, 0f, 1f, 1f, 1f, 1f, 1f,
            -H, 0f, H, 0f, 1f, 0f, 1f, 1f, 1f, 0f, 1f,
        ), intArrayOf(0, 1, 2, 2, 3, 0), VertexFormat.PositionNormalColorUv)
    }
}
