/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.cli

import com.awakekt.awake.core.audio.AudioClip
import com.awakekt.awake.core.audio.OfflineAudioMixer
import com.awakekt.awake.core.audio.WavDecoder
import com.awakekt.awake.director.AnglePreset
import com.awakekt.awake.director.AudioCueSpec
import com.awakekt.awake.director.DirectorColor
import com.awakekt.awake.director.DirectorScript
import com.awakekt.awake.director.DirectorVec3
import com.awakekt.awake.director.DurationRef
import com.awakekt.awake.director.FramingSpec
import com.awakekt.awake.director.LightingCueSpec
import com.awakekt.awake.director.ReelSegmentSpec
import com.awakekt.awake.director.ShotRigSpec
import com.awakekt.awake.director.ShotSize
import com.awakekt.awake.director.TimeRef
import com.awakekt.awake.director.TrailerReel
import com.awakekt.awake.director.directorScript
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.image.BufferedImage
import java.io.File
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.io.path.createTempDirectory
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Headless GPU verification of the end-to-end Vibe Camera Director pipeline on real Vulkan / WebGPU
 * backends (`*RenderTest` runs under `:awake:project:cli:renderTest`):
 *
 * 1. Multi-shot storyboard contact sheet (`awake storyboard --guides`) with dynamic lighting sweep
 *    (dim moonlit start vs bright noon/flash beat) and an intentional negative-control occluded shot
 *    behind a physics Pillar proving `FramingAnalyzer` detects occlusion when blocked and reports
 *    0% occlusion when unobstructed.
 * 2. MCP `preview_storyboard` returning both the PNG contact sheet image and structured JSON
 *    telemetry.
 * 3. Deterministic headless trailer capture (`awake capture`) producing PNG sequence + sample-exact
 *    mixed WAV soundtrack (proving non-zero frame-to-frame motion and non-silent audio output),
 *    plus MP4 muxing via `ffmpeg` and multi-scene `TrailerReel` capture when `ffmpeg` is available.
 */
