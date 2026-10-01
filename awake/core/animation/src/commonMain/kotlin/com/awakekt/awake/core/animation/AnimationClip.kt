/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.animation

/**
 * Which TRS component a channel drives -- a closed enum instead of an unchecked source-format
 * string (e.g. glTF's `"translation"/"rotation"/"scale"`), so [AnimationPose.sample]'s dispatch
 * can't silently no-op on a typo or an unrecognized value.
 */
enum class AnimationProperty {
    /** Drives local 3D translation coordinates. */
    Translation,

    /** Drives local rotation represented as a unit quaternion. */
    Rotation,

    /** Drives local 3D scale factors. */
    Scale,
}

/**
 * One channel's keyframe data -- [times] is strictly increasing (seconds), [values] is
 * flattened to `times.size * componentsPerKeyframe` floats (3 for translation/scale, 4 for a
 * rotation quaternion). Only linear interpolation between keyframes is sampled -- see
 * [AnimationPose.sample]'s own doc comment for why step/cubic-spline interpolation modes aren't
 * carried here.
 *
 * @property times Strictly increasing keyframe timestamps in seconds.
 * @property values Flattened array of keyframe component values corresponding to [times].
 * @property componentsPerKeyframe Number of float components per keyframe (e.g., 3 for translation/scale, 4 for rotation).
 */
data class AnimationSampler(
    val times: FloatArray,
    val values: FloatArray,
    val componentsPerKeyframe: Int,
)

/**
 * Targets one [Bone] (by index into the clip's [Skeleton]) with one [property]'s worth of
 * keyframes.
 *
 * @property targetBone Index of the target bone within the skeleton's bone hierarchy.
 * @property property The transform component (translation, rotation, or scale) driven by this channel.
 * @property sampler Keyframe timing and value sampler for this channel.
 */
data class AnimationChannel(
    val targetBone: Int,
    val property: AnimationProperty,
    val sampler: AnimationSampler,
)

/**
 * A named, format-neutral animation clip -- [name] is the source asset's own clip name when it
 * had one (an importer that can't recover a name passes `null`). [duration] is the last keyframe
 * time across every channel, i.e. the clip's own length in seconds -- a property of the clip
 * itself, not of whatever is currently playing it.
 *
 * @property name Optional human-readable name of the clip, or `null` if unnamed.
 * @property channels List of animation channels driving target bones in the skeleton.
 */
data class AnimationClip(
    val name: String?,
    val channels: List<AnimationChannel>,
) {
    /** Total duration of this animation clip in seconds, determined by the latest keyframe timestamp across all channels. */
    val duration: Float
        get() = channels.maxOfOrNull { it.sampler.times.lastOrNull() ?: 0f } ?: 0f
}
