/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.audio.AudioClip
import com.awakekt.awake.core.audio.AudioPlayer
import com.awakekt.awake.core.audio.SoundHandle
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.audio.AudioListener
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.core.Name
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A project's scene plays its sound as data: an `audio_source` loads with Core alone, its WAV clip
 * is read from the project's files, and it plays through the host's audio player, heard from the
 * camera the scene is seen through.
 */
class ProjectAudioTest {
    private val idle = { GameplayInput(Input().currentSnapshot, InputOwnership()) }

    @Test
    fun aScenesSoundLoadsWithCoreAndPlaysThroughTheHostsPlayer() = runTest {
        val project = loadProject(files())
        val world = World()
        project.scene.instantiate(world = world)
        activatePrimaryCamera(world)
        val player = RecordingPlayer()

        project.sceneSystems(idle, audio = player).use { systems ->
            repeat(FRAMES) { systems.frame.forEach { it.update(world, FRAME) } }
        }
        project.close()

        assertEquals(listOf(WIND), player.played.map { it.id }, "the wind plays once")
        assertTrue(player.played.single().pcmBytes.isNotEmpty(), "with the clip read from the project")
        val listener = world.query(AudioListener::class).single()
        assertEquals("Camera", world.get<Name>(listener)?.value, "heard from the active camera")
    }

    @Test
    fun aHostWithNoPlayerRunsTheSoundSilently() = runTest {
        val project = loadProject(files())
        val world = World()
        project.scene.instantiate(world = world)

        project.sceneSystems(idle).use { systems ->
            repeat(FRAMES) { systems.frame.forEach { it.update(world, FRAME) } }
        }
        project.close()
    }

    private fun files(): AssetSource {
        val text = mapOf(PROJECT_MANIFEST to MANIFEST, "scenes/main.scene.json" to SCENE)
        return AssetSource { path ->
            runCatching { if (path.value == WIND) wav() else text.getValue(path.value).encodeToByteArray() }
        }
    }

    private class RecordingPlayer : AudioPlayer {
        val played = mutableListOf<AudioClip>()
        override var masterVolume = 1f
        override var sfxVolume = 1f
        override var musicVolume = 1f

        override fun playSound(clip: AudioClip, volume: Float, pan: Float, loop: Boolean): SoundHandle {
            played += clip
            return object : SoundHandle {
                override val isPlaying = true

                override fun stop() = Unit
            }
        }

        override fun playMusic(clip: AudioClip, volume: Float, loop: Boolean) = Unit

        override fun stopMusic() = Unit

        override fun dispose() = Unit
    }

    private companion object {
        const val FRAME = 1f / 60f
        const val FRAMES = 3
        const val WIND = "assets/audio/wind.wav"
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""
        const val SCENE = """
{ "version": 1, "name": "harbor", "nodes": [
  { "name": "Camera", "transform": { "position": { "x": 0, "y": 2, "z": 8 } }, "components": [ { "component": "camera" } ] },
  { "name": "Wind", "transform": { "position": { "x": 0, "y": 0, "z": 4 } },
    "components": [ { "component": "audio_source", "clip": "assets/audio/wind.wav", "autoPlay": true, "loop": true } ] }
] }
"""

        /** A tenth of a second of 16-bit mono silence at 8 kHz, as a WAV file. */
        fun wav(): ByteArray {
            val samples = 800
            val data = samples * 2
            val bytes = ByteArray(44 + data)
            fun ascii(at: Int, text: String) = text.forEachIndexed { i, c -> bytes[at + i] = c.code.toByte() }
            fun int(at: Int, value: Int, size: Int) = repeat(size) { bytes[at + it] = (value shr (8 * it)).toByte() }
            ascii(0, "RIFF")
            int(4, 36 + data, 4)
            ascii(8, "WAVE")
            ascii(12, "fmt ")
            int(16, 16, 4)
            int(20, 1, 2)
            int(22, 1, 2)
            int(24, 8000, 4)
            int(28, 16000, 4)
            int(32, 2, 2)
            int(34, 16, 2)
            ascii(36, "data")
            int(40, data, 4)
            return bytes
        }
    }
}
