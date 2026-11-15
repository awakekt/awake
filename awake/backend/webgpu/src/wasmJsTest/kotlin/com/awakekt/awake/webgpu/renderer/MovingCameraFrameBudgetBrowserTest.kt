/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Aabb
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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * What a warm frame costs in the browser with the camera moving over a field of static props, the
 * frame a consumer's web build pays most of the time: GPU buffers created and wasm heap allocated
 * (#1621). Budgets, so a regression fails here instead of in a consumer's profile after a release.
 *
 * [PROPS] props over [MESHES] meshes, shadowed, the camera sweeping a loop. Heap is the median of
 * per-frame growth of the JS heap (where Kotlin/Wasm's objects live), each frame measured from a
 * collected heap so a collection inside the window is rare and the median drops it.
 */
class MovingCameraFrameBudgetBrowserTest {

    @Test
    fun aWarmMovingFrameStaysWithinItsBudgets() = runTest(timeout = 4.minutes) {
        assertTrue(heapMeasurable(), "needs the launcher's --js-flags=--expose-gc and --enable-precise-memory-info")
        val engine = BundleBrowserEngine(1)
        try {
            val renderer = engine.boot()
            val scene = Field(renderer)
            repeat(WARM_UP) { scene.frame(it) }
            countCreateBuffer(true)
            val grown = DoubleArray(MEASURED) { index ->
                val before = collectedHeap()
                scene.frame(WARM_UP + index)
                heapNow() - before
            }
            val created = createBufferCount()
            countCreateBuffer(false)
            grown.sort()
            val perFrame = grown[MEASURED / 2]
            println("MovingCameraFrameBudget: heap ${(perFrame / KB).toInt()} KB a frame (median), $created buffers over $MEASURED frames")

            assertEquals(0, created, "GPU buffers created over $MEASURED warm moving frames")
            assertTrue(perFrame <= HEAP_BUDGET_KB * KB, "a warm moving frame allocated ${(perFrame / KB).toInt()} KB, over $HEAP_BUDGET_KB KB")
            assertEquals(emptyList(), engine.errors, "WebGPU validation errors")
        } finally {
            countCreateBuffer(false)
            engine.destroy()
        }
    }

    /** Copies of [MESHES] props in a square field, the camera flying a loop low over it. */
    private class Field(private val renderer: Renderer) {
        private val target = renderer.createRenderTarget(BundleBrowserEngine.SIZE, BundleBrowserEngine.SIZE)
        private val lit = renderer.createMaterial(uniformFloatCount = MaterialUniformLayouts.LitShadow.total)
        private val meshes = List(MESHES) { renderer.createMesh(box(0.3f + it * 0.002f)) }
        private val sun = SceneLight(direction = Vec3f(0.4f, 1f, 0.3f), color = Vec3f(1f, 1f, 1f))
        private val scratch = ScenePassScratch()
        private val draws = List(PROPS) { index ->
            val x = (index % ROW - ROW / 2) * SPACING
            val z = (index / ROW - ROW / 2) * SPACING
            RenderDrawCommand(
                meshes[index % MESHES],
                lit,
                model = Mat4().translate(x, 0f, z),
                worldBounds = Aabb(Vec3f(x - 1f, -1f, z - 1f), Vec3f(x + 1f, 1f, z + 1f)),
            )
        }

        fun frame(index: Int) {
            val angle = index * STEP
            val eye = Vec3f(sin(angle) * RADIUS, HEIGHT, cos(angle) * RADIUS)
            val lens = Lens(eye = eye, center = Vec3f(eye.x * 0.5f, 0f, eye.z * 0.5f - 10f), fovYRadians = 1f, near = 0.1f, far = 200f)
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
        const val PROPS = 3_000
        const val MESHES = 190
        const val ROW = 55
        const val SPACING = 2f
        const val RADIUS = 30f
        const val HEIGHT = 18f

        /** One loop in [WARM_UP] frames: warm-up visits every view the measured frames see. */
        const val WARM_UP = 60
        const val STEP = (2 * kotlin.math.PI / WARM_UP).toFloat()
        const val MEASURED = 30
        const val KB = 1024.0

        /**
         * Just above what a warm frame allocates now, 600 KB measured (Chromium, lavapipe): 705 KB
         * before #1555's packet fixes fails it. Most of what is left is not per-draw; see #1621.
         */
        const val HEAP_BUDGET_KB = 640

        /** An upright box [half] wide: six quads, lit from every side. */
        fun box(half: Float): MeshGeometry {
            val vertices = ArrayList<Float>()
            val indices = ArrayList<Int>()
            val faces = listOf(
                Vec3f(1f, 0f, 0f),
                Vec3f(-1f, 0f, 0f),
                Vec3f(0f, 1f, 0f),
                Vec3f(0f, -1f, 0f),
                Vec3f(0f, 0f, 1f),
                Vec3f(0f, 0f, -1f),
            )
            for (normal in faces) {
                val base = vertices.size / FLOATS
                val (u, v) = axesOf(normal)
                for ((a, b) in listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f)) {
                    val p = Vec3f(
                        (normal.x + u.x * a + v.x * b) * half,
                        (normal.y + u.y * a + v.y * b) * half + half,
                        (normal.z + u.z * a + v.z * b) * half,
                    )
                    vertices += listOf(p.x, p.y, p.z, normal.x, normal.y, normal.z, 0.7f, 0.6f, 0.5f)
                }
                indices += listOf(base, base + 1, base + 2, base + 2, base + 3, base)
            }
            return MeshGeometry(vertices.toFloatArray(), indices.toIntArray(), VertexFormat.PositionNormalColor)
        }

        const val FLOATS = 9

        fun axesOf(normal: Vec3f): Pair<Vec3f, Vec3f> = when {
            normal.x != 0f -> Vec3f(0f, 1f, 0f) to Vec3f(0f, 0f, 1f)
            normal.y != 0f -> Vec3f(1f, 0f, 0f) to Vec3f(0f, 0f, 1f)
            else -> Vec3f(1f, 0f, 0f) to Vec3f(0f, 1f, 0f)
        }
    }
}

/** Whether the page can collect garbage on demand and read its heap to the byte. */
@JsFun("() => typeof globalThis.gc === 'function' && !!(performance.memory)")
private external fun heapMeasurable(): Boolean

/** The JS heap's size in bytes after a full collection. */
@JsFun("() => { globalThis.gc(); return performance.memory.usedJSHeapSize; }")
private external fun collectedHeap(): Double

/** The JS heap's size in bytes now. */
@JsFun("() => performance.memory.usedJSHeapSize")
private external fun heapNow(): Double

/** Starts or stops counting `GPUDevice.prototype.createBuffer` calls, wrapping it once per page. */
@JsFun(
    """(on) => {
    const proto = GPUDevice.prototype;
    if (!proto.__awakeBudgetCreateBuffer) {
        proto.__awakeBudgetCreateBuffer = proto.createBuffer;
        proto.createBuffer = function (descriptor) {
            if (globalThis.__awakeBudgetCounting) globalThis.__awakeBudgetCreated++;
            return proto.__awakeBudgetCreateBuffer.call(this, descriptor);
        };
    }
    globalThis.__awakeBudgetCounting = on;
    if (on) globalThis.__awakeBudgetCreated = 0;
}""",
)
private external fun countCreateBuffer(on: Boolean)

@JsFun("() => globalThis.__awakeBudgetCreated || 0")
private external fun createBufferCount(): Int
