/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.animation

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import kotlin.math.sqrt

/**
 * One [skeleton]'s current working pose -- a mutable per-bone TRS buffer, seeded from bind pose.
 * [sample] overwrites it from an [AnimationClip]; [blend] mixes it with another pose; [jointPalette]
 * turns it into the flat joint-matrix array a skinned material's uniform buffer expects.
 *
 * A pose is independent, cheap-to-construct state -- unlike this type's glTF-coupled predecessor
 * (`SkinnedAnimationPlayer`, one shared TRS buffer per loaded scene), nothing stops a caller from
 * holding several `AnimationPose`s over the same [Skeleton] at once (independently-timed crowd
 * instances, or the outgoing/current pair [AnimationCrossfade] needs for blending).
 *
 * It keeps its TRS as flat float arrays and builds the palette in a scratch buffer of its own, so
 * posing a model every frame allocates nothing but the palette [jointPalette] returns, and nothing
 * at all through the overload that writes into a caller's array.
 *
 * @param skeleton Target skeleton hierarchy defining bone indices and bind poses.
 */
class AnimationPose(private val skeleton: Skeleton) {
    private val bones = skeleton.bones
    private val translation = FloatArray(bones.size * VEC)
    private val rotation = FloatArray(bones.size * QUAT)
    private val scale = FloatArray(bones.size * VEC)

    /** Each bone's global transform while [jointPalette] builds, 16 column-major floats a bone. */
    private val globals = FloatArray(bones.size * MATRIX)
    private val reached = BooleanArray(bones.size)
    private val local = Mat4()
    private val localTranslation = Vec3f()
    private val localRotation = Quat()
    private val localScale = Vec3f()

    init {
        resetToBindPose()
    }

    /**
     * Restores every channel to the skeleton's authored bind pose. Whole-clip playback calls
     * this before sampling so a component the next clip does not animate cannot leak from the
     * preceding clip. Layered animation deliberately does not call this method.
     */
    fun resetToBindPose() {
        for (i in bones.indices) {
            val bone = bones[i]
            writeVec(translation, i, bone.translation)
            writeQuat(rotation, i, bone.rotation)
            writeVec(scale, i, bone.scale)
        }
    }

    /**
     * Returns a copy of this bone's current local translation -- whatever the last [sample], [blend]
     * or [setBoneTransform] left there, or its bind pose if nothing has written to it yet.
     *
     * @param bone Index of the bone within the skeleton.
     * @return Local translation vector for the specified bone.
     */
    fun boneTranslation(bone: Int): Vec3f =
        Vec3f(translation[bone * VEC], translation[bone * VEC + 1], translation[bone * VEC + 2])

    /**
     * Returns a copy of this bone's current local rotation; see [boneTranslation].
     *
     * @param bone Index of the bone within the skeleton.
     * @return Local rotation quaternion for the specified bone.
     */
    fun boneRotation(bone: Int): Quat =
        Quat(rotation[bone * QUAT], rotation[bone * QUAT + 1], rotation[bone * QUAT + 2], rotation[bone * QUAT + 3])

    /**
     * Overwrites one bone's local translation and rotation, leaving its scale alone.
     *
     * The seam for a pose driven by something other than a clip -- a ragdoll, an IK solver, a
     * procedural head-turn. Scale is untouched because none of those produce one: a rigid body has
     * no scale to report, and overwriting it with 1 would discard whatever the mesh was authored
     * with.
     *
     * @param bone Index of the target bone.
     * @param translation New local translation vector.
     * @param rotation New local rotation quaternion.
     */
    fun setBoneTransform(bone: Int, translation: Vec3f, rotation: Quat) {
        writeVec(this.translation, bone, translation)
        writeQuat(this.rotation, bone, rotation)
    }

    /**
     * Samples [clip] at [timeSeconds] into this pose's working TRS arrays -- a bone with no
     * channel targeting it keeps whatever TRS it already had (its authored bind pose, until/
     * unless a previous [sample] call overwrote it).
     *
     * @param clip Animation clip containing channels to sample.
     * @param timeSeconds Playback timestamp in seconds within the clip.
     */
    fun sample(clip: AnimationClip, timeSeconds: Float) {
        val channels = clip.channels
        for (c in channels.indices) {
            val channel = channels[c]
            when (channel.property) {
                AnimationProperty.Translation -> sampleInto(channel.sampler, timeSeconds, translation, channel.targetBone * VEC, VEC)
                AnimationProperty.Scale -> sampleInto(channel.sampler, timeSeconds, scale, channel.targetBone * VEC, VEC)
                AnimationProperty.Rotation -> sampleInto(channel.sampler, timeSeconds, rotation, channel.targetBone * QUAT, QUAT)
            }
        }
    }

