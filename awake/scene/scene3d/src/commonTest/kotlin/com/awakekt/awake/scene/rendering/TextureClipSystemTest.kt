/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.passes.uniforms.TextureAnimation
import com.awakekt.awake.render.renderer.UniformFields
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.rendering.mesh.SceneDrawCollector
import com.awakekt.awake.scene.rendering.mesh.SceneTextureClip
import com.awakekt.awake.scene.rendering.mesh.SceneTextureClips
import com.awakekt.awake.scene.rendering.mesh.TextureClipSystem
import com.awakekt.awake.scene.rendering.mesh.TextureClips
import com.awakekt.awake.scene.rendering.spatial.SceneCullingCompiler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class TextureClipSystemTest {
    /** A 4 x 4 sheet: walk is cells 8-11 at 10 a second, attack is cells 12-14 once at 10. */
    private val sheet = SceneTextureClips(
        columns = 4,
        rows = 4,
        clips = linkedMapOf(
            "walk" to SceneTextureClip(firstFrame = 8, frameCount = 4, framesPerSecond = 10f),
            "attack" to SceneTextureClip(firstFrame = 12, frameCount = 3, framesPerSecond = 10f, loop = false),
        ),
    )

    private fun held(cell: Int) = TextureAnimation(columns = 4, rows = 4, frameCount = 1, framesPerSecond = 0f, firstFrame = cell)

    private fun World.spriteWith(clips: TextureClips): Entity = create().also { add(it, clips) }

    @Test
    fun anEntityWithNoAnimationIsGivenOneHoldingItsFirstCell() {
        val world = World()
        val entity = world.spriteWith(TextureClips(sheet))

        TextureClipSystem().update(world, 0f)

        assertEquals(held(8), world.get<TextureAnimation>(entity))
    }

    @Test
    fun theAnimationFollowsTheClipAsTimePasses() {
        val world = World()
        val entity = world.spriteWith(TextureClips(sheet))
        val system = TextureClipSystem()

        val shown = buildList {
            system.update(world, 0f)
            add(world.get<TextureAnimation>(entity)!!.firstFrame)
            repeat(4) {
                system.update(world, 0.1f)
                add(world.get<TextureAnimation>(entity)!!.firstFrame)
            }
        }

        assertEquals(listOf(8, 9, 10, 11, 8), shown)
    }

    /** A cell is drawn for several frames; replacing the animation only when it changes keeps that to one object per cell. */
    @Test
    fun theAnimationIsReplacedOnlyWhenTheCellChanges() {
        val world = World()
        val entity = world.spriteWith(TextureClips(sheet))
        val system = TextureClipSystem()
        system.update(world, 0f)
        val first = assertNotNull(world.get<TextureAnimation>(entity))

        system.update(world, 0.01f)
        system.update(world, 0.01f)
        val same = assertNotNull(world.get<TextureAnimation>(entity))
        system.update(world, 0.1f)
        val next = assertNotNull(world.get<TextureAnimation>(entity))

        assertSame(first, same, "still inside the first cell")
        assertEquals(9, next.firstFrame)
    }

    @Test
    fun everyEntityIsGivenItsAnimationEvenWhenManyAreAddedInOneStep() {
        val world = World()
        val sprites = (0 until 5).map { world.spriteWith(TextureClips(sheet)) }

        TextureClipSystem().update(world, 0f)

        sprites.forEach { assertEquals(held(8), world.get<TextureAnimation>(it)) }
    }

    @Test
    fun asGamesChoiceOfClipShowsOnTheNextStep() {
        val world = World()
        val clips = TextureClips(sheet)
        val entity = world.spriteWith(clips)
        val system = TextureClipSystem()
        system.update(world, 0.25f)

        clips.play("attack")
        system.update(world, 0f)

        assertEquals(held(12), world.get<TextureAnimation>(entity))
    }

    @Test
    fun aPausedClockHoldsEveryCell() {
        val world = World()
        val entity = world.spriteWith(TextureClips(sheet))
        val system = TextureClipSystem()
        system.update(world, 0.25f)
        val before = world.get<TextureAnimation>(entity)

        repeat(100) { system.update(world, 0f) }

        assertSame(before, world.get<TextureAnimation>(entity))
    }

    /** The chain a player sees: the clock steps, the collector packs the draw, and the shader gets the cell in `textureScroll.w`. */
    @Test
    fun theCellReachesTheDrawsUniformsThroughTheCollector() {
        val world = World()
        val mesh = fakeMesh()
        val clips = TextureClips(sheet)
        val entity = world.spriteWith(clips)
        world.add(entity, Transform())
        world.add(entity, MeshRenderer(mesh, fakeMaterial()))
        val camera = Camera(Lens(eye = Vec3f(0f, 0f, 5f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 100f))
        val culling = SceneCullingCompiler(ClipSpace.WebGpu)
        val collector = SceneDrawCollector(culling)
        val system = TextureClipSystem()

        fun cellDrawn(): Float {
            val draws = collector.collectBeforeParticles(world, culling.prepare(world, camera), elapsedTimeSeconds = 0f)
            val payload = draws.single { it.mesh === mesh }.extraUniformFloats
            return MaterialUniformLayouts.PbrTexturedMaterial.readVec4(payload, UniformFields.TextureScroll).w
        }

        system.update(world, 0f)
        val first = cellDrawn()
        system.update(world, 0.25f)
        val later = cellDrawn()
        clips.play("attack")
        system.update(world, 0f)
        val attacking = cellDrawn()

        assertEquals(listOf(8f, 10f, 12f), listOf(first, later, attacking))
    }

    private fun fakeMesh(): Mesh = object : Mesh {
        override val format = VertexFormat.PositionNormalColorUv
        override val sizeBytes = 0L
        override fun destroy() = Unit
    }

    private fun fakeMaterial(): Material = object : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }
}
