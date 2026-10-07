/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.camera

import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.schema.PropertyRange
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import com.awakekt.awake.scene.document.SceneVec3
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Serializable camera component.
 *
 * @property eye World-space camera eye position.
 * @property center World-space look-at center point.
 * @property up Camera up vector.
 * @property fovYDegrees Vertical field of view in degrees.
 * @property near Near clipping plane distance.
 * @property far Far clipping plane distance.
 * @property primary Whether this camera acts as the primary viewport camera.
 * @property projection How the camera projects the scene. [Projection.Perspective] shrinks distant
 * things; [Projection.Orthographic] does not, so a 2D game's pixels and tiles keep their size
 * however far they sit along the view axis.
 * @property orthoHalfHeight Half the vertical world extent an orthographic view covers; the width
 * follows from it and the viewport's aspect ratio. Used only while [projection] is
 * [Projection.Orthographic], when [fovYDegrees] is ignored.
 * @property viewport Optional orthographic virtual viewport. When present, its dimensions replace
 * [orthoHalfHeight] for rendering; omitting it preserves the original camera behavior.
 */
@Serializable
@SerialName("camera")
data class SceneCamera(
    val eye: SceneVec3 = SceneVec3(0f, 0f, 5f),
    val center: SceneVec3 = SceneVec3(0f, 0f, 0f),
    val up: SceneVec3 = SceneVec3(0f, 1f, 0f),
    @PropertyRange(min = 0.0, max = 180.0, exclusiveMin = true, exclusiveMax = true) val fovYDegrees: Float = 60f,
    @PropertyRange(min = 0.0, exclusiveMin = true) val near: Float = 0.1f,
    val far: Float = 100f,
    val primary: Boolean = true,
    val projection: Projection = Projection.Perspective,
    @PropertyRange(min = 0.0, exclusiveMin = true) val orthoHalfHeight: Float = Lens.DEFAULT_ORTHO_HALF_HEIGHT,
    val viewport: SceneViewport? = null,
) : SceneComponent {
    /** How a [SceneCamera] projects the scene. */
    @Serializable
    enum class Projection {
        /** A cone of view: things shrink with distance. */
        @SerialName("perspective")
        Perspective,

        /** A box of view: things keep their size at any distance. */
        @SerialName("orthographic")
        Orthographic,
    }

    override val allowsMultiplePerNode: Boolean get() = false

    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        viewport?.let { view ->
            if (projection != Projection.Orthographic) {
                add(SceneValidationIssue(path, "camera.viewport requires orthographic projection"))
            }
            if (!view.width.isFinite() || view.width <= 0f) {
                add(SceneValidationIssue(path, "camera.viewport.width must be positive and finite"))
            }
            if (!view.height.isFinite() || view.height <= 0f) {
                add(SceneValidationIssue(path, "camera.viewport.height must be positive and finite"))
            }
        }
        if (near <= 0f) {
            add(SceneValidationIssue(path, "camera.near must be > 0"))
        }
        if (far <= near) {
            add(SceneValidationIssue(path, "camera.far must be greater than camera.near"))
        }
        if (fovYDegrees <= 0f || fovYDegrees >= 180f) {
            add(SceneValidationIssue(path, "camera.fovYDegrees must be between 0 and 180"))
        }
        if (!(orthoHalfHeight > 0f && orthoHalfHeight.isFinite())) {
            add(SceneValidationIssue(path, "camera.orthoHalfHeight must be a positive number"))
        }
    }
}
