/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.math

/**
 * A reusable policy for deriving depth planes from a caller-provided visible distance.
 *
 * @property preferredNear The preferred distance to the near clipping plane.
 * @property farPaddingMultiplier Multiplier applied to required far distance to ensure padding beyond scene elements.
 * @property maxDepthRangeRatio Maximum allowed ratio between far and near plane distances to avoid precision loss.
 */
data class CameraClipPreset(
    val preferredNear: Float,
    val farPaddingMultiplier: Float,
    val maxDepthRangeRatio: Float,
) {
    init {
        require(preferredNear > 0f) { "preferredNear must be positive." }
        require(farPaddingMultiplier >= 1f) { "farPaddingMultiplier must be at least one." }
        require(maxDepthRangeRatio > 1f) { "maxDepthRangeRatio must exceed one." }
    }

    /**
     * Standard predefined camera clip presets.
     */
    companion object {
        /** General scene-view policy: room beyond the target without excessive depth precision loss. */
        val Standard = CameraClipPreset(
            preferredNear = 0.1f,
            farPaddingMultiplier = 2f,
            maxDepthRangeRatio = 10_000f,
        )
    }
}

/**
 * A lens's depth interval, derived without choosing a graphics backend's clip convention.
 *
 * @property near Distance to the near clipping plane.
 * @property far Distance to the far clipping plane.
 */
data class CameraClipRange(
    val near: Float,
    val far: Float,
) {
    /**
     * Factory methods for calculating camera clip ranges.
     */
    companion object {
        /**
         * Derives valid clip planes for a scene whose farthest visible point is [requiredFar]
         * away from the camera. The caller decides how that distance is measured.
         *
         * @param requiredFar The maximum distance to visible geometry that must be contained.
         * @param preset The clipping preset configuration to use.
         * @return The computed [CameraClipRange].
         */
        fun forRequiredFar(
            requiredFar: Float,
            preset: CameraClipPreset = CameraClipPreset.Standard,
        ): CameraClipRange {
            require(requiredFar >= 0f) { "requiredFar must be non-negative." }

            val far = maxOf(
                requiredFar * preset.farPaddingMultiplier,
                preset.preferredNear * MINIMUM_DEPTH_RANGE_RATIO,
            )
            val near = maxOf(preset.preferredNear, far / preset.maxDepthRangeRatio)
            return CameraClipRange(near = near, far = far)
        }

        private const val MINIMUM_DEPTH_RANGE_RATIO = 2f
    }
}
