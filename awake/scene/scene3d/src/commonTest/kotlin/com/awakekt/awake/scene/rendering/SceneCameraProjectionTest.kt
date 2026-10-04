/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.scene.rendering.camera.CameraBinding.toComponent
import com.awakekt.awake.scene.rendering.camera.CameraBinding.toSceneComponent
import com.awakekt.awake.scene.rendering.camera.SceneCamera
import kotlin.math.PI
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A scene camera can be orthographic, which is what a 2D game draws through: `Lens` already builds the
 * matrix, so these cover the component carrying the choice into the lens and back, and the data being
 * checked.
 */
class SceneCameraProjectionTest {

    @Test
    fun aCameraIsPerspectiveUnlessTheSceneSaysOtherwise() {
        val camera = SceneCamera().toComponent()

        assertEquals(Lens.Projection.Perspective, camera.lens.projection)
        assertEquals(SceneCamera.Projection.Perspective, SceneCamera().projection)
    }

    @Test
    fun anOrthographicSceneCameraMakesAnOrthographicLens() {
        val lens = SceneCamera(projection = SceneCamera.Projection.Orthographic, orthoHalfHeight = 7f).toComponent().lens

        assertEquals(Lens.Projection.Orthographic, lens.projection)
        assertEquals(7f, lens.orthoHalfHeight)
    }

    @Test
    fun theProjectionSurvivesTheRoundTripBothWays() {
        listOf(
            SceneCamera(),
            SceneCamera(projection = SceneCamera.Projection.Orthographic, orthoHalfHeight = 3.5f),
            SceneCamera(projection = SceneCamera.Projection.Perspective, orthoHalfHeight = 12f),
        ).forEach { scene ->
            val exported = scene.toComponent().toSceneComponent()

            assertEquals(scene.projection, exported.projection)
            assertEquals(scene.orthoHalfHeight, exported.orthoHalfHeight)
        }
    }

    @Test
    fun theLensBuildsTheSameMatrixAsAHandBuiltOrthographicOne() {
        val scene = SceneCamera(
            eye = com.awakekt.awake.scene.document.SceneVec3(0f, 0f, 10f),
            projection = SceneCamera.Projection.Orthographic,
            orthoHalfHeight = 4f,
            near = 0.5f,
            far = 50f,
        )
        val byHand = Lens(
            eye = Vec3f(0f, 0f, 10f),
            center = Vec3f(0f, 0f, 0f),
            up = Vec3f(0f, 1f, 0f),
            fovYRadians = 1f,
            near = 0.5f,
            far = 50f,
        ).also {
            it.projection = Lens.Projection.Orthographic
            it.orthoHalfHeight = 4f
        }

        val fromScene = scene.toComponent().lens.viewProjectionMatrix(16f / 9f, ClipSpace.WebGpu)
        val expected = byHand.viewProjectionMatrix(16f / 9f, ClipSpace.WebGpu)

        assertContentEquals(expected.data, fromScene.data, "the scene camera must build exactly the orthographic matrix")
    }

    @Test
    fun anOrthographicViewIgnoresTheFieldOfView() {
        fun matrix(fovYDegrees: Float) = SceneCamera(
            fovYDegrees = fovYDegrees,
            projection = SceneCamera.Projection.Orthographic,
        ).toComponent().lens.viewProjectionMatrix(1f, ClipSpace.WebGpu).data

        assertContentEquals(matrix(20f), matrix(120f))
    }

    @Test
    fun theDefaultOrthographicSizeFramesWhatTheDefaultPerspectiveLensFrames() {
        // 5 units away at a 45 degree field of view: half the view height is 5 * tan(22.5 degrees).
        val expected = 5f * tan(45f * (PI.toFloat() / 180f) / 2f)

        assertEquals(expected, SceneCamera().orthoHalfHeight, 1e-4f)
        assertEquals(2.07f, SceneCamera().orthoHalfHeight, 0.005f, "the documented default, about 2.07")
    }

    @Test
    fun anOrthographicCameraNeedsAPositiveFiniteHalfHeight() {
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY).forEach { bad ->
            val problems = SceneCamera(projection = SceneCamera.Projection.Orthographic, orthoHalfHeight = bad)
                .validate("camera").map { it.message }

            assertTrue(problems.any { "orthoHalfHeight" in it }, "$bad should be refused: $problems")
        }
    }

    @Test
    fun aWellFormedOrthographicCameraHasNoProblems() {
        val camera = SceneCamera(projection = SceneCamera.Projection.Orthographic, orthoHalfHeight = 6f)

        assertEquals(emptyList(), camera.validate("camera").map { it.message })
    }
}
