/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.sky

import com.awakekt.awake.asset.shaders.AttachedContentFeature
import com.awakekt.awake.asset.shaders.ContentFeatureHost
import com.awakekt.awake.asset.shaders.ContentFeatureSource
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.ecs.World
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SkyboxCelestialSystemTest {

    private class RecordingHost : ContentFeatureHost {
        var attached = 0
        var detached = 0

        override suspend fun attachContentFeature(source: ContentFeatureSource): AttachedContentFeature {
            attached++
            return AttachedContentFeature { detached++ }
        }
    }

    @Test
    fun aDefaultProceduralSkyDrawsItsOwnBodiesAndAttachesNothing() = runTest {
        val host = RecordingHost()
        val world = World()
        val sky = Skybox()
        world.add(world.create(), sky)
        val system = SkyboxCelestialSystem(host, this, AssetSource { Result.success(IMAGE) })

        frame(system, world)

        assertEquals(0, host.attached)
        assertFalse(sky.drawsCelestials)
    }

    @Test
    fun bodiesAttachWhenAskedForOrGivenAPhaseOrATextureAndDetachWhenNot() = runTest {
        val host = RecordingHost()
        val world = World()
        val sky = Skybox(mode = Skybox.Mode.Cubemap(assetPath = "sky.png")).also { it.sunAndMoon = true }
        world.add(world.create(), sky)
        val system = SkyboxCelestialSystem(host, this, AssetSource { Result.success(IMAGE) })

        frame(system, world)
        assertEquals(1 to 0, host.attached to host.detached, "a cubemap sky with sunAndMoon draws them")

        sky.moonPhase = 0.25f
        frame(system, world)
        assertEquals(1 to 0, host.attached to host.detached, "a new phase needs no reload")

        sky.sunTexture = "sun.png"
        frame(system, world)
        assertEquals(2 to 1, host.attached to host.detached, "a new texture replaces the feature")

        sky.sunAndMoon = false
        sky.sunTexture = null
        sky.moonPhase = 0.5f
        frame(system, world)
        assertEquals(2 to 2, host.attached to host.detached, "none of it asked for detaches")
        assertTrue(!sky.drawsCelestials)
    }

    @Test
    fun aTextureThatFailsToLoadLeavesTheDisc() = runTest {
        val host = RecordingHost()
        val world = World()
        world.add(world.create(), Skybox().also { it.sunTexture = "missing.png" })
        val system = SkyboxCelestialSystem(host, this, AssetSource { Result.failure(IllegalStateException("missing")) })

        frame(system, world)

        assertEquals(1, host.attached, "the body still draws, as its disc")
    }

    @Test
    fun itDrawsBehindTheCubemapSkyByReattachingWhenTheSkyChanges() = runTest {
        val cubemapHost = RecordingHost()
        val host = RecordingHost()
        val world = World()
        val sky = Skybox(mode = Skybox.Mode.Cubemap(assetPath = "a.png")).also { it.sunAndMoon = true }
        world.add(world.create(), sky)
        val cubemap = SkyboxCubemapSystem(cubemapHost, this, AssetSource { Result.success(STRIP) })
        val system = SkyboxCelestialSystem(host, this, AssetSource { Result.success(IMAGE) }, cubemap)

        frame(cubemap, world)
        frame(system, world)
        assertEquals(1 to 1, cubemapHost.attached to host.attached)

        sky.mode = Skybox.Mode.Cubemap(assetPath = "b.png", exposure = 2f)
        frame(cubemap, world)
        frame(system, world)

        assertEquals(2 to 2, cubemapHost.attached to host.attached, "a new sky is painted over, so the bodies follow it")
        assertEquals(1, host.detached, "the old bodies are replaced, not stacked")
    }

    private fun TestScope.frame(system: SkyboxCelestialSystem, world: World) {
        system.update(world, 0f)
        advanceUntilIdle()
        system.update(world, 0f)
    }

    private fun TestScope.frame(system: SkyboxCubemapSystem, world: World) {
        system.update(world, 0f)
        advanceUntilIdle()
        system.update(world, 0f)
    }

    private companion object {
        fun png(width: Int): ByteArray = ByteArrayOutputStream().also {
            ImageIO.write(BufferedImage(width, 2, BufferedImage.TYPE_INT_ARGB), "png", it)
        }.toByteArray()

        val IMAGE: ByteArray = png(2)

        /** Six 2 x 2 faces. */
        val STRIP: ByteArray = png(12)
    }
}
