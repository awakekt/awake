/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.director

import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.input.InputRecorder
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.math.ArcLengthSpline3f
import com.awakekt.awake.core.math.Vec3f
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DirectorPlayerTest {

    private class FakeStage(
        val subjects: MutableMap<String, StageSubjectState> = mutableMapOf(),
        val splines: MutableMap<String, ArcLengthSpline3f> = mutableMapOf(),
        val namedCameras: MutableMap<String, DirectedCameraPose> = mutableMapOf(),
        var terrainHeight: Float = 0f,
        var occludeAllRays: Boolean = false,
        var occluderName: String? = null,
    ) : StageQuery {
        override fun readSubject(name: String, out: StageSubjectState): Boolean {
            val s = subjects[name] ?: return false
            out.found = true
            out.position.set(s.position)
            out.boundsMin.set(s.boundsMin)
            out.boundsMax.set(s.boundsMax)
            out.forward.set(s.forward)
            out.velocity.set(s.velocity)
            return true
        }

        override fun readSpline(name: String): ArcLengthSpline3f? = splines[name]

        override fun readNamedCamera(name: String, out: DirectedCameraPose): Boolean {
            val cam = namedCameras[name] ?: return false
            out.copyFrom(cam)
            return true
        }

        override fun groundHeight(x: Float, z: Float): Float = terrainHeight

        override fun castSightline(from: Vec3f, to: Vec3f, ignoreEntityName: String?, outHit: StageRayHit): Boolean {
            if (occludeAllRays) {
                outHit.occluded = true
                outHit.fraction = 0.4f
                outHit.occluderName = occluderName
                return true
            }
            outHit.reset()
            return false
        }
    }

    private fun heroSubject(x: Float = 0f, y: Float = 0f, z: Float = 0f): StageSubjectState = StageSubjectState(
        found = true,
        position = Vec3f(x, y, z),
        boundsMin = Vec3f(x - 0.4f, y, z - 0.4f),
        boundsMax = Vec3f(x + 0.4f, y + 1.8f, z + 0.4f),
        forward = Vec3f(0f, 0f, -1f),
        velocity = Vec3f(0f, 0f, 0f),
    )

    @Test
    fun beatClockResolvesSecondsAndBeatsAccurately() {
        val clock = BeatClock(bpm = 120f, beatsPerBar = 4, offsetSeconds = 0.5f)
        assertEquals(0.5f, clock.secondsPerBeat, 1e-4f)
        assertEquals(2.0f, clock.secondsPerBar, 1e-4f)
        assertEquals(0.5f, clock.secondsAtBeat(0f), 1e-4f)
        assertEquals(2.5f, clock.secondsAtBeat(4f), 1e-4f)
        assertEquals(4f, clock.beatAt(2.5f), 1e-4f)
        assertEquals(0.5f, clock.beatPhase(2.75f), 1e-4f)
    }

    @Suppress("LongMethod")
    @Test
    fun semanticFramingMatchesShotSizeAndRuleOfThirds() {
        val stage = FakeStage(mutableMapOf("Hero" to heroSubject()))
        val solver = FramingSolver()
        val pose = DirectedCameraPose()

        // Full body shot centered.
        pose.fovYRadians = LensSpec(focalLengthMm = 50f).resolvedFovYRadians()
        solver.solve(
            spec = FramingSpec(
                subjects = listOf("Hero"),
                shotSize = ShotSize.Full,
                angle = AnglePreset.EyeLevel,
                composition = ScreenComposition.Center,
            ),
            stage = stage,
            fovYRadians = pose.fovYRadians,
            aspectRatio = 16f / 9f,
            outEye = pose.eye,
            outCenter = pose.center,
            outUp = pose.up,
        )
        val fullMetrics = FramingAnalyzer.analyze(
            shotId = "full_center",
            timeSeconds = 0f,
            beat = 0f,
            pose = pose,
            aspectRatio = 16f / 9f,
            subjects = listOf("Hero"),
            targetShotSize = ShotSize.Full,
            stage = stage,
        )
        assertTrue(fullMetrics.subjectVisible)
        assertEquals(0.5f, fullMetrics.screenCenterX, 0.04f)
        assertTrue(
            fullMetrics.coverageHeight in 0.20f..0.34f,
            "Full shot should cover ~26% height, got ${fullMetrics.coverageHeight}",
        )
        assertTrue(fullMetrics.warnings.isEmpty(), "Expected no warnings, got ${fullMetrics.warnings}")

        // Rule-of-Thirds Left should shift subject toward u ~= 0.333.
        solver.solve(
            spec = FramingSpec(
                subjects = listOf("Hero"),
                shotSize = ShotSize.Medium,
                angle = AnglePreset.EyeLevel,
                composition = ScreenComposition.RuleOfThirdsLeft,
            ),
            stage = stage,
            fovYRadians = pose.fovYRadians,
            aspectRatio = 16f / 9f,
            outEye = pose.eye,
            outCenter = pose.center,
            outUp = pose.up,
        )
        val thirdsMetrics = FramingAnalyzer.analyze(
            shotId = "thirds_left",
            timeSeconds = 0f,
            beat = 0f,
            pose = pose,
            aspectRatio = 16f / 9f,
            subjects = listOf("Hero"),
            targetShotSize = ShotSize.Medium,
            stage = stage,
        )
        assertTrue(
            thirdsMetrics.screenCenterX in 0.28f..0.39f,
            "RuleOfThirdsLeft should place subject near u=0.333, got ${thirdsMetrics.screenCenterX}",
        )
    }

    @Test
    fun dollyZoomMaintainsSubjectCoverageWhileShiftingFovAndDistance() {
        val stage = FakeStage(mutableMapOf("Hero" to heroSubject()))
        val script = directorScript("vertigo") {
            shot("zoom") {
                seconds(2f)
                dollyZoom(subject = "Hero", startFocalLengthMm = 24f, endFocalLengthMm = 85f, shotSize = ShotSize.Full)
                collision = null
            }
        }
        val player = DirectorPlayer(script)
        val pose = DirectedCameraPose()
        val atmo = DirectedAtmosphereState()

        player.step(0f, 16f / 9f, stage, pose, atmo)
        val startDist = pose.eye.distanceTo(pose.center)
        val startFov = pose.fovYRadians
        val startCov = FramingAnalyzer.analyze("zoom", 0f, 0f, pose, 16f / 9f, listOf("Hero"), stage = stage).coverageHeight

        repeat(120) { player.step(1f / 60f, 16f / 9f, stage, pose, atmo) }
        val endDist = pose.eye.distanceTo(pose.center)
        val endFov = pose.fovYRadians
        val endCov = FramingAnalyzer.analyze("zoom", 2f, 4f, pose, 16f / 9f, listOf("Hero"), stage = stage).coverageHeight

        assertTrue(endDist > startDist * 2.5f, "Dolly zoom must pull camera back substantially")
        assertTrue(endFov < startFov * 0.45f, "Dolly zoom must narrow FOV substantially")
        assertTrue(
            abs(endCov - startCov) < 0.05f,
            "Subject vertical coverage must stay steady during Vertigo zoom ($startCov vs $endCov)",
        )
    }

    @Suppress("LongMethod")
    @Test
    fun lightingAudioActionAndSpeedRampTracksExecuteDeterministically() {
        val stage = FakeStage(mutableMapOf("Hero" to heroSubject(0f, 0f, 0f)))
        val script = directorScript("full_trailer") {
            bpm = 120f
            music("audio/theme.wav", volume = 0.7f, fadeOutSeconds = 0.5f)
            shot("open") {
                beats(4f) // 2.0s
                crane("Hero", startHeightDelta = 0f, endHeightDelta = 4f)
                hudVisible = false
            }
            shot("action") {
                beats(4f) // 2.0s (total 4.0s)
                orbit("Hero", startAzimuthDeg = -40f, endAzimuthDeg = 40f)
                transitionIn = ShotTransitionSpec.SmoothBlend(DurationRef.seconds(0.5f))
                hudVisible = true
            }
            lighting(
                LightingCueSpec.DayCycleSweep(
                    at = TimeRef.beat(0f),
                    duration = DurationRef.beats(8f),
                    fromTime = 0.25f,
                    toTime = 0.75f,
                ),
            )
            lighting(
                LightingCueSpec.HeroRimLight(
                    at = TimeRef.beat(0f),
                    duration = DurationRef.beats(8f),
                    subject = "Hero",
                    intensity = 4f,
                ),
            )
            lighting(
                LightingCueSpec.LightFlash(
                    at = TimeRef.seconds(2.0f),
                    duration = DurationRef.seconds(0.5f),
                    subject = "Hero",
                    peakIntensity = 10f,
                ),
            )
            audio(AudioCueSpec.PlaySfx(at = TimeRef.beat(4f), path = "audio/impact.wav", duckMusicGain = 0.25f))
            action(ActionCueSpec.InputAction(at = TimeRef.seconds(1.0f), action = "sprint", duration = DurationRef.seconds(1.0f)))
            action(ActionCueSpec.PlayAnimation(at = TimeRef.seconds(2.0f), entity = "Hero", clip = "cast"))
            action(ActionCueSpec.CameraTrauma(at = TimeRef.seconds(2.0f), amount = 0.9f))
            action(
                ActionCueSpec.TimeScaleRamp(
                    at = TimeRef.seconds(2.0f),
                    targetScale = 0.2f,
                    rampInSeconds = 0.1f,
                    holdSeconds = 0.8f,
                    rampOutSeconds = 0.1f,
                ),
            )
        }

        assertTrue(script.validate().isEmpty(), "Script validation issues: ${script.validate()}")
        assertEquals(4.0f, script.totalDurationSeconds(), 1e-4f)

        var musicStarted = 0
        var musicControls = 0
        val sfxFired = mutableListOf<String>()
        val animsFired = mutableListOf<String>()
        val inputEvents = mutableListOf<Pair<String, Boolean>>()
        val hudEvents = mutableListOf<Boolean>()
        val sink = object : DirectorCueSink {
            override fun onStartMusic(music: MusicTrackSpec) { musicStarted++ }
            override fun onMusicState(cue: AudioCueSpec.MusicControl) { musicControls++ }
            override fun onPlaySfx(cue: AudioCueSpec.PlaySfx) { sfxFired += cue.path }
            override fun onAnimationCue(cue: ActionCueSpec.PlayAnimation) { animsFired += cue.clip }
            override fun onInputAction(action: String, active: Boolean, durationSeconds: Float) {
                inputEvents += action to active
            }
            override fun onHudVisibility(visible: Boolean) { hudEvents += visible }
        }

        val player = DirectorPlayer(script)
        val pose = DirectedCameraPose()
        val atmo = DirectedAtmosphereState()

        var minTimeScale = 1f
        var maxDynamicLights = 0
        var minDayTime = 1f
        var maxDayTime = 0f

        repeat(240) {
            player.step(1f / 60f, 16f / 9f, stage, pose, atmo, sink)
            if (player.currentTimeScale < minTimeScale) minTimeScale = player.currentTimeScale
            if (atmo.activeDynamicLightCount > maxDynamicLights) maxDynamicLights = atmo.activeDynamicLightCount
            if (atmo.hasDayTime) {
                if (atmo.dayTime < minDayTime) minDayTime = atmo.dayTime
                if (atmo.dayTime > maxDayTime) maxDayTime = atmo.dayTime
            }
        }

        assertTrue(player.isFinished)
        assertEquals(1, musicStarted)
        assertTrue(musicControls >= 1, "Tail music fade-out should trigger")
        assertEquals(listOf("audio/impact.wav"), sfxFired)
        assertEquals(listOf("cast"), animsFired)
        assertEquals(listOf("sprint" to true, "sprint" to false), inputEvents)
        assertEquals(listOf(false, true), hudEvents)
        assertTrue(minTimeScale <= 0.21f, "Slow-mo ramp should drop timeScale to ~0.2, got $minTimeScale")
        assertEquals(1f, player.currentTimeScale, 1e-3f)
        assertTrue(maxDynamicLights >= 2, "Expected both HeroRimLight and LightFlash active simultaneously")
        assertTrue(maxDayTime - minDayTime > 0.45f, "Day cycle should sweep from 0.25 to 0.75")
    }

    @Test
    fun framingAnalyzerDetectsOcclusionAndSubterraneanCamera() {
        val stage = FakeStage(
            subjects = mutableMapOf("Hero" to heroSubject()),
            terrainHeight = 5f,
            occludeAllRays = true,
            occluderName = "HarborWall",
        )
        val pose = DirectedCameraPose(
            eye = Vec3f(0f, 2f, 5f),
            center = Vec3f(0f, 1f, 0f),
        )
        val metrics = FramingAnalyzer.analyze(
            shotId = "blocked",
            timeSeconds = 0.5f,
            beat = 1f,
            pose = pose,
            aspectRatio = 16f / 9f,
            subjects = listOf("Hero"),
            stage = stage,
            averageLuminance = 0.01f,
        )
        assertEquals(1f, metrics.occlusionFraction, 1e-4f)
        assertEquals("HarborWall", metrics.occluderName)
        assertTrue(metrics.warnings.any { "occluded" in it && "HarborWall" in it })
        assertTrue(metrics.warnings.any { "terrain" in it })
        assertTrue(metrics.warnings.any { "dark" in it })
    }

    @Test
    fun directorScriptReelAndInputTakeJsonRoundTripCleanly() {
        val script = directorScript("roundtrip") {
            bpm = 128f
            shot("s1") {
                beats(4f)
                orbit("Hero")
                feel = CameraFeelSpec(handheldIntensity = 0.35f)
            }
            shot("s2") {
                seconds(2f)
                rig = ShotRigSpec.SplineRail(
                    waypoints = listOf(DirectorVec3(0f, 2f, 8f), DirectorVec3(5f, 3f, 4f), DirectorVec3(0f, 4f, -6f)),
                    lookMode = SplineLookMode.Subject,
                    framing = FramingSpec(subjects = listOf("Hero")),
                )
                transitionIn = ShotTransitionSpec.FadeThroughBlack(DurationRef.seconds(0.3f))
            }
        }
        val decoded = DirectorScript.decode(DirectorScript.encode(script))
        assertEquals(script, decoded)

        val reel = TrailerReel(
            name = "launch_trailer",
            music = MusicTrackSpec("audio/trailer_bgm.wav"),
            segments = listOf(
                ReelSegmentSpec(id = "town", scene = "scenes/harbor.awakescene", inlineDirector = script),
            ),
        )
        assertTrue(reel.validate().isEmpty())
        assertEquals(reel, TrailerReel.decode(TrailerReel.encode(reel)))

        val input = Input()
        val recorder = InputRecorder(scenePath = "scenes/harbor.awakescene", seed = 7L)
        input.setKeyDown(Key.W, true)
        recorder.recordFrame(input.updateSnapshot(), gameplayCameraYaw = 1.2f, gameplayCameraPitch = -0.2f)
        val take = recorder.finish()
        assertEquals(take, InputTakeJson.decode(InputTakeJson.encode(take)))
        assertFalse(take.frames.isEmpty())
    }
}