class DirectorCaptureRenderTest {
    private val root: File = createTempDirectory("awake-director-render").toFile()

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Suppress("LongMethod")
    @Test
    fun storyboardMeasuresLightingDeltaAndDetectsOccludedNegativeControl() {
        createRenderProject()
        val backend = backends().first()

        // Shot 1: clear front view under dim sun (intensity 0.15).
        // Shot 2: clear front view under bright sun + close warm light flash (intensity 4.0).
        // Shot 3 (negative control): camera placed directly behind the physics Pillar at z=-10
        // looking through Pillar (z=-4) toward Hero (z=0), proving occlusion detection fires.
        val script = directorScript("storyboard_probe") {
            shot("dim_front") {
                seconds(0.5f)
                rig = ShotRigSpec.Static(
                    eye = DirectorVec3(0f, 1.5f, 8f),
                    center = DirectorVec3(0f, 1.5f, 0f),
                    framing = null,
                )
            }
            shot("bright_front") {
                seconds(0.5f)
                rig = ShotRigSpec.Orbit(
                    framing = FramingSpec(
                        subjects = listOf("Hero"),
                        shotSize = ShotSize.Full,
                        angle = AnglePreset.EyeLevel,
                        azimuthDeg = 180f,
                    ),
                    startAzimuthDeg = -6f,
                    endAzimuthDeg = 6f,
                )
            }
            shot("occluded_behind_pillar") {
                seconds(0.5f)
                rig = ShotRigSpec.Static(
                    framing = FramingSpec(
                        subjects = listOf("Hero"),
                        shotSize = ShotSize.Full,
                        angle = AnglePreset.EyeLevel,
                        azimuthDeg = 0f,
                        elevationDeg = 0f,
                    ),
                )
            }
            lighting(
                LightingCueSpec.DirectionalLight(
                    at = TimeRef.seconds(0f),
                    duration = DurationRef.seconds(0.5f),
                    color = DirectorColor(0.25f, 0.30f, 0.45f),
                    intensity = 0.12f,
                    ambient = 0.04f,
                ),
            )
            lighting(
                LightingCueSpec.DirectionalLight(
                    at = TimeRef.seconds(0.5f),
                    duration = DurationRef.seconds(0.5f),
                    color = DirectorColor(1f, 0.96f, 0.88f),
                    intensity = 3.2f,
                    ambient = 0.45f,
                ),
            )
            lighting(
                LightingCueSpec.HeroRimLight(
                    at = TimeRef.seconds(0.5f),
                    duration = DurationRef.seconds(0.5f),
                    subject = "Hero",
                    color = DirectorColor(1f, 0.9f, 0.7f),
                    intensity = 5f,
                    range = 14f,
                ),
            )
        }
        val scriptFile = root.resolve("scripts/storyboard.director.json")
        scriptFile.writeText(DirectorScript.encode(script))
        val sheetPng = root.resolve("out/storyboard.png")

        val out = StringBuilder()
        val err = StringBuilder()
        val code = AwakeCli(out, err, root).run(
            listOf(
                "storyboard", "harbor",
                "--director", "scripts/storyboard.director.json",
                "--output", sheetPng.path,
                "--width", "$TILE_W",
                "--height", "$TILE_H",
                "--samples", "0.5",
                "--guides",
                "--backend", backend,
                "--json",
            ),
        )
        assertEquals(0, code, "$out$err")
        assertTrue(sheetPng.isFile && sheetPng.length() > 0L, "storyboard PNG should be written")

        val report = Json.decodeFromString(StoryboardReport.serializer(), out.toString())
        assertEquals(3, report.samples.size, "expected 3 sampled shots")
        val dimSample = report.samples.single { it.shotId == "dim_front" }
        val brightSample = report.samples.single { it.shotId == "bright_front" }
        val occludedSample = report.samples.single { it.shotId == "occluded_behind_pillar" }

        val dimLum = dimSample.averageLuminance ?: 0f
        val brightLum = brightSample.averageLuminance ?: 0f
        println(
            "$backend storyboard telemetry: dimLum=$dimLum brightLum=$brightLum " +
                "clearOcc=${brightSample.occlusionFraction} blockedOcc=${occludedSample.occlusionFraction} " +
                "occluder=${occludedSample.occluderName} warnings=${occludedSample.warnings}",
        )
        assertTrue(
            brightLum > dimLum + MIN_LUMINANCE_DELTA,
            "$backend: bright lighting cue ($brightLum) must visibly brighten the rendered scene over dim shot ($dimLum)",
        )
        assertTrue(
            brightSample.subjectVisible && brightSample.coverageHeight > 0.1f,
            "$backend: clear front shot must frame Hero cleanly (covH=${brightSample.coverageHeight})",
        )
        assertEquals(0f, brightSample.occlusionFraction, "$backend: clear front shot must have 0 occlusion")
        assertTrue(
            occludedSample.occlusionFraction >= 0.4f,
            "$backend: negative-control shot behind Pillar must report occlusion >= 0.4, got ${occludedSample.occlusionFraction}",
        )
        assertTrue(
            occludedSample.warnings.any { "occluded" in it },
            "$backend: expected occlusion warning on blocked shot, got ${occludedSample.warnings}",
        )

        val img = ImageIO.read(sheetPng)
        assertTrue(img.width >= TILE_W && img.height >= TILE_H * 3, "contact sheet must contain 3 stacked tiles")

        // Also verify MCP preview_storyboard returns both PNG image content and JSON report.
        val mcpOut = StringBuilder()
        val mcpErr = StringBuilder()
        val mcpCall = """{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"preview_storyboard","arguments":""" +
            """{"scene":"harbor","director":"scripts/storyboard.director.json","tile_width":$TILE_W,"tile_height":$TILE_H,"backend":"$backend"}}}"""
        assertEquals(0, AwakeCli(mcpOut, mcpErr, root) { mcpCall.reader().buffered() }.run(listOf("mcp")), "$mcpOut$mcpErr")
        val content = Json.parseToJsonElement(mcpOut.toString().trim()).jsonObject["result"]!!.jsonObject["content"]!!.jsonArray
        assertEquals("image", content[0].jsonObject["type"]!!.jsonPrimitive.content)
        val decodedPng = Base64.getDecoder().decode(content[0].jsonObject["data"]!!.jsonPrimitive.content)
        val mcpImg = ImageIO.read(decodedPng.inputStream())
        assertTrue(mcpImg.width > TILE_W && mcpImg.height > TILE_H, "MCP storyboard must be multi-tile image")
    }

