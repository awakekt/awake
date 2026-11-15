/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.imagepass.ImagePassChain
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.ScenePassScratch
import com.awakekt.awake.render.passes.shadowCascadeUniforms
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.renderer.createMaterial
import kotlinx.coroutines.test.runTest
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * In the browser, a camera moving over a shadowed scene re-records retained bundles every frame and
 * creates no GPU buffer to do so once warm (#1555), counted at `GPUDevice.createBuffer` itself.
 *
 * Every buffer created is a new wgpu4k `Buffer`, whose web wrapper builds a logger by capturing and
 * parsing a JS stack trace. Before, each re-recorded cell created one, which put the logger, the
 * stack getter and its regex parsing at the top of a consumer's CPU profile.
 */
class BundleArgsPoolBrowserTest {

    @Test
    fun aMovingCameraCreatesNoBuffersOnceWarm() = runTest(timeout = 2.minutes) {
        val engine = BundleBrowserEngine(1)
        try {
            val renderer = engine.boot()
            val scene = Scene(renderer)
            repeat(WARM_UP) { scene.frame(it) }
            val encoded = renderer.retainedBundles.counts.encoded
            countCreateBuffer(true)

            repeat(MEASURED) { scene.frame(WARM_UP + it) }

            val created = createBufferCount()
            countCreateBuffer(false)
            assertTrue(renderer.retainedBundles.counts.encoded > encoded, "the moving camera re-records cells, or this proves nothing")
            assertEquals(0, created, "GPU buffers created over $MEASURED moving frames")
            assertEquals(emptyList(), engine.errors, "WebGPU validation errors")
        } finally {
            countCreateBuffer(false)
            engine.destroy()
        }
    }

    /** Copies of four props over the ground, the camera sweeping a loop and some copies culled in turn. */
    private class Scene(private val renderer: Renderer) {
        private val target = renderer.createRenderTarget(BundleBrowserEngine.SIZE, BundleBrowserEngine.SIZE)
        private val lit = renderer.createMaterial(uniformFloatCount = MaterialUniformLayouts.LitShadow.total)
        private val ground = renderer.createMesh(quad(GROUND))
        private val props = List(KINDS) { renderer.createMesh(quad(PROP + it * PROP_STEP, height = 0.4f)) }
        private val sun = SceneLight(direction = Vec3f(0.3f, 1f, 0.2f), color = Vec3f(1f, 1f, 1f))
        private val scratch = ScenePassScratch()

        fun frame(index: Int) {
            val lens = Lens(eye = Vec3f((index % LOOP) * 0.3f - 3f, 5f, 5f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 30f)
            val draws = buildList {
                add(RenderDrawCommand(ground, lit, model = Mat4()))
                for (copy in 0 until COPIES) {
                    for (kind in props.indices) {
                        if (kind == (index % KINDS) && copy < index % 3) continue
                        add(RenderDrawCommand(props[kind], lit, model = Mat4().apply { data[12] = copy * 0.7f - 2f; data[14] = kind * 0.9f - 1.4f }))
                    }
                }
            }
            val light = sun.copy(cascades = shadowCascadeUniforms(sun, lens, 1f, renderer.clipSpace))
            val preparer = (renderer as GpuDrawPreparationSource).gpuDrawPreparer
            val pass = ScenePassCompiler.compile(
                lens, draws, light, EnvironmentUniforms.Default, renderer.clipSpace, 1f, null, preparer, emptyList(),
                ImagePassChain.Empty, null, scratch,
            )
            renderer.renderToTexture(target, pass)
        }
    }

    private companion object {
        const val WARM_UP = 3 * 20
        const val MEASURED = 40
        const val LOOP = 20
        const val KINDS = 4
        const val COPIES = 6
        const val GROUND = 3f
        const val PROP = 0.15f
        const val PROP_STEP = 0.04f

        /** A flat square [half] wide, [height] above the ground, facing up. */
        fun quad(half: Float, height: Float = 0f): MeshGeometry {
            val corners = listOf(-half to -half, -half to half, half to half, half to -half)
            val vertices = corners.flatMap { (x, z) -> listOf(x, height, z, 0f, 1f, 0f, 0.8f, 0.7f, 0.6f) }
            return MeshGeometry(vertices.toFloatArray(), intArrayOf(0, 1, 2, 2, 3, 0), VertexFormat.PositionNormalColor)
        }
    }
}

/** Starts or stops counting `GPUDevice.prototype.createBuffer` calls, wrapping it once. */
@JsFun(
    """(on) => {
    const proto = GPUDevice.prototype;
    if (!proto.__awakeCreateBuffer) {
        proto.__awakeCreateBuffer = proto.createBuffer;
        globalThis.__awakeCreateBufferCount = 0;
        proto.createBuffer = function (descriptor) {
            if (globalThis.__awakeCountCreateBuffer) globalThis.__awakeCreateBufferCount++;
            return proto.__awakeCreateBuffer.call(this, descriptor);
        };
    }
    globalThis.__awakeCountCreateBuffer = on;
    if (on) globalThis.__awakeCreateBufferCount = 0;
}""",
)
private external fun countCreateBuffer(on: Boolean)

@JsFun("() => globalThis.__awakeCreateBufferCount || 0")
private external fun createBufferCount(): Int