    /**
     * Overwrites this pose's working TRS arrays with [other]'s -- used to reset a scratch pose
     * to a frozen base before blending it toward a live one each frame, so repeated blending
     * doesn't compound (see [AnimationCrossfade]).
     *
     * @param other Source pose whose local transforms will be copied into this pose.
     */
    fun copyFrom(other: AnimationPose) {
        other.translation.copyInto(translation)
        other.rotation.copyInto(rotation)
        other.scale.copyInto(scale)
    }

    /**
     * Mutates this pose in place to become the interpolation of (this, [other]) at [weight] --
     * `0` leaves this pose unchanged, `1` adopts [other]'s pose exactly. Per-bone: translation/
     * scale lerp componentwise, rotation nlerps (never a naive per-component lerp, which would
     * visibly cut corners on a large rotation instead of sweeping through it). Both poses must
     * share the same [Skeleton] -- this is the caller's responsibility, same as every other
     * method here that assumes bone-index alignment.
     *
     * @param other Target pose to interpolate toward.
     * @param weight Blend factor between 0.0 (keep this pose) and 1.0 (adopt target pose).
     */
    fun blend(other: AnimationPose, weight: Float) {
        for (i in translation.indices) {
            translation[i] += (other.translation[i] - translation[i]) * weight
            scale[i] += (other.scale[i] - scale[i]) * weight
        }
        for (bone in bones.indices) {
            nlerpInPlace(rotation, bone * QUAT, other.rotation, bone * QUAT, weight)
        }
    }

    /**
     * Every joint's current global transform (walking [skeleton]'s bone hierarchy from its own
     * roots, multiplying local transforms along the way) times that joint's own inverse-bind
     * matrix, flattened into `16 * skin.joints.size` floats -- one 4x4 column-major matrix per
     * joint, the layout a skinned material's `jointPalette` uniform array expects.
     *
     * @param skin Mesh skin binding providing joint bone indices and inverse bind matrices.
     * @return Flattened array of column-major joint matrices for GPU vertex skinning.
     */
    fun jointPalette(skin: Skin): FloatArray = jointPalette(skin, FloatArray(skin.joints.size * MATRIX))

    /**
     * [jointPalette] written into [into], which must hold `16 * skin.joints.size` floats: for a caller
     * that poses the same model every frame and keeps its palette.
     *
     * @param skin Mesh skin binding providing joint bone indices and inverse bind matrices.
     * @param into Array the joint matrices are written into.
     * @return [into].
     */
    fun jointPalette(skin: Skin, into: FloatArray): FloatArray {
        require(into.size >= skin.joints.size * MATRIX) { "A palette for ${skin.joints.size} joints needs ${skin.joints.size * MATRIX} floats." }
        reached.fill(false)
        val roots = skeleton.roots
        for (r in roots.indices) visit(roots[r], parentAt = -1)
        val joints = skin.joints
        for (i in joints.indices) {
            val bone = joints[i]
            val inverseBind = skin.inverseBindMatrices[i].data
            // A joint bone unreachable from any skeleton root has no global transform -- shouldn't
            // happen for a well-formed skeleton (every joint is part of the hierarchy); identity is
            // a harmless fallback rather than a crash if one ever is.
            if (bone in bones.indices && reached[bone]) {
                multiply(globals, bone * MATRIX, inverseBind, into, i * MATRIX)
            } else {
                inverseBind.copyInto(into, i * MATRIX)
            }
        }
        return into
    }

    /** Writes [bone]'s global transform, its parent's at [parentAt] (-1 for none) times its local one, then its children's. */
    private fun visit(bone: Int, parentAt: Int) {
        if (reached[bone]) return
        reached[bone] = true
        val at = bone * MATRIX
        val authored = bones[bone].matrix
        val localData = if (authored != null) {
            authored.data
        } else {
            localTranslation.set(translation[bone * VEC], translation[bone * VEC + 1], translation[bone * VEC + 2])
            localRotation.x = rotation[bone * QUAT]
            localRotation.y = rotation[bone * QUAT + 1]
            localRotation.z = rotation[bone * QUAT + 2]
            localRotation.w = rotation[bone * QUAT + 3]
            localScale.set(scale[bone * VEC], scale[bone * VEC + 1], scale[bone * VEC + 2])
            local.setTrs(localTranslation, localRotation, localScale).data
        }
        if (parentAt < 0) localData.copyInto(globals, at) else multiply(globals, parentAt, localData, globals, at)
        val children = bones[bone].children
        for (c in children.indices) visit(children[c], at)
    }
}

