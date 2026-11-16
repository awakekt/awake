/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.command.BufferHandle
import com.awakekt.awake.render.command.GpuDrawPreparationContext
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuDrawRequest
import com.awakekt.awake.render.command.GpuResolvedDraw
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.passes.ShadowCasterVolume
import com.awakekt.awake.render.renderer.RenderDrawStage
import com.awakekt.awake.render.renderer.RenderDrawStages
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.light.Light
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.rendering.mesh.SceneDrawCollector
import com.awakekt.awake.scene.rendering.spatial.SceneCullingCompiler
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Where a still frame's planning heap goes on Kotlin/Wasm, part by part (#1621). The JVM, with
 * escape analysis off, plans this town in about 30 KB a frame; a browser pays far more, so each
 * part is measured where it is paid. No GPU: a [NoopRenderer] and a preparer that builds each mesh's
 * draw once, so every byte is the engine's planning.
 *
 * Each figure is the median heap growth, over [SAMPLES] passes from a collected heap, divided by
 * the renderers it walked.
 */
class PlanningHeapBreakdownBrowserTest {
    @Test
    fun aStillFramesPlanningHeapIsNamedPartByPart() {
        assertTrue(heapMeasurable(), "needs the launcher's --js-flags=--expose-gc and --enable-precise-memory-info")
        val renderer = NoopRenderer()
        val world = town(renderer)
        val camera = Camera(Lens(eye = Vec3f(0f, 30f, 60f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 400f))
        world.add(world.create(), camera)
        world.add(world.create(), Light(direction = Vec3f(0.4f, 1f, 0.3f)))

        val features = SceneFeatureCollector3D(ClipSpace.WebGpu, emptyList())
        val culling = SceneCullingCompiler(ClipSpace.WebGpu)
        val collector = SceneDrawCollector(culling)
        val shadows = ShadowCasterVolume()
        val contributions = features.collect(world, camera, 0f, 1f)
        shadows.set(contributions.light, contributions.environment)
        val frame = culling.prepare(world, camera, 1f, shadows)

        val stages = StageHeap()
        val system = RenderSystem3D(renderer, drawPreparer = CachingPreparer()).apply { drawStages = stages }
        val parts = listOf(
            "features" to { features.collect(world, camera, 0f, 1f) },
            "culling" to { culling.prepare(world, camera, 1f, shadows) },
            "beforeParticles" to { collector.collectBeforeParticles(world, frame, 0f) },
            "afterParticles" to { collector.collectAfterParticles(world, frame, camera) },
            "frame" to { system.update(world, DELTA) },
        )
        val costs = parts.map { (name, pass) -> name to bytesPerRenderer(pass) }
        println(
            "PlanningHeapBreakdown: $PROPS renderers, bytes a renderer: " + costs.joinToString { (name, bytes) -> "$name=${bytes.toInt()}" } +
                "; frame stages collect ${(stages.collect / KB).toInt()} KB, compile ${(stages.compile / KB).toInt()} KB",
        )
        assertTrue(costs.isNotEmpty())
    }

    private fun bytesPerRenderer(pass: () -> Any?): Double {
        repeat(WARMUP) { pass() }
        val growth = DoubleArray(SAMPLES) {
            val before = collectedHeap()
            pass()
            heapNow() - before
        }
        growth.sort()
        return growth[SAMPLES / 2] / PROPS
    }

    /** The heap at the frame's collect and compile edges. */
    private class StageHeap : RenderDrawStages {
        var collect = 0.0
        var compile = 0.0
        private var start = 0.0

        override fun begin(stage: RenderDrawStage) {
            start = heapNow()
        }

        override fun end(stage: RenderDrawStage) {
            when (stage) {
                RenderDrawStage.Collect -> collect = heapNow() - start
                RenderDrawStage.Compile -> compile = heapNow() - start
                else -> Unit
            }
        }
    }

    /** One resolved draw per mesh, built once: a backend's own allocation is not measured here. */
    private class CachingPreparer : GpuDrawPreparer {
        private val byMesh = HashMap<Any, GpuResolvedDraw>()

        override fun prepare(request: GpuDrawRequest, sourceIndex: Int, context: GpuDrawPreparationContext): GpuResolvedDraw =
            byMesh.getOrPut(request.mesh) {
                GpuResolvedDraw(pipeline = Handle, materialBinding = Handle, vertexBuffer = Handle, indexBuffer = null, elementCount = 36)
            }

        override fun canInstance(format: VertexFormat): Boolean = true
    }

    private object Handle : PipelineHandle, MaterialBinding, BufferHandle

    /** The JVM probe's town: props on a 4 m grid, cycling through [MESHES] meshes and materials. */
    private fun town(renderer: NoopRenderer): World {
        val world = World()
        val meshes = List(MESHES) { renderer.createMesh(MeshGeometry(FloatArray(0), IntArray(0), VertexFormat.PositionNormalColor)) }
        val materials = List(MESHES) { renderer.createMaterial() }
        val side = kotlin.math.sqrt(PROPS.toDouble()).toInt() + 1
        repeat(PROPS) { index ->
            val entity = world.create()
            world.add(entity, Transform(position = Vec3f((index % side - side / 2) * SPACING, 0f, (index / side - side / 2) * SPACING)))
            world.add(entity, MeshRenderer(meshes[index % MESHES], materials[index % MESHES]))
            world.add(entity, MeshBounds(UNIT_BOX))
        }
        return world
    }

    private companion object {
        const val PROPS = 3_000
        const val MESHES = 190
        const val SPACING = 4f
        const val DELTA = 1f / 60f
        const val WARMUP = 10
        const val SAMPLES = 9
        const val KB = 1024.0
        val UNIT_BOX = Aabb(Vec3f(-1f, -1f, -1f), Vec3f(1f, 1f, 1f))
    }
}

@JsFun("() => typeof globalThis.gc === 'function' && !!(performance.memory)")
private external fun heapMeasurable(): Boolean

@JsFun("() => { globalThis.gc(); return performance.memory.usedJSHeapSize; }")
private external fun collectedHeap(): Double

@JsFun("() => performance.memory.usedJSHeapSize")
private external fun heapNow(): Double
