/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio.state

import com.awakekt.awake.core.logging.Log
import com.awakekt.awake.core.logging.LogLevel
import com.awakekt.awake.core.logging.LogRingBuffer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class StudioStoreLoggingTest {

    private val buffer = LogRingBuffer(capacity = 32)

    @BeforeTest
    fun setup() {
        Log.reset()
        Log.install(buffer)
        Log.minimumLevel = LogLevel.Debug
    }

    @AfterTest
    fun tearDown() {
        Log.reset()
    }

    @Test
    fun storeIntentsAreLoggedToConsoleBuffer() {
        val store = StudioStore()

        store.startPlay()
        store.stopPlay()
        store.reloadFixture()
        store.dispatch(StudioContract.Intent.SaveScene)
        store.dispatch(StudioContract.Intent.SceneSaved("/path/to/scene.json"))

        val messages = buffer.snapshot().map { it.message }

        assertTrue(messages.any { it.contains("Entering play mode") })
        assertTrue(messages.any { it.contains("Exiting play mode") })
        assertTrue(messages.any { it.contains("Reloading default scene fixture") })
        assertTrue(messages.any { it.contains("Save scene requested") })
        assertTrue(messages.any { it.contains("Scene saved successfully to: /path/to/scene.json") })
    }
}
