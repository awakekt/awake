/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.android

import android.app.Activity
import android.os.Bundle
import com.awakekt.awake.engine.platform.VulkanView
import com.awakekt.awake.showcase.app.createEngineShowcaseVulkanApplication

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val application = createEngineShowcaseVulkanApplication()
        setContentView(VulkanView(this, application))
    }
}
