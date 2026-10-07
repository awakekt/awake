/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.app

import com.awakekt.awake.core.audio.AudioPlayerFactory
import com.awakekt.awake.core.audio.WebAudioPlayer
import com.awakekt.awake.webgpu.application.launchWebGpuGame

fun main() {
    installShowcaseWebAudio()
    launchWebGpuGame(applicationFactory = ::createEngineShowcaseWebGpuApplication)
}

/** Install eagerly so the scene-selection gesture can unlock sound before its first frame. */
internal fun installShowcaseWebAudio(): WebAudioPlayer = WebAudioPlayer().also { player ->
    AudioPlayerFactory.provider = { player }
}
