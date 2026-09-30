/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.docs.vulkan

import com.awakekt.awake.asset.shaders.RenderPlan
import com.awakekt.awake.core.input.PointerCursor
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.vulkan.application.runVulkanDesktopGame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

// --8<-- [start:cursor]
fun runWithUiCursor(game: AwakeAppLifecycle, plan: RenderPlan) {
    val runtime = game.requireService<SceneAppLifecycleRuntime>()
    // Each frame the window shows the pointer shape the UI under it asks for.
    runVulkanDesktopGame(game, plan, cursor = { runtime.cursor })
}
// --8<-- [end:cursor]

/**
 * The "Vulkan backend" guide's cursor sample. Opening a window needs a display, so this checks the
 * sample compiles against the real overload and that the cursor it reads exists before the loop.
 */
class VulkanDocsSampleTest {

    @Test
    fun theRuntimeCursorIsReadableBeforeTheWindowOpens() {
        val game = app { scene("hello") {} }
        val launch: (AwakeAppLifecycle, RenderPlan) -> Unit = ::runWithUiCursor

        assertEquals(PointerCursor.Default, game.requireService<SceneAppLifecycleRuntime>().cursor)
        assertNotNull(launch)
    }
}
