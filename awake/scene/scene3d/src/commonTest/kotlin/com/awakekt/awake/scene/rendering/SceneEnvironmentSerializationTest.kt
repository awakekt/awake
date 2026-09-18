/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.scene.document.SceneColor
import com.awakekt.awake.scene.document.SceneVec3
import com.awakekt.awake.scene.rendering.fog.FogBinding.toComponent
import com.awakekt.awake.scene.rendering.fog.SceneFog
import com.awakekt.awake.scene.rendering.light.AmbientLightBinding.toComponent
import com.awakekt.awake.scene.rendering.light.LightBinding.toComponent
import com.awakekt.awake.scene.rendering.light.SceneAmbientLight
import com.awakekt.awake.scene.rendering.light.SceneLight
import com.awakekt.awake.scene.rendering.sky.SceneSkybox
import com.awakekt.awake.scene.rendering.sky.Skybox
import com.awakekt.awake.scene.rendering.sky.SkyboxBinding.toComponent
import com.awakekt.awake.scene.rendering.sky.SkyboxBinding.toSceneComponent
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SceneEnvironmentSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    @Test
    fun testSkyboxSerializationWithSceneColor() {
        val skybox = SceneSkybox(
            enabled = true,
            horizonColor = SceneColor(0.8f, 0.9f, 1.0f, 1f),
            zenithColor = SceneColor.fromHex("#3361AD"),
        )
        val serialized = json.encodeToString(SceneSkybox.serializer(), skybox)
        val deserialized = json.decodeFromString(SceneSkybox.serializer(), serialized)

        assertEquals(skybox.horizonColor.r, deserialized.horizonColor.r)
        assertEquals(skybox.zenithColor.r, deserialized.zenithColor.r)

        val component = deserialized.toComponent()
        assertEquals(0.8f, component.horizonColor.r)
        assertTrue(component.zenithColor.r in 0.19f..0.21f)
        assertIs<Skybox.Mode.Procedural>(component.mode)
    }

    @Test
    fun testSkyboxSealedModesSerialization() {
        val cubemapSkybox = Skybox(
            enabled = true,
            mode = Skybox.Mode.Cubemap(assetPath = "textures/sky.ktx2", exposure = 1.5f),
        )
        val cubemapScene = cubemapSkybox.toSceneComponent()
        assertEquals("Cubemap", cubemapScene.type)
        assertEquals("textures/sky.ktx2", cubemapScene.cubemapPath)
        assertEquals(1.5f, cubemapScene.exposure)

        val restoredCubemap = cubemapScene.toComponent()
        assertIs<Skybox.Mode.Cubemap>(restoredCubemap.mode)
        val cubemapMode = restoredCubemap.mode as Skybox.Mode.Cubemap
        assertEquals("textures/sky.ktx2", cubemapMode.assetPath)
        assertEquals(1.5f, cubemapMode.exposure)

        val solidSkybox = Skybox(
            enabled = true,
            mode = Skybox.Mode.SolidColor(color = com.awakekt.awake.core.color.Color(0.2f, 0.3f, 0.4f, 1f)),
        )
        val solidScene = solidSkybox.toSceneComponent()
        assertEquals("SolidColor", solidScene.type)
        val restoredSolid = solidScene.toComponent()
        assertIs<Skybox.Mode.SolidColor>(restoredSolid.mode)
        val solidMode = restoredSolid.mode as Skybox.Mode.SolidColor
        assertEquals(0.2f, solidMode.color.r)
    }

    @Test
    fun testSkyboxLegacyJsonCompatibility() {
        val legacyJson = """
            {
                "enabled": true,
                "horizonColorR": 0.5,
                "horizonColorG": 0.6,
                "horizonColorB": 0.7,
                "zenithColorR": 0.1,
                "zenithColorG": 0.2,
                "zenithColorB": 0.3
            }
        """.trimIndent()
        val deserialized = json.decodeFromString(SceneSkybox.serializer(), legacyJson)
        val component = deserialized.toComponent()

        assertEquals(0.5f, component.horizonColor.r)
        assertEquals(0.6f, component.horizonColor.g)
        assertEquals(0.7f, component.horizonColor.b)
        assertEquals(0.1f, component.zenithColor.r)
        assertEquals(0.2f, component.zenithColor.g)
        assertEquals(0.3f, component.zenithColor.b)
    }

    @Test
    fun testFogSerializationWithSceneColor() {
        val fog = SceneFog(
            enabled = true,
            density = 0.02f,
            color = SceneColor.fromHex("#8090A0"),
        )
        val serialized = json.encodeToString(SceneFog.serializer(), fog)
        val deserialized = json.decodeFromString(SceneFog.serializer(), serialized)

        val component = deserialized.toComponent()
        assertEquals(0.02f, component.density)
        assertTrue(component.color.r in 0.49f..0.51f)
    }

    @Test
    fun testFogLegacyJsonCompatibility() {
        val legacyJson = """
            {
                "enabled": true,
                "density": 0.05,
                "colorR": 0.7,
                "colorG": 0.8,
                "colorB": 0.9
            }
        """.trimIndent()
        val deserialized = json.decodeFromString(SceneFog.serializer(), legacyJson)
        val component = deserialized.toComponent()

        assertEquals(0.05f, component.density)
        assertEquals(0.7f, component.color.r)
        assertEquals(0.8f, component.color.g)
        assertEquals(0.9f, component.color.b)
    }

    @Test
    fun testAmbientLightSerializationAndLegacy() {
        val ambient = SceneAmbientLight(
            intensity = 0.4f,
            color = SceneColor(1f, 0.9f, 0.8f, 1f),
        )
        val serialized = json.encodeToString(SceneAmbientLight.serializer(), ambient)
        val deserialized = json.decodeFromString(SceneAmbientLight.serializer(), serialized)
        assertEquals(0.4f, deserialized.toComponent().intensity)
        assertEquals(0.9f, deserialized.toComponent().color.g)

        val legacyJson = """{ "intensity": 0.5, "colorR": 0.2, "colorG": 0.3, "colorB": 0.4 }"""
        val legacy = json.decodeFromString(SceneAmbientLight.serializer(), legacyJson).toComponent()
        assertEquals(0.5f, legacy.intensity)
        assertEquals(0.2f, legacy.color.r)
    }

    @Test
    fun testLightWithSceneColorAndLegacyVec3() {
        val lightWithColor = SceneLight(
            color = SceneColor.fromHex("#FFAA00"),
            intensity = 2f,
            type = SceneLight.Type.Directional,
            direction = SceneVec3(0f, 1f, 0f),
        )
        val serialized = json.encodeToString(SceneLight.serializer(), lightWithColor)
        val deserialized = json.decodeFromString(SceneLight.serializer(), serialized)
        val component = deserialized.toComponent()
        assertEquals(1f, component.color.x)
        assertTrue(component.color.y in 0.65f..0.68f)

        // Legacy format where color was serialized as Vec3: {"x": 1.0, "y": 0.5, "z": 0.25}
        val legacyJson = """
            {
                "color": { "x": 1.0, "y": 0.5, "z": 0.25 },
                "intensity": 1.5,
                "type": "Directional"
            }
        """.trimIndent()
        val legacyDeserialized = json.decodeFromString(SceneLight.serializer(), legacyJson).toComponent()
        assertEquals(1.0f, legacyDeserialized.color.x)
        assertEquals(0.5f, legacyDeserialized.color.y)
        assertEquals(0.25f, legacyDeserialized.color.z)
    }
}
