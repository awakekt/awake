/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MusicStreamTest {
    @Test
    fun inferAudioMimeTypeHandlesExtensionsQueriesAndUnknowns() {
        assertEquals("audio/mpeg", inferAudioMimeType("music/harbor_town.mp3"))
        assertEquals("audio/ogg", inferAudioMimeType("music/theme.OGG?v=2#loop"))
        assertEquals("audio/opus", inferAudioMimeType("bgm/wind.opus"))
        assertEquals("audio/mp4", inferAudioMimeType("bgm/forest.m4a"))
        assertEquals("audio/wav", inferAudioMimeType("audio/chime.wav"))
        assertEquals("audio/flac", inferAudioMimeType("audio/lossless.flac"))
        assertNull(inferAudioMimeType("music/raw_no_ext"))
        assertNull(inferAudioMimeType("music/folder.mp3/track"))
    }

    @Test
    fun encodedBytesEqualityComparesContents() {
        val a = MusicStreamSource.EncodedBytes("bgm/town.mp3", byteArrayOf(1, 2, 3))
        val b = MusicStreamSource.EncodedBytes("bgm/town.mp3", byteArrayOf(1, 2, 3))
        val c = MusicStreamSource.EncodedBytes("bgm/town.mp3", byteArrayOf(1, 2, 4))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertEquals("audio/mpeg", a.mimeType)
        assertNotEquals(a, c)
    }

    @Test
    fun focusAudioPlayerPausesAndResumesStreamedMusicAndPropagatesDecline() {
        val stream = MusicStreamSource.EncodedBytes("music/harbor.mp3", byteArrayOf(0x49, 0x44, 0x33))
        val backend = RecordingStreamAudioPlayer(acceptStreams = true)
        var focused = false
        val player = FocusAudioPlayer(backend) { focused }

        // While unfocused, stream is remembered without starting immediately.
        assertTrue(player.playMusicStream(stream, volume = 0.7f, loop = true))
        assertNull(backend.activeStream)

        // Focus gain starts the remembered stream.
        focused = true
        player.syncFocus()
        assertEquals(stream, backend.activeStream)
        assertEquals(0.7f, backend.lastVolume)
        assertTrue(backend.lastLoop)

        // Focus loss stops playback; regaining focus resumes it.
        focused = false
        player.syncFocus()
        assertNull(backend.activeStream)
        focused = true
        player.syncFocus()
        assertEquals(stream, backend.activeStream)

        // When delegate declines streaming, FocusAudioPlayer returns false so callers can fall back.
        backend.acceptStreams = false
        assertFalse(player.playMusicStream(stream, volume = 0.5f, loop = false))
        assertNull(backend.activeStream)

        // Empty sources are rejected cleanly.
        assertFalse(player.playMusicStream(MusicStreamSource.EncodedBytes("empty.mp3", byteArrayOf())))
        assertFalse(player.playMusicStream(MusicStreamSource.FilePath("blank", "   ")))
        player.dispose()
        assertFalse(player.playMusicStream(stream))
    }

    @Test
    fun noOpAndOfflineAudioMixerHandleMusicStreams() {
        val noOp = NoOpAudioPlayer()
        assertTrue(noOp.playMusicStream(MusicStreamSource.FilePath("town", "/bgm/town.ogg")))
        assertFalse(noOp.playMusicStream(MusicStreamSource.FilePath("blank", "")))
        assertFalse(noOp.playMusicStream(MusicStreamSource.EncodedBytes("empty", byteArrayOf())))

        // Build a short valid WAV in memory via OfflineAudioMixer and stream it into a second mixer.
        val sourceMixer = OfflineAudioMixer(sampleRate = 8_000)
        val tone = SoundSynthesizer.tone("bgm", "Tone", 440f, 0.05f)
        sourceMixer.playMusic(tone, volume = 1f, loop = false)
        sourceMixer.advance(0.05f)
        val wavBytes = sourceMixer.encodeWav()

        val targetMixer = OfflineAudioMixer(sampleRate = 8_000)
        assertTrue(targetMixer.playMusicStream(MusicStreamSource.EncodedBytes("bgm.wav", wavBytes), volume = 0.8f, loop = false))
        targetMixer.advance(0.05f)
        assertTrue(targetMixer.peakAmplitude() > 0.1f, "Streamed WAV bytes must mix audibly in OfflineAudioMixer")
        assertFalse(targetMixer.playMusicStream(MusicStreamSource.EncodedBytes("bad.mp3", byteArrayOf(1, 2, 3))))
        assertFalse(targetMixer.playMusicStream(MusicStreamSource.FilePath("file", "bgm.mp3")))
        targetMixer.dispose()
    }

    private class RecordingStreamAudioPlayer(
        var acceptStreams: Boolean,
    ) : NoOpAudioPlayer() {
        var activeStream: MusicStreamSource? = null
        var lastVolume: Float = 0f
        var lastLoop: Boolean = false

        override fun playMusicStream(source: MusicStreamSource, volume: Float, loop: Boolean): Boolean {
            activeStream = null
            if (!acceptStreams) return false
            activeStream = source
            lastVolume = volume
            lastLoop = loop
            return true
        }

        override fun stopMusic() {
            activeStream = null
        }
    }
}
