/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.rendering.environment.FogSystem
import kotlin.test.Test
import kotlin.test.assertEquals

class FogSystemTest {

    private class FakeRenderer : NoopRenderer() {
        override var fogDensity: Float = 0f
        override var fogColor: Color = Color.Black
    }

    @Test
    fun fogParametersArePushedToRenderer() {
        val renderer = FakeRenderer()
        val system = FogSystem(renderer)
        val world = World()

        val fogEntity = world.create()
        world.add(
            fogEntity,
            Fog(
                enabled = true,
                density = 0.04f,
                color = Color(0.6f, 0.7f, 0.8f, 1f),
            ),
        )

        system.update(world, 1f / 60f)

        assertEquals(0.04f, renderer.fogDensity)
        assertEquals(Color(0.6f, 0.7f, 0.8f, 1f), renderer.fogColor)
    }

    @Test
    fun disabledFogTurnsOffInRenderer() {
        val renderer = FakeRenderer()
        val system = FogSystem(renderer)
        val world = World()

        val fogEntity = world.create()
        world.add(fogEntity, Fog(enabled = false, density = 0.05f))

        system.update(world, 1f / 60f)

        assertEquals(0f, renderer.fogDensity)
    }

    @Test
    fun fallsBackToLegacyEnvironmentWhenFogAbsent() {
        val renderer = FakeRenderer()
        val system = FogSystem(renderer)
        val world = World()

        val envEntity = world.create()
        world.add(
            envEntity,
            Environment(
                fogDensity = 0.015f,
                fogColor = Color(0.5f, 0.5f, 0.5f, 1f),
            ),
        )

        system.update(world, 1f / 60f)

        assertEquals(0.015f, renderer.fogDensity)
        assertEquals(Color(0.5f, 0.5f, 0.5f, 1f), renderer.fogColor)
    }
}
