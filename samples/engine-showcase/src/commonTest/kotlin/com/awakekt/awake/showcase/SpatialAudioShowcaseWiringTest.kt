/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase

import com.awakekt.awake.core.audio.NoOpAudioPlayer
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.audio.AudioListener
import com.awakekt.awake.scene.audio.AudioSource
import com.awakekt.awake.scene.binding.Scene
import com.awakekt.awake.scene.binding.SceneNodeInstance
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.rendering.camera.Camera
import com.awakekt.awake.scene.rendering.camera.SceneCamera
import com.awakekt.awake.scene.runtime.DefaultSceneComponentResolvers
import com.awakekt.awake.showcase.examples.SpatialAudioExampleDriver
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Structural wiring test for the spatial audio showcase: verifies scene document contents,
 * beacons, audio system lifecycle, and emitter attachment / detachment.
 */
class SpatialAudioShowcaseWiringTest {
    init {
        DefaultSceneComponentResolvers.install()
    }

    private val showcase = EngineShowcases.single { it.id == "spatial-audio" }

    @BeforeTest
    fun setup() {
        SpatialAudioExampleDriver.dispose()
    }

    @AfterTest
    fun tearDown() {
        SpatialAudioExampleDriver.dispose()
    }

    @Test
    fun theSceneDocumentContainsBeaconsAndCamera() = runTest {
        val document = SceneLoader.loadFromResource(showcase.scenePath)
        val names = document.nodes.mapNotNull { it.name }

        assertTrue("camera" in names, "scene contains camera node")
        assertTrue("ground" in names, "scene contains ground node")
        assertTrue("listener-beacon" in names, "scene contains center listener beacon")
        assertTrue("left-beacon" in names, "scene contains left beacon")
        assertTrue("right-beacon" in names, "scene contains right beacon")
        assertTrue("left-light" in names, "scene contains left point light")
        assertTrue("right-light" in names, "scene contains right point light")

        val cameraNode = document.nodes.single { it.name == "camera" }
        val camera = cameraNode.components.filterIsInstance<SceneCamera>().single()
        assertTrue(camera.primary, "camera is marked primary")
    }

    @Test
    fun theShowcaseDefinesDriverLifecycleAndControls() {
        assertNotNull(showcase.driver, "showcase defines frame driver")
        assertNotNull(showcase.onActivated, "showcase defines activation hook")
        assertNotNull(showcase.onDeactivated, "showcase defines deactivation hook")
        assertNotNull(showcase.controls, "showcase defines controls panel")
    }

    @Test
    fun activationAttachesAudioListenerAndSpatialSources() {
        val world = World()
        val mockPlayer = NoOpAudioPlayer()
        com.awakekt.awake.core.audio.AudioPlayerFactory.provider = { mockPlayer }

        val cameraEntity = world.create()
        world.add(cameraEntity, Transform())
        val lens = Lens(
            eye = Vec3f(0f, 4f, 9f),
            center = Vec3f(0f, 0.5f, 0f),
            up = Vec3f(0f, 1f, 0f),
            fovYRadians = 0.785f,
            near = 0.1f,
            far = 100f,
        )
        world.add(cameraEntity, Camera(lens = lens, isPrimary = true))

        val leftEntity = world.create()
        world.add(leftEntity, Transform())
        val rightEntity = world.create()
        world.add(rightEntity, Transform())

        val mockScene = Scene(
            world = world,
            roots = listOf(
                SceneNodeInstance(name = "left-beacon", entity = leftEntity, children = emptyList()),
                SceneNodeInstance(name = "right-beacon", entity = rightEntity, children = emptyList()),
            ),
        )

        SpatialAudioExampleDriver.attach(mockScene, world)

        assertNotNull(world.get<AudioListener>(cameraEntity), "Camera receives AudioListener component")
        val leftSource = world.get<AudioSource>(leftEntity)
        val rightSource = world.get<AudioSource>(rightEntity)
        assertNotNull(leftSource, "Left beacon receives AudioSource")
        assertNotNull(rightSource, "Right beacon receives AudioSource")

        assertTrue(leftSource.isSpatial3D, "Left source is 3D spatial")
        assertTrue(rightSource.isSpatial3D, "Right source is 3D spatial")
        assertTrue(leftSource.autoPlay, "Left source auto-plays")
        assertTrue(rightSource.autoPlay, "Right source auto-plays")

        // Advance driver
        SpatialAudioExampleDriver.advance(world, 1f / 60f)

        // Detach cleans up sources
        SpatialAudioExampleDriver.detach(world)
        assertNull(world.get<AudioSource>(leftEntity), "Left source is removed on detach")
        assertNull(world.get<AudioSource>(rightEntity), "Right source is removed on detach")
    }

    @Test
    fun masterVolumeAndMuteUpdatePlayer() {
        val player = NoOpAudioPlayer()
        com.awakekt.awake.core.audio.AudioPlayerFactory.provider = { player }

        val world = World()
        val emptyScene = Scene(world = world, roots = emptyList())
        SpatialAudioExampleDriver.attach(emptyScene, world)

        SpatialAudioExampleDriver.masterVolume = 0.5f
        assertEquals(0.5f, player.masterVolume)

        SpatialAudioExampleDriver.isMuted = true
        assertEquals(0.0f, player.masterVolume)

        SpatialAudioExampleDriver.isMuted = false
        assertEquals(0.5f, player.masterVolume)
    }

    @Test
    fun soundClipsSynthesizeValidPcmData() {
        val left = SpatialAudioExampleDriver.leftToneClip
        val right = SpatialAudioExampleDriver.rightToneClip

        assertTrue(left.pcmBytes.isNotEmpty(), "left tone clip contains PCM bytes")
        assertTrue(right.pcmBytes.isNotEmpty(), "right tone clip contains PCM bytes")
        assertEquals(44100, left.sampleRate)
        assertEquals(44100, right.sampleRate)
    }
}
