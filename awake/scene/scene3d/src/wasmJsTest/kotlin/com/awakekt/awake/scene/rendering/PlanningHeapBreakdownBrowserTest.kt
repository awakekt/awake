/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
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
import com.awakekt.awake.scene.core.transform.TransformSystem
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
        // Transforms with no version, as a host without a TransformSystem has; then built by one, as
        // an app's are, so each renderer's bounds are cached by version rather than compared.
        val unversioned = measure(builtByTransformSystem = false)
        val versioned = measure(builtByTransformSystem = true)
        println("PlanningHeapBreakdown: $PROPS renderers, bytes a renderer; unversioned: $unversioned; versioned: $versioned")
    }

    private fun measure(builtByTransformSystem: Boolean): String {
        val renderer = NoopRenderer()
        val world = town(renderer)
        if (builtByTransformSystem) TransformSystem().update(world, 0f)
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

        // The compile on its own, over this frame's draws: the caching preparer alone, then the whole
        // compile with it, to tell the scene's compile cost from the passes' own (#1621).
        val draws = ArrayList(collector.collectBeforeParticles(world, frame, 0f))
        val preparer = CachingPreparer()
        val scratch = com.awakekt.awake.render.passes.ScenePassScratch()
        val context = GpuDrawPreparationContext(viewProjection = camera.lens.viewProjectionMatrix(1f, ClipSpace.WebGpu), cameraEye = camera.lens.eye)
        // The same draws as fresh commands carrying only the fields the synthetic frame sets, then
        // with one more of the scene's fields each, to find the one the compile pays for (#1621).
        val copies = CompileFieldCopies.plain(draws)
        val allFields = CompileFieldCopies.all(draws)
        val byField = CompileFieldCopies.byField(draws)
        fun compileWith(drawn: List<com.awakekt.awake.render.passes.RenderDrawCommand>, light: com.awakekt.awake.render.passes.uniforms.SceneLight?, environment: com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms) =
            com.awakekt.awake.render.passes.ScenePassCompiler.compile(
                camera.lens, drawn, light, environment, ClipSpace.WebGpu, 1f, null, preparer,
                emptyList(), com.awakekt.awake.render.imagepass.ImagePassChain.Empty, null, scratch,
            )
        val compileParts = listOf(
            "compileNoLight" to { compileWith(draws, null, contributions.environment) },
            "compileDefaultEnvironment" to { compileWith(draws, contributions.light, com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms.Default) },
            "compileCopiedDraws" to { compileWith(copies, contributions.light, contributions.environment) },
            "compileAllFields" to { compileWith(allFields, contributions.light, contributions.environment) },
            "compilePreparerOnly" to { for (i in draws.indices) preparer.prepare(draws[i], i, context) },
            "compileSceneDraws" to {
                com.awakekt.awake.render.passes.ScenePassCompiler.compile(
                    camera.lens, draws, contributions.light, contributions.environment, ClipSpace.WebGpu, 1f, null, preparer,
                    emptyList(), com.awakekt.awake.render.imagepass.ImagePassChain.Empty, null, scratch,
                )
            },
        ) + byField.map { (field, drawn) ->
            "compileWith_$field" to { compileWith(drawn, contributions.light, contributions.environment) }
        }

        val stages = StageHeap()
        val system = RenderSystem3D(renderer, drawPreparer = CachingPreparer()).apply { drawStages = stages }
        val parts = listOf(
            "features" to { features.collect(world, camera, 0f, 1f) },
            "culling" to { culling.prepare(world, camera, 1f, shadows) },
            "beforeParticles" to { collector.collectBeforeParticles(world, frame, 0f) },
            "afterParticles" to { collector.collectAfterParticles(world, frame, camera) },
            "frame" to { system.update(world, DELTA) },
        )
        val costs = (parts + compileParts).map { (name, pass) -> name to bytesPerRenderer(pass) }
        val beforeParticles = costs.first { it.first == "beforeParticles" }.second
        assertTrue(beforeParticles <= BEFORE_PARTICLES_BUDGET, "the draw loop took $beforeParticles B a renderer")
        // The scene's own draws compile as copies of them do: each renderer's own animation vector once
        // cost 157 B a renderer through Vec3f's data-class equals (#1621).
        val compile = costs.first { it.first == "compileSceneDraws" }.second
        assertTrue(compile <= COMPILE_BUDGET, "the compile of the scene's draws took $compile B a renderer")
        return costs.joinToString { (name, bytes) -> "$name=${bytes.toInt()}" } +
            ", frame stages collect ${(stages.collect / KB).toInt()} KB, compile ${(stages.compile / KB).toInt()} KB"
    }

    private fun bytesPerRenderer(pass: () -> Any?): Double = medianGrowth(PROPS, WARMUP, SAMPLES, pass)

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

        /** The draw loop, bytes a renderer: 224 (157 over versioned transforms) before #1696, 0 after. */
        const val BEFORE_PARTICLES_BUDGET = 8.0

        /** The compile of the scene's draws, bytes a renderer: 172 (130 versioned) before #1716; plain copies of them, 15 (17). */
        const val COMPILE_BUDGET = 48.0
        val UNIT_BOX = Aabb(Vec3f(-1f, -1f, -1f), Vec3f(1f, 1f, 1f))
    }
}
