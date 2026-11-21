/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.animation.SocketAttachmentComponent
import com.awakekt.awake.scene.rendering.mesh.DetailPin
import com.awakekt.awake.scene.rendering.mesh.LodFollow
import com.awakekt.awake.scene.rendering.mesh.LodGroup
import com.awakekt.awake.scene.rendering.mesh.LodLevel
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A [DetailPin]ned entity draws its finest detail level whatever its distance (#1805): its own group,
 * every group that follows it, and the groups of what is socket-attached to it. Beside it, an
 * unpinned group at the same distance draws its far level.
 */
class DetailPinDrawTest {
    private val world = World()
    private val system = RenderSystem3D(RenderSystemTest.RecordingRenderer())

    @Test
    fun aPinnedGroupFarBeyondItsLastThresholdDrawsLevelZero() {
        camera()
        val pinned = model(z = FAR).also { world.add(it, DetailPin) }
        val unpinned = model(z = FAR, x = 2f)
        system.update(world, DELTA)

        assertEquals(0, level(pinned), "pinned")
        assertEquals(FAR_LEVEL, level(unpinned), "unpinned beside it")
    }

    @Test
    fun aPinRemovedDrawsTheFarLevelOnTheNextFrame() {
        camera()
        val model = model(z = FAR).also { world.add(it, DetailPin) }
        system.update(world, DELTA)
        world.remove(model, DetailPin::class)
        system.update(world, DELTA)

        assertEquals(FAR_LEVEL, level(model))
    }

    @Test
    fun theFollowersOfAPinnedGroupDrawLevelZeroToo() {
        camera()
        val body = model(z = FAR).also { world.add(it, DetailPin) }
        val head = model(z = FAR, half = 0.1f).also { group(it).follow = LodFollow(body, levelOffset = 1) }
        val hair = model(z = FAR, half = 0.05f).also { group(it).follow = LodFollow(head, levelOffset = 1) }
        system.update(world, DELTA)

        assertEquals(listOf(0, 0, 0), listOf(level(body), level(head), level(hair)), "a pin overrides a follower's offset")
    }

    @Test
    fun aSocketAttachedEntityOfAPinnedOneIsPinned() {
        camera()
        val character = model(z = FAR).also { world.add(it, DetailPin) }
        val weapon = model(z = FAR, half = 0.2f).also { world.add(it, SocketAttachmentComponent(targetEntity = character)) }
        val rider = model(z = FAR, x = 1f).also { world.add(it, SocketAttachmentComponent(targetEntity = weapon)) }
        val loose = model(z = FAR, x = 3f).also { world.add(it, SocketAttachmentComponent(targetEntity = character, enabled = false)) }
        system.update(world, DELTA)

        assertEquals(0, level(weapon), "attached to the pinned character")
        assertEquals(0, level(rider), "two attachments away")
        assertEquals(FAR_LEVEL, level(loose), "a disabled attachment carries no pin")
    }

    private fun level(entity: Entity): Int = group(entity).activeLevel

    private fun group(entity: Entity): LodGroup = requireNotNull(world.get<LodGroup>(entity))

    private fun camera() {
        val lens = Lens(eye = Vec3f(0f, 0f, 0f), center = Vec3f(0f, 0f, -1f), fovYRadians = 1f, near = 0.1f, far = 2_000f)
        world.add(world.create(), Camera(lens))
    }

    private fun model(z: Float, x: Float = 0f, half: Float = 0.5f): Entity = world.create().also { entity ->
        world.add(entity, Transform(worldMatrix = Mat4().translate(x, 0f, z)))
        world.add(entity, MeshRenderer(FakeMesh(), FakeMaterial()))
        world.add(entity, MeshBounds(Aabb(Vec3f(-half, -half, -half), Vec3f(half, half, half))))
        world.add(
            entity,
            LodGroup(List(LEVELS) { LodLevel(FakeMesh(), FakeMaterial(), maxDistance = 0f) }).also {
                it.minScreenCoverage = floatArrayOf(0.25f, 0.1f, 0f)
            },
        )
    }

    private class FakeMesh : Mesh {
        override val format = VertexFormat.PositionNormalColor
        override val sizeBytes = 0L
        override fun destroy() = Unit
    }

    private class FakeMaterial : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }

    private companion object {
        const val DELTA = 1f / 60f
        const val LEVELS = 3
        const val FAR_LEVEL = LEVELS - 1

        /** A kilometre out: far past the last coverage threshold of every model here. */
        const val FAR = -1_000f
    }
}
