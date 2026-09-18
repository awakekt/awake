/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.math

import kotlin.math.PI

/**
 * Orientation vectors for a single cubemap face.
 *
 * @property direction The target view direction vector.
 * @property up The camera up vector for the face coordinate system.
 */
data class CubemapFaceOrientation(
    val direction: Vec3f,
    val up: Vec3f,
)

/**
 * Standard 6 cubemap face orientations and camera lens factory.
 *
 * Face index order conforms to Vulkan, WebGPU, and OpenGL cubemap layer conventions:
 * - 0: +X (Right)
 * - 1: -X (Left)
 * - 2: +Y (Top)
 * - 3: -Y (Bottom)
 * - 4: +Z (Back)
 * - 5: -Z (Front)
 */
object CubemapFaces {
    const val COUNT: Int = 6

    val Faces: Array<CubemapFaceOrientation> = arrayOf(
        CubemapFaceOrientation(Vec3f.RIGHT, Vec3f.DOWN),
        CubemapFaceOrientation(Vec3f.LEFT, Vec3f.DOWN),
        CubemapFaceOrientation(Vec3f.UP, Vec3f.FORWARD),
        CubemapFaceOrientation(Vec3f.DOWN, Vec3f.BACK),
        CubemapFaceOrientation(Vec3f.BACK, Vec3f.DOWN),
        CubemapFaceOrientation(Vec3f.FORWARD, Vec3f.DOWN),
    )

    /** Returns the canonical orientation for [faceIndex] in `0..5`. */
    fun orientation(faceIndex: Int): CubemapFaceOrientation =
        Faces[faceIndex.coerceIn(0, COUNT - 1)]

    /**
     * Builds a square-aspect 90° FOV [Lens] centered at [eye] pointing along [faceIndex].
     */
    fun lens(
        eye: Vec3f,
        faceIndex: Int,
        near: Float = 0.05f,
        far: Float = 100f,
    ): Lens {
        val face = orientation(faceIndex)
        return Lens(
            eye = eye,
            center = eye + face.direction,
            up = face.up,
            fovYRadians = (PI / 2.0).toFloat(),
            near = near,
            far = far,
        )
    }
}
