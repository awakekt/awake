/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.docs.browser

import com.awakekt.awake.engine.platform.dsl.AppWindowBackend
import com.awakekt.awake.showcase.docs.GameRenderPlan
import com.awakekt.awake.showcase.docs.firstScene
import kotlin.test.Test
import kotlin.test.assertEquals
// --8<-- [start:imports]
import com.awakekt.awake.webgpu.application.WebGpuEngine
import com.awakekt.awake.webgpu.application.launchWebGpuGame
// --8<-- [end:imports]

/**
 * Holds the "Run in the browser" entry point so it keeps compiling against the WebGPU backend. A
 * member, not a top-level `main`, so the test bundle never launches it: a test page has no canvas.
 */
@Suppress("unused")
private object BrowserEntryPoint {
    // --8<-- [start:main]
    fun main() = launchWebGpuGame {
        WebGpuEngine(appLifecycle = firstScene(), requestedPlan = GameRenderPlan)
    }
    // --8<-- [end:main]
}

class RunInTheBrowserDocsSampleTest {

    /** `runVulkanDesktopGame` accepts `DEFAULT` too, which is what lets one app serve both hosts. */
    @Test
    fun theSharedAppDoesNotPinABackend() {
        assertEquals(AppWindowBackend.DEFAULT, firstScene().windowConfig.backend)
    }
}
