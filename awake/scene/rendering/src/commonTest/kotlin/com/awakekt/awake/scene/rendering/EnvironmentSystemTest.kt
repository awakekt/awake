/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.rendering.environment.AmbientLightBinding
import com.awakekt.awake.scene.rendering.environment.AmbientLightBinding.toComponent
import com.awakekt.awake.scene.rendering.environment.AmbientLightBinding.toSceneComponent
import com.awakekt.awake.scene.rendering.environment.EnvironmentSystem
import com.awakekt.awake.scene.rendering.environment.FogBinding
import com.awakekt.awake.scene.rendering.environment.FogBinding.toComponent
import com.awakekt.awake.scene.rendering.environment.FogBinding.toSceneComponent
import com.awakekt.awake.scene.rendering.environment.SkyboxBinding
import com.awakekt.awake.scene.rendering.environment.SkyboxBinding.toComponent
import com.awakekt.awake.scene.rendering.environment.SkyboxBinding.toSceneComponent
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnvironmentSystemTest {

    private class FakeRenderer : NoopRenderer() {
        override var showEnvironment: Boolean = false
        override var horizonColor: Color = Color.Black
        override var zenithColor: Color = Color.Black
        override var fogDensity: Float = 0f
        override var fogColor: Color = Color.Black
        override var shadowsEnabled: Boolean = false
    }

    @Test
    fun decoupledSkyboxAndFogArePushedToRenderer() {
        val renderer = FakeRenderer()
        val system = EnvironmentSystem(renderer)
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

        val fogEntity = world.create()
        world.add(
            fogEntity,
            Fog(
                enabled = true,
                density = 0.05f,
                color = Color(0.7f, 0.8f, 0.9f, 1f),
            ),
        )

        system.update(world, 1f / 60f)

        assertTrue(renderer.showEnvironment)
        assertEquals(Color(0.1f, 0.2f, 0.3f, 1f), renderer.horizonColor)
        assertEquals(Color(0.4f, 0.5f, 0.6f, 1f), renderer.zenithColor)
        assertEquals(0.05f, renderer.fogDensity)
        assertEquals(Color(0.7f, 0.8f, 0.9f, 1f), renderer.fogColor)
    }

    @Test
    fun disabledSkyboxAndFogTurnOffInRenderer() {
        val renderer = FakeRenderer()
        val system = EnvironmentSystem(renderer)
        val world = World()

        val skyEntity = world.create()
        world.add(skyEntity, Skybox(enabled = false))

        val fogEntity = world.create()
        world.add(fogEntity, Fog(enabled = false, density = 0.1f))

        system.update(world, 1f / 60f)

        assertFalse(renderer.showEnvironment)
        assertEquals(0f, renderer.fogDensity)
    }

    @Test
    fun fallsBackToLegacyEnvironmentWhenDecoupledAbsent() {
        val renderer = FakeRenderer()
        val system = EnvironmentSystem(renderer)
        val world = World()

        val envEntity = world.create()
        world.add(
            envEntity,
            Environment(
                showEnvironment = true,
                horizonColor = Color(0.2f, 0.3f, 0.4f, 1f),
                zenithColor = Color(0.5f, 0.6f, 0.7f, 1f),
                fogDensity = 0.02f,
                fogColor = Color(0.8f, 0.8f, 0.8f, 1f),
            ),
        )

        system.update(world, 1f / 60f)

        assertTrue(renderer.showEnvironment)
        assertEquals(Color(0.2f, 0.3f, 0.4f, 1f), renderer.horizonColor)
        assertEquals(0.02f, renderer.fogDensity)
    }

    @Test
    fun directionalLightShadowToggleSyncs() {
        val renderer = FakeRenderer()
        val system = EnvironmentSystem(renderer)
        val world = World()

        val lightEntity = world.create()
        world.add(lightEntity, Light(type = Light.Type.Directional, shadowsEnabled = true))

        system.update(world, 1f / 60f)
        assertTrue(renderer.shadowsEnabled)

        world.add(lightEntity, Light(type = Light.Type.Directional, shadowsEnabled = false))
        system.update(world, 1f / 60f)
        assertFalse(renderer.shadowsEnabled)
    }

    @Test
    fun bindingsSerializeAndDeserialize() {
        val json = Json { prettyPrint = true }

        val skybox = Skybox(
            enabled = true,
            horizonColor = Color(0.1f, 0.2f, 0.3f, 1f),
            zenithColor = Color(0.4f, 0.5f, 0.6f, 1f),
            cubemapPath = "textures/skybox/sky.hdr",
            exposure = 1.5f,
            type = Skybox.Type.Cubemap,
        )
        val sceneSkybox = skybox.toSceneComponent()
        val skyboxJson = json.encodeToString(SkyboxBinding.serializer, sceneSkybox)
        val decodedSkybox = json.decodeFromString(SkyboxBinding.serializer, skyboxJson).toComponent()
        assertEquals(skybox, decodedSkybox)

        val fog = Fog(enabled = true, density = 0.03f, color = Color(0.5f, 0.6f, 0.7f, 1f))
        val sceneFog = fog.toSceneComponent()
        val fogJson = json.encodeToString(FogBinding.serializer, sceneFog)
        val decodedFog = json.decodeFromString(FogBinding.serializer, fogJson).toComponent()
        assertEquals(fog, decodedFog)

        val ambient = AmbientLight(intensity = 0.4f, color = Color(0.9f, 0.8f, 0.7f, 1f))
        val sceneAmbient = ambient.toSceneComponent()
        val ambientJson = json.encodeToString(AmbientLightBinding.serializer, sceneAmbient)
        val decodedAmbient = json.decodeFromString(AmbientLightBinding.serializer, ambientJson).toComponent()
        assertEquals(ambient, decodedAmbient)
    }
}
