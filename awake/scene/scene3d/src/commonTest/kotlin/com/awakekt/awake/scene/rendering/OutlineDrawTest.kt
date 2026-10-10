/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.command.NO_MASK_LAYER
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.renderer.OutlineStyle
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.rendering.mesh.SceneDrawCollector
import com.awakekt.awake.scene.rendering.outline.OutlineStyles
import com.awakekt.awake.scene.rendering.outline.Outlined
import com.awakekt.awake.scene.rendering.outline.outline
import com.awakekt.awake.scene.rendering.outline.outlineMaskLayers
import com.awakekt.awake.scene.rendering.spatial.SceneCullingCompiler
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OutlineDrawTest {
    /** An outlined entity's draw names its set's mask layer; any other draw names none. */
    @Test
    fun anOutlinedEntitysDrawJoinsItsSetsMask() {
        val world = World()
        val outlined = mesh()
        val plain = mesh()
        world.add(world.create().also { world.add(it, Transform()) }, MeshRenderer(outlined, material()))
        world.add(world.create().also { world.add(it, Transform()) }, MeshRenderer(plain, material()))
        world.queryEach<MeshRenderer> { entity, renderer -> if (renderer.mesh === outlined) world.add(entity, Outlined(set = 1)) }

        val draws = collect(world)

        assertEquals(1, draws.single { it.mesh === outlined }.maskLayer)
        assertEquals(NO_MASK_LAYER, draws.single { it.mesh === plain }.maskLayer)
    }

    /** [outline] marks an entity and everything parented under it, and null takes the marks off again. */
    @Test
    fun outliningAModelOutlinesEveryMeshUnderIt() {
        val world = World()
        val root = world.create().also { world.add(it, Transform()) }
        val arm = world.create().also { world.add(it, Transform(parent = root)) }
        val hand = world.create().also { world.add(it, Transform(parent = arm)) }
        val other = world.create().also { world.add(it, Transform()) }

        world.outline(root, set = 0)
        val marked = listOf(root, arm, hand, other).map { world.get<Outlined>(it)?.set }
        world.outline(root, set = null)

        assertEquals(listOf(0, 0, 0, null), marked)
        assertTrue(listOf(root, arm, hand).all { world.get<Outlined>(it) == null })
    }

    /** The mask's layers carry the world's outline styles, or the defaults, and only while something is outlined. */
    @Test
    fun theMaskLayersCarryTheStylesWhileSomethingIsOutlined() {
        val world = World()
        val empty = world.outlineMaskLayers()
        val entity = world.create().also { world.add(it, Outlined()) }
        val defaults = world.outlineMaskLayers()
        val own = OutlineStyles(listOf(OutlineStyle(Color(0f, 1f, 0f, 1f), 5f), OutlineStyle(Color(0f, 0f, 1f, 1f), 1f)))
        world.add(world.create(), own)

        assertTrue(empty.isEmpty())
        assertContentEquals(OutlineStyles.Default.styles[0].packed(), defaults[0])
        assertContentEquals(own.styles[0].packed(), world.outlineMaskLayers()[0])
        world.remove<Outlined>(entity)
        assertTrue(world.outlineMaskLayers().isEmpty())
    }

    @Test
    fun anOutlineSetIsOneOfTheMasksLayers() {
        assertFailsWith<IllegalArgumentException> { Outlined(set = 2) }
    }

    private fun collect(world: World) = SceneCullingCompiler(ClipSpace.WebGpu).let { culling ->
        val camera = Camera(Lens(eye = Vec3f(0f, 0f, 5f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 100f))
        SceneDrawCollector(culling).collectBeforeParticles(world, culling.prepare(world, camera), elapsedTimeSeconds = 0f)
    }

    private fun mesh(): Mesh = object : Mesh {
        override val format = VertexFormat.PositionNormalColor
        override val sizeBytes = 0L
        override fun destroy() = Unit
    }

    private fun material(): Material = object : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }
}
