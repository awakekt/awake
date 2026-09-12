/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.animation.SkinnedPose
import com.awakekt.awake.scene.rendering.animation.SocketAttachmentComponent
import com.awakekt.awake.scene.rendering.animation.SocketAttachmentSystem
import kotlin.test.Test
import kotlin.test.assertEquals

class SocketAttachmentSystemTest {

    @Test
    fun tracksJointTranslationInWorldSpace() {
        val world = World()
        val system = SocketAttachmentSystem()

        // Create character entity with Transform and SkinnedPose
        val character = world.create()
        val charTransform = Transform(position = Vec3f(10f, 0f, 5f))
        world.add(character, charTransform)

        // Joint palette with joint 0 translated by (1f, 2f, 3f)
        val palette = FloatArray(32)
        palette[0] = 1f
        palette[5] = 1f
        palette[10] = 1f
        palette[15] = 1f
        palette[12] = 1f
        palette[13] = 2f
        palette[14] = 3f

        // Joint 1 translated by (4f, 5f, 6f)
        palette[16] = 1f
        palette[21] = 1f
        palette[26] = 1f
        palette[31] = 1f
        palette[28] = 4f
        palette[29] = 5f
        palette[30] = 6f

        world.add(character, SkinnedPose(palette))

        val weapon = world.create()
        val weaponTransform = Transform(position = Vec3f(0f, 0f, 0f))
        world.add(weapon, weaponTransform)
        world.add(
            weapon,
            SocketAttachmentComponent(
                targetEntity = character,
                jointIndex = 0,
                offsetPosition = Vec3f(0.5f, 0f, 0f),
            ),
        )

        system.update(world, 0.016f)

        assertEquals(11.5f, weaponTransform.position.x)
        assertEquals(2.0f, weaponTransform.position.y)
        assertEquals(8.0f, weaponTransform.position.z)

        val attachment = world.get<SocketAttachmentComponent>(weapon)!!
        attachment.jointIndex = 1
        attachment.offsetPosition = Vec3f(0f, 0f, 0f)

        system.update(world, 0.016f)

        assertEquals(14.0f, weaponTransform.position.x)
        assertEquals(5.0f, weaponTransform.position.y)
        assertEquals(11.0f, weaponTransform.position.z)
    }
}
