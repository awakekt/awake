/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.director

import com.awakekt.awake.core.audio.OfflineAudioMixer
import com.awakekt.awake.core.audio.SoundSynthesizer
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.input.InputRecorder
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.director.ActionCueSpec
import com.awakekt.awake.director.AudioCueSpec
import com.awakekt.awake.director.DirectorColor
import com.awakekt.awake.director.DirectorScript
import com.awakekt.awake.director.DirectorVec3
import com.awakekt.awake.director.DurationRef
import com.awakekt.awake.director.LightingCueSpec
import com.awakekt.awake.director.ShotAtmosphereSpec
import com.awakekt.awake.director.ShotRigSpec
import com.awakekt.awake.director.TimeRef
import com.awakekt.awake.director.directorScript
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.effects.ToneMappingGradeEffect
import com.awakekt.awake.render.effects.ToneMappingMode
import com.awakekt.awake.scene.audio.AudioListener
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.controls.camera.ActiveCamera
import com.awakekt.awake.scene.controls.camera.CameraMode
import com.awakekt.awake.scene.controls.camera.CameraRig
import com.awakekt.awake.scene.controls.movement.MatrixRelativeMovementSystem
import com.awakekt.awake.scene.controls.movement.MovementControl
import com.awakekt.awake.scene.controls.movement.PlayerInputSystem
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.document.SceneValidator
import com.awakekt.awake.scene.document.SceneVec3
import com.awakekt.awake.scene.rendering.camera.Camera
import com.awakekt.awake.scene.rendering.fog.Fog
import com.awakekt.awake.scene.rendering.light.DayCycle
import com.awakekt.awake.scene.rendering.light.Light
import com.awakekt.awake.scene.rendering.light.SceneDayCycle
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import com.awakekt.awake.scene.rendering.postprocessing.PostProcessing
import com.awakekt.awake.scene.rendering.sky.Skybox
import com.awakekt.awake.scene.rendering.tonemapping.ToneMapping
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DirectorSystemTest {
    init {
        DirectorSceneComponents.bindings.forEach(SceneComponentRegistry::registerGlobal)
    }

    @Test
    fun sceneComponentsRoundTripAndValidate() {
        val script = directorScript("doc_test") {
            shot("s1") {
                seconds(2f)
                orbit("Hero")
            }
        }
        val doc = SceneDocument(
            name = "director-test",
            nodes = listOf(
                SceneNode(
                    name = "Director",
                    components = listOf(SceneCameraDirector(inlineScript = script, autoPlay = true, priority = 5)),
                ),
                SceneNode(
                    name = "OverlookCam",
                    components = listOf(SceneVirtualCamera(eye = SceneVec3(0f, 5f, 10f), center = SceneVec3(0f, 1f, 0f))),
                ),
                SceneNode(
                    name = "DollyTrack",
                    components = listOf(
                        SceneCameraRail(
                            waypoints = listOf(SceneVec3(0f, 2f, 8f), SceneVec3(4f, 3f, 0f), SceneVec3(0f, 4f, -8f)),
                        ),
                    ),
                ),
                SceneNode(
                    name = "DuelMark",
                    components = listOf(SceneStageMarker(tags = listOf("duel"), radius = 2f)),
                ),
            ),
        )
        val issues = SceneValidator.validate(doc)
        assertTrue(issues.isEmpty(), "Expected valid scene doc, got $issues")
        val roundTripped = SceneLoader.decode(SceneLoader.encode(doc))
        assertEquals(doc.nodes.size, roundTripped.nodes.size)
        val decodedDirector = roundTripped.nodes[0].components.single() as SceneCameraDirector
        assertEquals(script, decodedDirector.inlineScript)
        assertEquals(5, decodedDirector.priority)

        // Invalid rail with 1 waypoint fails validation.
        val badRail = SceneCameraRail(waypoints = listOf(SceneVec3(0f, 0f, 0f)))
        assertFalse(badRail.validate("rail").isEmpty())
    }

    @Suppress("LongMethod")
    @Test
    fun directorSystemOrchestratesCameraLightingAudioPostGradeAndCleansUp() {
        val world = World()

        val hero = world.create()
        world.add(hero, Name("Hero"))
        world.add(hero, Transform(position = Vec3f(0f, 0f, 0f)))
        world.add(hero, MeshBounds(Aabb(Vec3f(-0.4f, 0f, -0.4f), Vec3f(0.4f, 1.8f, 0.4f))))

        val playerCam = world.create()
        world.add(playerCam, Name("PlayerCamera"))
        world.add(playerCam, Transform(position = Vec3f(0f, 2f, 5f)))
        world.add(playerCam, Camera(lens = Lens.perspective(), isPrimary = true))
        world.add(playerCam, ActiveCamera())
        world.add(playerCam, AudioListener())

        val sun = world.create()
        world.add(sun, Light(type = Light.Type.Directional, intensity = 1f))
        world.add(sun, DayCycle(SceneDayCycle(time = 0.5f)))
        world.add(sun, Fog(enabled = true, density = 0.001f))
        world.add(sun, Skybox(enabled = true))
        world.add(sun, ToneMapping(exposure = 1f))

        val mixer = OfflineAudioMixer(sampleRate = 48_000)
        val musicClip = SoundSynthesizer.tone("theme", "theme", 220f, 2f)
        val sfxClip = SoundSynthesizer.tone("boom", "boom", 110f, 0.3f)

        val script = directorScript("showcase") {
            bpm = 120f
            music("audio/theme.wav", volume = 0.6f)
            shot("hero_orbit") {
                seconds(2f)
                orbit("Hero", startAzimuthDeg = -30f, endAzimuthDeg = 30f)
                hudVisible = false
                atmosphere = ShotAtmosphereSpec(exposure = 1.25f, contrast = 1.15f, bloomIntensity = 0.4f)
            }
            lighting(LightingCueSpec.DayCycleSweep(TimeRef.seconds(0f), DurationRef.seconds(2f), fromTime = 0.25f, toTime = 0.75f))
            lighting(LightingCueSpec.HeroRimLight(TimeRef.seconds(0f), DurationRef.seconds(2f), subject = "Hero", intensity = 4f))
            lighting(
                LightingCueSpec.LightFlash(
                    at = TimeRef.seconds(0.5f),
                    duration = DurationRef.seconds(0.5f),
                    subject = "Hero",
                    color = DirectorColor(0.8f, 0.9f, 1f),
                    peakIntensity = 12f,
                ),
            )
            lighting(LightingCueSpec.FogRamp(TimeRef.seconds(0f), DurationRef.seconds(2f), fromDensity = 0.002f, toDensity = 0.02f))
            audio(AudioCueSpec.PlaySfx(TimeRef.seconds(0.5f), path = "audio/boom.wav", entity = "Hero", maxDistance = 30f, duckMusicGain = 0.3f))
            action(ActionCueSpec.ActorMove(TimeRef.seconds(0f), entity = "Hero", targetPosition = DirectorVec3(0f, 0f, -4f), duration = DurationRef.seconds(1f)))
            action(ActionCueSpec.TimeScaleRamp(TimeRef.seconds(0.5f), targetScale = 0.25f, rampInSeconds = 0.05f, holdSeconds = 0.4f, rampOutSeconds = 0.05f))
        }

        var latestTimeScale = 1f
        var latestHud: Boolean? = null
        val directorSystem = DirectorSystem(
            audioPlayer = mixer,
            audioClips = mapOf("audio/theme.wav" to musicClip, "audio/boom.wav" to sfxClip),
            overrideScript = script,
            onTimeScaleChanged = { latestTimeScale = it },
            onHudVisibilityChanged = { latestHud = it },
        )

        var sawSlowMo = false
        var sawPointLights = 0
        repeat(60) {
            directorSystem.update(world, 1f / 60f)
            mixer.advance(1f / 60f, sfxPitchScale = directorSystem.activePlayer?.currentAudioPitchScale ?: 1f)
            if (latestTimeScale < 0.5f) sawSlowMo = true
            var pts = 0
            world.queryEach(Light::class) { _, light ->
                if (light.type == Light.Type.Point && light.intensity > 0f) pts++
            }
            if (pts > sawPointLights) sawPointLights = pts
        }

        // Player camera stays ActiveCamera (preserving gameplay basis), while Director camera isPrimary & AudioListener!
        assertFalse(world.get<Camera>(playerCam)!!.isPrimary)
        assertTrue(world.has(playerCam, ActiveCamera::class))
        assertFalse(world.has(playerCam, AudioListener::class))

        var primaryDirCam: com.awakekt.awake.ecs.Entity? = null
        world.queryEach(Camera::class, Name::class) { entity, cam, name ->
            if (name.value == DirectorSystem.DIRECTOR_CAMERA_NAME && cam.isPrimary) primaryDirCam = entity
        }
        val dirEntity = assertNotNull(primaryDirCam, "Director camera must be primary")
        assertTrue(world.has(dirEntity, AudioListener::class), "AudioListener must track the directed camera")

        // PostProcessing uses Passthrough (never double-encoding PbrNeutral!).
        val pp = assertNotNull(world.get<PostProcessing>(dirEntity))
        val grade = pp.effects.filterIsInstance<ToneMappingGradeEffect>().single()
        assertEquals(ToneMappingMode.Passthrough, grade.mode)
        assertEquals(1.25f, grade.exposure, 1e-3f)

        assertTrue(sawSlowMo, "Slow-mo timeScale callback should have triggered")
        assertTrue(sawPointLights >= 2, "Expected HeroRimLight + LightFlash point lights, got $sawPointLights")
        assertEquals(false, latestHud)
        assertTrue(world.get<Fog>(sun)!!.density > 0.005f, "Fog ramp should increase density")
        assertTrue(world.get<Transform>(hero)!!.position.z < -1.5f, "ActorMove cue should move Hero toward -Z")
        assertTrue(mixer.rmsEnergy() > 0.05f, "OfflineAudioMixer should capture mixed music + 3D SFX")

        // Teardown restores original primary camera, AudioListener, and destroys pooled dynamic lights.
        directorSystem.releaseDirectorTakeover(world)
        assertTrue(world.get<Camera>(playerCam)!!.isPrimary)
        assertTrue(world.has(playerCam, AudioListener::class))
        assertEquals(1f, latestTimeScale)
        var remainingPointLights = 0
        world.queryEach(Light::class) { _, light -> if (light.type == Light.Type.Point) remainingPointLights++ }
        assertEquals(0, remainingPointLights)
    }

    @Test
    fun inputReplayPreservesGameplayMovementTrajectoryWhileDirectorOrbits() {
        val input = Input()
        val recorder = InputRecorder()
        input.setKeyDown(Key.W, true)
        repeat(30) {
            recorder.recordFrame(input.updateSnapshot(), gameplayCameraYaw = 0f, gameplayCameraPitch = -0.3f)
        }
        val take = recorder.finish()

        // Helper running one world with W held; withDirector orbits camera 180 degrees to the opposite side!
        fun runWorld(withDirector: Boolean): Vec3f {
            val world = World()
            val hero = world.create()
            world.add(hero, Name("Hero"))
            world.add(hero, Transform(position = Vec3f(0f, 0f, 0f)))
            world.add(hero, MovementControl())

            val gameCam = world.create()
            world.add(gameCam, Camera(lens = Lens.perspective(eye = Vec3f(0f, 2f, 5f), center = Vec3f(0f, 0f, 0f)), isPrimary = true))
            world.add(gameCam, CameraRig().apply { mode = CameraMode.ThirdPerson; yaw = 0f })
            world.add(gameCam, ActiveCamera())

            val replay = InputReplaySession(take)
            val replaySys = InputReplaySystem(replay)
            val inputSys = PlayerInputSystem { replay.currentGameplayInput() }
            val moveSys = MatrixRelativeMovementSystem(speed = 6f)

            val director = if (withDirector) {
                DirectorSystem(
                    overrideScript = directorScript("orbit_180") {
                        shot("flip") {
                            seconds(2f)
                            rig = ShotRigSpec.Static(eye = DirectorVec3(0f, 2f, -10f), center = DirectorVec3(0f, 0f, 0f))
                        }
                    },
                )
            } else {
                null
            }

            repeat(30) {
                replaySys.update(world, 1f / 60f)
                inputSys.update(world, 1f / 60f)
                moveSys.update(world, 1f / 60f)
                director?.update(world, 1f / 60f)
            }
            return world.get<Transform>(hero)!!.position
        }

        val baseline = runWorld(withDirector = false)
        val directed = runWorld(withDirector = true)

        assertTrue(baseline.z < -2f, "Hero should move forward along -Z, got $baseline")
        assertEquals(baseline.x, directed.x, 1e-4f)
        assertEquals(baseline.y, directed.y, 1e-4f)
        assertEquals(baseline.z, directed.z, 1e-4f)
    }
}
