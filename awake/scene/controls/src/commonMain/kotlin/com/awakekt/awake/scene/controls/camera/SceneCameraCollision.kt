/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls.camera

import com.awakekt.awake.core.camera.CameraCollision
import com.awakekt.awake.core.camera.OccluderFade
import com.awakekt.awake.core.schema.PropertyRange
import com.awakekt.awake.scene.document.SceneValidationIssue
import kotlinx.serialization.Serializable

/**
 * A third-person `camera_rig`'s collision, as authored in a scene: `camera_rig.collision`. Mapped
 * field for field onto [CameraCollision]; see it for what each one does.
 *
 * @property ground Whether the eye is kept [groundMargin] above the ground.
 * @property terrain Whether the eye is pulled in front of terrain between the pivot and it.
 * @property groundMargin Least height of the eye above the ground, in world units.
 * @property radius Clearance kept between the line from the pivot to the eye and what blocks it, in world units.
 * @property easeOutSeconds Half-life of the ease back out once the way clears, in seconds.
 * @property obstacleLayers Physics layers whose bodies also pull the eye in; empty, the default, sweeps none.
 */
@Serializable
data class SceneCameraCollision(
    val ground: Boolean = true,
    val terrain: Boolean = true,
    @PropertyRange(min = 0.0) val groundMargin: Float = CameraCollision.DEFAULT_GROUND_MARGIN,
    @PropertyRange(min = 0.0) val radius: Float = CameraCollision.DEFAULT_RADIUS,
    @PropertyRange(min = 0.0) val easeOutSeconds: Float = CameraCollision.DEFAULT_EASE_OUT_SECONDS,
    val obstacleLayers: List<Int> = emptyList(),
) {
    /** What is wrong with these values, reported under [path]. */
    fun validate(path: String): List<SceneValidationIssue> = buildList {
        if (groundMargin < 0f) add(SceneValidationIssue(path, "camera_rig.collision.groundMargin must not be negative"))
        if (radius < 0f) add(SceneValidationIssue(path, "camera_rig.collision.radius must not be negative"))
        if (easeOutSeconds < 0f) add(SceneValidationIssue(path, "camera_rig.collision.easeOutSeconds must not be negative"))
        if (obstacleLayers.any { it < 0 }) add(SceneValidationIssue(path, "camera_rig.collision.obstacleLayers must not be negative"))
    }
}

/** The capability's setting for this authored collision. */
internal fun SceneCameraCollision.toCameraCollision(): CameraCollision = CameraCollision(
    ground = ground,
    terrain = terrain,
    groundMargin = groundMargin,
    radius = radius,
    easeOutSeconds = easeOutSeconds,
    obstacleLayers = obstacleLayers,
)

/** The authored form of [CameraCollision], for a scene saved from a running world. */
internal fun CameraCollision.toScene(): SceneCameraCollision = SceneCameraCollision(
    ground = ground,
    terrain = terrain,
    groundMargin = groundMargin,
    radius = radius,
    easeOutSeconds = easeOutSeconds,
    obstacleLayers = obstacleLayers,
)

/**
 * A third-person `camera_rig`'s occluder fading, as authored in a scene: `camera_rig.occluderFade`.
 * Mapped field for field onto [OccluderFade]; see it for what each one does.
 *
 * @property opacity Opacity an occluder fades to, from 0 to 1.
 * @property fadeSeconds Half-life of the fade, both ways, in seconds.
 */
@Serializable
data class SceneOccluderFade(
    @PropertyRange(min = 0.0, max = 1.0) val opacity: Float = OccluderFade.DEFAULT_OPACITY,
    @PropertyRange(min = 0.0) val fadeSeconds: Float = OccluderFade.DEFAULT_FADE_SECONDS,
) {
    /** What is wrong with these values, reported under [path]. */
    fun validate(path: String): List<SceneValidationIssue> = buildList {
        if (opacity !in 0f..1f) add(SceneValidationIssue(path, "camera_rig.occluderFade.opacity must be within 0..1"))
        if (fadeSeconds < 0f) add(SceneValidationIssue(path, "camera_rig.occluderFade.fadeSeconds must not be negative"))
    }
}

/** The capability's setting for this authored fade. */
internal fun SceneOccluderFade.toOccluderFade(): OccluderFade = OccluderFade(opacity = opacity, fadeSeconds = fadeSeconds)

/** The authored form of [OccluderFade]. */
internal fun OccluderFade.toScene(): SceneOccluderFade = SceneOccluderFade(opacity = opacity, fadeSeconds = fadeSeconds)
