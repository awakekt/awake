/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.app

import com.awakekt.awake.core.audio.AudioPlayerFactory
import kotlin.test.Test
import kotlin.test.assertSame

class ShowcaseWebAudioTest {
    @Test
    fun browserStartupInstallsTheRealPlayerBeforeSceneActivation() {
        val previousProvider = AudioPlayerFactory.provider
        val player = installShowcaseWebAudio()
        try {
            assertSame(player, AudioPlayerFactory.create())
        } finally {
            player.dispose()
            AudioPlayerFactory.provider = previousProvider
        }
    }
}
