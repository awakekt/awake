/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.rendering

import io.github.awakelab.awake.core.animation.AnimationLibrary
import io.github.awakelab.awake.core.animation.AnimationPlayer
import io.github.awakelab.awake.core.animation.Skin
import io.github.awakelab.awake.core.geometry.VertexFormat
import io.github.awakelab.awake.core.math.Mat4
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.render.material.Material
import io.github.awakelab.awake.render.mesh.Mesh
import io.github.awakelab.awake.scene.rendering.animation.Animator
import io.github.awakelab.awake.scene.rendering.animation.ModularCharacterComponent
import io.github.awakelab.awake.scene.rendering.animation.ModularSkeletalSystem
import io.github.awakelab.awake.scene.rendering.animation.SkinnedPose
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ModularCharacterComponentTest {

    private val testSkin = Skin(joints = listOf(0), inverseBindMatrices = listOf(Mat4()))

    private fun fakeMesh(): Mesh = object : Mesh {
        override val format = VertexFormat.PositionNormalColor
        override val sizeBytes: Long = 0
        override fun destroy() = Unit
    }

    private fun fakeMaterial(): Material = object : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }

    @Test
    fun modularCharacterComponentEquipsAndUnequipsSlots() {
        val character = ModularCharacterComponent(skin = testSkin)
        assertEquals(0, character.slots.size)
        assertEquals(0, character.visibleSlotCount)

        val hairMesh = fakeMesh()
        val hairMat = fakeMaterial()
        character.equip("hair", hairMesh, hairMat)

        assertTrue(character.hasSlot("hair"))
        assertEquals(1, character.slots.size)
        assertEquals(1, character.visibleSlotCount)
        assertNotNull(character.getSlot("hair"))

        val chestMesh = fakeMesh()
        val chestMat = fakeMaterial()
        character.equip("chest", chestMesh, chestMat)
        assertEquals(2, character.slots.size)
        assertEquals(2, character.visibleSlotCount)

        // Toggle visibility
        character.getSlot("hair")!!.isVisible = false
        assertEquals(1, character.visibleSlotCount)

        val unequipped = character.unequip("hair")
        assertNotNull(unequipped)
        assertEquals(1, character.slots.size)

        character.clearSlots()
        assertEquals(0, character.slots.size)
    }

    @Test
    fun modularSkeletalSystemUpdatesSharedSkinnedPose() {
        val world = World()
        val system = ModularSkeletalSystem()

        val entity = world.create()
        val character = ModularCharacterComponent(skin = testSkin)

        val bone = io.github.awakelab.awake.core.animation.Bone(
            translation = io.github.awakelab.awake.core.math.Vec3f.ZERO,
            rotation = io.github.awakelab.awake.core.math.Quat(),
            scale = io.github.awakelab.awake.core.math.Vec3f(1f, 1f, 1f),
            matrix = null,
            children = emptyList(),
        )
        val skeleton = io.github.awakelab.awake.core.animation.Skeleton(
            bones = listOf(bone),
            roots = listOf(0),
        )
        val clip = io.github.awakelab.awake.core.animation.AnimationClip(
            name = "idle",
            channels = emptyList(),
        )
        val library = AnimationLibrary(skeleton = skeleton, clips = mapOf("idle" to clip))
        val player = AnimationPlayer(library)
        val animator = Animator(player = player, skin = testSkin)

        world.add(entity, character)
        world.add(entity, animator)

        system.update(world, delta = 0.016f)

        val pose = world.get<SkinnedPose>(entity)
        assertNotNull(pose, "ModularSkeletalSystem must attach SkinnedPose to the entity.")
        assertEquals(16, pose.jointPalette.size)
    }
}