    @Suppress("LongMethod")
    @Test
    fun captureExportsMovingFramesSyncedWavAndMp4WhenFfmpegPresent() {
        createRenderProject()
        val backend = backends().first()

        // Generate a real 440Hz sine WAV soundtrack + 880Hz SFX ping under audio/.
        root.resolve("audio/theme.wav").writeBytes(generateSineWav(freqHz = 440f, durationSec = 1.5f, amplitude = 0.5f))
        root.resolve("audio/hit.wav").writeBytes(generateSineWav(freqHz = 880f, durationSec = 0.2f, amplitude = 0.8f))

        val script = directorScript("capture_probe") {
            music("audio/theme.wav", volume = 0.7f, fadeInSeconds = 0.05f)
            shot("orbit_sweep") {
                seconds(0.5f)
                orbit("Hero", startAzimuthDeg = -45f, endAzimuthDeg = 45f)
            }
            audio(AudioCueSpec.PlaySfx(at = TimeRef.seconds(0.15f), path = "audio/hit.wav", volume = 0.9f))
        }
        root.resolve("scripts/capture.director.json").writeText(DirectorScript.encode(script))

        // 1. PNG sequence + synced WAV capture.
        val seqDir = root.resolve("out/seq")
        val seqOut = StringBuilder()
        val seqErr = StringBuilder()
        val seqCode = AwakeCli(seqOut, seqErr, root).run(
            listOf(
                "capture", "harbor",
                "--director", "scripts/capture.director.json",
                "--output", seqDir.path,
                "--format", "png-sequence",
                "--width", "$TILE_W",
                "--height", "$TILE_H",
                "--fps", "12",
                "--backend", backend,
                "--json",
            ),
        )
        assertEquals(0, seqCode, "$seqOut$seqErr")
        val seqReport = Json.decodeFromString(TrailerCaptureReport.serializer(), seqOut.toString())
        assertEquals(6, seqReport.framesCaptured, "0.5s @ 12fps = 6 frames")
        assertTrue(seqReport.audioPeakAmplitude > 0.2f, "mixed WAV must have non-zero audio amplitude")
        val wavFile = seqDir.resolve("audio.wav")
        assertTrue(wavFile.isFile && wavFile.length() > 44L, "audio.wav must be written")
        val decodedWav = WavDecoder.decode("audio", "audio", wavFile.readBytes())
        assertTrue(decodedWav != null && decodedWav.pcmBytes.isNotEmpty(), "captured WAV must decode cleanly")

        val firstFrame = ImageIO.read(seqDir.resolve("frame_000001.png"))
        val lastFrame = ImageIO.read(seqDir.resolve("frame_000006.png"))
        val motionDiff = meanAbsPixelDiff(firstFrame, lastFrame)
        println("$backend capture probe: frames=${seqReport.framesCaptured} motionDiff=$motionDiff audioPeak=${seqReport.audioPeakAmplitude}")
        assertTrue(motionDiff > 2.0, "$backend: orbiting camera must produce distinct motion between frame 1 and frame 6 (diff=$motionDiff)")

        // 2. When ffmpeg is on PATH, also verify multi-scene TrailerReel -> MP4 capture with muxed audio!
        if (findExecutable("ffmpeg") != null) {
            val reel = TrailerReel(
                name = "harbor_reel",
                segments = listOf(
                    ReelSegmentSpec(id = "seg_harbor", scene = "harbor", directorPath = "scripts/capture.director.json"),
                ),
            )
            root.resolve("scripts/harbor.reel.json").writeText(TrailerReel.encode(reel))
            val mp4 = root.resolve("out/trailer.mp4")
            val mp4Out = StringBuilder()
            val mp4Err = StringBuilder()
            val mp4Code = AwakeCli(mp4Out, mp4Err, root).run(
                listOf(
                    "capture",
                    "--reel", "scripts/harbor.reel.json",
                    "--output", mp4.path,
                    "--width", "$TILE_W",
                    "--height", "$TILE_H",
                    "--fps", "12",
                    "--backend", backend,
                    "--json",
                ),
            )
            assertEquals(0, mp4Code, "$mp4Out$mp4Err")
            val mp4Report = Json.decodeFromString(TrailerCaptureReport.serializer(), mp4Out.toString())
            assertEquals("ffmpeg", mp4Report.encoder)
            assertTrue(mp4.isFile && mp4.length() > 512L, "encoded MP4 with muxed audio must be non-empty (${mp4.length()} bytes)")
            println("$backend mp4 trailer encoded: ${mp4.length()} bytes, ${mp4Report.framesCaptured} frames")
        }
    }

