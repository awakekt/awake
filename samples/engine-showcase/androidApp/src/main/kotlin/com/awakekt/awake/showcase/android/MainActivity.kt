/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.android

import android.app.Activity
import android.os.Bundle
import com.awakekt.awake.engine.window.AwakeSurfaceView
import com.awakekt.awake.showcase.app.createEngineShowcaseVulkanApplication
import com.awakekt.awake.showcase.app.engineShowcaseApp
import com.awakekt.awake.showcase.showcaseIdOf
import com.awakekt.awake.showcase.showcaseLaunchOptions

/**
 * The showcase on Android. String extras pick the demo and its options, as the desktop's `-P`
 * properties do, for example
 * `adb shell am start -n com.awakekt.awake.showcase.android/.MainActivity --es showcase many-followers --es benchmark true`.
 */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val extras = intent?.extras
        val arguments = LAUNCH_KEYS.mapNotNull { key -> extras?.getString(key)?.let { key to it } }.toMap()
        val showcase = showcaseIdOf(arguments)
        val options = showcaseLaunchOptions(arguments)
        val game = if (showcase == null) engineShowcaseApp(options = options) else engineShowcaseApp(showcase, options)
        setContentView(AwakeSurfaceView(this, createEngineShowcaseVulkanApplication(game)))
    }

    private companion object {
        val LAUNCH_KEYS = listOf("showcase", "vsync", "perfLog", "entities", "followers", "benchmark")
    }
}