/**
 * Writes [sampler]'s value at [timeSeconds] -- its enclosing keyframe pair lerped, or nlerped
 * for a four-component rotation -- as [count] floats at [at] in [out].
 */
private fun sampleInto(sampler: AnimationSampler, timeSeconds: Float, out: FloatArray, at: Int, count: Int) {
    val times = sampler.times
    val values = sampler.values
    val comps = sampler.componentsPerKeyframe
    when {
        times.isEmpty() -> out.fill(0f, at, at + count)
        timeSeconds <= times.first() -> values.copyInto(out, at, 0, count)
        timeSeconds >= times.last() -> values.copyInto(out, at, (times.size - 1) * comps, (times.size - 1) * comps + count)
        else -> {
            val hi = firstKeyAtOrAfter(times, timeSeconds)
            val lo = hi - 1
            val t = (timeSeconds - times[lo]) / (times[hi] - times[lo])
            if (comps == QUAT) {
                values.copyInto(out, at, lo * comps, lo * comps + QUAT)
                nlerpInPlace(out, at, values, hi * comps, t)
            } else {
                for (i in 0 until count) {
                    val a = values[lo * comps + i]
                    out[at + i] = a + (values[hi * comps + i] - a) * t
                }
            }
        }
    }
}

private fun writeVec(out: FloatArray, bone: Int, value: Vec3f) {
    out[bone * VEC] = value.x
    out[bone * VEC + 1] = value.y
    out[bone * VEC + 2] = value.z
}

private fun writeQuat(out: FloatArray, bone: Int, value: Quat) {
    out[bone * QUAT] = value.x
    out[bone * QUAT + 1] = value.y
    out[bone * QUAT + 2] = value.z
    out[bone * QUAT + 3] = value.w
}

/** The first index from 1 whose key time is at or after [time]; [time] lies strictly inside [times]. */
private fun firstKeyAtOrAfter(times: FloatArray, time: Float): Int {
    var low = 1
    var high = times.size - 1
    while (low < high) {
        val mid = (low + high) ushr 1
        if (times[mid] < time) low = mid + 1 else high = mid
    }
    return low
}

/** [Quat.nlerp] from the quaternion at [at] in [out] toward the one at [bAt] in [b], written back at [at]. */
private fun nlerpInPlace(out: FloatArray, at: Int, b: FloatArray, bAt: Int, t: Float) {
    val ax = out[at]
    val ay = out[at + 1]
    val az = out[at + 2]
    val aw = out[at + 3]
    val sign = if (ax * b[bAt] + ay * b[bAt + 1] + az * b[bAt + 2] + aw * b[bAt + 3] < 0f) -1f else 1f
    val rx = ax + (sign * b[bAt] - ax) * t
    val ry = ay + (sign * b[bAt + 1] - ay) * t
    val rz = az + (sign * b[bAt + 2] - az) * t
    val rw = aw + (sign * b[bAt + 3] - aw) * t
    val length = sqrt(rx * rx + ry * ry + rz * rz + rw * rw)
    if (length == 0f) {
        out[at] = 0f
        out[at + 1] = 0f
        out[at + 2] = 0f
        out[at + 3] = 1f
    } else {
        out[at] = rx / length
        out[at + 1] = ry / length
        out[at + 2] = rz / length
        out[at + 3] = rw / length
    }
}

/** The column-major product of the matrix at [aAt] in [a] and the one [b] holds, written at [outAt] in [out]. */
private fun multiply(a: FloatArray, aAt: Int, b: FloatArray, out: FloatArray, outAt: Int) {
    for (col in 0 until 4) {
        for (row in 0 until 4) {
            var sum = 0f
            for (k in 0 until 4) sum += a[aAt + k * 4 + row] * b[col * 4 + k]
            out[outAt + col * 4 + row] = sum
        }
    }
}

private const val VEC = 3
private const val QUAT = 4
private const val MATRIX = 16
