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

@OptIn(ExperimentalCoroutinesApi::class)
class SkyboxCubemapSystemTest {

    private class RecordingHost : ContentFeatureHost {
        var attached = 0
        var detached = 0

        override suspend fun attachContentFeature(source: ContentFeatureSource): AttachedContentFeature {
            attached++
            return AttachedContentFeature { detached++ }
        }
    }

    @Test
    fun aCubemapSkyAttachesSwapsAndDetachesWithItsMode() = runTest {
        val host = RecordingHost()
        val world = World()
        val sky = Skybox(mode = Skybox.Mode.Cubemap(assetPath = "sky/day.png"))
        world.add(world.create(), sky)
        val system = SkyboxCubemapSystem(host, this, AssetSource { Result.success(STRIP) })

        frame(system, world)
        assertEquals(1 to 0, host.attached to host.detached, "the cubemap sky attaches once loaded")

        sky.mode = Skybox.Mode.Cubemap(assetPath = "sky/sunset.png")
        frame(system, world)
        assertEquals(2 to 1, host.attached to host.detached, "a new strip replaces the old one")

        sky.mode = Skybox.Mode.Procedural()
        frame(system, world)
        assertEquals(2 to 2, host.attached to host.detached, "leaving cubemap mode detaches it")
    }

    @Test
    fun aStripThatFailsToLoadAttachesNothing() = runTest {
        val host = RecordingHost()
        val world = World()
        world.add(world.create(), Skybox(mode = Skybox.Mode.Cubemap(assetPath = "sky/missing.png")))
        val system = SkyboxCubemapSystem(host, this, AssetSource { Result.failure(IllegalStateException("missing")) })

        frame(system, world)

        assertEquals(0, host.attached)
    }

    /** One update to start a load, the load itself, and one update to take its result. */
    private fun TestScope.frame(system: SkyboxCubemapSystem, world: World) {
        system.update(world, 0f)
        advanceUntilIdle()
        system.update(world, 0f)
    }

    private companion object {
        /** Six 2 x 2 faces. */
        val STRIP: ByteArray = ByteArrayOutputStream().also {
            ImageIO.write(BufferedImage(12, 2, BufferedImage.TYPE_INT_ARGB), "png", it)
        }.toByteArray()
    }
}
