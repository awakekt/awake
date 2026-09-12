/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.rendering.environment.SkyboxSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SkyboxSystemTest {

    private class FakeRenderer : NoopRenderer() {
        override var showEnvironment: Boolean = false
        override var horizonColor: Color = Color.Black
        override var zenithColor: Color = Color.Black
    }

    @Test
    fun skyboxParametersArePushedToRenderer() {
        val renderer = FakeRenderer()
        val system = SkyboxSystem(renderer)
        val world = World()

        val skyEntity = world.create()
        world.add(
            skyEntity,
            Skybox(
                enabled = true,
                horizonColor = Color(0.1f, 0.2f, 0.3f, 1f),
                zenithColor = Color(0.4f, 0.5f, 0.6f, 1f),
            ),
        )

        system.update(world, 1f / 60f)

        assertTrue(renderer.showEnvironment)
        assertEquals(Color(0.1f, 0.2f, 0.3f, 1f), renderer.horizonColor)
        assertEquals(Color(0.4f, 0.5f, 0.6f, 1f), renderer.zenithColor)
    }

    @Test
    fun disabledSkyboxTurnsOffInRenderer() {
        val renderer = FakeRenderer()
        val system = SkyboxSystem(renderer)
        val world = World()

        val skyEntity = world.create()
        world.add(skyEntity, Skybox(enabled = false))

        system.update(world, 1f / 60f)

        assertFalse(renderer.showEnvironment)
    }

    @Test
    fun fallsBackToLegacyEnvironmentWhenSkyboxAbsent() {
        val renderer = FakeRenderer()
        val system = SkyboxSystem(renderer)
        val world = World()

        val envEntity = world.create()
        world.add(
            envEntity,
            Environment(
                showEnvironment = true,
                horizonColor = Color(0.2f, 0.3f, 0.4f, 1f),
                zenithColor = Color(0.5f, 0.6f, 0.7f, 1f),
            ),
        )

        system.update(world, 1f / 60f)

        assertTrue(renderer.showEnvironment)
        assertEquals(Color(0.2f, 0.3f, 0.4f, 1f), renderer.horizonColor)
        assertEquals(Color(0.5f, 0.6f, 0.7f, 1f), renderer.zenithColor)
    }
}
