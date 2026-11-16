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
import com.awakekt.awake.core.pool.ScratchPool
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ShadowCasterVolume
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.core.transform.TransformSystem
import com.awakekt.awake.scene.rendering.light.Light
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.rendering.mesh.beyondDrawDistance
import com.awakekt.awake.scene.rendering.spatial.SceneCullingCompiler
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Each call `SceneDrawCollector.collectBeforeParticles` makes per renderer, measured on its own on
 * Kotlin/Wasm (#1621): the pass costs about 157 bytes a renderer in a browser over versioned
 * transforms, against nothing on the JVM, and the whole pass is too coarse to say which call pays.
 * Each figure is bytes a renderer over [PROPS] renderers, from [medianGrowth].
 */
class PlanningLoopHeapBrowserTest {
    @Test
    fun eachCallOfTheDrawLoopIsMeasuredOnItsOwn() {
        assertTrue(heapMeasurable(), "needs the launcher's --js-flags=--expose-gc and --enable-precise-memory-info")
        val renderer = NoopRenderer()
        val world = town(renderer)
        TransformSystem().update(world, 0f)
        val camera = Camera(Lens(eye = Vec3f(0f, 30f, 60f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 400f))
        world.add(world.create(), camera)
        world.add(world.create(), Light(direction = Vec3f(0.4f, 1f, 0.3f)))
        val culling = SceneCullingCompiler(ClipSpace.WebGpu)
        val contributions = SceneFeatureCollector3D(ClipSpace.WebGpu, emptyList()).collect(world, camera, 0f, 1f)
        val shadows = ShadowCasterVolume().set(contributions.light, contributions.environment)
        val frame = culling.prepare(world, camera, 1f, shadows)
        val family = world.family<Transform, MeshRenderer>()
        val boundsType = world.typeId(MeshBounds::class)
        val scratch = Aabb(Vec3f(0f, 0f, 0f), Vec3f(0f, 0f, 0f))
        val commands = ScratchPool { RenderDrawCommand(mesh = it0, material = it1, model = com.awakekt.awake.core.math.Mat4()) }
        val drawn = ArrayList<RenderDrawCommand>(PROPS)
        var sink = 0
        val parts = listOf<Pair<String, () -> Unit>>(
            "iterate" to { family.forEach { _, _, _ -> sink++ } },
            "boundsGet" to {
                val store = world.componentStore<MeshBounds>(boundsType)
                family.forEach { entity, _, _ -> if (store?.get(entity) != null) sink++ }
            },
            "drawDistance" to { family.forEach { _, transform, mesh -> if (mesh.beyondDrawDistance(camera, transform)) sink++ } },
            "classify" to {
                val store = world.componentStore<MeshBounds>(boundsType)
                family.forEach { entity, transform, _ ->
                    val bounds = store?.get(entity) ?: return@forEach
                    culling.classify(entity.id, transform, bounds, frame, castsShadows = true)
                }
            },
            "writeBounds" to {
                val store = world.componentStore<MeshBounds>(boundsType)
                family.forEach { entity, transform, _ -> store?.get(entity)?.writeWorldBounds(transform, scratch) }
            },
            "commandSet" to {
                commands.reset()
                drawn.clear()
                family.forEach { _, transform, mesh ->
                    drawn.add(
                        commands.obtain().set(
                            mesh = mesh.mesh,
                            material = mesh.material,
                            model = transform.worldMatrix,
                            vertexAnimation = NO_ANIMATION,
                            cullMode = mesh.cullMode,
                            transparent = mesh.transparent,
                            worldBounds = scratch,
                        ),
                    )
                }
            },
            "obtainOnly" to {
                commands.reset()
                family.forEach { _, _, _ -> if (commands.obtain().timeSeconds < 0f) sink++ }
            },
            "setEveryArgument" to {
                commands.reset()
                family.forEach { _, transform, mesh ->
                    commands.obtain().set(
                        mesh.mesh, mesh.material, transform.worldMatrix, EMPTY_EXTRAS, NO_ANIMATION, 0f,
                        null, null, null, null, mesh.cullMode, com.awakekt.awake.render.pipeline.AlphaMode.Opaque, 0.5f,
                        mesh.transparent, false, scratch, false, 0, -1, 0, true, false,
                    )
                }
            },
        )
        val costs = parts.map { (name, pass) -> name to medianGrowth(PROPS, pass = pass) }
        println("PlanningLoopHeap: $PROPS renderers, bytes a renderer: " + costs.joinToString { (name, bytes) -> "$name=${bytes.toInt()}" } + " ($sink)")
        assertTrue(costs.isNotEmpty())
    }

    private fun town(renderer: NoopRenderer): World {
        val world = World()
        val meshes = List(MESHES) { renderer.createMesh(MeshGeometry(FloatArray(0), IntArray(0), VertexFormat.PositionNormalColor)) }
        val materials = List(MESHES) { renderer.createMaterial() }
        it0 = meshes[0]
        it1 = materials[0]
        val side = kotlin.math.sqrt(PROPS.toDouble()).toInt() + 1
        repeat(PROPS) { index ->
            val entity = world.create()
            world.add(entity, Transform(position = Vec3f((index % side - side / 2) * SPACING, 0f, (index / side - side / 2) * SPACING)))
            world.add(entity, MeshRenderer(meshes[index % MESHES], materials[index % MESHES]))
            world.add(entity, MeshBounds(UNIT_BOX))
        }
        return world
    }

    private lateinit var it0: com.awakekt.awake.render.mesh.Mesh
    private lateinit var it1: com.awakekt.awake.render.material.Material

    private companion object {
        const val PROPS = 3_000
        const val MESHES = 190
        const val SPACING = 4f
        val UNIT_BOX = Aabb(Vec3f(-1f, -1f, -1f), Vec3f(1f, 1f, 1f))
        val NO_ANIMATION = Vec3f(0f, 0f, 0f)
        val EMPTY_EXTRAS = FloatArray(0)
    }
}
