/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.audio.AudioClip
import com.awakekt.awake.core.audio.AudioPlayer
import com.awakekt.awake.core.audio.SoundHandle
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.audio.AudioSource
import com.awakekt.awake.scene.audio.AudioSystem
import com.awakekt.awake.scene.audio.SceneAudioSource
import com.awakekt.awake.scene.audio.audioClipLoads
import com.awakekt.awake.scene.document.SceneNode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A prewarm decodes a tree's audio clips into the scene's, without playing them, for the first play. */
class ScenePrewarmAudioTest : PrewarmFixture() {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val game = SceneAppLifecycleRuntime(spec().apply { sessionDispatcher = dispatcher }).apply { initialize(NoServices) }
    private val player = RecordingPlayer()
    private val readable = CompletableDeferred<Unit>()
    private var clipReads = 0
    private var cancelledReads = 0
    private val files = AssetSource { path ->
        clipReads++
        try {
            readable.await()
        } catch (cancelled: CancellationException) {
            cancelledReads++
            throw cancelled
        }
        Result.success(if (path.value == BELL) wav() else ByteArray(WAV_HEADER))
    }

    // --8<-- [start:audio]
    // One set of clips for the scene: its AudioSystem plays them, and a prewarm decodes into them.
    private val clips = audioClipLoads(files, context = dispatcher).also { library.audioClips = it }
    private val audio = AudioSystem(player, clips)
    // --8<-- [end:audio]

    @Test
    fun aPrewarmDecodesItsClipsWithoutPlayingThemAndTheFirstPlayHasThem() {
        val prewarm = game.prewarm(node(BELL))
        finishReading()

        assertTrue(prewarm.isReady)
        assertEquals(1 to 1, prewarm.settled to prewarm.total)
        assertEquals(emptyList(), player.played, "a prewarm plays nothing")

        val world = World().also { it.add(it.create(), placeholder(BELL)) }
        audio.update(world, 0f)

        assertEquals(listOf(BELL), player.played.map { it.id }, "it plays on the spawn's first frame")
        assertTrue(player.played.single().pcmBytes.isNotEmpty())
        assertEquals(1, clipReads)
    }

    @Test
    fun prewarmsAndTheAudioSystemShareOneDecodePerClip() {
        val first = game.prewarm(node(BELL))
        val second = game.prewarm(node(BELL))
        val world = World().also { it.add(it.create(), placeholder(BELL)) }
        audio.update(world, 0f)

        finishReading()

        assertTrue(first.isReady && second.isReady)
        assertEquals(1, clipReads)
    }

    @Test
    fun closingAPrewarmCancelsADecodeNothingElseWaitsFor() {
        val prewarm = game.prewarm(node(BELL))
        scheduler.runCurrent()

        prewarm.close()
        finishReading()

        assertEquals(1, cancelledReads)
        assertEquals(0, clips.holdCount(BELL))
        assertNull(clips.loaded[BELL])
    }

    @Test
    fun tearingTheSceneDownCancelsAPrewarmsDecode() = runTest {
        val prewarm = game.prewarm(node(BELL))
        scheduler.runCurrent()

        game.session.dispose(game)
        scheduler.runCurrent()

        assertEquals(1, cancelledReads)
        assertEquals(0, clips.holdCount(BELL))
        assertFailsWith<CancellationException> { prewarm.await() }
    }

    @Test
    fun aClipThatIsNotAWavFileShowsUpInFailed() {
        val prewarm = game.prewarm(node(BROKEN))
        finishReading()

        assertTrue(prewarm.isReady)
        val failure = prewarm.failed.getValue(BROKEN)
        assertTrue("16-bit PCM WAV" in failure.message.orEmpty(), failure.message)
    }

    private fun finishReading() {
        scheduler.runCurrent()
        readable.complete(Unit)
        scheduler.runCurrent()
        library.runPrewarms()
    }

    private class RecordingPlayer : AudioPlayer {
        val played = mutableListOf<AudioClip>()
        override var masterVolume = 1f
        override var sfxVolume = 1f
        override var musicVolume = 1f

        override fun playSound(clip: AudioClip, volume: Float, pan: Float, loop: Boolean): SoundHandle? {
            played += clip
            return null
        }

        override fun playMusic(clip: AudioClip, volume: Float, loop: Boolean) = Unit
        override fun stopMusic() = Unit
        override fun dispose() = Unit
    }

    private companion object {
        const val BELL = "audio/bell.wav"
        const val BROKEN = "audio/broken.wav"
        const val WAV_HEADER = 44

        fun node(clip: String) = SceneNode(name = "Bell", components = listOf(SceneAudioSource(clip = clip, autoPlay = true, isSpatial3D = false)))

        /** The source `audio_source`'s binding attaches: the clip's name, with no samples yet. */
        fun placeholder(clip: String) =
            AudioSource(clip = AudioClip(id = clip, name = clip, pcmBytes = ByteArray(0), channels = 1), autoPlay = true, isSpatial3D = false)

        /** A tenth of a second of 16-bit mono silence at 8 kHz. */
        fun wav(): ByteArray {
            val data = 1600
            val bytes = ByteArray(WAV_HEADER + data)
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
