/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.studio

import io.github.awakelab.awake.core.logging.Log
import io.github.awakelab.awake.core.logging.LogLevel
import io.github.awakelab.awake.core.logging.LogRingBuffer
import io.github.awakelab.awake.core.logging.Logger
import io.github.awakelab.awake.engine.bootstrap.dsl.app
import io.github.awakelab.awake.engine.bootstrap.dsl.module
import io.github.awakelab.awake.studio.state.StudioStore
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The runtime stamps records with the frame they happened in.
 *
 * That stamp is the reason `LogRecord` carries a frame at all, and it is only true if something
 * advances the counter at a frame boundary. Nothing else can: a logging call site has no idea where
 * a frame begins.
 */
class StudioLoggingTest {

    @AfterTest
    fun uninstall() {
        Log.reset()
    }

    @Test
    fun recordsAreStampedWithTheFrameTheyHappenedIn() = runTest {
        val buffer = LogRingBuffer(capacity = 16)
        val game = app { module(studioModule(StudioStore())) }
        game.ready(RecordingCameraRenderer())

        // Installed after ready() so Studio's own startup records do not fill the buffer first.
        Log.reset()
        Log.install(buffer)
        Log.minimumLevel = LogLevel.Trace
        val log = Logger("test")

        game.update(1f / 60f, 1440f, 900f)
        log.info { "after one frame" }
        val first = buffer[0].frame

        game.update(1f / 60f, 1440f, 900f)
        log.info { "after two" }
        val second = buffer[1].frame

        assertEquals(
            first + 1,
            second,
            "each runtime frame must advance the stamp, or every record reads as one frame",
        )
    }

    @Test
    fun studioInstallsItsOwnSinksAndListensAtDebug() = runTest {
        val game = app { module(studioModule(StudioStore())) }
        game.ready(RecordingCameraRenderer())

        assertTrue(Log.hasSinks, "studio is a dev tool and listens by default")
        assertTrue(
            Log.isEnabled(LogLevel.Debug),
            "studio lowers the threshold to Debug; a game would install nothing and pay nothing",
        )
    }
}
