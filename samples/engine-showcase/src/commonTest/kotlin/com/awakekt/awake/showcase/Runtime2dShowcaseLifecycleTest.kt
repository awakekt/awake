/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.scene.physics.PhysicsBody
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.scene.scene2d.Tilemap
import com.awakekt.awake.showcase.app.engineShowcaseApp
import com.awakekt.awake.showcase.docs.DrawlessRenderer
import com.awakekt.awake.showcase.examples.ShowcasePhysics
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Exercise the sample's real activation/deactivation hooks with Jolt and no GPU. */
class Runtime2dShowcaseLifecycleTest {
    @Test
    fun switchingAwayRemovesTheFloorAndSwitchingBackRestoresAuthoredTiles() = runTest {
        val app = engineShowcaseApp(initialShowcaseId = "empty")
        val renderer = DrawlessRenderer()
        try {
            app.ready(renderer)
            val runtime = app.requireService<SceneAppLifecycleRuntime>()
            val loader = EngineShowcaseLoader()
            loader.preload()
            loader.activate("runtime-2d", runtime)
            app.update(STEP, WIDTH, HEIGHT)
            val instance = requireNotNull(runtime.sceneManager.current)
            val floor = instance.roots.single { it.name == "floor" }.let { requireNotNull(runtime.world.get<PhysicsBody>(it.entity)) }
            val map = instance.roots.single { it.name == "tiles" }.let { requireNotNull(runtime.world.get<Tilemap>(it.entity)) }
            val physics = requireNotNull(ShowcasePhysics.world)
            val floorHandle = requireNotNull(floor.handle)
            val rayOrigin = Vec3f(4f, 0f, 0.25f)
            val rayDirection = Vec3f(0f, -1f, 0f)
            assertEquals(floorHandle, assertNotNull(physics.raycast(rayOrigin, rayDirection, 10f)).handle)
            map.setTile(7, 3, 1)

            loader.activate("empty", runtime)
            assertNull(floor.handle, "deactivation must destroy the native body before its entity")
            assertNull(physics.raycast(rayOrigin, rayDirection, 10f), "the old floor must not remain solid")
            app.update(STEP, WIDTH, HEIGHT)

            loader.activate("runtime-2d", runtime)
            app.update(STEP, WIDTH, HEIGHT)
            val restored = requireNotNull(runtime.sceneManager.current).roots.single { it.name == "tiles" }
            assertEquals(-1, requireNotNull(runtime.world.get<Tilemap>(restored.entity)).grid[7, 3])
            assertNotNull(physics.raycast(rayOrigin, rayDirection, 10f), "the new scene must create its own floor")
            loader.activate("empty", runtime)
        } finally {
            app.dispose()
            renderer.destroy()
        }
        assertNull(ShowcasePhysics.world, "app disposal must release its one physics world")
    }

    private companion object {
        const val STEP = 1f / 60f
        const val WIDTH = 384f
        const val HEIGHT = 224f
    }
}
