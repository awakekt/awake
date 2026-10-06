/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.asset.shaders.ContentFeatureHost
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.scene.authoring.SceneAppDsl
import com.awakekt.awake.scene.authoring.dsl.fog
import com.awakekt.awake.scene.authoring.dsl.scene
import com.awakekt.awake.scene.authoring.dsl.skybox
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneValidator
import com.awakekt.awake.scene.rendering.fog.Fog
import com.awakekt.awake.scene.rendering.light.DayCycleSystem
import com.awakekt.awake.scene.rendering.light.Light
import com.awakekt.awake.scene.rendering.sky.Skybox
import com.awakekt.awake.scene.rendering.sky.SkyboxCubemapSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The "Sky and fog" guide shows a gradient sky with fog and a cubemap sky, each as a scene document
 * and in the scene DSL, plus the system a cubemap sky needs, and a day cycle. This keeps them
 * compiling, equal and valid.
 */
class SkyAndFogDocsSampleTest {

    @Test
    fun theSceneDocumentAndTheSceneDslDescribeTheSameSkyAndFog() {
        val world = World()
        // --8<-- [start:sky-dsl]
        world.scene {
            entity("environment") {
                skybox(
                    Skybox(
                        mode = Skybox.Mode.Procedural(
                            horizonColor = Color.fromHex("#F2C9A0"),
                            zenithColor = Color.fromHex("#3A5F9E"),
                        ),
                    ),
                )
                fog(Fog(density = 0.02f, color = Color.fromHex("#C8D2DC")))
            }
        }
        // --8<-- [end:sky-dsl]
        val document = SceneLoader.instantiate(rendering("sky.scene.json")).world

        assertEquals(document.only<Skybox>(), world.only<Skybox>())
        assertEquals(document.only<Fog>(), world.only<Fog>())
        assertEquals(true, world.only<Fog>().enabled)
    }

    @Test
    fun theSceneDocumentAndTheSceneDslDescribeTheSameCubemapSky() {
        val world = World()
        // --8<-- [start:cubemap-dsl]
        world.scene {
            entity("environment") {
                skybox(Skybox(mode = Skybox.Mode.Cubemap(assetPath = "sky/day.png", exposure = 1.2f)))
            }
        }
        // --8<-- [end:cubemap-dsl]
        val document = SceneLoader.instantiate(rendering("cubemap-sky.scene.json")).world

        assertEquals(document.only<Skybox>(), world.only<Skybox>())
        assertEquals(Skybox.Type.Cubemap, world.only<Skybox>().type)
    }

    @Test
    fun theCubemapSystemAttachesTheSkyOnceItsStripLoads() = runTest {
        val renderer = DocsRenderer()
        val strip = AssetSource { Result.success(STRIP) }
        val game = app {
            scene("sky") {
                scene(rendering("cubemap-sky.scene.json"))
                cubemapSky(scope = this@runTest, assets = strip)
            }
        }
        game.ready(renderer)
        game.update(FRAME, WIDTH, HEIGHT)
        advanceUntilIdle()
        game.update(FRAME, WIDTH, HEIGHT)

        assertEquals(1, renderer.attachedFeatures, "the strip becomes one cubemap sky feature")
        game.dispose()
    }

    @Test
    fun theDayCycleSampleIsValidAndLightsTheSceneAtItsStartTime() {
        val document = rendering("day-cycle.scene.json")
        assertEquals(emptyList(), SceneValidator.validate(document))
        val world = SceneLoader.instantiate(document).world
        val authoredHorizon = world.only<Skybox>().horizonColor

        DayCycleSystem().update(world, 0f)

        val sun = world.only<Light>()
        assertTrue(sun.direction.y > 0f, "0.3 is after sunrise")
        assertEquals(0.68f, sun.intensity, 1e-4f, "a fifth of the way from the sunrise stop to noon")
        assertNotEquals(authoredHorizon, world.only<Skybox>().horizonColor)
    }

    private inline fun <reified T : Any> World.only(): T = query(T::class).single().let { get<T>(it)!! }

    private companion object {
        /** Six 2 x 2 faces side by side. */
        val STRIP: ByteArray = ByteArrayOutputStream().also {
            ImageIO.write(BufferedImage(12, 2, BufferedImage.TYPE_INT_ARGB), "png", it)
        }.toByteArray()
    }
}

// --8<-- [start:cubemap-system]
/** Loads the scene's cubemap sky from [assets] off the frame thread. */
fun SceneAppDsl.cubemapSky(scope: CoroutineScope, assets: AssetSource) {
    frameSystem("cubemap-sky") {
        SkyboxCubemapSystem(renderer as ContentFeatureHost, scope, assets)
    }
}
// --8<-- [end:cubemap-system]
