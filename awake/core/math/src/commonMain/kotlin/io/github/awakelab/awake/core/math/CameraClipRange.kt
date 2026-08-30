/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.core.math

/** A reusable policy for deriving depth planes from a caller-provided visible distance. */
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

    companion object {
        /** General scene-view policy: room beyond the target without excessive depth precision loss. */
        val Standard = CameraClipPreset(
            preferredNear = 0.1f,
            farPaddingMultiplier = 2f,
            maxDepthRangeRatio = 10_000f,
        )
    }
}

/** A lens's depth interval, derived without choosing a graphics backend's clip convention. */
data class CameraClipRange(
    val near: Float,
    val far: Float,
) {
    companion object {
        /**
         * Derives valid clip planes for a scene whose farthest visible point is [requiredFar]
         * away from the camera. The caller decides how that distance is measured.
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