    private fun createRenderProject() {
        root.resolve("awake.project.json").writeText(
            """{"formatVersion":1,"id":"com.example.harbor","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/harbor.scene.json"}""",
        )
        root.resolve("scenes").mkdirs()
        root.resolve("scripts").mkdirs()
        root.resolve("audio").mkdirs()
        root.resolve("scenes/harbor.scene.json").writeText(
            """
            {
              "version": 1,
              "name": "harbor",
              "nodes": [
                {
                  "name": "Camera",
                  "components": [
                    { "component": "camera", "eye": { "x": 0.0, "y": 2.0, "z": 10.0 }, "center": { "x": 0.0, "y": 1.5, "z": 0.0 }, "fovYDegrees": 45.0, "primary": true }
                  ]
                },
                {
                  "name": "Sun",
                  "components": [
                    { "component": "light", "type": "Directional", "intensity": 1.0, "direction": { "x": 0.3, "y": 0.8, "z": 0.5 } }
                  ]
                },
                {
                  "name": "Sky",
                  "components": [
                    { "component": "skybox", "enabled": true, "type": "Procedural" }
                  ]
                },
                {
                  "name": "Hero",
                  "transform": { "position": { "x": 0.0, "y": 1.5, "z": 0.0 }, "scale": { "x": 2.5, "y": 3.0, "z": 2.5 } },
                  "components": [
                    { "component": "mesh_renderer", "mesh": "cube", "material": "lit-shadow" }
                  ]
                },
                {
                  "name": "Pillar",
                  "transform": { "position": { "x": 0.0, "y": 2.0, "z": -3.5 }, "scale": { "x": 4.0, "y": 5.0, "z": 1.0 } },
                  "components": [
                    { "component": "mesh_renderer", "mesh": "cube", "material": "lit-shadow" },
                    { "component": "physics_body", "motion": "STATIC", "shape": { "type": "box", "halfExtents": { "x": 2.0, "y": 2.5, "z": 0.5 } } }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )
    }

    private fun generateSineWav(freqHz: Float, durationSec: Float, amplitude: Float): ByteArray {
        val sampleRate = 48_000
        val sampleCount = (sampleRate * durationSec).roundToInt()
        val pcm = ByteArray(sampleCount * 2)
        for (i in 0 until sampleCount) {
            val s = (sin(2.0 * PI * freqHz * i / sampleRate) * amplitude * 32767.0).roundToInt().coerceIn(-32767, 32767)
            pcm[i * 2] = (s and 0xFF).toByte()
            pcm[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }
        val clip = AudioClip("gen", "gen", pcm, sampleRate = sampleRate, channels = 1, bitsPerSample = 16)
        val mixer = OfflineAudioMixer(sampleRate = sampleRate)
        mixer.playSound(clip, volume = 1f)
        mixer.advance(durationSec)
        return mixer.encodeWav()
    }

    private fun meanAbsPixelDiff(a: BufferedImage, b: BufferedImage): Double {
        var sum = 0L
        val pixels = a.width * a.height
        for (y in 0 until a.height) {
            for (x in 0 until a.width) {
                val p1 = a.getRGB(x, y)
                val p2 = b.getRGB(x, y)
                sum += abs(((p1 shr 16) and 0xFF) - ((p2 shr 16) and 0xFF))
                sum += abs(((p1 shr 8) and 0xFF) - ((p2 shr 8) and 0xFF))
                sum += abs((p1 and 0xFF) - (p2 and 0xFF))
            }
        }
        return sum.toDouble() / (pixels * 3.0)
    }

    private fun backends(): List<String> =
        System.getProperty("awake.render.backends", RenderBackend.default().label).split(',').map(String::trim)

    private companion object {
        const val TILE_W = 192
        const val TILE_H = 108
        const val MIN_LUMINANCE_DELTA = 0.04f
    }
}
